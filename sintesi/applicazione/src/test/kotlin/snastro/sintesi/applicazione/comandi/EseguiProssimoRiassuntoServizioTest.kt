package snastro.sintesi.applicazione.comandi

import io.mockk.spyk
import io.mockk.verify
import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.sintesi.applicazione.eventi.RiassuntoAvviato
import snastro.sintesi.applicazione.eventi.RiassuntoFallito
import snastro.sintesi.applicazione.eventi.RiassuntoPronto
import snastro.sintesi.applicazione.porte.AzioneRisposta
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguistico
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguisticoFinta
import snastro.sintesi.applicazione.porte.ElementoRisposta
import snastro.sintesi.applicazione.porte.ErroreApplicazioneSintesi
import snastro.sintesi.applicazione.porte.LettoreNomi
import snastro.sintesi.applicazione.porte.LettoreNomiFinta
import snastro.sintesi.applicazione.porte.LettoreTrascritto
import snastro.sintesi.applicazione.porte.LettoreTrascrittoFinta
import snastro.sintesi.applicazione.porte.ModelloLinguistico
import snastro.sintesi.applicazione.porte.ModelloLinguisticoFinto
import snastro.sintesi.applicazione.porte.RiassuntoRepository
import snastro.sintesi.applicazione.porte.RiassuntoRepositoryFinta
import snastro.sintesi.applicazione.porte.RichiestaRiassunto
import snastro.sintesi.applicazione.porte.RispostaModello
import snastro.sintesi.applicazione.porte.SegmentoSintesi
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico
import snastro.sintesi.applicazione.porte.conAvvio
import snastro.sintesi.applicazione.porte.conCompletamento
import snastro.sintesi.applicazione.porte.statoOsservabile
import snastro.sintesi.applicazione.porte.unRiassunto
import snastro.sintesi.applicazione.porte.unaStruttura
import snastro.sintesi.dominio.BozzaRiassunto
import snastro.sintesi.dominio.IngressoRiassunto
import snastro.sintesi.dominio.MotivoFallimento
import snastro.sintesi.dominio.Riassunto
import snastro.sintesi.dominio.RiassuntoId
import snastro.sintesi.dominio.SegmentoIngresso
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [EseguiProssimoRiassuntoServizio] against the ports' fakes (D1): AC-S83..AC-S89. [modello] is built
 * WITH [transazioni], so any read invoked while a transaction is open throws (AC-S84, ADR 0012 (b)) —
 * every test in this file shares that guard. AC-S84 additionally wires [LettoreTrascrittoConGuardia] and
 * [LettoreNomiConGuardia] — the same `check(!transazioneAperta)` for both lettori, so a regression moving
 * either read into the claim transaction is also caught there (rework 1, FAIL 2).
 */
class EseguiProssimoRiassuntoServizioTest {
    private val riassunti = RiassuntoRepositoryFinta()
    private val transazioni = UnitaDiLavoroFinta(riassunti)
    private val eventi = DispatcherEventiFinta(transazioni)
    private val orologio: Clock = Clock.fixed(ADESSO, ZoneOffset.UTC)
    private val modello = ModelloLinguisticoFinto(transazioni)

    private fun servizio(
        trascritti: LettoreTrascritto = LettoreTrascrittoFinta(),
        nomi: LettoreNomi = LettoreNomiFinta(),
        modello: ModelloLinguistico = this.modello,
        disponibilita: DisponibilitaModelloLinguistico =
            DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.Installato),
    ): EseguiProssimoRiassuntoServizio =
        EseguiProssimoRiassuntoServizio(
            eventi.unitaDiLavoro,
            orologio,
            riassunti,
            trascritti,
            nomi,
            modello,
            disponibilita,
            eventi,
        )

    /** Wired with a Trascritto whose Segmenti match [SEGMENTI]: the model's default answer verifies whole. */
    private fun servizioConSegmenti(registrazioneId: RegistrazioneId = REG1): EseguiProssimoRiassuntoServizio =
        servizio(trascritti = LettoreTrascrittoFinta(mapOf(registrazioneId to SEGMENTI)))

    @Test
    fun `AC-S83 tra due in_attesa il piu vecchio diventa in_corso e pubblica RiassuntoAvviato`() {
        riassunti.salva(unRiassunto("r1", REG1, richiestoAlle = T1)).atteso()
        riassunti.salva(unRiassunto("r2", REG2, richiestoAlle = T2)).atteso()
        val nonInstallato = DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.NonInstallato(1))

        val esito = servizio(disponibilita = nonInstallato).esegui(EseguiProssimoRiassunto())

        assertEquals(RisultatoRiassunto.Avviato(RiassuntoId("r1")), esito.atteso())
        assertEquals(ADESSO, checkNotNull(riassunti.trova(RiassuntoId("r1"))).avviatoAlle)
        assertEquals(listOf("r2"), riassunti.inAttesa().map { it.id.valore })
        assertTrue(eventi.pubblicati.contains(RiassuntoAvviato(REG1)))
    }

    @Test
    fun `AC-S83 primaDi uguale al richiestoAlle del piu vecchio rifiuta il claim`() {
        riassunti.salva(unRiassunto("r1", REG1, richiestoAlle = T1)).atteso()
        val nonInstallato = DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.NonInstallato(1))

        val esito = servizio(disponibilita = nonInstallato).esegui(EseguiProssimoRiassunto(primaDi = T1))

        assertEquals(RisultatoRiassunto.Nessuno, esito.atteso())
        assertTrue(checkNotNull(riassunti.trova(RiassuntoId("r1"))).inAttesa)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-S83 un id in esclusi lascia claimare il successivo`() {
        riassunti.salva(unRiassunto("r1", REG1, richiestoAlle = T1)).atteso()
        riassunti.salva(unRiassunto("r2", REG2, richiestoAlle = T2)).atteso()
        val nonInstallato = DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.NonInstallato(1))

        val esito = servizio(disponibilita = nonInstallato).esegui(EseguiProssimoRiassunto(esclusi = setOf("r1")))

        assertEquals(RisultatoRiassunto.Avviato(RiassuntoId("r2")), esito.atteso())
        assertTrue(checkNotNull(riassunti.trova(RiassuntoId("r1"))).inAttesa)
    }

    @Test
    fun `AC-S83 coda vuota restituisce Nessuno senza scrivere nulla`() {
        val esito = servizio().esegui(EseguiProssimoRiassunto())

        assertEquals(RisultatoRiassunto.Nessuno, esito.atteso())
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-S84 il modello e i lettori sono invocati fuori da ogni transazione`() {
        riassunti.salva(unRiassunto("r1", REG1, richiestoAlle = T1)).atteso()
        val trascrittiConGuardia =
            LettoreTrascrittoConGuardia(transazioni, LettoreTrascrittoFinta(mapOf(REG1 to SEGMENTI)))
        val nomiConGuardia = LettoreNomiConGuardia(transazioni, LettoreNomiFinta())

        // ModelloLinguisticoFinto(transazioni) throws if riassumi runs while `transazioni` has an open
        // transaction; trascrittiConGuardia / nomiConGuardia throw the same way for segmenti()/nomi() —
        // reaching a result here already proves phases 2/3's reads/call all ran outside one (rework 1, FAIL 2).
        val esito = servizio(trascritti = trascrittiConGuardia, nomi = nomiConGuardia)
            .esegui(EseguiProssimoRiassunto())

        assertEquals(RisultatoRiassunto.Avviato(RiassuntoId("r1")), esito.atteso())
    }

    @Test
    fun `AC-S85 INV-S10 la richiesta porta l ingresso etichettato l argomento e il cap del Riassunto`() {
        val nomiPorta = LettoreNomiFinta(
            attribuzioni = mapOf(VoceRef(REG1, VoceId(1)) to "parlante-1"),
            nomiParlanti = mapOf("parlante-1" to "Anna"),
        )
        // "Progetto changed to 2500 before the run": this service never re-reads that setting, only
        // the Riassunto's own 1500.
        riassunti.salva(unRiassunto("r1", REG1, argomento = "il combattimento", parole = 1500, richiestoAlle = T1))
            .atteso()

        servizio(trascritti = LettoreTrascrittoFinta(mapOf(REG1 to SEGMENTI)), nomi = nomiPorta)
            .esegui(EseguiProssimoRiassunto()).atteso()

        val inviata = checkNotNull(modello.ultimaRichiesta)
        assertEquals("il combattimento", inviata.argomento)
        assertEquals(1500, inviata.lunghezzaMassimaParole)
        val atteso = IngressoRiassunto.costruisci(
            SEGMENTI.map { SegmentoIngresso(it.segmentoId, it.voceId, it.intervallo.inizioMs, it.testo) },
            mapOf(VoceId(1) to "Anna"), // V2 unattributed: both sides default it to "Voce 2"
        )
        assertEquals(atteso, inviata.ingresso)
    }

    @Test
    fun `AC-S86 INV-S3 il pronto passa solo per concludi che rimuove da solo il pronto precedente`() {
        val repoSpia = spyk(RiassuntoRepositoryFinta())
        val transazioniSpia = UnitaDiLavoroFinta(repoSpia)
        val eventiSpia = DispatcherEventiFinta(transazioniSpia)
        val servizioSpia = EseguiProssimoRiassuntoServizio(
            eventiSpia.unitaDiLavoro,
            orologio,
            repoSpia,
            LettoreTrascrittoFinta(mapOf(REG1 to SEGMENTI)),
            LettoreNomiFinta(),
            ModelloLinguisticoFinto(transazioniSpia),
            DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.Installato),
            eventiSpia,
        )
        val precedente = unRiassunto("precedente", REG1, richiestoAlle = T0).conAvvio()
            .conCompletamento(BOZZA_SOLO_SOMMARIO, unaStruttura(1 to 1))
        repoSpia.salva(precedente).atteso()
        repoSpia.salva(unRiassunto("r1", REG1, richiestoAlle = T1)).atteso()

        servizioSpia.esegui(EseguiProssimoRiassunto()).atteso()

        verify(exactly = 0) { repoSpia.rimuovi(any()) }
        verify(exactly = 0) { repoSpia.rimuoviDiRegistrazione(any()) }
        verify(exactly = 1) { repoSpia.concludi(any()) }
        val pronti = repoSpia.diRegistrazione(REG1).filter { it.pronto }
        assertEquals(listOf("r1"), pronti.map { it.id.valore })
        assertTrue(eventiSpia.pubblicati.contains(RiassuntoPronto(REG1)))
    }

    @Test
    fun `AC-S87 modello non installato fallisce senza chiamare il modello`() {
        val modelloSpia = spyk(ModelloLinguisticoFinto(transazioni))
        riassunti.salva(unRiassunto("r1", REG1, richiestoAlle = T1)).atteso()
        val nonInstallato = DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.NonInstallato(1))

        servizio(disponibilita = nonInstallato, modello = modelloSpia).esegui(EseguiProssimoRiassunto()).atteso()

        verify(exactly = 0) { modelloSpia.riassumi(any(), any()) }
        val concluso = checkNotNull(riassunti.trova(RiassuntoId("r1")))
        assertTrue(concluso.fallito)
        assertEquals(MotivoFallimento.MODELLO_NON_DISPONIBILE, concluso.motivoFallimento)
        assertTrue(eventi.pubblicati.contains(RiassuntoFallito(REG1, "modello_non_disponibile")))
    }

    @Test
    fun `AC-S87 mappa ogni errore del modello al motivo corretto`() {
        val casi = listOf(
            ErroreApplicazioneSintesi.ModelloNonDisponibile to MotivoFallimento.MODELLO_NON_DISPONIBILE,
            ErroreApplicazioneSintesi.IngressoTroppoLungo(31_000) to MotivoFallimento.TROPPO_LUNGA,
            ErroreApplicazioneSintesi.ErroreRuntime("metal non disponibile") to MotivoFallimento.ERRORE_MODELLO,
            ErroreApplicazioneSintesi.RispostaNonValida to MotivoFallimento.ERRORE_MODELLO,
        )

        casi.forEachIndexed { indice, (errore, motivoAtteso) ->
            val reg = RegistrazioneId("registrazione-caso-$indice")
            val modelloCaso = ModelloLinguisticoFinto(transazioni).apply { fallisci(errore) }
            riassunti.salva(unRiassunto("r-caso-$indice", reg, richiestoAlle = T1)).atteso()

            servizio(trascritti = LettoreTrascrittoFinta(mapOf(reg to SEGMENTI)), modello = modelloCaso)
                .esegui(EseguiProssimoRiassunto()).atteso()

            val concluso = checkNotNull(riassunti.trova(RiassuntoId("r-caso-$indice")))
            assertEquals(motivoAtteso, concluso.motivoFallimento, "caso $errore")
            assertTrue(eventi.pubblicati.contains(RiassuntoFallito(reg, motivoAtteso.codice)), "evento caso $errore")
        }
    }

    @Test
    fun `AC-S87 INV-S4 quando la Verifica scarta tutto il Riassunto e fallito nessun_contenuto_verificabile`() {
        modello.rispondi(
            RispostaModello(
                sommario = null,
                decisioni = emptyList(),
                questioniAperte = emptyList(),
                azioni = emptyList(),
                puntiChiave = emptyList(),
            ),
        )
        riassunti.salva(unRiassunto("r1", REG1, richiestoAlle = T1)).atteso()

        servizioConSegmenti().esegui(EseguiProssimoRiassunto()).atteso()

        val concluso = checkNotNull(riassunti.trova(RiassuntoId("r1")))
        assertTrue(concluso.fallito)
        assertEquals(MotivoFallimento.NESSUN_CONTENUTO_VERIFICABILE, concluso.motivoFallimento)
        assertTrue(eventi.pubblicati.contains(RiassuntoFallito(REG1, "nessun_contenuto_verificabile")))
    }

    @Test
    fun `INV-S4 Fonti e un token di Voce fuori dalla struttura del run sono scartati e contati`() {
        // SEGMENTI's structure only has {V1, V2}: fonte 99 doesn't exist, V9 isn't a Voce of the structure.
        modello.rispondi(
            RispostaModello(
                sommario = "{V1} riassume.",
                decisioni = listOf(ElementoRisposta("fuori struttura", fonti = listOf(99))),
                questioniAperte = emptyList(),
                azioni = listOf(AzioneRisposta("{V9} fa qualcosa", fonti = listOf(1), responsabile = null)),
                puntiChiave = emptyList(),
            ),
        )
        riassunti.salva(unRiassunto("r1", REG1, richiestoAlle = T1)).atteso()

        servizioConSegmenti().esegui(EseguiProssimoRiassunto()).atteso()

        val concluso = checkNotNull(riassunti.trova(RiassuntoId("r1")))
        assertTrue(concluso.pronto, "il Sommario solo resta verificabile")
        assertEquals(2, concluso.omessi) // la Decisione (fonte 99) e l'Azione (Voce 9 nel testo)
        assertTrue(concluso.decisioni.isEmpty())
        assertTrue(concluso.azioni.isEmpty())
    }

    @Test
    fun `AC-S87 INV-S3 un fallimento lascia intatto il pronto precedente della stessa Registrazione`() {
        val precedente = unRiassunto("precedente", REG1, richiestoAlle = T0).conAvvio()
            .conCompletamento(BOZZA_SOLO_SOMMARIO, unaStruttura(1 to 1))
        riassunti.salva(precedente).atteso()
        modello.fallisci(ErroreApplicazioneSintesi.RispostaNonValida)
        riassunti.salva(unRiassunto("r1", REG1, richiestoAlle = T1)).atteso()

        servizioConSegmenti().esegui(EseguiProssimoRiassunto()).atteso()

        assertEquals(precedente.statoOsservabile(), checkNotNull(riassunti.trova(precedente.id)).statoOsservabile())
    }

    @Test
    fun `INV-S8 il Riassunto rimosso mentre il modello gira non scrive nulla e non pubblica nulla`() {
        riassunti.salva(unRiassunto("r1", REG1, richiestoAlle = T1)).atteso()
        val modelloCheRimuove = object : ModelloLinguistico {
            override fun riassumi(richiesta: RichiestaRiassunto, annullato: () -> Boolean): Esito<RispostaModello> {
                riassunti.rimuovi(RiassuntoId("r1")).atteso() // a concurrent eliminazione/sostituzione policy
                return Esito.Ok(ModelloLinguisticoFinto.RISPOSTA_PREDEFINITA)
            }
        }

        servizio(trascritti = LettoreTrascrittoFinta(mapOf(REG1 to SEGMENTI)), modello = modelloCheRimuove)
            .esegui(EseguiProssimoRiassunto()).atteso()

        assertNull(riassunti.trova(RiassuntoId("r1")))
        assertTrue(eventi.pubblicati.none { it is RiassuntoPronto || it is RiassuntoFallito })
    }

    @Test
    fun `ADR 0003 un Errore di concludi si propaga da esegui e non pubblica la conclusione`() {
        val concludiGuasto = object : RiassuntoRepository by riassunti {
            override fun concludi(r: Riassunto): Esito<Boolean> = Esito.Errore(ErroreDiProva.Fallito("concludi"))
        }
        riassunti.salva(unRiassunto("r1", REG1, richiestoAlle = T1)).atteso()
        val servizioGuasto = EseguiProssimoRiassuntoServizio(
            eventi.unitaDiLavoro,
            orologio,
            concludiGuasto,
            LettoreTrascrittoFinta(mapOf(REG1 to SEGMENTI)),
            LettoreNomiFinta(),
            modello,
            DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.Installato),
            eventi,
        )

        val esito = servizioGuasto.esegui(EseguiProssimoRiassunto())

        assertEquals(ErroreDiProva.Fallito("concludi"), esito.erroreAtteso<ErroreDiProva.Fallito>())
        assertTrue(checkNotNull(riassunti.trova(RiassuntoId("r1"))).inCorso, "rollback: resta in_corso")
        assertEquals(listOf(RiassuntoAvviato(REG1)), eventi.pubblicati)
    }

    @Test
    fun `INV-S8 Annullato non scrive nulla`() {
        riassunti.salva(unRiassunto("r1", REG1, richiestoAlle = T1)).atteso()
        modello.fallisci(ErroreApplicazioneSintesi.Annullato)

        servizioConSegmenti().esegui(EseguiProssimoRiassunto()).atteso()

        val riassunto = checkNotNull(riassunti.trova(RiassuntoId("r1")))
        assertTrue(riassunto.inCorso, "resta in_corso: nessuna conclusione scritta")
        // The claim itself still publishes RiassuntoAvviato (AC-S83); only the conclusion is skipped.
        assertEquals(listOf(RiassuntoAvviato(REG1)), eventi.pubblicati)
    }

    @Test
    fun `AC-S88 INV-S4 la struttura letta durante l esecuzione rende il Riassunto superato dopo una revisione`() {
        // The FIRST segmenti() call is what the run must use — the one where LettoreTrascritto still shows
        // the pre-Revisione assignment. A SECOND call (only a regression re-reading at completion would make
        // one) returns [dopoRevisione]: real Voci reassigned by a Revisione COMMITTED while this run was
        // in_corso, between the run's own Segmenti read and completion (rework 1, HIGH).
        val dopoRevisione = SEGMENTI.map { it.copy(voceId = if (it.voceId == VoceId(1)) VoceId(2) else VoceId(1)) }
        val trascrittiConRevisioneTardiva = LettoreTrascrittoConRevisioneTardiva(SEGMENTI, dopoRevisione)
        riassunti.salva(unRiassunto("r1", REG1, richiestoAlle = T1)).atteso()

        servizio(trascritti = trascrittiConRevisioneTardiva).esegui(EseguiProssimoRiassunto()).atteso()

        val concluso = checkNotNull(riassunti.trova(RiassuntoId("r1")))
        assertTrue(concluso.pronto)
        val strutturaLettaNelRun = unaStruttura(1 to 1, 2 to 2, 3 to 1) // SEGMENTI, as the run's OWN read saw it
        assertEquals(strutturaLettaNelRun.chiave, concluso.struttura)
        val strutturaCorrenteDelLettore = unaStruttura(1 to 2, 2 to 1, 3 to 2) // what a fresh read gives NOW
        assertTrue(concluso.superato(strutturaCorrenteDelLettore))
    }

    private companion object {
        val REG1: RegistrazioneId = RegistrazioneId("registrazione-1")
        val REG2: RegistrazioneId = RegistrazioneId("registrazione-2")
        val T0: Instant = Instant.parse("2026-09-26T09:00:00Z")
        val T1: Instant = Instant.parse("2026-09-26T10:00:00Z")
        val T2: Instant = Instant.parse("2026-09-26T11:00:00Z")
        val ADESSO: Instant = Instant.parse("2026-09-26T12:00:00Z")

        /** Matches [ModelloLinguisticoFinto.RISPOSTA_PREDEFINITA]: the Verifica keeps it whole (omessi = 0). */
        val SEGMENTI: List<SegmentoSintesi> = listOf(
            SegmentoSintesi(
                segmentoId = SegmentoId(1),
                voceId = VoceId(1),
                intervallo = IntervalloMs(0, 7_000),
                testo = "Decidiamo di tenere il combattimento a turni.",
            ),
            SegmentoSintesi(
                segmentoId = SegmentoId(2),
                voceId = VoceId(2),
                intervallo = IntervalloMs(7_000, 15_000),
                testo = "Va bene, preparo io il prototipo entro venerdi.",
            ),
            SegmentoSintesi(
                segmentoId = SegmentoId(3),
                voceId = VoceId(1),
                intervallo = IntervalloMs(15_000, 20_000),
                testo = "Resta da capire quanti nemici per stanza.",
            ),
        )

        val BOZZA_SOLO_SOMMARIO: BozzaRiassunto = BozzaRiassunto(
            sommario = "{V1} riassume.",
            decisioni = emptyList(),
            questioniAperte = emptyList(),
            azioni = emptyList(),
            puntiChiave = emptyList(),
        )
    }
}

/**
 * Delegates to [delegato], but throws if invoked while [transazioni] has a transaction open — the same
 * ADR 0012 (b) guard [ModelloLinguisticoFinto] carries for the model, here for [LettoreTrascritto]
 * (AC-S84, rework 1 FAIL 2).
 */
private class LettoreTrascrittoConGuardia(
    private val transazioni: UnitaDiLavoroFinta,
    private val delegato: LettoreTrascritto,
) : LettoreTrascritto {
    override fun segmenti(r: RegistrazioneId): List<SegmentoSintesi>? {
        check(!transazioni.transazioneAperta) { "segmenti invocato dentro una transazione (ADR 0012 (b))" }
        return delegato.segmenti(r)
    }

    override fun elaborazioneAperta(r: RegistrazioneId): Boolean = delegato.elaborazioneAperta(r)
}

/** Same guard as [LettoreTrascrittoConGuardia], for [LettoreNomi] (AC-S84, rework 1 FAIL 2). */
private class LettoreNomiConGuardia(
    private val transazioni: UnitaDiLavoroFinta,
    private val delegato: LettoreNomi,
) : LettoreNomi {
    override fun nomi(r: RegistrazioneId): Map<VoceRef, String> {
        check(!transazioni.transazioneAperta) { "nomi invocato dentro una transazione (ADR 0012 (b))" }
        return delegato.nomi(r)
    }
}

/**
 * Simulates a Revisione COMMITTED by another command while this run is `in_corso`: the FIRST [segmenti]
 * call — the one this run's own phase 2 makes — returns [original]; any further call returns
 * [dopoRevisione] instead. Only a regression re-reading the Segmenti at completion (rather than reusing
 * the structure captured during the run) would ever trigger a second call (AC-S88, rework 1 HIGH).
 */
private class LettoreTrascrittoConRevisioneTardiva(
    private val original: List<SegmentoSintesi>,
    private val dopoRevisione: List<SegmentoSintesi>,
) : LettoreTrascritto {
    private var chiamate = 0

    override fun segmenti(r: RegistrazioneId): List<SegmentoSintesi>? {
        chiamate++
        return if (chiamate == 1) original else dopoRevisione
    }

    override fun elaborazioneAperta(r: RegistrazioneId): Boolean = false
}
