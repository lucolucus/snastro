package snastro.parlanti.applicazione.comandi

import snastro.kernel.CampioniAudio
import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.Esito
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.parlanti.applicazione.eventi.AttribuzioneConfermata
import snastro.parlanti.applicazione.eventi.ParlanteCreato
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.porte.AttribuzioneRepositoryFinta
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import snastro.parlanti.applicazione.porte.DecodificatoreAudioFinta
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.applicazione.porte.EstrattoreImprontaFinta
import snastro.parlanti.applicazione.porte.LettoreRegistrazioneFinta
import snastro.parlanti.applicazione.porte.LettoreVoci
import snastro.parlanti.applicazione.porte.LettoreVociFinta
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.applicazione.porte.ParlanteRepositoryFinta
import snastro.parlanti.applicazione.porte.RegistrazioneVista
import snastro.parlanti.applicazione.porte.SegmentoDiVoce
import snastro.parlanti.applicazione.porte.VoceVista
import snastro.parlanti.dominio.Attribuzione
import snastro.parlanti.dominio.ErroreParlanti
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
import kotlin.test.assertTrue

/**
 * [SaltaVoceServizio] against the ports' fakes (D1): INV-19 tasks + AC-88/AC-89 of `salta-voce`.
 */
class SaltaVoceServizioTest {
    private val parlanti = ParlanteRepositoryFinta()
    private val attribuzioni = AttribuzioneRepositoryFinta()

    private val uow = UnitaDiLavoroFinta(parlanti, attribuzioni)

    // ML fakes wired to the UnitaDiLavoroFinta: any decode/extract inside a transaction throws (AC-288).
    private val decodificatore = DecodificatoreAudioFinta(uow)
    private val estrattore = EstrattoreImprontaFinta(unitaDiLavoro = uow, modello = "modello-x")
    private val eventi = DispatcherEventiFinta(uow)
    private val transazioni = UnitaDiLavoroCheConta(eventi.unitaDiLavoro)

    private fun servizio(
        registrazioni: LettoreRegistrazioneFinta,
        voci: LettoreVoci,
        decoder: DecodificatoreAudio = decodificatore,
        generatoreId: GeneratoreIdFinto = GeneratoreIdFinto(),
        estrattoreUsato: EstrattoreImpronta = estrattore,
    ): SaltaVoceServizio =
        SaltaVoceServizio(
            transazioni, generatoreId, parlanti, attribuzioni, registrazioni, voci, decoder, estrattoreUsato,
            eventi,
        )

    private fun unaRegistrazione(
        id: RegistrazioneId = REGISTRAZIONE,
        progettoId: ProgettoId = PROGETTO,
        data: LocalDate = DATA,
    ): LettoreRegistrazioneFinta =
        LettoreRegistrazioneFinta(
            mapOf(
                id to RegistrazioneVista(
                    registrazioneId = id,
                    progettoId = progettoId,
                    titolo = "Riunione",
                    riferimentoAudio = RiferimentoAudio("audio/${id.valore}.wav"),
                    dataRegistrazione = data,
                    durataMs = 60_000L,
                ),
            ),
        )

    private fun unaVoce(voceRef: VoceRef = VOCE, id: RegistrazioneId = REGISTRAZIONE): LettoreVociFinta =
        LettoreVociFinta(mapOf(id to listOf(VoceVista(voceRef, listOf(IntervalloMs(0, 1_000))))))

    private fun unNome(testo: String): Nome = Nome.di(testo).atteso()

    private fun unParlante(id: String, nome: String, progettoId: ProgettoId = PROGETTO): Parlante =
        Parlante.crea(ParlanteId(id), progettoId, unNome(nome), TipoParlante.OCCASIONALE).aggregato

    @Test
    fun `INV-19 crea un occasionale Ospite del 12-09-2026 con l impronta conservata e attribuisce la Voce`() {
        val servizio = servizio(unaRegistrazione(), unaVoce())

        servizio.esegui(SaltaVoce(VOCE)).atteso()

        val parlanteId = ParlanteId("id-1")
        val trovato = assertNotNull(parlanti.trova(parlanteId))
        assertEquals("Ospite del 12/09/2026", trovato.nome.valore)
        assertEquals(TipoParlante.OCCASIONALE, trovato.tipo)
        assertTrue(trovato.attivo)
        val improntaAttesa = estrattore.estrai(decodificatore.campioni(REGISTRAZIONE, listOf(IntervalloMs(0, 1_000))))
        assertEquals(1, trovato.impronte.size)
        assertEquals(VOCE, trovato.impronte.single().voceRef)
        assertEquals(improntaAttesa, trovato.impronte.single().impronta)
        assertEquals(parlanteId, assertNotNull(attribuzioni.trova(VOCE)).parlanteId)
    }

    @Test
    fun `INV-19 un nome gia preso da un attivo con confronto normalizzato prende il primo suffisso libero`() {
        parlanti.salva(unParlante("id-esistente-1", "OSPITE DEL 12/09/2026")).atteso()
        parlanti.salva(unParlante("id-esistente-2", "Ospite del 12/09/2026 (2)")).atteso()
        val servizio = servizio(unaRegistrazione(), unaVoce())

        servizio.esegui(SaltaVoce(VOCE)).atteso()

        val nuovo = assertNotNull(attribuzioni.trova(VOCE)).parlanteId
        assertEquals("Ospite del 12/09/2026 (3)", assertNotNull(parlanti.trova(nuovo)).nome.valore)
    }

    @Test
    fun `INV-19 il nome di un occasionale creato resta invariato dopo una successiva modifica della data`() {
        val generatoreId = GeneratoreIdFinto()
        val servizio1 = servizio(unaRegistrazione(data = DATA), unaVoce(), generatoreId = generatoreId)
        servizio1.esegui(SaltaVoce(VOCE)).atteso()
        val primoParlanteId = ParlanteId("id-1")
        assertEquals("Ospite del 12/09/2026", assertNotNull(parlanti.trova(primoParlanteId)).nome.valore)

        // La Registrazione ha ora una DataRegistrazione diversa (una ModificaDataRegistrazione a monte).
        val altraVoce = VoceRef(REGISTRAZIONE, VoceId(2))
        val vociAggiornate = LettoreVociFinta(
            mapOf(
                REGISTRAZIONE to listOf(
                    VoceVista(VOCE, listOf(IntervalloMs(0, 1_000))),
                    VoceVista(altraVoce, listOf(IntervalloMs(1_000, 2_000))),
                ),
            ),
        )
        val servizio2 = servizio(
            unaRegistrazione(data = LocalDate.of(2026, 9, 20)),
            vociAggiornate,
            generatoreId = generatoreId,
        )
        servizio2.esegui(SaltaVoce(altraVoce)).atteso()
        val secondoParlanteId = ParlanteId("id-2")

        assertEquals(
            "Ospite del 12/09/2026",
            assertNotNull(parlanti.trova(primoParlanteId)).nome.valore,
            "il nome del primo occasionale non deve seguire la data cambiata",
        )
        assertEquals("Ospite del 20/09/2026", assertNotNull(parlanti.trova(secondoParlanteId)).nome.valore)
    }

    @Test
    fun `AC-88 SaltaVoce pubblica ParlanteCreato e AttribuzioneConfermata`() {
        val servizio = servizio(unaRegistrazione(), unaVoce())

        servizio.esegui(SaltaVoce(VOCE)).atteso()

        val parlanteId = ParlanteId("id-1")
        assertEquals(
            listOf(
                ParlanteCreato(parlanteId, PROGETTO, "Ospite del 12/09/2026", TipoParlanteVista.OCCASIONALE),
                AttribuzioneConfermata(VOCE, parlanteId, precedente = null),
            ),
            eventi.pubblicati,
        )
    }

    @Test
    fun `AC-89 saltare una Voce gia attribuita e VoceGiaAttribuita e nulla cambia`() {
        attribuzioni.salva(Attribuzione.conferma(VOCE, PROGETTO, ParlanteId("id-esistente")).aggregato)
        val decoderContato = DecodificatoreAudioContaChiamate(decodificatore)
        val servizio = servizio(unaRegistrazione(), unaVoce(), decoder = decoderContato)

        val errore = servizio.esegui(SaltaVoce(VOCE)).erroreAtteso<ErroreParlanti.VoceGiaAttribuita>()

        assertEquals(VOCE, errore.voceRef)
        assertEquals(ParlanteId("id-esistente"), assertNotNull(attribuzioni.trova(VOCE)).parlanteId)
        assertTrue(parlanti.delProgetto(PROGETTO).isEmpty(), "nessun Parlante deve essere creato")
        assertTrue(eventi.pubblicati.isEmpty())
        assertEquals(0, decoderContato.chiamate, "una Voce gia attribuita non deve mai toccare l audio")
    }

    @Test
    fun `una Registrazione senza Trascritto e TrascrittoNonTrovato`() {
        val servizio = servizio(unaRegistrazione(), LettoreVociFinta(emptyMap()))

        val errore = servizio.esegui(SaltaVoce(VOCE)).erroreAtteso<ErroreParlanti.TrascrittoNonTrovato>()

        assertEquals(REGISTRAZIONE, errore.registrazioneId)
        assertTrue(eventi.pubblicati.isEmpty())
    }

    @Test
    fun `una Voce assente dal Trascritto corrente e VoceNonTrovata`() {
        val altra = VoceRef(REGISTRAZIONE, VoceId(9))
        val servizio = servizio(unaRegistrazione(), unaVoce(voceRef = altra))

        val errore = servizio.esegui(SaltaVoce(VOCE)).erroreAtteso<ErroreParlanti.VoceNonTrovata>()

        assertEquals(VOCE, errore.voceRef)
        assertTrue(eventi.pubblicati.isEmpty())
    }

    @Test
    fun `F2 il backstop dell indice ADR 0007 e propagato senza persistere l Attribuzione ne pubblicare`() {
        val stub = RepositoryBackstopSempre(parlanti)
        val servizioLocale = SaltaVoceServizio(
            transazioni, GeneratoreIdFinto(), stub, attribuzioni, unaRegistrazione(), unaVoce(),
            decodificatore, estrattore, eventi,
        )

        val errore = servizioLocale.esegui(SaltaVoce(VOCE)).erroreAtteso<ErroreParlanti.NomeGiaInUso>()

        assertEquals("Ospite del 12/09/2026", errore.nome)
        assertNull(attribuzioni.trova(VOCE), "l Attribuzione non deve essere salvata se il Parlante non lo e")
        assertTrue(eventi.pubblicati.isEmpty())
    }

    @Test
    fun `AC-286 se la Voce cambia tra l estrazione e la transazione e VoceCambiata e nessun Parlante e creato`() {
        val voci = LettoreVociCheCambia(
            primaLettura = listOf(VoceVista(VOCE, listOf(IntervalloMs(0, 2_000)))),
            poi = listOf(VoceVista(VOCE, listOf(IntervalloMs(0, 2_000), IntervalloMs(4_000, 7_000)))),
        )

        val errore = servizio(unaRegistrazione(), voci).esegui(SaltaVoce(VOCE))
            .erroreAtteso<ErroreParlanti.VoceCambiata>()

        assertEquals(ErroreParlanti.VoceCambiata(VOCE), errore)
        assertTrue(parlanti.delProgetto(PROGETTO).isEmpty(), "nessun Parlante creato")
        assertNull(attribuzioni.trova(VOCE))
        assertTrue(eventi.pubblicati.isEmpty())
    }

    @Test
    fun `AC-287 per una Voce di oltre 30 s si decodifica solo SorgenteImpronta, 30 000 ms in tutto`() {
        val intervalliVoce = listOf(IntervalloMs(0, 20_000), IntervalloMs(25_000, 45_000), IntervalloMs(50_000, 50_500))
        val registra = DecodificatoreAudioContaChiamate(decodificatore)
        val voci = LettoreVociFinta(mapOf(REGISTRAZIONE to listOf(VoceVista(VOCE, intervalliVoce))))

        servizio(unaRegistrazione(), voci, decoder = registra).esegui(SaltaVoce(VOCE)).atteso()

        val decodificati = registra.intervalli.single()
        assertEquals(listOf(IntervalloMs(0, 20_000), IntervalloMs(25_000, 35_000)), decodificati)
        assertEquals(SorgenteImpronta.di(intervalliVoce).intervalli, decodificati)
        assertEquals(30_000L, decodificati.sumOf { it.fineMs - it.inizioMs })
    }

    @Test
    fun `AC-288 nessuna decodifica ne estrazione con una transazione aperta`() {
        // decodificatore/estrattore are wired to the UnitaDiLavoroFinta: inside the transaction they throw.
        servizio(unaRegistrazione(), unaVoce()).esegui(SaltaVoce(VOCE)).atteso()

        assertEquals(1, transazioni.aperte)
        assertNotNull(attribuzioni.trova(VOCE))
    }

    @Test
    fun `AC-289 la riga d impronta del nuovo occasionale conserva sorgente = chiave e modello dell estrattore`() {
        val intervalli = listOf(IntervalloMs(1_200, 5_400), IntervalloMs(8_000, 15_000))
        val voci = LettoreVociFinta(mapOf(REGISTRAZIONE to listOf(VoceVista(VOCE, intervalli))))

        servizio(unaRegistrazione(), voci).esegui(SaltaVoce(VOCE)).atteso()

        val riga = assertNotNull(parlanti.trova(ParlanteId("id-1"))).impronte.single()
        assertEquals("1200-5400,8000-15000", riga.sorgente)
        assertEquals(SorgenteImpronta.di(intervalli).chiave, riga.sorgente)
        assertEquals("modello-x", riga.modello)
    }

    @Test
    fun `AC-290 se l estrazione fallisce nessuna transazione e aperta e nulla e scritto`() {
        val guasto = object : EstrattoreImpronta {
            override val modello: String = "modello-x"

            override fun estrai(c: CampioniAudio): Impronta = throw GuastoDiProva()
        }

        assertFailsWith<GuastoDiProva> {
            servizio(unaRegistrazione(), unaVoce(), estrattoreUsato = guasto).esegui(SaltaVoce(VOCE))
        }

        assertEquals(0, transazioni.aperte)
        assertTrue(parlanti.delProgetto(PROGETTO).isEmpty())
        assertNull(attribuzioni.trova(VOCE))
        assertTrue(eventi.pubblicati.isEmpty())
    }

    @Test
    fun `AC-290 se la decodifica fallisce nessuna transazione e aperta e nulla e scritto`() {
        val guasto = object : DecodificatoreAudio {
            override fun campioni(id: RegistrazioneId, intervalli: List<IntervalloMs>): CampioniAudio =
                throw GuastoDiProva()
        }

        assertFailsWith<GuastoDiProva> {
            servizio(unaRegistrazione(), unaVoce(), decoder = guasto).esegui(SaltaVoce(VOCE))
        }

        assertEquals(0, transazioni.aperte)
        assertTrue(parlanti.delProgetto(PROGETTO).isEmpty())
        assertNull(attribuzioni.trova(VOCE))
    }

    private class GuastoDiProva : RuntimeException("guasto nativo di prova")

    /** Counts the transactions actually opened (AC-288/AC-290). */
    private class UnitaDiLavoroCheConta(private val delegata: UnitaDiLavoro) : UnitaDiLavoro {
        var aperte: Int = 0
            private set

        override fun <T> inTransazione(blocco: () -> Esito<T>): Esito<T> {
            aperte++
            return delegata.inTransazione(blocco)
        }
    }

    /** [LettoreVoci] whose Voci change after the first read: the Voce is edited between extraction and write. */
    private class LettoreVociCheCambia(
        private val primaLettura: List<VoceVista>,
        private val poi: List<VoceVista>,
    ) : LettoreVoci {
        private var letture = 0

        override fun voci(id: RegistrazioneId): List<VoceVista> = if (letture++ == 0) primaLettura else poi

        override fun segmenti(id: RegistrazioneId): List<SegmentoDiVoce> = error("non usato da questo test (AC-494)")
    }

    /**
     * F2: `nomeAttivoInUso` sempre libero (il pre-check manca il conflitto), `salva` rifiuta sempre
     * come l'indice reale.
     */
    private class RepositoryBackstopSempre(
        private val delegato: ParlanteRepository,
    ) : ParlanteRepository by delegato {
        override fun nomeAttivoInUso(progettoId: ProgettoId, nome: Nome, escluso: ParlanteId?): Boolean = false

        override fun salva(p: Parlante): Esito<Unit> = Esito.Errore(ErroreParlanti.NomeGiaInUso(p.nome.valore))
    }

    /** AC-89: proves the early exit never reaches the native decode/extract path (RC-9: a fake, not MockK). */
    private class DecodificatoreAudioContaChiamate(
        private val delegato: DecodificatoreAudio,
    ) : DecodificatoreAudio {
        val intervalli: MutableList<List<IntervalloMs>> = mutableListOf()
        val chiamate: Int get() = intervalli.size

        override fun campioni(id: RegistrazioneId, intervalli: List<IntervalloMs>): CampioniAudio {
            this.intervalli += intervalli
            return delegato.campioni(id, intervalli)
        }
    }

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
        val VOCE = VoceRef(REGISTRAZIONE, VoceId(1))
        val DATA: LocalDate = LocalDate.of(2026, 9, 12)
    }
}
