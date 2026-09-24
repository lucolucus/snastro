package snastro.parlanti.applicazione.letture

import snastro.kernel.CampioniAudio
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.porte.ConfrontoImpronte
import snastro.parlanti.applicazione.porte.ConfrontoImpronteFinta
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import snastro.parlanti.applicazione.porte.DecodificatoreAudioFinta
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.applicazione.porte.EstrattoreImprontaFinta
import snastro.parlanti.applicazione.porte.Fascia
import snastro.parlanti.applicazione.porte.LettoreRegistrazione
import snastro.parlanti.applicazione.porte.LettoreRegistrazioneFinta
import snastro.parlanti.applicazione.porte.LettoreVoci
import snastro.parlanti.applicazione.porte.LettoreVociFinta
import snastro.parlanti.applicazione.porte.ParlanteRepositoryFinta
import snastro.parlanti.applicazione.porte.RegistrazioneVista
import snastro.parlanti.applicazione.porte.VoceVista
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.SorgenteImpronta
import snastro.parlanti.dominio.TipoParlante
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [Proposta] against the ports' fakes (D1): [INV-20], AC-170..AC-173, AC-308/AC-309, ADR 0017
 * AC-422/AC-423. The Voce under Proposta always lives in [REGISTRAZIONE]; a Candidato's stored
 * `ImprontaVocale` is sourced from a Voce of [STORICA] (a past Registrazione, like a real Galleria
 * print), so [EstrattoAudio] can resolve its `estratto`.
 */
class PropostaTest {

    @Test
    fun `AC-171 una Galleria vuota produce una lista di Candidati vuota`() {
        val ambiente = Ambiente()

        val proposta = assertNotNull(ambiente.api.perVoce(VOCE_1))

        assertEquals(VoceId(1), proposta.voceId)
        assertEquals(emptyList(), proposta.candidati)
    }

    @Test
    fun `una Registrazione senza Trascritto non genera una Proposta`() {
        val ambiente = Ambiente(lettoreVoci = LettoreVociFinta())

        assertNull(ambiente.api.perVoce(VOCE_1))
    }

    @Test
    fun `una Voce senza piu alcun intervallo non genera una Proposta`() {
        val vuota = mapOf(REGISTRAZIONE to listOf(VoceVista(VOCE_1, emptyList())))

        val ambiente = Ambiente(lettoreVoci = LettoreVociFinta(vuota))

        assertNull(ambiente.api.perVoce(VOCE_1))
    }

    @Test
    fun `INV-20 solo Parlanti attivi dello stesso Progetto con impronte del modello corrente sono Candidati`() {
        val ambiente = Ambiente()

        val incluso = unParlante("p-1", "Marco")
        incluso.registraImpronta(voceStorica(1), impronta(1f), "s1", MODELLO).atteso()
        ambiente.parlanti.salva(incluso).atteso()

        val eliminato = unParlante("p-2", "Elena")
        eliminato.registraImpronta(voceStorica(2), impronta(2f), "s2", MODELLO).atteso()
        eliminato.elimina().atteso()
        ambiente.parlanti.salva(eliminato).atteso()

        val senzaImpronte = unParlante("p-3", "Aldo")
        ambiente.parlanti.salva(senzaImpronte).atteso()

        val altroProgetto = unParlante("p-4", "Bea", progettoId = ProgettoId("altro-progetto"))
        altroProgetto.registraImpronta(voceStorica(4), impronta(4f), "s4", MODELLO).atteso()
        ambiente.parlanti.salva(altroProgetto).atteso()

        val nomi = assertNotNull(ambiente.api.perVoce(VOCE_1)).candidati.map { it.nome }

        assertEquals(listOf("Marco"), nomi)
    }

    @Test
    fun `INV-20 la vista espone voceId e per ogni Candidato parlanteId nome tipoParlante fascia ed estratto`() {
        val ambiente = Ambiente()
        val p = unParlante("p-1", "Marco")
        p.registraImpronta(voceStorica(1), impronta(1f), "s1", MODELLO).atteso()
        ambiente.parlanti.salva(p).atteso()

        val proposta = assertNotNull(ambiente.api.perVoce(VOCE_1))

        assertEquals(VoceId(1), proposta.voceId)
        val candidato = proposta.candidati.single()
        assertEquals(p.id, candidato.parlanteId)
        assertEquals("Marco", candidato.nome)
        assertEquals(TipoParlanteVista.RICORRENTE, candidato.tipoParlante)
        assertEquals(Fascia.NESSUNA, candidato.fascia, "impronta non programmata sul finto => NESSUNA di default")
        assertEquals(ambiente.estrattoAudio.estratto(voceStorica(1)), candidato.estratto)
    }

    @Test
    fun `INV-20 i Candidati sono ordinati ricorrenti prima poi per Fascia poi per Nome`() {
        val bruno = impronta(10f)
        val anna = impronta(11f)
        val elena = impronta(12f)
        val giulia = impronta(13f)
        val marco = impronta(14f)
        val programmate = mapOf(
            bruno to Fascia.FORTE,
            anna to Fascia.FORTE,
            elena to Fascia.DEBOLE,
            marco to Fascia.FORTE,
        )
        val ambiente = Ambiente(confronto = ConfrontoImpronteFinta(programmate))
        aggiungiCandidato(ambiente, Seed("p-1", "Bruno", TipoParlante.RICORRENTE, bruno, voceStorica(1)))
        aggiungiCandidato(ambiente, Seed("p-2", "Anna", TipoParlante.RICORRENTE, anna, voceStorica(2)))
        aggiungiCandidato(ambiente, Seed("p-3", "Marco", TipoParlante.OCCASIONALE, marco, voceStorica(3)))
        aggiungiCandidato(ambiente, Seed("p-4", "Elena", TipoParlante.RICORRENTE, elena, voceStorica(4)))
        aggiungiCandidato(ambiente, Seed("p-5", "Giulia", TipoParlante.RICORRENTE, giulia, voceStorica(5)))

        val nomi = assertNotNull(ambiente.api.perVoce(VOCE_1)).candidati.map { it.nome }

        val attesi = listOf("Anna", "Bruno", "Elena", "Giulia", "Marco")
        assertEquals(attesi, nomi, "ricorrenti prima, poi Fascia, poi Nome")
    }

    @Test
    fun `AC-170 la Fascia di un Candidato e la migliore tra le sue impronte, l estratto viene dalla migliore`() {
        val debole = impronta(20f)
        val forte = impronta(21f)
        val programmate = mapOf(debole to Fascia.DEBOLE, forte to Fascia.FORTE)
        val ambiente = Ambiente(confronto = ConfrontoImpronteFinta(programmate))
        val p = unParlante("p-1", "Marco")
        p.registraImpronta(voceStorica(1), debole, "s1", MODELLO).atteso() // impronta debole, prima
        p.registraImpronta(voceStorica(2), forte, "s2", MODELLO).atteso() // impronta forte, seconda
        ambiente.parlanti.salva(p).atteso()

        val candidato = assertNotNull(ambiente.api.perVoce(VOCE_1)).candidati.single()

        assertEquals(Fascia.FORTE, candidato.fascia, "la migliore delle due impronte")
        val attesoDallaMigliore = ambiente.estrattoAudio.estratto(voceStorica(2))
        assertEquals(attesoDallaMigliore, candidato.estratto, "l'estratto viene dalla migliore, non dalla prima")
    }

    @Test
    fun `AC-309 un Parlante con solo impronte di un altro modello non e Candidato`() {
        val ambiente = Ambiente()
        val p = unParlante("p-1", "Marco")
        p.registraImpronta(voceStorica(1), impronta(1f), "s1", "modello-vecchio").atteso()
        ambiente.parlanti.salva(p).atteso()

        val proposta = assertNotNull(ambiente.api.perVoce(VOCE_1))

        assertEquals(emptyList(), proposta.candidati)
    }

    @Test
    fun `AC-309 tra piu impronte solo quella del modello corrente e usata nel confronto`() {
        val delModelloVecchio = impronta(30f)
        val delModelloCorrente = impronta(31f)
        // se quella vecchia venisse considerata darebbe FORTE: deve essere ignorata (AC-309)
        val programmate = mapOf(delModelloVecchio to Fascia.FORTE, delModelloCorrente to Fascia.DEBOLE)
        val ambiente = Ambiente(confronto = ConfrontoImpronteFinta(programmate))
        val p = unParlante("p-1", "Marco")
        p.registraImpronta(voceStorica(1), delModelloVecchio, "s1", "modello-vecchio").atteso()
        p.registraImpronta(voceStorica(2), delModelloCorrente, "s2", MODELLO).atteso()
        ambiente.parlanti.salva(p).atteso()

        val candidato = assertNotNull(ambiente.api.perVoce(VOCE_1)).candidati.single()

        assertEquals(Fascia.DEBOLE, candidato.fascia)
        assertEquals(ambiente.estrattoAudio.estratto(voceStorica(2)), candidato.estratto)
    }

    @Test
    fun `AC-172 calcolare una Proposta non scrive nessuna riga impronta_vocale`() {
        val ambiente = Ambiente()
        val p = unParlante("p-1", "Marco")
        p.registraImpronta(voceStorica(1), impronta(1f), "s1", MODELLO).atteso()
        ambiente.parlanti.salva(p).atteso()
        val contoPrima = ambiente.parlanti.righeImpronte(p.id)

        ambiente.api.perVoce(VOCE_1)

        assertEquals(contoPrima, ambiente.parlanti.righeImpronte(p.id))
    }

    @Test
    fun `AC-173 invalida per Voce e per Registrazione forza il ricalcolo, senza resta la cache`() {
        val ambiente = Ambiente()
        val p1 = unParlante("p-1", "Marco")
        p1.registraImpronta(voceStorica(1), impronta(1f), "s1", MODELLO).atteso()
        ambiente.parlanti.salva(p1).atteso()
        assertEquals(1, assertNotNull(ambiente.api.perVoce(VOCE_1)).candidati.size)

        val p2 = unParlante("p-2", "Giulia")
        p2.registraImpronta(voceStorica(2), impronta(2f), "s2", MODELLO).atteso()
        ambiente.parlanti.salva(p2).atteso()
        val senzaInvalidare = assertNotNull(ambiente.api.perVoce(VOCE_1)).candidati.size
        assertEquals(1, senzaInvalidare, "senza invalidare resta la cache")

        ambiente.api.invalida(VOCE_1)
        val dopoInvalidaVoce = assertNotNull(ambiente.api.perVoce(VOCE_1)).candidati.size
        assertEquals(2, dopoInvalidaVoce, "invalida(VoceRef): un'Attribuzione")

        val p3 = unParlante("p-3", "Elena")
        p3.registraImpronta(voceStorica(3), impronta(3f), "s3", MODELLO).atteso()
        ambiente.parlanti.salva(p3).atteso()
        val ancoraDallaCache = assertNotNull(ambiente.api.perVoce(VOCE_1)).candidati.size
        assertEquals(2, ancoraDallaCache, "ancora dalla cache")

        ambiente.api.invalida(REGISTRAZIONE)
        val dopoInvalidaRegistrazione = assertNotNull(ambiente.api.perVoce(VOCE_1)).candidati.size
        assertEquals(3, dopoInvalidaRegistrazione, "invalida(RegistrazioneId): una Revisione o ImpronteRiallineate")
    }

    @Test
    fun `AC-308 l impronta transitoria e estratta da SorgenteImpronta, nessuna transazione e aperta`() {
        val intervalli = listOf(IntervalloMs(0, 20_000), IntervalloMs(25_000, 45_000), IntervalloMs(50_000, 50_500))
        lateinit var decodificatoreSpia: DecodificatoreAudioCheRegistra
        val ambiente = Ambiente(
            lettoreVoci = lettoreVociScenario(voce1Intervalli = intervalli),
            decodificatore = { uow ->
                DecodificatoreAudioCheRegistra(DecodificatoreAudioFinta(uow)).also { decodificatoreSpia = it }
            },
        )

        ambiente.api.perVoce(VOCE_1)

        val decodificati = decodificatoreSpia.chiamate.single().second
        assertEquals(SorgenteImpronta.di(intervalli).intervalli, decodificati)
        assertEquals(30_000L, decodificati.sumOf { it.fineMs - it.inizioMs })
        // il finto DecodificatoreAudio/EstrattoreImpronta lancerebbero se invocati con una transazione
        // aperta (ADR 0012 (b)): Proposta non tiene nessuna UnitaDiLavoro, quindi non puo mai aprirne una.
    }

    @Test
    fun `AC-422 calcolare la Proposta chiama estrai esattamente una volta, mai una per Candidato`() {
        lateinit var estrattoreSpia: EstrattoreImprontaCheConta
        val ambiente = Ambiente(
            estrattore = { uow ->
                EstrattoreImprontaCheConta(EstrattoreImprontaFinta(unitaDiLavoro = uow)).also { estrattoreSpia = it }
            },
        )
        val p1 = unParlante("p-1", "Marco")
        p1.registraImpronta(voceStorica(1), impronta(1f), "s1", MODELLO).atteso()
        val p2 = unParlante("p-2", "Giulia")
        p2.registraImpronta(voceStorica(2), impronta(2f), "s2", MODELLO).atteso()
        ambiente.parlanti.salva(p1).atteso()
        ambiente.parlanti.salva(p2).atteso()

        ambiente.api.perVoce(VOCE_1)

        assertEquals(1, estrattoreSpia.chiamate)
    }

    @Test
    fun `AC-423 un calcolo annullato non lascia alcuna voce in cache, la richiesta successiva ricalcola`() {
        val ambiente = Ambiente(estrattore = { EstrattoreCheAnnullaUnaVolta() })
        val p = unParlante("p-1", "Marco")
        p.registraImpronta(voceStorica(1), impronta(1f), "s1", MODELLO).atteso()
        ambiente.parlanti.salva(p).atteso()

        assertFailsWith<InterruptedException> { ambiente.api.perVoce(VOCE_1) }

        val proposta = assertNotNull(
            ambiente.api.perVoce(VOCE_1),
            "la richiesta successiva ricalcola: nessuna voce di cache rotta dal calcolo annullato",
        )
        assertEquals(1, proposta.candidati.size)
    }

    private fun aggiungiCandidato(ambiente: Ambiente, seme: Seed) {
        val p = unParlante(seme.id, seme.nome, seme.tipo)
        p.registraImpronta(seme.storica, seme.impronta, "s-${seme.id}", MODELLO).atteso()
        ambiente.parlanti.salva(p).atteso()
    }

    private data class Seed(
        val id: String,
        val nome: String,
        val tipo: TipoParlante,
        val impronta: Impronta,
        val storica: VoceRef,
    )

    @Suppress("LongParameterList") // one parameter per collaborator, mirrors the read-model's own constructor
    private class Ambiente(
        val parlanti: ParlanteRepositoryFinta = ParlanteRepositoryFinta(),
        registrazioni: LettoreRegistrazione = LettoreRegistrazioneFinta(
            mapOf(REGISTRAZIONE to unaRegistrazioneVista()),
        ),
        lettoreVoci: LettoreVoci = lettoreVociScenario(),
        confronto: ConfrontoImpronte = ConfrontoImpronteFinta(),
        decodificatore: (UnitaDiLavoroFinta) -> DecodificatoreAudio = { DecodificatoreAudioFinta(it) },
        estrattore: (UnitaDiLavoroFinta) -> EstrattoreImpronta = { EstrattoreImprontaFinta(unitaDiLavoro = it) },
    ) {
        private val uow = UnitaDiLavoroFinta()
        val estrattoAudio: EstrattoAudio = EstrattoAudio(lettoreVoci)
        val api: Proposta = Proposta(
            lettoreVoci,
            registrazioni,
            parlanti,
            decodificatore(uow),
            estrattore(uow),
            confronto,
            estrattoAudio,
        )
    }

    /** Records every (Registrazione, intervalli) it is asked to decode, delegating for real samples. */
    private class DecodificatoreAudioCheRegistra(private val delegato: DecodificatoreAudio) : DecodificatoreAudio {
        val chiamate: MutableList<Pair<RegistrazioneId, List<IntervalloMs>>> = mutableListOf()

        override fun campioni(id: RegistrazioneId, intervalli: List<IntervalloMs>): CampioniAudio {
            chiamate += id to intervalli
            return delegato.campioni(id, intervalli)
        }
    }

    private class EstrattoreImprontaCheConta(private val delegato: EstrattoreImpronta) : EstrattoreImpronta {
        var chiamate: Int = 0
            private set

        override val modello: String get() = delegato.modello

        override fun estrai(c: CampioniAudio): Impronta {
            chiamate++
            return delegato.estrai(c)
        }
    }

    /** Throws [InterruptedException] on the FIRST call only (ADR 0017 S1.5), succeeds from the second on. */
    private class EstrattoreCheAnnullaUnaVolta : EstrattoreImpronta {
        private var chiamate = 0
        override val modello: String = MODELLO

        override fun estrai(c: CampioniAudio): Impronta {
            chiamate++
            if (chiamate == 1) throw InterruptedException("annullato")
            return impronta(99f)
        }
    }

    private fun unParlante(
        id: String,
        nome: String,
        tipo: TipoParlante = TipoParlante.RICORRENTE,
        progettoId: ProgettoId = PROGETTO,
    ): Parlante = Parlante.crea(ParlanteId(id), progettoId, Nome.di(nome).atteso(), tipo).aggregato

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
        val STORICA = RegistrazioneId("storica-1")
        val VOCE_1 = VoceRef(REGISTRAZIONE, VoceId(1))
        val MODELLO = EstrattoreImprontaFinta.MODELLO

        fun impronta(seme: Float): Impronta = Impronta(FloatArray(8) { i -> seme + i })

        fun voceStorica(n: Int): VoceRef = VoceRef(STORICA, VoceId(n))

        fun unaRegistrazioneVista(): RegistrazioneVista = RegistrazioneVista(
            registrazioneId = REGISTRAZIONE,
            progettoId = PROGETTO,
            titolo = "Seduta",
            riferimentoAudio = RiferimentoAudio("audio/${REGISTRAZIONE.valore}.m4a"),
            dataRegistrazione = LocalDate.of(2026, 9, 20),
            durataMs = 3_600_000L,
        )

        /** [REGISTRAZIONE]'s Voce 1 (under Proposta) plus 5 Voci of [STORICA] (the Candidati' print sources). */
        fun lettoreVociScenario(voce1Intervalli: List<IntervalloMs> = listOf(IntervalloMs(0, 2_000))): LettoreVoci {
            val storiche = (1..5).map { VoceVista(voceStorica(it), listOf(IntervalloMs(0, 2_000))) }
            val voci = mapOf(REGISTRAZIONE to listOf(VoceVista(VOCE_1, voce1Intervalli)), STORICA to storiche)
            return LettoreVociFinta(voci)
        }
    }
}
