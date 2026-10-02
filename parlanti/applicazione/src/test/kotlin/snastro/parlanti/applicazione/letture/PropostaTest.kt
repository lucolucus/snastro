package snastro.parlanti.applicazione.letture

import snastro.kernel.CampioniAudio
import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.kernel.unIncontroDi
import snastro.kernel.unicaParteDi
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
import snastro.parlanti.applicazione.porte.lettoreVociDiUnicheParti
import snastro.parlanti.applicazione.porte.ogniRegistrazioneNota
import snastro.parlanti.applicazione.porte.unaVoceVista
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
        val ambiente = Ambiente(lettoreVoci = lettoreVociDiUnicheParti())

        assertNull(ambiente.api.perVoce(VOCE_1))
    }

    @Test
    fun `una Voce senza piu alcun intervallo non genera una Proposta`() {
        val vuota = mapOf(REGISTRAZIONE to listOf(unaVoceVista(VOCE_1, emptyList())))

        val ambiente = Ambiente(lettoreVoci = lettoreVociDiUnicheParti(vuota))

        assertNull(ambiente.api.perVoce(VOCE_1))
    }

    @Test
    fun `INV-20 solo Parlanti attivi dello stesso Progetto con impronte del modello corrente sono Candidati`() {
        val ambiente = Ambiente()

        val incluso = unParlante("p-1", "Marco")
        incluso.aggiungiImpronta(voceStorica(1), unicaParteDi(voceStorica(1)), impronta(1f), "s1", MODELLO).atteso()
        ambiente.parlanti.salva(incluso).atteso()

        val eliminato = unParlante("p-2", "Elena")
        eliminato.aggiungiImpronta(voceStorica(2), unicaParteDi(voceStorica(2)), impronta(2f), "s2", MODELLO).atteso()
        eliminato.elimina().atteso()
        ambiente.parlanti.salva(eliminato).atteso()

        val senzaImpronte = unParlante("p-3", "Aldo")
        ambiente.parlanti.salva(senzaImpronte).atteso()

        val altroProgetto = unParlante("p-4", "Bea", progettoId = ProgettoId("altro-progetto"))
        altroProgetto.aggiungiImpronta(
            voceStorica(4),
            unicaParteDi(voceStorica(4)),
            impronta(4f),
            "s4",
            MODELLO,
        ).atteso()
        ambiente.parlanti.salva(altroProgetto).atteso()

        val nomi = assertNotNull(ambiente.api.perVoce(VOCE_1)).candidati.map { it.nome }

        assertEquals(listOf("Marco"), nomi)
    }

    @Test
    fun `INV-20 la vista espone voceId e per ogni Candidato parlanteId nome tipoParlante fascia ed estratto`() {
        val ambiente = Ambiente()
        val p = unParlante("p-1", "Marco")
        p.aggiungiImpronta(voceStorica(1), unicaParteDi(voceStorica(1)), impronta(1f), "s1", MODELLO).atteso()
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
        p.aggiungiImpronta(
            voceStorica(1),
            unicaParteDi(voceStorica(1)),
            debole,
            "s1",
            MODELLO,
        ).atteso() // impronta debole, prima
        p.aggiungiImpronta(
            voceStorica(2),
            unicaParteDi(voceStorica(2)),
            forte,
            "s2",
            MODELLO,
        ).atteso() // impronta forte, seconda
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
        p.aggiungiImpronta(voceStorica(1), unicaParteDi(voceStorica(1)), impronta(1f), "s1", "modello-vecchio").atteso()
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
        p.aggiungiImpronta(
            voceStorica(1),
            unicaParteDi(voceStorica(1)),
            delModelloVecchio,
            "s1",
            "modello-vecchio",
        ).atteso()
        p.aggiungiImpronta(voceStorica(2), unicaParteDi(voceStorica(2)), delModelloCorrente, "s2", MODELLO).atteso()
        ambiente.parlanti.salva(p).atteso()

        val candidato = assertNotNull(ambiente.api.perVoce(VOCE_1)).candidati.single()

        assertEquals(Fascia.DEBOLE, candidato.fascia)
        assertEquals(ambiente.estrattoAudio.estratto(voceStorica(2)), candidato.estratto)
    }

    @Test
    fun `AC-172 calcolare una Proposta non scrive nessuna riga impronta_vocale`() {
        val ambiente = Ambiente()
        val p = unParlante("p-1", "Marco")
        p.aggiungiImpronta(voceStorica(1), unicaParteDi(voceStorica(1)), impronta(1f), "s1", MODELLO).atteso()
        ambiente.parlanti.salva(p).atteso()
        val contoPrima = ambiente.parlanti.righeImpronte(p.id)

        ambiente.api.perVoce(VOCE_1)

        assertEquals(contoPrima, ambiente.parlanti.righeImpronte(p.id))
    }

    @Test
    fun `AC-173 invalida per Voce e per Registrazione forza il ricalcolo, senza resta la cache`() {
        val ambiente = Ambiente()
        val p1 = unParlante("p-1", "Marco")
        p1.aggiungiImpronta(voceStorica(1), unicaParteDi(voceStorica(1)), impronta(1f), "s1", MODELLO).atteso()
        ambiente.parlanti.salva(p1).atteso()
        assertEquals(1, assertNotNull(ambiente.api.perVoce(VOCE_1)).candidati.size)

        val p2 = unParlante("p-2", "Giulia")
        p2.aggiungiImpronta(voceStorica(2), unicaParteDi(voceStorica(2)), impronta(2f), "s2", MODELLO).atteso()
        ambiente.parlanti.salva(p2).atteso()
        val senzaInvalidare = assertNotNull(ambiente.api.perVoce(VOCE_1)).candidati.size
        assertEquals(1, senzaInvalidare, "senza invalidare resta la cache")

        ambiente.api.invalida(VOCE_1)
        val dopoInvalidaVoce = assertNotNull(ambiente.api.perVoce(VOCE_1)).candidati.size
        assertEquals(2, dopoInvalidaVoce, "invalida(VoceRef): un'Attribuzione")

        val p3 = unParlante("p-3", "Elena")
        p3.aggiungiImpronta(voceStorica(3), unicaParteDi(voceStorica(3)), impronta(3f), "s3", MODELLO).atteso()
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
        p1.aggiungiImpronta(voceStorica(1), unicaParteDi(voceStorica(1)), impronta(1f), "s1", MODELLO).atteso()
        val p2 = unParlante("p-2", "Giulia")
        p2.aggiungiImpronta(voceStorica(2), unicaParteDi(voceStorica(2)), impronta(2f), "s2", MODELLO).atteso()
        ambiente.parlanti.salva(p1).atteso()
        ambiente.parlanti.salva(p2).atteso()

        ambiente.api.perVoce(VOCE_1)

        assertEquals(1, estrattoreSpia.chiamate)
    }

    @Test
    fun `AC-423 un calcolo annullato non lascia alcuna voce in cache, la richiesta successiva ricalcola`() {
        val ambiente = Ambiente(estrattore = { EstrattoreCheAnnullaUnaVolta() })
        val p = unParlante("p-1", "Marco")
        p.aggiungiImpronta(voceStorica(1), unicaParteDi(voceStorica(1)), impronta(1f), "s1", MODELLO).atteso()
        ambiente.parlanti.salva(p).atteso()

        assertFailsWith<InterruptedException> { ambiente.api.perVoce(VOCE_1) }

        val proposta = assertNotNull(
            ambiente.api.perVoce(VOCE_1),
            "la richiesta successiva ricalcola: nessuna voce di cache rotta dal calcolo annullato",
        )
        assertEquals(1, proposta.candidati.size)
    }

    @Test
    fun `INV-20 la Proposta per la Voce 5 della parte 2 propone Anna con impronta dalla parte 1 dell Incontro`() {
        val ambiente = AmbienteDueParti(voce5 = mapOf(PARTE_2 to listOf(IntervalloMs(0, 4_000))))
        val anna = unParlante("p-anna", "Anna")
        anna.aggiungiImpronta(VOCE_ANNA, PARTE_1, impronta(1f), "s1", MODELLO).atteso()
        ambiente.parlanti.salva(anna).atteso()

        val candidato = assertNotNull(ambiente.api.perVoce(VOCE_5)).candidati.single()

        assertEquals(anna.id, candidato.parlanteId)
        assertEquals(ambiente.estrattoAudio.estratto(VOCE_ANNA, PARTE_1), candidato.estratto)
        assertEquals(PARTE_1, candidato.estratto.registrazioneId, "INV-I17: dalla parte dell'impronta scelta")
    }

    @Test
    fun `INV-20 la Fascia e la migliore sulle coppie fetta per impronta, l estratto dalla parte dell impronta`() {
        val fettaParte1 = listOf(IntervalloMs(0, 4_000))
        val fettaParte2 = listOf(IntervalloMs(10_000, 14_000))
        val debole = impronta(40f)
        lateinit var estrattoreSpia: EstrattoreImprontaCheConta
        val ambiente = AmbienteDueParti(
            voce5 = mapOf(PARTE_1 to fettaParte1, PARTE_2 to fettaParte2),
            confronto = ConfrontoImpronteFinta(mapOf(debole to Fascia.DEBOLE)),
            estrattore = { EstrattoreImprontaCheConta(EstrattoreImprontaFinta()).also { estrattoreSpia = it } },
        )
        // Anna: una impronta DEBOLE (parte 1) e una identica alla fetta della Voce 5 nella parte 2 (FORTE solo con
        // quella fetta): FORTE esiste solo se si confrontano TUTTE le fette con TUTTE le impronte.
        val comeFettaParte2 = EstrattoreImprontaFinta().estrai(
            DecodificatoreAudioFinta().campioni(PARTE_2, SorgenteImpronta.di(fettaParte2).intervalli),
        )
        val anna = unParlante("p-anna", "Anna")
        anna.aggiungiImpronta(VOCE_ANNA, PARTE_1, debole, "s1", MODELLO).atteso()
        anna.aggiungiImpronta(VOCE_ANNA, PARTE_2, comeFettaParte2, "s2", MODELLO).atteso()
        ambiente.parlanti.salva(anna).atteso()

        val candidato = assertNotNull(ambiente.api.perVoce(VOCE_5)).candidati.single()

        assertEquals(Fascia.FORTE, candidato.fascia)
        assertEquals(ambiente.estrattoAudio.estratto(VOCE_ANNA, PARTE_2), candidato.estratto)
        assertEquals(2, estrattoreSpia.chiamate, "una impronta transitoria per Parte in cui la Voce parla")
    }

    @Test
    fun `INV-20 un Candidato non espone alcun punteggio numerico`() {
        val campi = Candidato::class.java.declaredFields.map { it.name }.toSet()

        assertEquals(setOf("parlanteId", "nome", "tipoParlante", "fascia", "estratto"), campi)
    }

    private fun aggiungiCandidato(ambiente: Ambiente, seme: Seed) {
        val p = unParlante(seme.id, seme.nome, seme.tipo)
        p.aggiungiImpronta(seme.storica, unicaParteDi(seme.storica), seme.impronta, "s-${seme.id}", MODELLO).atteso()
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
        val estrattoAudio: EstrattoAudio = EstrattoAudio(lettoreVoci, ogniRegistrazioneNota())
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

    /** An Incontro of two Parti: Voce 1 (Anna's source) speaks in both, Voce 5 (under Proposta) in [voce5]. */
    private class AmbienteDueParti(
        voce5: Map<RegistrazioneId, List<IntervalloMs>>,
        confronto: ConfrontoImpronte = ConfrontoImpronteFinta(),
        estrattore: () -> EstrattoreImpronta = { EstrattoreImprontaFinta() },
    ) {
        val parlanti = ParlanteRepositoryFinta()
        private val registrazioni = LettoreRegistrazioneFinta(
            mapOf(
                PARTE_1 to unaRegistrazioneVista().copy(registrazioneId = PARTE_1, incontroId = INCONTRO),
                PARTE_2 to unaRegistrazioneVista().copy(registrazioneId = PARTE_2, incontroId = INCONTRO),
            ),
        )
        private val voci = LettoreVociFinta(
            mapOf(
                INCONTRO to listOf(
                    VoceVista(
                        VOCE_ANNA,
                        mapOf(
                            PARTE_1 to listOf(IntervalloMs(20_000, 23_000)),
                            PARTE_2 to listOf(IntervalloMs(30_000, 32_000)),
                        ),
                    ),
                    VoceVista(VOCE_5, voce5),
                ),
            ),
        )
        val estrattoAudio = EstrattoAudio(voci, registrazioni)
        val api = Proposta(
            voci,
            registrazioni,
            parlanti,
            DecodificatoreAudioFinta(),
            estrattore(),
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
        val VOCE_1 = VoceRef(unIncontroDi(REGISTRAZIONE), VoceId(1))
        val MODELLO = EstrattoreImprontaFinta.MODELLO
        val INCONTRO = IncontroId("incontro-2-parti")
        val PARTE_1 = RegistrazioneId("parte-1")
        val PARTE_2 = RegistrazioneId("parte-2")
        val VOCE_ANNA = VoceRef(INCONTRO, VoceId(1))
        val VOCE_5 = VoceRef(INCONTRO, VoceId(5))

        fun impronta(seme: Float): Impronta = Impronta(FloatArray(8) { i -> seme + i })

        fun voceStorica(n: Int): VoceRef = VoceRef(unIncontroDi(STORICA), VoceId(n))

        fun unaRegistrazioneVista(): RegistrazioneVista = RegistrazioneVista(
            registrazioneId = REGISTRAZIONE,
            incontroId = unIncontroDi(REGISTRAZIONE),
            progettoId = PROGETTO,
            titolo = "Seduta",
            riferimentoAudio = RiferimentoAudio("audio/${REGISTRAZIONE.valore}.m4a"),
            dataRegistrazione = LocalDate.of(2026, 9, 20),
            durataMs = 3_600_000L,
        )

        /** [REGISTRAZIONE]'s Voce 1 (under Proposta) plus 5 Voci of [STORICA] (the Candidati' print sources). */
        fun lettoreVociScenario(voce1Intervalli: List<IntervalloMs> = listOf(IntervalloMs(0, 2_000))): LettoreVoci {
            val storiche = (1..5).map { unaVoceVista(voceStorica(it), listOf(IntervalloMs(0, 2_000))) }
            val voci = mapOf(REGISTRAZIONE to listOf(unaVoceVista(VOCE_1, voce1Intervalli)), STORICA to storiche)
            return lettoreVociDiUnicheParti(voci)
        }
    }
}
