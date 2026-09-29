package snastro.sintesi.adattatori.eventi

import io.mockk.spyk
import io.mockk.verify
import snastro.kernel.AbbonatoDopoCommit
import snastro.kernel.AbbonatoSincrono
import snastro.kernel.CampioniAudio
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IntervalloMs
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.SegmentoId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.sintesi.adattatori.porte.LettoreTrascrittoDaTrascrizione
import snastro.sintesi.applicazione.politiche.ApplicaSostituzioneTrascrittoSintesiPolitica
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguisticoFinta
import snastro.sintesi.applicazione.porte.LettoreTrascritto
import snastro.sintesi.applicazione.porte.LettoreTrascrittoFinta
import snastro.sintesi.applicazione.porte.LunghezzaMassimaRiassuntoRepositoryFinta
import snastro.sintesi.applicazione.porte.RiassuntoRepository
import snastro.sintesi.applicazione.porte.RiassuntoRepositoryFinta
import snastro.sintesi.applicazione.porte.SegmentoSintesi
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico
import snastro.sintesi.applicazione.porte.unRiassunto
import snastro.sintesi.dominio.ErroreSintesi
import snastro.sintesi.dominio.LimiteIngresso
import snastro.sintesi.dominio.RiassuntoId
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.comandi.AvviaElaborazioneServizio
import snastro.trascrizione.applicazione.comandi.EseguiProssimaElaborazione
import snastro.trascrizione.applicazione.comandi.EseguiProssimaElaborazioneServizio
import snastro.trascrizione.applicazione.comandi.PortePipeline
import snastro.trascrizione.applicazione.comandi.RisultatoAvanzamento
import snastro.trascrizione.applicazione.eventi.ElaborazioneCompletata
import snastro.trascrizione.applicazione.eventi.TrascrittoSostituito
import snastro.trascrizione.applicazione.letture.FasiInCorso
import snastro.trascrizione.applicazione.letture.StatiElaborazione
import snastro.trascrizione.applicazione.letture.VociDelTrascritto
import snastro.trascrizione.applicazione.porte.Allineatore
import snastro.trascrizione.applicazione.porte.DecodificatoreAudioFinta
import snastro.trascrizione.applicazione.porte.DiarizzatoreFinta
import snastro.trascrizione.applicazione.porte.ElaborazioneRepositoryFinta
import snastro.trascrizione.applicazione.porte.LettoreRegistrazioneFinta
import snastro.trascrizione.applicazione.porte.RegistrazioneVista
import snastro.trascrizione.applicazione.porte.SegmentoGrezzo
import snastro.trascrizione.applicazione.porte.SegnalatoreFaseFinta
import snastro.trascrizione.applicazione.porte.TrascrittoRepositoryFinta
import snastro.trascrizione.applicazione.porte.Turno
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * [AbbonatoTrascrizioneSintesi] (AC-S115/AC-S116). Two groups on fakes prove the WIRING (the value's
 * shape, routing, transaction placement, doom) — the policy's own rule coverage is
 * [snastro.sintesi.applicazione.politiche.ApplicaSostituzioneTrascrittoSintesiPoliticaTest]'s job. The
 * last test is the carry-over end-to-end proof that AC-S96's own fake (one generation only) could not
 * give: a REAL re-run through Trascrizione's OWN commands, over a REAL [LettoreTrascrittoDaTrascrizione],
 * shows the re-queued Riassunto is decided from the NEW Trascritto, not the one it replaces.
 */
class AbbonatoTrascrizioneSintesiTest {
    @Test
    fun `AC-S115 e un valore AbbonatoSincrono, mai dopo commit, e costruirlo non registra nulla`() {
        val a = unAmbiente()
        val dispatcher = spyk(DispatcherEventiInMemoria(a.transazioni))

        val abbonato: Any = AbbonatoTrascrizioneSintesi(politicaCon(a, dispatcher))

        assertIs<AbbonatoSincrono>(abbonato)
        assertFalse(abbonato is AbbonatoDopoCommit)
        verify(exactly = 0) { dispatcher.registraSincrono(any()) } // ADR 0030 §1, AC-C67: the composition registers
        verify(exactly = 0) { dispatcher.registraDopoCommit(any()) }
    }

    @Test
    fun `AC-S115 ricevi ignora ogni evento diverso da TrascrittoSostituito, mai chiama la politica`() {
        val a = unAmbiente()
        val dispatcher = DispatcherEventiInMemoria(a.transazioni)
        val politica = spyk(politicaCon(a, dispatcher))
        dispatcher.registraSincrono(AbbonatoTrascrizioneSintesi(politica))

        val esito = dispatcher.unitaDiLavoro.inTransazione {
            dispatcher.pubblica(ElaborazioneCompletata(REG))
            dispatcher.pubblica(object : EventoPubblicato {})
            Esito.Ok(Unit)
        }

        esito.atteso()
        verify(exactly = 0) { politica.applica(any()) }
    }

    @Test
    fun `AC-S116 TrascrittoSostituito applica la politica dentro la transazione che lo pubblica, effetto committato`() {
        val a = unAmbiente()
        a.riassunti.salva(unRiassunto("vecchio", REG, argomento = "Tema")).atteso()
        val dispatcher = DispatcherEventiInMemoria(a.transazioni)
        val politica = spyk(politicaCon(a, dispatcher))
        dispatcher.registraSincrono(AbbonatoTrascrizioneSintesi(politica))

        val esito = dispatcher.unitaDiLavoro.inTransazione {
            dispatcher.pubblica(TrascrittoSostituito(REG))
            Esito.Ok(Unit)
        }

        esito.atteso()
        verify(exactly = 1) { politica.applica(REG) }
        val righe = a.riassunti.diRegistrazione(REG)
        assertEquals(1, righe.size, "il vecchio e rimosso, uno nuovo riaccodato: l'effetto e davvero applicato")
        assertTrue(righe.single().inAttesa)
        assertNotEquals(RiassuntoId("vecchio"), righe.single().id)
    }

    @Test
    fun `AC-S116 un Errore della politica condanna pubblica e annulla tutta la transazione (rollback)`() {
        val a = unAmbiente()
        a.riassunti.salva(unRiassunto("esistente", REG)).atteso()
        val dispatcher = DispatcherEventiInMemoria(a.transazioni)
        val riassuntiGuasti = object : RiassuntoRepository by a.riassunti {
            override fun rimuoviDiRegistrazione(r: RegistrazioneId): Esito<Int> =
                Esito.Errore(ErroreSintesi.TrascrittoNonDisponibile(r))
        }
        dispatcher.registraSincrono(
            AbbonatoTrascrizioneSintesi(politicaCon(a, dispatcher, riassunti = riassuntiGuasti)),
        )

        val esito = dispatcher.unitaDiLavoro.inTransazione {
            a.riassunti.salva(unRiassunto("altra-reg", ALTRA)) // una scrittura della stessa transazione, prima del veto
            dispatcher.pubblica(TrascrittoSostituito(REG))
            Esito.Ok(Unit)
        }

        esito.erroreAtteso<ErroreSintesi.TrascrittoNonDisponibile>()
        assertEquals(1, a.riassunti.diRegistrazione(REG).size, "rollback: la riga originale di REG resta")
        assertEquals(
            emptyList(),
            a.riassunti.diRegistrazione(ALTRA),
            "rollback: annulla anche la scrittura precedente al veto",
        )
    }

    /**
     * Carry-over (composer review): AC-S96's own policy test can only prove the read happens "with the
     * transaction open", never "the NEW rather than the OLD Trascritto" — its fake holds one generation.
     * Here a REAL re-run through [EseguiProssimaElaborazioneServizio] replaces a Trascritto whose OLD text
     * is deliberately OVER [LimiteIngresso.LIMITE_TOKEN] with a NEW, short one, inside the SAME completion
     * transaction where [AbbonatoTrascrizioneSintesi] runs. A subscriber reading a stale/cached/old
     * generation would see the over-limit estimate and refuse to re-queue (AC-S95's own shape); reading the
     * NEW one (ADR 0018 §2 order: `salva` before `pubblica`) re-queues instead — the only way this test's
     * assertion holds.
     */
    @Test
    fun `il Riassunto riaccodato viene dal Trascritto NUOVO sostituito, non da quello vecchio fuori limite`() {
        val scenario = ScenarioRitrascrizione()
        val r = scenario.aggiungiRegistrazione()
        scenario.accoda(r)
        val vecchioTesto = "a".repeat(LimiteIngresso.LIMITE_TOKEN * 3)
        scenario.completa(r, vecchioTesto)
        assertTrue(
            LimiteIngresso.stimaToken(vecchioTesto) > LimiteIngresso.LIMITE_TOKEN,
            "il testo vecchio deve essere fuori limite perche il test sia discriminante",
        )
        scenario.riassunti.salva(unRiassunto("vecchio", r, argomento = "Tema")).atteso()

        scenario.accoda(r)
        val nuovoTesto = "Versione nuova, breve e diversa."
        scenario.completa(r, nuovoTesto)

        val righe = scenario.riassunti.diRegistrazione(r)
        assertEquals(1, righe.size, "il vecchio Riassunto e rimosso e uno nuovo riaccodato dal Trascritto NUOVO")
        val nuovo = righe.single()
        assertTrue(
            nuovo.inAttesa,
            "se la politica avesse letto il vecchio Trascritto (fuori limite) non avrebbe riaccodato nulla",
        )
        assertNotEquals(RiassuntoId("vecchio"), nuovo.id)
        assertEquals(
            listOf(nuovoTesto),
            scenario.lettore.segmenti(r)?.map { it.testo },
            "il Trascritto letto ora e quello nuovo",
        )
    }

    private fun unSegmentoSintesi(testo: String = "Testo di prova."): SegmentoSintesi =
        SegmentoSintesi(SEGMENTO_ID, VOCE_ID, IntervalloMs(0, 1_000), testo)

    private data class Ambiente(
        val riassunti: RiassuntoRepositoryFinta,
        val lunghezze: LunghezzaMassimaRiassuntoRepositoryFinta,
        val trascritti: LettoreTrascritto,
        val transazioni: UnitaDiLavoroFinta,
    )

    /** A fresh set of fakes, wired so `UnitaDiLavoroFinta` genuinely rolls a doomed transaction back. */
    private fun unAmbiente(
        trascritti: LettoreTrascritto = LettoreTrascrittoFinta(mapOf(REG to listOf(unSegmentoSintesi()))),
    ): Ambiente {
        val riassunti = RiassuntoRepositoryFinta()
        val lunghezze = LunghezzaMassimaRiassuntoRepositoryFinta()
        return Ambiente(riassunti, lunghezze, trascritti, UnitaDiLavoroFinta(riassunti, lunghezze))
    }

    private fun politicaCon(
        a: Ambiente,
        dispatcher: DispatcherEventiInMemoria,
        riassunti: RiassuntoRepository = a.riassunti,
    ): ApplicaSostituzioneTrascrittoSintesiPolitica = ApplicaSostituzioneTrascrittoSintesiPolitica(
        GeneratoreIdFinto(),
        CLOCK,
        PROGETTO,
        riassunti,
        a.lunghezze,
        a.trascritti,
        DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.Installato),
        dispatcher,
    )

    /**
     * Drives a real re-run end to end: Trascrizione's OWN commands (`AvviaElaborazione` +
     * `EseguiProssimaElaborazione`, with fake ML/audio ports) over its OWN in-memory port fakes, a REAL
     * [LettoreTrascrittoDaTrascrizione] reading them, and the REAL [ApplicaSostituzioneTrascrittoSintesiPolitica]
     * wired through [AbbonatoTrascrizioneSintesi] on the SAME [DispatcherEventiInMemoria] that Trascrizione's
     * completion publishes through — exactly the shared-dispatcher wiring `avvio-sintesi` composes (ADR 0021 §10).
     * Never imports `snastro.trascrizione.dominio` (CR-1): every `Elaborazione`/`Trascritto` instance method is
     * reached only through inference on `:trascrizione:applicazione`'s own repository fakes, mirroring
     * `LettoreTrascrittoDaTrascrizioneTest`'s established D2 seeding technique for this exact module.
     */
    private class ScenarioRitrascrizione {
        private val clock = Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"), ZoneOffset.UTC)
        private val generatoreId = GeneratoreIdFinto()
        private val elaborazioni = ElaborazioneRepositoryFinta()
        private val trascrittiTrascrizione = TrascrittoRepositoryFinta()
        val riassunti = RiassuntoRepositoryFinta()
        private val lunghezze = LunghezzaMassimaRiassuntoRepositoryFinta()
        private val dispatcher = DispatcherEventiInMemoria(
            UnitaDiLavoroFinta(elaborazioni, trascrittiTrascrizione, riassunti, lunghezze),
        )
        private val registrazioniViste = mutableMapOf<RegistrazioneId, RegistrazioneVista>()
        private val registrazioni = LettoreRegistrazioneFinta(registrazioniViste)
        val lettore: LettoreTrascritto = LettoreTrascrittoDaTrascrizione(
            VociDelTrascritto(trascrittiTrascrizione),
            StatiElaborazione(elaborazioni, trascrittiTrascrizione, FasiInCorso()),
        )

        init {
            val politica = ApplicaSostituzioneTrascrittoSintesiPolitica(
                generatoreId,
                clock,
                PROGETTO,
                riassunti,
                lunghezze,
                lettore,
                DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.Installato),
                dispatcher,
            )
            dispatcher.registraSincrono(AbbonatoTrascrizioneSintesi(politica))
        }

        fun aggiungiRegistrazione(): RegistrazioneId {
            val id = RegistrazioneId(generatoreId.nuovo())
            registrazioniViste[id] = RegistrazioneVista(
                registrazioneId = id,
                progettoId = PROGETTO,
                titolo = "Registrazione ${id.valore}",
                riferimentoAudio = RiferimentoAudio("audio/${id.valore}.wav"),
                dataRegistrazione = LocalDate.of(2026, 9, 23),
                durataMs = DURATA_MS,
            )
            return id
        }

        fun accoda(r: RegistrazioneId) {
            AvviaElaborazioneServizio(dispatcher.unitaDiLavoro, generatoreId, clock, registrazioni, elaborazioni)
                .esegui(AvviaElaborazione(r))
                .atteso()
        }

        /** Runs the queued (FIFO head) Elaborazione of [r] to `completata` with one turno carrying [testo]. */
        fun completa(r: RegistrazioneId, testo: String) {
            val vista = registrazioniViste.getValue(r)
            val intervallo = IntervalloMs(0, 1_000)
            val decodificatore = DecodificatoreAudioFinta(mapOf(vista.riferimentoAudio to vista.durataMs))
            val pipeline = PortePipeline(
                registrazioni,
                decodificatore,
                DiarizzatoreFinta(listOf(Turno(intervallo, voceIndice = 0))),
                AllineatoreDiTesto(testo),
                SegnalatoreFaseFinta(),
            )
            val servizio = EseguiProssimaElaborazioneServizio(
                dispatcher.unitaDiLavoro,
                clock,
                elaborazioni,
                trascrittiTrascrizione,
                pipeline,
                dispatcher,
            )
            val risultato = servizio.esegui(EseguiProssimaElaborazione()).atteso()
            check(risultato is RisultatoAvanzamento.Avviata) { "atteso Avviata per ${r.valore}, ottenuto $risultato" }
        }
    }

    /** [Allineatore] that plays back the SAME [testo] for every turno (one turno per call here). */
    private class AllineatoreDiTesto(private val testo: String) : Allineatore {
        override fun allinea(campioni: CampioniAudio, turni: List<Turno>): List<SegmentoGrezzo> =
            turni.map { SegmentoGrezzo(it.voceIndice, it.intervallo, testo) }
    }

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val REG = RegistrazioneId("registrazione-1")
        val ALTRA = RegistrazioneId("registrazione-2")
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"), ZoneOffset.UTC)
        val SEGMENTO_ID = SegmentoId(1)
        val VOCE_ID = VoceId(1)
        const val DURATA_MS = 60_000L
    }
}
