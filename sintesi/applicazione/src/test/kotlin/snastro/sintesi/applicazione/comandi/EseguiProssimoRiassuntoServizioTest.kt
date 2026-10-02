package snastro.sintesi.applicazione.comandi

import io.mockk.spyk
import io.mockk.verify
import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.SegmentoRef
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.kernel.unIncontroDi
import snastro.sintesi.applicazione.eventi.RiassuntoAvviato
import snastro.sintesi.applicazione.eventi.RiassuntoFallito
import snastro.sintesi.applicazione.eventi.RiassuntoPronto
import snastro.sintesi.applicazione.porte.AzioneRisposta
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguistico
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguisticoFinta
import snastro.sintesi.applicazione.porte.ElementoRisposta
import snastro.sintesi.applicazione.porte.ErroreApplicazioneSintesi
import snastro.sintesi.applicazione.porte.LettoreIncontroFinta
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
import snastro.sintesi.applicazione.porte.StatoParteSintesi
import snastro.sintesi.applicazione.porte.conAvvio
import snastro.sintesi.applicazione.porte.conCompletamento
import snastro.sintesi.applicazione.porte.inIngresso
import snastro.sintesi.applicazione.porte.ogniIncontroConUnaParte
import snastro.sintesi.applicazione.porte.statoOsservabile
import snastro.sintesi.applicazione.porte.unRiassunto
import snastro.sintesi.applicazione.porte.unaStruttura
import snastro.sintesi.dominio.BozzaRiassunto
import snastro.sintesi.dominio.IngressoRiassunto
import snastro.sintesi.dominio.MotivoFallimento
import snastro.sintesi.dominio.Riassunto
import snastro.sintesi.dominio.RiassuntoId
import snastro.sintesi.dominio.SegmentoIngresso
import snastro.sintesi.dominio.StatoParte
import snastro.sintesi.dominio.StrutturaIncontro
import snastro.sintesi.dominio.StrutturaTrascritto
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
 * every test in this file shares that guard. AC-S84 additionally wires [LettoreTrascrittoConGuardia] — the same
 * `check(!transazioneAperta)` on the read, so a regression moving it into the claim transaction is also caught
 * there (rework 1, FAIL 2).
 */
class EseguiProssimoRiassuntoServizioTest {
    private val riassunti = RiassuntoRepositoryFinta()
    private val transazioni = UnitaDiLavoroFinta(riassunti)
    private val eventi = DispatcherEventiFinta(transazioni)
    private val orologio: Clock = Clock.fixed(ADESSO, ZoneOffset.UTC)
    private val modello = ModelloLinguisticoFinto(transazioni)

    private fun servizio(
        trascritti: LettoreTrascritto = LettoreTrascrittoFinta(),
        modello: ModelloLinguistico = this.modello,
        disponibilita: DisponibilitaModelloLinguistico =
            DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.Installato),
    ): EseguiProssimoRiassuntoServizio =
        EseguiProssimoRiassuntoServizio(
            eventi.unitaDiLavoro,
            orologio,
            riassunti,
            trascritti,
            ogniIncontroConUnaParte(),
            modello,
            disponibilita,
            eventi,
        )

    /** An Incontro read live from [parti] (mutable on purpose); the Trascritti of REG1 and REG2 per the flags. */
    private fun servizioMultiParte(
        parti: Map<IncontroId, List<RegistrazioneId>>,
        modello: ModelloLinguistico = this.modello,
        conSegmenti: Boolean = true,
        conSegmentiParte2: Boolean = true,
    ): EseguiProssimoRiassuntoServizio = EseguiProssimoRiassuntoServizio(
        eventi.unitaDiLavoro,
        orologio,
        riassunti,
        LettoreTrascrittoFinta(
            listOfNotNull(
                (REG1 to SEGMENTI).takeIf { conSegmenti },
                (REG2 to SEGMENTI_PARTE_2).takeIf { conSegmentiParte2 },
            ).toMap(),
        ),
        LettoreIncontroFinta(parti),
        modello,
        DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.Installato),
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
        assertTrue(eventi.pubblicati.contains(RiassuntoAvviato(unIncontroDi(REG1))))
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
    fun `AC-S83 esclusi e primaDi insieme, il vincolo primaDi si applica al primo candidato non escluso`() {
        riassunti.salva(unRiassunto("r1", REG1, richiestoAlle = T1)).atteso()
        riassunti.salva(unRiassunto("r2", REG2, richiestoAlle = T2)).atteso()
        riassunti.salva(unRiassunto("r3", REG3, richiestoAlle = T3)).atteso()
        val nonInstallato = DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.NonInstallato(1))

        // r1 escluso: r2 e' il primo candidato, ma richiestoAlle=T2 non e' < primaDi=T2 (limite stretto): rifiutato
        // — r3 (che rispetterebbe da solo il vincolo) non e' scansionato dopo quel rifiuto.
        val rifiutato = servizio(disponibilita = nonInstallato)
            .esegui(EseguiProssimoRiassunto(esclusi = setOf("r1"), primaDi = T2))
        assertEquals(RisultatoRiassunto.Nessuno, rifiutato.atteso())
        assertTrue(checkNotNull(riassunti.trova(RiassuntoId("r2"))).inAttesa, "r2 non claimato")
        assertTrue(checkNotNull(riassunti.trova(RiassuntoId("r3"))).inAttesa, "r3 mai raggiunto")

        // Stesso esclusi, primaDi ora oltre T2: r2 rispetta il vincolo e viene claimato.
        val claimato = servizio(disponibilita = nonInstallato)
            .esegui(EseguiProssimoRiassunto(esclusi = setOf("r1"), primaDi = T2.plusSeconds(1)))
        assertEquals(RisultatoRiassunto.Avviato(RiassuntoId("r2")), claimato.atteso())
    }

    @Test
    fun `AC-S84 il modello e i lettori sono invocati fuori da ogni transazione`() {
        riassunti.salva(unRiassunto("r1", REG1, richiestoAlle = T1)).atteso()
        val trascrittiConGuardia =
            LettoreTrascrittoConGuardia(transazioni, LettoreTrascrittoFinta(mapOf(REG1 to SEGMENTI)))

        // ModelloLinguisticoFinto(transazioni) throws if riassumi runs while `transazioni` has an open
        // transaction; trascrittiConGuardia throws the same way for segmenti() — reaching a result here already
        // proves phases 2/3's read/call all ran outside one (rework 1, FAIL 2).
        val esito = servizio(trascritti = trascrittiConGuardia)
            .esegui(EseguiProssimoRiassunto())

        assertEquals(RisultatoRiassunto.Avviato(RiassuntoId("r1")), esito.atteso())
    }

    @Test
    fun `AC-S85 INV-S10 la richiesta porta l ingresso con legenda Voce n anche se c e un Nome, l argomento e il cap`() {
        // INV-S10: this service has no LunghezzaMassimaRiassuntoRepository collaborator at all (only
        // ModificaLunghezzaMassimaRiassuntoServizio writes it) — it holds by construction, not by re-checking a
        // scenario here; the cap it can send is only ever the Riassunto's OWN [parole], fixed at Riassumi time.
        riassunti.salva(unRiassunto("r1", REG1, argomento = "il combattimento", parole = 1500, richiestoAlle = T1))
            .atteso()

        servizio(trascritti = LettoreTrascrittoFinta(mapOf(REG1 to SEGMENTI)))
            .esegui(EseguiProssimoRiassunto()).atteso()

        val inviata = checkNotNull(modello.ultimaRichiesta)
        assertEquals("il combattimento", inviata.argomento)
        assertEquals(1500, inviata.lunghezzaMassimaParole)
        val atteso = IngressoRiassunto.costruisci(
            listOf(SEGMENTI.map { SegmentoIngresso(REG1, it.segmentoId, it.voceId, it.intervallo.inizioMs, it.testo) }),
        )
        assertEquals(atteso.testo, inviata.ingresso)
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
            ogniIncontroConUnaParte(),
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
        verify(exactly = 0) { repoSpia.rimuoviDiIncontro(any()) }
        verify(exactly = 1) { repoSpia.concludi(any()) }
        val pronti = repoSpia.trova(unIncontroDi(REG1)).filter { it.pronto }
        assertEquals(listOf("r1"), pronti.map { it.id.valore })
        assertTrue(eventiSpia.pubblicati.contains(RiassuntoPronto(unIncontroDi(REG1))))
    }

    @Test
    fun `AC-S87 modello non installato fallisce senza chiamare il modello`() {
        // ModelloLinguisticoFinto already records `ultimaRichiesta` (null unless invoked, AC-S15): a recording
        // fake, so a MockK spy is not the only alternative here (RC-9) and is dropped.
        val modelloReale = ModelloLinguisticoFinto(transazioni)
        riassunti.salva(unRiassunto("r1", REG1, richiestoAlle = T1)).atteso()
        val nonInstallato = DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.NonInstallato(1))

        servizio(disponibilita = nonInstallato, modello = modelloReale).esegui(EseguiProssimoRiassunto()).atteso()

        assertNull(modelloReale.ultimaRichiesta, "il modello non e' mai stato chiamato")
        val concluso = checkNotNull(riassunti.trova(RiassuntoId("r1")))
        assertTrue(concluso.fallito)
        assertEquals(MotivoFallimento.MODELLO_NON_DISPONIBILE, concluso.motivoFallimento)
        assertTrue(eventi.pubblicati.contains(RiassuntoFallito(unIncontroDi(REG1), "modello_non_disponibile")))
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
            // INV-S3: a pronto precedente of the SAME Registrazione, seeded for every case (not only
            // RispostaNonValida, which the separate INV-S3 test below already covers on its own) — a failure
            // never touches it, whatever the model's own error.
            val precedente = unRiassunto("precedente-caso-$indice", reg, richiestoAlle = T0).conAvvio()
                .conCompletamento(BOZZA_SOLO_SOMMARIO, unaStruttura(1 to 1))
            riassunti.salva(precedente).atteso()
            val precedentePrima = precedente.statoOsservabile()
            riassunti.salva(unRiassunto("r-caso-$indice", reg, richiestoAlle = T1)).atteso()

            servizio(trascritti = LettoreTrascrittoFinta(mapOf(reg to SEGMENTI)), modello = modelloCaso)
                .esegui(EseguiProssimoRiassunto()).atteso()

            val concluso = checkNotNull(riassunti.trova(RiassuntoId("r-caso-$indice")))
            assertEquals(motivoAtteso, concluso.motivoFallimento, "caso $errore")
            assertTrue(
                eventi.pubblicati.contains(RiassuntoFallito(unIncontroDi(reg), motivoAtteso.codice)),
                "evento caso $errore",
            )
            assertEquals(
                precedentePrima,
                checkNotNull(riassunti.trova(precedente.id)).statoOsservabile(),
                "caso $errore: il pronto precedente resta byte-identico",
            )
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
        assertTrue(eventi.pubblicati.contains(RiassuntoFallito(unIncontroDi(REG1), "nessun_contenuto_verificabile")))
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
    fun `INV-I12 nessuna Parte con un Trascritto dopo il claim e fallito nessun_contenuto_verificabile`() {
        riassunti.salva(unRiassunto("r1", REG1, richiestoAlle = T1)).atteso()

        servizio(trascritti = LettoreTrascrittoFinta()).esegui(EseguiProssimoRiassunto()).atteso()

        val riassunto = checkNotNull(riassunti.trova(RiassuntoId("r1")))
        assertTrue(riassunto.fallito)
        assertEquals(MotivoFallimento.NESSUN_CONTENUTO_VERIFICABILE, riassunto.motivoFallimento)
        assertNull(modello.ultimaRichiesta, "il modello non e' chiamato senza testo")
        assertTrue(eventi.pubblicati.contains(RiassuntoFallito(unIncontroDi(REG1), "nessun_contenuto_verificabile")))
    }

    @Test
    fun `INV-I12 l Incontro cessato durante il run non scrive nulla e non pubblica la conclusione`() {
        riassunti.salva(unRiassunto("r1", INCONTRO, richiestoAlle = T1)).atteso()
        val parti = mutableMapOf(INCONTRO to listOf(REG1, REG2))
        val modelloCheCessa = object : ModelloLinguistico {
            override fun riassumi(richiesta: RichiestaRiassunto, annullato: () -> Boolean): Esito<RispostaModello> {
                riassunti.rimuovi(RiassuntoId("r1")).atteso() // the Incontro ceased: its Riassunto is removed
                return Esito.Ok(ModelloLinguisticoFinto.RISPOSTA_PREDEFINITA)
            }
        }

        servizioMultiParte(parti, modello = modelloCheCessa).esegui(EseguiProssimoRiassunto()).atteso()

        assertNull(riassunti.trova(RiassuntoId("r1")))
        assertTrue(eventi.pubblicati.none { it is RiassuntoPronto || it is RiassuntoFallito })
    }

    @Test
    fun `INV-I12 l Incontro gia cessato al run e trattato come CAS fallito, mai un throw`() {
        riassunti.salva(unRiassunto("r1", INCONTRO, richiestoAlle = T1)).atteso()

        servizioMultiParte(emptyMap()).esegui(EseguiProssimoRiassunto()).atteso()

        assertTrue(checkNotNull(riassunti.trova(RiassuntoId("r1"))).inCorso)
        assertNull(modello.ultimaRichiesta)
        assertEquals(listOf(RiassuntoAvviato(INCONTRO)), eventi.pubblicati)
    }

    @Test
    fun `AC-I36 un Incontro di 2 Parti manda al modello UN ingresso su entrambe e salva le Fonti giuste`() {
        riassunti.salva(unRiassunto("r1", INCONTRO, richiestoAlle = T1)).atteso()
        // s1..s3 = Parte 1, s4..s5 = Parte 2 (segmentoId 1 and 2 of REG2: the same numbers as REG1's, other Parte).
        modello.rispondi(
            RispostaModello(
                sommario = "{V1} e {V3} riassumono.",
                decisioni = listOf(ElementoRisposta("Si tiene il turni", fonti = listOf(1, 5))),
                questioniAperte = emptyList(),
                azioni = emptyList(),
                puntiChiave = emptyList(),
            ),
        )

        servizioMultiParte(mapOf(INCONTRO to listOf(REG1, REG2))).esegui(EseguiProssimoRiassunto()).atteso()

        val atteso = IngressoRiassunto.costruisci(
            listOf(
                SEGMENTI.map { it.inIngresso(REG1) },
                SEGMENTI_PARTE_2.map { it.inIngresso(REG2) },
            ),
        )
        assertEquals(atteso.testo, checkNotNull(modello.ultimaRichiesta).ingresso)
        assertTrue(atteso.testo.contains("[s4 V3] Seconda parte, uno."))
        val concluso = checkNotNull(riassunti.trova(RiassuntoId("r1")))
        assertTrue(concluso.pronto)
        assertEquals(0, concluso.omessi)
        assertEquals(
            setOf(SegmentoRef(REG1, SegmentoId(1)), SegmentoRef(REG2, SegmentoId(2))),
            concluso.decisioni.single().fonti,
        )
        val corrente = StrutturaIncontro(
            listOf(REG1 to unaStruttura(1 to 1, 2 to 2, 3 to 1), REG2 to unaStruttura(1 to 3, 2 to 3)),
        )
        assertEquals(corrente.chiave, concluso.struttura)
        assertTrue(!concluso.superato(corrente))
    }

    @Test
    fun `INV-I12 la Parte 2 senza Trascritto al claim gira sulla sola Parte 1 e nasce superato`() {
        riassunti.salva(unRiassunto("r1", INCONTRO, richiestoAlle = T1)).atteso()

        servizioMultiParte(mapOf(INCONTRO to listOf(REG1, REG2)), conSegmentiParte2 = false)
            .esegui(EseguiProssimoRiassunto()).atteso()

        val concluso = checkNotNull(riassunti.trova(RiassuntoId("r1")))
        assertTrue(concluso.pronto)
        val soloParte1 = IngressoRiassunto.costruisci(listOf(SEGMENTI.map { it.inIngresso(REG1) }))
        assertEquals(soloParte1.testo, checkNotNull(modello.ultimaRichiesta).ingresso)
        val strutturaParte1 = unaStruttura(1 to 1, 2 to 2, 3 to 1)
        assertEquals("${REG1.valore}=${strutturaParte1.chiave}", concluso.struttura)
        assertTrue(!concluso.superato(StrutturaIncontro(listOf(REG1 to strutturaParte1))))
        val conParte2Trascritta = StrutturaIncontro(listOf(REG1 to strutturaParte1, REG2 to unaStruttura(1 to 3)))
        assertTrue(concluso.superato(conParte2Trascritta), "la Parte 2 senza Trascritto: nato superato")
    }

    @Test
    fun `INV-I12 una Revisione tra Parti durante il run fa nascere il Riassunto superato`() {
        riassunti.salva(unRiassunto("r1", INCONTRO, richiestoAlle = T1)).atteso()
        val rivista = SEGMENTI_PARTE_2.map { it.copy(voceId = VoceId(1)) }
        val modelloCheRivede = object : ModelloLinguistico {
            override fun riassumi(richiesta: RichiestaRiassunto, annullato: () -> Boolean): Esito<RispostaModello> =
                Esito.Ok(ModelloLinguisticoFinto.RISPOSTA_PREDEFINITA)
        }
        val strutturaRivista = StrutturaTrascritto.di(rivista.map { it.segmentoId to it.voceId })
        val corrente = StrutturaIncontro(
            listOf(REG1 to unaStruttura(1 to 1, 2 to 2, 3 to 1), REG2 to strutturaRivista),
        )

        servizioMultiParte(mapOf(INCONTRO to listOf(REG1, REG2)), modello = modelloCheRivede)
            .esegui(EseguiProssimoRiassunto()).atteso()

        val concluso = checkNotNull(riassunti.trova(RiassuntoId("r1")))
        assertTrue(concluso.pronto)
        assertTrue(concluso.superato(corrente), "la struttura registrata e' quella letta dal run, non la rivista")
    }

    @Test
    fun `AC-I210 una Parte non ultima eliminata prima del run il Riassunto completa sulle Parti rimaste`() {
        riassunti.salva(unRiassunto("r1", INCONTRO, richiestoAlle = T1)).atteso()

        // Parte 1 deleted while the Riassunto was queued: only REG2 is listed now.
        servizioMultiParte(mapOf(INCONTRO to listOf(REG2))).esegui(EseguiProssimoRiassunto()).atteso()

        val concluso = checkNotNull(riassunti.trova(RiassuntoId("r1")))
        assertTrue(concluso.pronto, "mai lasciato in_corso")
        assertEquals(
            "${REG2.valore}=${unaStruttura(1 to 3, 2 to 3).chiave}",
            concluso.struttura,
        )
        assertTrue(riassunti.inCorso().isEmpty() && riassunti.inAttesa().isEmpty())
    }

    @Test
    fun `AC-I210 una Parte non ultima eliminata durante il run il Riassunto completa e nasce superato`() {
        riassunti.salva(unRiassunto("r1", INCONTRO, richiestoAlle = T1)).atteso()
        val parti = mutableMapOf(INCONTRO to listOf(REG1, REG2))
        val modelloCheElimina = object : ModelloLinguistico {
            override fun riassumi(richiesta: RichiestaRiassunto, annullato: () -> Boolean): Esito<RispostaModello> {
                parti[INCONTRO] = listOf(REG2) // the Parte 1 is eliminated while the model runs
                return Esito.Ok(
                    RispostaModello(
                        sommario = "{V3} riassume.",
                        decisioni = emptyList(),
                        questioniAperte = emptyList(),
                        azioni = emptyList(),
                        puntiChiave = emptyList(),
                    ),
                )
            }
        }

        servizioMultiParte(parti, modello = modelloCheElimina).esegui(EseguiProssimoRiassunto()).atteso()

        val concluso = checkNotNull(riassunti.trova(RiassuntoId("r1")))
        assertTrue(concluso.pronto, "mai lasciato in_corso")
        assertTrue(concluso.superato(StrutturaIncontro(listOf(REG2 to unaStruttura(1 to 3, 2 to 3)))))
        assertTrue(riassunti.inCorso().isEmpty())
    }

    @Test
    fun `AC-I210 con la sola Parte rimasta senza Trascritto il Riassunto e fallito, non in_corso`() {
        riassunti.salva(unRiassunto("r1", INCONTRO, richiestoAlle = T1)).atteso()

        servizioMultiParte(mapOf(INCONTRO to listOf(REG1, REG2)), conSegmenti = false, conSegmentiParte2 = false)
            .esegui(EseguiProssimoRiassunto()).atteso()

        val concluso = checkNotNull(riassunti.trova(RiassuntoId("r1")))
        assertTrue(concluso.fallito)
        assertEquals(MotivoFallimento.NESSUN_CONTENUTO_VERIFICABILE, concluso.motivoFallimento)
        assertTrue(riassunti.inCorso().isEmpty())
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
            ogniIncontroConUnaParte(),
            modello,
            DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.Installato),
            eventi,
        )

        val esito = servizioGuasto.esegui(EseguiProssimoRiassunto())

        assertEquals(ErroreDiProva.Fallito("concludi"), esito.erroreAtteso<ErroreDiProva.Fallito>())
        assertTrue(checkNotNull(riassunti.trova(RiassuntoId("r1"))).inCorso, "rollback: resta in_corso")
        assertEquals(listOf(RiassuntoAvviato(unIncontroDi(REG1))), eventi.pubblicati)
    }

    @Test
    fun `INV-S8 Annullato non scrive nulla`() {
        riassunti.salva(unRiassunto("r1", REG1, richiestoAlle = T1)).atteso()
        modello.fallisci(ErroreApplicazioneSintesi.Annullato)

        servizioConSegmenti().esegui(EseguiProssimoRiassunto()).atteso()

        val riassunto = checkNotNull(riassunti.trova(RiassuntoId("r1")))
        assertTrue(riassunto.inCorso, "resta in_corso: nessuna conclusione scritta")
        // The claim itself still publishes RiassuntoAvviato (AC-S83); only the conclusion is skipped.
        assertEquals(listOf(RiassuntoAvviato(unIncontroDi(REG1))), eventi.pubblicati)
    }

    @Test
    fun `AC-S88 la struttura letta durante l esecuzione rende il Riassunto superato dopo una revisione`() {
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
        assertEquals("${REG1.valore}=${strutturaLettaNelRun.chiave}", concluso.struttura)
        val strutturaCorrenteDelLettore = unaStruttura(1 to 2, 2 to 1, 3 to 2) // what a fresh read gives NOW
        assertTrue(concluso.superato(StrutturaIncontro(listOf(REG1 to strutturaCorrenteDelLettore))))
    }

    @Test
    fun `AC-S88 la lettura della fase 2 e' quella corrente dopo il claim, non una vista precedente`() {
        // The OTHER direction from the test above: the claim (phase 1) never calls LettoreTrascritto at all, so
        // there is no earlier read to go stale — but nothing besides AC-S84's "outside a transaction" timing
        // guard was locking that the run's OWN read reflects a Revisione committed after the claim, rather than
        // some state assumed by construction. This asserts the stored struttura against that revised data itself.
        val revisionata = SEGMENTI.map { if (it.voceId == VoceId(1)) it.copy(voceId = VoceId(3)) else it }
        riassunti.salva(unRiassunto("r1", REG1, richiestoAlle = T1)).atteso()

        servizio(trascritti = LettoreTrascrittoFinta(mapOf(REG1 to revisionata))).esegui(EseguiProssimoRiassunto())
            .atteso()

        val concluso = checkNotNull(riassunti.trova(RiassuntoId("r1")))
        assertTrue(concluso.pronto)
        val strutturaRivista = unaStruttura(1 to 3, 2 to 2, 3 to 3)
        assertEquals(
            "${REG1.valore}=${strutturaRivista.chiave}",
            concluso.struttura,
            "il run vede la revisione, non uno stato precedente",
        )
    }

    private companion object {
        val REG1: RegistrazioneId = RegistrazioneId("registrazione-1")
        val REG2: RegistrazioneId = RegistrazioneId("registrazione-2")
        val REG3: RegistrazioneId = RegistrazioneId("registrazione-3")
        val T0: Instant = Instant.parse("2026-09-26T09:00:00Z")
        val T1: Instant = Instant.parse("2026-09-26T10:00:00Z")
        val T2: Instant = Instant.parse("2026-09-26T11:00:00Z")
        val T3: Instant = Instant.parse("2026-09-26T11:30:00Z")
        val INCONTRO: IncontroId = IncontroId("incontro-2-parti")
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

        /** Parte 2 of [INCONTRO]: Voci 3 (a Voce of the Incontro unseen in Parte 1). */
        val SEGMENTI_PARTE_2: List<SegmentoSintesi> = listOf(
            SegmentoSintesi(SegmentoId(1), VoceId(3), IntervalloMs(0, 5_000), "Seconda parte, uno."),
            SegmentoSintesi(SegmentoId(2), VoceId(3), IntervalloMs(5_000, 9_000), "Seconda parte, due."),
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

    override fun statoParte(r: RegistrazioneId): StatoParteSintesi = delegato.statoParte(r)
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

    override fun statoParte(r: RegistrazioneId): StatoParteSintesi = StatoParte.TRASCRITTA
}
