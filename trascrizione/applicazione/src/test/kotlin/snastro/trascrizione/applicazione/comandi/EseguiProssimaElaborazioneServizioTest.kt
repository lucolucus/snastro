package snastro.trascrizione.applicazione.comandi

import snastro.kernel.CampioniAudio
import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.kernel.IntervalloMs
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.Ripristinabile
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.trascrizione.applicazione.eventi.ElaborazioneAvviata
import snastro.trascrizione.applicazione.eventi.ElaborazioneCompletata
import snastro.trascrizione.applicazione.eventi.ElaborazioneFallita
import snastro.trascrizione.applicazione.porte.Allineatore
import snastro.trascrizione.applicazione.porte.AllineatoreFinta
import snastro.trascrizione.applicazione.porte.DecodificatoreAudio
import snastro.trascrizione.applicazione.porte.DecodificatoreAudioFinta
import snastro.trascrizione.applicazione.porte.Diarizzatore
import snastro.trascrizione.applicazione.porte.DiarizzatoreFinta
import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.applicazione.porte.ElaborazioneRepositoryFinta
import snastro.trascrizione.applicazione.porte.FaseElaborazione
import snastro.trascrizione.applicazione.porte.FaseElaborazione.ALLINEAMENTO
import snastro.trascrizione.applicazione.porte.FaseElaborazione.DECODIFICA
import snastro.trascrizione.applicazione.porte.FaseElaborazione.DIARIZZAZIONE
import snastro.trascrizione.applicazione.porte.FaseElaborazione.TRASCRIZIONE
import snastro.trascrizione.applicazione.porte.LettoreRegistrazione
import snastro.trascrizione.applicazione.porte.LettoreRegistrazioneFinta
import snastro.trascrizione.applicazione.porte.RegistrazioneVista
import snastro.trascrizione.applicazione.porte.SegmentoGrezzo
import snastro.trascrizione.applicazione.porte.SegnalatoreFase
import snastro.trascrizione.applicazione.porte.SegnalatoreFaseFinta
import snastro.trascrizione.applicazione.porte.TrascrittoRepository
import snastro.trascrizione.applicazione.porte.TrascrittoRepositoryFinta
import snastro.trascrizione.applicazione.porte.Turno
import snastro.trascrizione.dominio.Elaborazione
import snastro.trascrizione.dominio.ErroreTrascrizione
import snastro.trascrizione.dominio.NumeroPersone
import snastro.trascrizione.dominio.StatoElaborazione
import snastro.trascrizione.dominio.Trascritto
import snastro.trascrizione.dominio.unaElaborazione
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EseguiProssimaElaborazioneServizioTest {
    @Test
    fun `AC-68 senza in_attesa il comando non ha effetti`() {
        val elaborazioni = ElaborazioneRepositoryFinta()
        val trascritti = TrascrittoRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))
        val servizio = servizio(eventi.unitaDiLavoro, eventi, elaborazioni, trascritti, pipeline())

        servizio.esegui(EseguiProssimaElaborazione).atteso()

        assertEquals(emptyList(), eventi.pubblicati)
        assertEquals(emptyList(), trascritti.conTrascritto())
    }

    @Test
    fun `AC-68 parte sempre la piu vecchia in_attesa FIFO`() {
        val elaborazioni = ElaborazioneRepositoryFinta()
        val trascritti = TrascrittoRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))
        val vecchia = RegistrazioneId("registrazione-vecchia")
        val recente = RegistrazioneId("registrazione-recente")
        val riferimentoVecchia = RiferimentoAudio("audio/vecchia.m4a")
        val riferimentoRecente = RiferimentoAudio("audio/recente.m4a")
        val registrazioni = LettoreRegistrazioneFinta(
            mapOf(
                vecchia to unaVista(vecchia, riferimentoVecchia, DURATA),
                recente to unaVista(recente, riferimentoRecente, DURATA),
            ),
        )
        val decodificatore = DecodificatoreAudioFinta(mapOf(riferimentoVecchia to DURATA, riferimentoRecente to DURATA))
        val servizio = servizio(
            eventi.unitaDiLavoro,
            eventi,
            elaborazioni,
            trascritti,
            pipeline(registrazioni = registrazioni, decodificatore = decodificatore),
        )
        elaborazioni.salva(unaInAttesa(recente, CREATA_RECENTE)).atteso()
        elaborazioni.salva(unaInAttesa(vecchia, CREATA_VECCHIA)).atteso()

        servizio.esegui(EseguiProssimaElaborazione).atteso()

        assertEquals(listOf(recente), elaborazioni.inAttesa().map { it.registrazioneId })
        assertTrue(elaborazioni.diRegistrazione(vecchia).single().completata)
    }

    @Test
    fun `AC-69 le fasi sono segnalate in ordine decodifica diarizzazione trascrizione allineamento`() {
        val id = RegistrazioneId("registrazione-1")
        val riferimento = RiferimentoAudio("audio/registrazione-1.m4a")
        val elaborazioni = ElaborazioneRepositoryFinta()
        val trascritti = TrascrittoRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))
        val segnalatore = SegnalatoreFaseFinta()
        val registrazioni = LettoreRegistrazioneFinta(mapOf(id to unaVista(id, riferimento, DURATA)))
        val decodificatore = DecodificatoreAudioFinta(mapOf(riferimento to DURATA))
        val servizio = servizio(
            eventi.unitaDiLavoro,
            eventi,
            elaborazioni,
            trascritti,
            pipeline(registrazioni = registrazioni, decodificatore = decodificatore, segnalatore = segnalatore),
        )
        elaborazioni.salva(unaInAttesa(id)).atteso()

        servizio.esegui(EseguiProssimaElaborazione).atteso()

        assertEquals(listOf(DECODIFICA, DIARIZZAZIONE, TRASCRIZIONE, ALLINEAMENTO), segnalatore.fasi(id))
        assertEquals(listOf(id), segnalatore.terminate)
        assertTrue(elaborazioni.diRegistrazione(id).single().completata)
        assertEquals(
            listOf(ElaborazioneAvviata(id, OROLOGIO.instant()), ElaborazioneCompletata(id)),
            eventi.pubblicati,
            "il successo deve pubblicare ElaborazioneCompletata",
        )
    }

    private val vistaGuasto = LettoreRegistrazioneFinta(
        mapOf(REGISTRAZIONE_GUASTO to unaVista(REGISTRAZIONE_GUASTO, RIFERIMENTO_GUASTO, DURATA)),
    )
    private val decodificaGuasto = DecodificatoreAudioFinta(mapOf(RIFERIMENTO_GUASTO to DURATA))

    @Test
    fun `AC-70 registrazione non trovata diventa fallita con motivo semplice`() {
        verificaGuastoDiPipeline(
            motivoAtteso = "registrazione non più disponibile",
            faseAttesa = emptyList(),
            portePipeline = pipeline(registrazioni = LettoreRegistrazioneFinta(emptyMap())),
        )
    }

    @Test
    fun `AC-70 decodifica guasta (tutti lancia) diventa fallita con motivo semplice`() {
        verificaGuastoDiPipeline(
            motivoAtteso = "impossibile leggere l'audio",
            faseAttesa = listOf(DECODIFICA),
            portePipeline = pipeline(
                registrazioni = vistaGuasto,
                decodificatore = DecodificatoreCheFallisceSuTutti(decodificaGuasto),
            ),
        )
    }

    @Test
    fun `AC-70 diarizzazione guasta diventa fallita con motivo semplice`() {
        verificaGuastoDiPipeline(
            motivoAtteso = "errore nella separazione delle voci",
            faseAttesa = listOf(DECODIFICA, DIARIZZAZIONE),
            portePipeline = pipeline(
                registrazioni = vistaGuasto,
                decodificatore = decodificaGuasto,
                diarizzatore = DiarizzatoreCheLancia(),
            ),
        )
    }

    @Test
    fun `AC-70 allineamento guasto diventa fallita con motivo semplice`() {
        verificaGuastoDiPipeline(
            motivoAtteso = "errore nella trascrizione",
            faseAttesa = listOf(DECODIFICA, DIARIZZAZIONE, TRASCRIZIONE),
            portePipeline = pipeline(
                registrazioni = vistaGuasto,
                decodificatore = decodificaGuasto,
                allineatore = AllineatoreCheLancia(),
            ),
        )
    }

    /** F6: shared shape of every AC-70 fault point — one port guasto per test, the rest normal. */
    private fun verificaGuastoDiPipeline(
        motivoAtteso: String,
        faseAttesa: List<FaseElaborazione>,
        portePipeline: PortePipeline,
        segnalatore: SegnalatoreFaseFinta = portePipeline.segnalatore as SegnalatoreFaseFinta,
    ) {
        val elaborazioni = ElaborazioneRepositoryFinta()
        val trascritti = TrascrittoRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))
        val servizio = servizio(eventi.unitaDiLavoro, eventi, elaborazioni, trascritti, portePipeline)
        elaborazioni.salva(unaInAttesa(REGISTRAZIONE_GUASTO)).atteso()

        servizio.esegui(EseguiProssimaElaborazione).atteso()

        val salvata = elaborazioni.diRegistrazione(REGISTRAZIONE_GUASTO).single()
        assertTrue(salvata.fallita)
        assertEquals(motivoAtteso, salvata.motivoFallimento)
        assertEquals(faseAttesa, segnalatore.fasi(REGISTRAZIONE_GUASTO))
        assertEquals(listOf(REGISTRAZIONE_GUASTO), segnalatore.terminate)
        assertEquals(emptyList(), trascritti.conTrascritto())
        assertEquals(
            listOf(
                ElaborazioneAvviata(REGISTRAZIONE_GUASTO, OROLOGIO.instant()),
                ElaborazioneFallita(REGISTRAZIONE_GUASTO, motivoAtteso),
            ),
            eventi.pubblicati,
        )
    }

    @Test
    fun `AC-71 le Voci del Trascritto sono numerate per prima apparizione`() {
        val id = RegistrazioneId("registrazione-1")
        val riferimento = RiferimentoAudio("audio/registrazione-1.m4a")
        val elaborazioni = ElaborazioneRepositoryFinta()
        val trascritti = TrascrittoRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))
        val registrazioni = LettoreRegistrazioneFinta(mapOf(id to unaVista(id, riferimento, 5_000)))
        val decodificatore = DecodificatoreAudioFinta(mapOf(riferimento to 5_000L))
        // Nell'ordine restituito dal Diarizzatore la voce 5 parla per prima, ma la voce 2 e' la prima ad
        // apparire nel tempo (inizia a 0 ms contro 2_000 ms): deve diventare Voce 1 (AC-71).
        val turni = listOf(
            Turno(IntervalloMs(2_000, 3_000), voceIndice = 5),
            Turno(IntervalloMs(0, 1_000), voceIndice = 2),
        )
        val diarizzatore = DiarizzatoreFinta(turni)
        val servizio = servizio(
            eventi.unitaDiLavoro,
            eventi,
            elaborazioni,
            trascritti,
            pipeline(registrazioni = registrazioni, decodificatore = decodificatore, diarizzatore = diarizzatore),
        )
        elaborazioni.salva(unaInAttesa(id)).atteso()

        servizio.esegui(EseguiProssimaElaborazione).atteso()

        val trascritto = checkNotNull(trascritti.trova(id))
        val voci = trascritto.voci
        assertEquals(2, voci.size)
        assertEquals("voce 2 0-1000", voci[0].segmenti.single().testo, "prima apparizione (0 ms) -> Voce 1")
        assertEquals("voce 5 2000-3000", voci[1].segmenti.single().testo, "seconda apparizione (2000 ms) -> Voce 2")
    }

    @Test
    fun `AC-72 zero turni diventa fallita nessun parlato rilevato`() {
        val id = RegistrazioneId("registrazione-1")
        val riferimento = RiferimentoAudio("audio/registrazione-1.m4a")
        val elaborazioni = ElaborazioneRepositoryFinta()
        val trascritti = TrascrittoRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))
        val registrazioni = LettoreRegistrazioneFinta(mapOf(id to unaVista(id, riferimento, DURATA)))
        val decodificatore = DecodificatoreAudioFinta(mapOf(riferimento to DURATA))
        val diarizzatoreSenzaTurni = DiarizzatoreFinta(emptyList())
        val servizio = servizio(
            eventi.unitaDiLavoro,
            eventi,
            elaborazioni,
            trascritti,
            pipeline(
                registrazioni = registrazioni,
                decodificatore = decodificatore,
                diarizzatore = diarizzatoreSenzaTurni,
            ),
        )
        elaborazioni.salva(unaInAttesa(id)).atteso()

        servizio.esegui(EseguiProssimaElaborazione).atteso()

        val salvata = elaborazioni.diRegistrazione(id).single()
        assertTrue(salvata.fallita)
        assertEquals("nessun parlato rilevato", salvata.motivoFallimento)
        assertEquals(emptyList(), trascritti.conTrascritto())
        assertEquals(
            listOf(ElaborazioneAvviata(id, OROLOGIO.instant()), ElaborazioneFallita(id, "nessun parlato rilevato")),
            eventi.pubblicati,
        )
    }

    @Test
    fun `AC-72 (F12) turni presenti ma nessun Segmento allineato diventa fallita nessun parlato rilevato`() {
        val id = RegistrazioneId("registrazione-1")
        val riferimento = RiferimentoAudio("audio/registrazione-1.m4a")
        val elaborazioni = ElaborazioneRepositoryFinta()
        val trascritti = TrascrittoRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))
        val registrazioni = LettoreRegistrazioneFinta(mapOf(id to unaVista(id, riferimento, DURATA)))
        val decodificatore = DecodificatoreAudioFinta(mapOf(riferimento to DURATA))
        val diarizzatoreConTurni = DiarizzatoreFinta(listOf(Turno(IntervalloMs(0, 500), voceIndice = 0)))
        val allineatoreVuoto = AllineatoreVuoto() // il diarizzatore trova turni, ma nulla viene allineato
        val servizio = servizio(
            eventi.unitaDiLavoro,
            eventi,
            elaborazioni,
            trascritti,
            pipeline(
                registrazioni = registrazioni,
                decodificatore = decodificatore,
                diarizzatore = diarizzatoreConTurni,
                allineatore = allineatoreVuoto,
            ),
        )
        elaborazioni.salva(unaInAttesa(id)).atteso()

        servizio.esegui(EseguiProssimaElaborazione).atteso()

        val salvata = elaborazioni.diRegistrazione(id).single()
        assertTrue(salvata.fallita)
        assertEquals("nessun parlato rilevato", salvata.motivoFallimento)
        assertEquals(emptyList(), trascritti.conTrascritto())
        assertEquals(
            listOf(ElaborazioneAvviata(id, OROLOGIO.instant()), ElaborazioneFallita(id, "nessun parlato rilevato")),
            eventi.pubblicati,
        )
    }

    @Test
    fun `AC-73 nessuna transazione e aperta mentre le porte ML audio lavorano`() {
        val id = RegistrazioneId("registrazione-1")
        val riferimento = RiferimentoAudio("audio/registrazione-1.m4a")
        val elaborazioni = ElaborazioneRepositoryFinta()
        val trascritti = TrascrittoRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))
        val sorveglia = SorvegliaTransazione(eventi.unitaDiLavoro)
        val letteDurante = mutableListOf<Boolean>()
        val registrazioni = LettoreRegistrazioneFinta(mapOf(id to unaVista(id, riferimento, DURATA)))
        val decodificatore = DecodificatoreSorvegliato(
            DecodificatoreAudioFinta(mapOf(riferimento to DURATA)),
            sorveglia,
            letteDurante,
        )
        val diarizzatore = DiarizzatoreSorvegliato(DiarizzatoreFinta(), sorveglia, letteDurante)
        val allineatore = AllineatoreSorvegliato(AllineatoreFinta(), sorveglia, letteDurante)
        val servizio = servizio(
            sorveglia,
            eventi,
            elaborazioni,
            trascritti,
            pipeline(
                registrazioni = registrazioni,
                decodificatore = decodificatore,
                diarizzatore = diarizzatore,
                allineatore = allineatore,
            ),
        )
        elaborazioni.salva(unaInAttesa(id)).atteso()

        servizio.esegui(EseguiProssimaElaborazione).atteso()

        assertTrue(letteDurante.isNotEmpty(), "il test deve aver osservato almeno una chiamata alle porte ML/audio")
        assertFalse(letteDurante.any { it }, "nessuna porta ML/audio deve lavorare mentre una transazione e aperta")
    }

    @Test
    fun `INV-5 se il salvataggio del Trascritto lancia rollback e compensazione a fallita`() {
        val id = RegistrazioneId("registrazione-1")
        val riferimento = RiferimentoAudio("audio/registrazione-1.m4a")
        val elaborazioni = ElaborazioneRepositoryFinta()
        val trascrittiGuasti = TrascrittoRepositoryGuasta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascrittiGuasti))
        val registrazioni = LettoreRegistrazioneFinta(mapOf(id to unaVista(id, riferimento, DURATA)))
        val decodificatore = DecodificatoreAudioFinta(mapOf(riferimento to DURATA))
        val segnalatore = SegnalatoreFaseFinta()
        val servizio = servizio(
            eventi.unitaDiLavoro,
            eventi,
            elaborazioni,
            trascrittiGuasti,
            pipeline(registrazioni = registrazioni, decodificatore = decodificatore, segnalatore = segnalatore),
        )
        elaborazioni.salva(unaInAttesa(id)).atteso()

        servizio.esegui(EseguiProssimaElaborazione).atteso() // F2: l'eccezione non deve piu' propagare al chiamante

        val salvata = elaborazioni.diRegistrazione(id).single()
        assertFalse(salvata.completata, "il salvataggio del Trascritto e' fallito: non deve risultare completata")
        assertTrue(salvata.fallita, "F2: la transazione di compensazione marca l'Elaborazione fallita")
        assertEquals("salvataggio del risultato non riuscito", salvata.motivoFallimento)
        assertEquals(emptyList(), trascrittiGuasti.conTrascritto())
        assertEquals(listOf(id), segnalatore.terminate, "F2: terminata deve essere sempre segnalata")
        assertEquals(
            listOf(
                ElaborazioneAvviata(id, OROLOGIO.instant()),
                ElaborazioneFallita(id, "salvataggio del risultato non riuscito"),
            ),
            eventi.pubblicati,
        )
    }

    @Test
    fun `V1 se il salvataggio della completata fallisce dopo il Trascritto rollback e compensazione a fallita`() {
        val id = RegistrazioneId("registrazione-1")
        val riferimento = RiferimentoAudio("audio/registrazione-1.m4a")
        val elaborazioniReali = ElaborazioneRepositoryFinta()
        val elaborazioni = ElaborazioneRepositoryCheRifiutaIlCompletamento(elaborazioniReali)
        val trascritti = TrascrittoRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))
        val registrazioni = LettoreRegistrazioneFinta(mapOf(id to unaVista(id, riferimento, DURATA)))
        val decodificatore = DecodificatoreAudioFinta(mapOf(riferimento to DURATA))
        val segnalatore = SegnalatoreFaseFinta()
        val servizio = servizio(
            eventi.unitaDiLavoro,
            eventi,
            elaborazioni,
            trascritti,
            pipeline(registrazioni = registrazioni, decodificatore = decodificatore, segnalatore = segnalatore),
        )
        elaborazioni.salva(unaInAttesa(id)).atteso()

        servizio.esegui(EseguiProssimaElaborazione).atteso()

        assertEquals(
            emptyList(),
            trascritti.conTrascritto(),
            "il salvataggio del Trascritto, gia' avvenuto nella stessa transazione, deve essere annullato dal rollback",
        )
        val salvata = elaborazioni.diRegistrazione(id).single()
        assertFalse(salvata.completata, "il salvataggio della completata e' fallito: non deve risultare completata")
        assertTrue(salvata.fallita, "la compensazione deve marcare l'Elaborazione fallita")
        assertEquals("salvataggio del risultato non riuscito", salvata.motivoFallimento)
        assertEquals(listOf(id), segnalatore.terminate)
        assertEquals(
            listOf(
                ElaborazioneAvviata(id, OROLOGIO.instant()),
                ElaborazioneFallita(id, "salvataggio del risultato non riuscito"),
            ),
            eventi.pubblicati,
        )
    }

    @Test
    fun `F3 elaborazione recuperata come fallita nel frattempo non viene sovrascritta`() {
        val id = RegistrazioneId("registrazione-1")
        val elaborazioneId = ElaborazioneId("elab-1")
        val riferimento = RiferimentoAudio("audio/registrazione-1.m4a")
        val elaborazioni = ElaborazioneRepositoryFinta()
        val trascritti = TrascrittoRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))
        val registrazioni = LettoreRegistrazioneFinta(mapOf(id to unaVista(id, riferimento, DURATA)))
        val decodificatoreBase = DecodificatoreAudioFinta(mapOf(riferimento to DURATA))
        val decodificatore =
            DecodificatoreCheSimulaRecuperoConcorrente(decodificatoreBase, elaborazioni, elaborazioneId, id)
        val servizio = servizio(
            eventi.unitaDiLavoro,
            eventi,
            elaborazioni,
            trascritti,
            pipeline(registrazioni = registrazioni, decodificatore = decodificatore),
        )
        elaborazioni.salva(Elaborazione.accoda(elaborazioneId, id, CREATA_VECCHIA, null).aggregato).atteso()

        servizio.esegui(EseguiProssimaElaborazione).atteso()

        val salvata = elaborazioni.diRegistrazione(id).single()
        assertTrue(salvata.fallita)
        assertEquals(
            "interrotta",
            salvata.motivoFallimento,
            "la riga recuperata nel frattempo (fallita) non deve essere sovrascritta",
        )
        assertEquals(
            emptyList(),
            trascritti.conTrascritto(),
            "il pipeline non deve completare su una riga gia' terminale",
        )
    }

    @Test
    fun `F5 la durata usata per Trascritto viene dai campioni decodificati non dal catalogo`() {
        val id = RegistrazioneId("registrazione-1")
        val riferimento = RiferimentoAudio("audio/registrazione-1.m4a")
        val elaborazioni = ElaborazioneRepositoryFinta()
        val trascritti = TrascrittoRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))
        // Il catalogo dichiara 1999 ms (un possibile arrotondamento), ma l'audio decodificato ne ha
        // 2000: un turno che finisce esattamente a 2000 ms non deve far fallire l'elaborazione (F5).
        val registrazioni = LettoreRegistrazioneFinta(mapOf(id to unaVista(id, riferimento, durataMs = 1_999L)))
        val decodificatore = DecodificatoreAudioFinta(mapOf(riferimento to 2_000L))
        val diarizzatore = DiarizzatoreFinta(listOf(Turno(IntervalloMs(0, 2_000), voceIndice = 0)))
        val servizio = servizio(
            eventi.unitaDiLavoro,
            eventi,
            elaborazioni,
            trascritti,
            pipeline(registrazioni = registrazioni, decodificatore = decodificatore, diarizzatore = diarizzatore),
        )
        elaborazioni.salva(unaInAttesa(id)).atteso()

        servizio.esegui(EseguiProssimaElaborazione).atteso()

        assertTrue(
            elaborazioni.diRegistrazione(id).single().completata,
            "un surplus di decodifica di 1 ms rispetto al catalogo non deve far fallire l'elaborazione",
        )
        assertEquals(1, trascritti.trova(id)?.segmenti?.size)
    }

    @Test
    fun `AC-70 (F-A) LettoreRegistrazione guasto diventa fallita con motivo semplice distinto dalla mancanza`() {
        verificaGuastoDiPipeline(
            motivoAtteso = "impossibile leggere i dati della registrazione",
            faseAttesa = emptyList(),
            portePipeline = pipeline(registrazioni = LettoreRegistrazioneCheLancia()),
        )
    }

    @Test
    fun `AC-70 (F-A) un segnale di fase guasto e fatale come ogni porta con il motivo della sua fase`() {
        val registro = SegnalatoreFaseFinta()
        verificaGuastoDiPipeline(
            motivoAtteso = "errore nella separazione delle voci",
            faseAttesa = listOf(DECODIFICA),
            portePipeline = pipeline(
                registrazioni = vistaGuasto,
                decodificatore = decodificaGuasto,
                segnalatore = SegnalatoreCheFallisceSu(DIARIZZAZIONE, registro),
            ),
            segnalatore = registro,
        )
    }

    @Test
    fun `AC-70 (F-A) il segnale della fase allineamento guasto diventa fallita`() {
        val registro = SegnalatoreFaseFinta()
        verificaGuastoDiPipeline(
            motivoAtteso = "errore nell'allineamento del testo",
            faseAttesa = listOf(DECODIFICA, DIARIZZAZIONE, TRASCRIZIONE),
            portePipeline = pipeline(
                registrazioni = vistaGuasto,
                decodificatore = decodificaGuasto,
                segnalatore = SegnalatoreCheFallisceSu(ALLINEAMENTO, registro),
            ),
            segnalatore = registro,
        )
    }

    @Test
    fun `INV-7 un segmento oltre la durata decodificata diventa fallita con motivo semplice`() {
        verificaGuastoDiPipeline(
            motivoAtteso = "un segmento supera la durata della registrazione",
            faseAttesa = listOf(DECODIFICA, DIARIZZAZIONE, TRASCRIZIONE, ALLINEAMENTO),
            portePipeline = pipeline(
                registrazioni = vistaGuasto,
                decodificatore = decodificaGuasto,
                allineatore = AllineatoreFisso(IntervalloMs(0, DURATA + 500)),
            ),
        )
    }

    @Test
    fun `F4 una cancellazione non e un guasto di porta propaga e terminata e comunque segnalata`() {
        verificaInterruzioneNonGuasto(
            CancellationException::class.java,
            pipeline(
                registrazioni = vistaGuasto,
                decodificatore = decodificaGuasto,
                diarizzatore = DiarizzatoreCheLancia(CancellationException("annullata")),
            ),
        )
    }

    @Test
    fun `F4 un'interruzione non e un guasto di porta propaga ripristina il flag e terminata e segnalata`() {
        try {
            verificaInterruzioneNonGuasto(
                InterruptedException::class.java,
                pipeline(
                    registrazioni = vistaGuasto,
                    decodificatore = decodificaGuasto,
                    allineatore = AllineatoreCheLancia(InterruptedException("interrotto")),
                ),
            )
            assertTrue(Thread.currentThread().isInterrupted, "il flag di interruzione deve essere ripristinato")
        } finally {
            Thread.interrupted() // pulisce il flag per i test successivi
        }
    }

    private fun verificaInterruzioneNonGuasto(atteso: Class<out Exception>, portePipeline: PortePipeline) {
        val elaborazioni = ElaborazioneRepositoryFinta()
        val trascritti = TrascrittoRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))
        val segnalatore = portePipeline.segnalatore as SegnalatoreFaseFinta
        val servizio = servizio(eventi.unitaDiLavoro, eventi, elaborazioni, trascritti, portePipeline)
        elaborazioni.salva(unaInAttesa(REGISTRAZIONE_GUASTO)).atteso()

        val lanciata = runCatching { servizio.esegui(EseguiProssimaElaborazione) }.exceptionOrNull()

        assertTrue(atteso.isInstance(lanciata), "atteso ${atteso.simpleName}, ottenuto $lanciata")
        val salvata = elaborazioni.diRegistrazione(REGISTRAZIONE_GUASTO).single()
        assertFalse(salvata.fallita, "una cancellazione/interruzione non va registrata come fallita")
        assertEquals(listOf(REGISTRAZIONE_GUASTO), segnalatore.terminate, "terminata sempre segnalata")
        assertEquals(listOf(ElaborazioneAvviata(REGISTRAZIONE_GUASTO, OROLOGIO.instant())), eventi.pubblicati)
    }

    @Test
    fun `F-B completamento e compensazione lanciano entrambi il guasto della compensazione propaga`() {
        val guastoCompletamento = GuastoDiPortaDiProva("salva(completata)")
        val guastoCompensazione = GuastoDiPortaDiProva("salva(fallita)")
        val esecuzione = eseguiConConclusioneGuasta { e ->
            throw if (e.completata) guastoCompletamento else guastoCompensazione
        }

        val lanciata = esecuzione.lanciata
        assertEquals(guastoCompensazione, lanciata, "il guasto della compensazione non deve sparire")
        assertEquals(listOf<Throwable>(guastoCompletamento), lanciata?.suppressed?.toList(), "il primo e' soppresso")
        verificaLasciataInCorso(esecuzione)
    }

    @Test
    fun `F-B completamento e compensazione entrambi rifiutati il rifiuto propaga col primo soppresso`() {
        val esecuzione = eseguiConConclusioneGuasta { e ->
            Esito.Errore(ErroreTrascrizione.ElaborazioneGiaCompletata(e.registrazioneId))
        }

        val lanciata = esecuzione.lanciata
        assertIs<IllegalStateException>(lanciata, "un rifiuto della compensazione non deve sparire")
        assertTrue(lanciata.message.orEmpty().startsWith("compensazione rifiutata"))
        val soppressa = lanciata.suppressed.single()
        assertTrue(soppressa.message.orEmpty().startsWith("transazione finale rifiutata"))
        verificaLasciataInCorso(esecuzione)
    }

    private data class EsecuzioneGuasta(
        val lanciata: Throwable?,
        val elaborazioni: ElaborazioneRepositoryFinta,
        val segnalatore: SegnalatoreFaseFinta,
        val eventi: DispatcherEventiFinta,
    )

    private fun eseguiConConclusioneGuasta(conclusione: (Elaborazione) -> Esito<Unit>): EsecuzioneGuasta {
        val elaborazioniReali = ElaborazioneRepositoryFinta()
        val elaborazioni = ElaborazioneRepositoryCheRifiutaLaConclusione(elaborazioniReali, conclusione)
        val trascritti = TrascrittoRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))
        val segnalatore = SegnalatoreFaseFinta()
        val servizio = servizio(
            eventi.unitaDiLavoro,
            eventi,
            elaborazioni,
            trascritti,
            pipeline(registrazioni = vistaGuasto, decodificatore = decodificaGuasto, segnalatore = segnalatore),
        )
        elaborazioniReali.salva(unaInAttesa(REGISTRAZIONE_GUASTO)).atteso()

        val lanciata = runCatching { servizio.esegui(EseguiProssimaElaborazione) }.exceptionOrNull()

        assertNull(trascritti.trova(REGISTRAZIONE_GUASTO), "nessun Trascritto: rollback (INV-5)")
        return EsecuzioneGuasta(lanciata, elaborazioniReali, segnalatore, eventi)
    }

    /** Left `in_corso` for RecuperaElaborazioniInterrotte; terminata already signalled; no terminal event. */
    private fun verificaLasciataInCorso(esecuzione: EsecuzioneGuasta) {
        assertEquals(listOf(REGISTRAZIONE_GUASTO), esecuzione.elaborazioni.inCorso().map { it.registrazioneId })
        assertEquals(listOf(REGISTRAZIONE_GUASTO), esecuzione.segnalatore.terminate, "terminata prima di propagare")
        assertEquals(
            listOf(ElaborazioneAvviata(REGISTRAZIONE_GUASTO, OROLOGIO.instant())),
            esecuzione.eventi.pubblicati,
        )
    }

    @Test
    fun `F5 un millisecondo parziale di campioni decodificati conta come millisecondo intero`() {
        val elaborazioni = ElaborazioneRepositoryFinta()
        val trascritti = TrascrittoRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))
        // 2000 ms + 1 campione (16 kHz): la durata e' 2001 ms, un segmento che finisce a 2001 ms e' valido.
        val servizio = servizio(
            eventi.unitaDiLavoro,
            eventi,
            elaborazioni,
            trascritti,
            pipeline(
                registrazioni = vistaGuasto,
                decodificatore = DecodificatoreConCampioni(DURATA.toInt() * CAMPIONI_PER_MS + 1),
                allineatore = AllineatoreFisso(IntervalloMs(0, DURATA + 1)),
            ),
        )
        elaborazioni.salva(unaInAttesa(REGISTRAZIONE_GUASTO)).atteso()

        servizio.esegui(EseguiProssimaElaborazione).atteso()

        assertTrue(elaborazioni.diRegistrazione(REGISTRAZIONE_GUASTO).single().completata)
    }

    private fun servizio(
        uow: UnitaDiLavoro,
        eventi: DispatcherEventiFinta,
        elaborazioni: ElaborazioneRepository,
        trascritti: TrascrittoRepository,
        pipeline: PortePipeline,
    ): EseguiProssimaElaborazioneServizio =
        EseguiProssimaElaborazioneServizio(uow, OROLOGIO, elaborazioni, trascritti, pipeline, eventi)

    private fun pipeline(
        registrazioni: LettoreRegistrazione = LettoreRegistrazioneFinta(),
        decodificatore: DecodificatoreAudio = DecodificatoreAudioFinta(emptyMap()),
        diarizzatore: Diarizzatore = DiarizzatoreFinta(),
        allineatore: Allineatore = AllineatoreFinta(),
        segnalatore: SegnalatoreFase = SegnalatoreFaseFinta(),
    ): PortePipeline = PortePipeline(registrazioni, decodificatore, diarizzatore, allineatore, segnalatore)

    private fun unaVista(id: RegistrazioneId, riferimento: RiferimentoAudio, durataMs: Long): RegistrazioneVista =
        RegistrazioneVista(
            registrazioneId = id,
            progettoId = PROGETTO,
            titolo = "Riunione",
            riferimentoAudio = riferimento,
            dataRegistrazione = LocalDate.of(2026, 9, 20),
            durataMs = durataMs,
        )

    private fun unaInAttesa(id: RegistrazioneId, creataAlle: Instant = CREATA_VECCHIA): Elaborazione =
        Elaborazione.accoda(ElaborazioneId("elab-${id.valore}"), id, creataAlle, numeroPersone = null).aggregato

    private companion object {
        const val DURATA = 2_000L
        const val CAMPIONI_PER_MS = 16
        val OROLOGIO: Clock = Clock.fixed(Instant.parse("2026-09-23T10:10:00Z"), ZoneOffset.UTC)
        val CREATA_VECCHIA: Instant = Instant.parse("2026-09-23T09:00:00Z")
        val CREATA_RECENTE: Instant = Instant.parse("2026-09-23T09:05:00Z")
        val PROGETTO = ProgettoId("progetto-1")
        val REGISTRAZIONE_GUASTO = RegistrazioneId("registrazione-1")
        val RIFERIMENTO_GUASTO = RiferimentoAudio("audio/registrazione-1.m4a")
    }
}

/** Tracks whether a [EseguiProssimaElaborazioneServizio] transaction is currently open (AC-73). */
private class SorvegliaTransazione(private val delegata: UnitaDiLavoro) : UnitaDiLavoro {
    var aperta: Boolean = false
        private set

    override fun <T> inTransazione(blocco: () -> Esito<T>): Esito<T> {
        aperta = true
        return try {
            delegata.inTransazione(blocco)
        } finally {
            aperta = false
        }
    }
}

private class DecodificatoreSorvegliato(
    private val delegata: DecodificatoreAudio,
    private val sorveglia: SorvegliaTransazione,
    private val letteDurante: MutableList<Boolean>,
) : DecodificatoreAudio {
    override fun decodifica(id: RegistrazioneId, sorgente: RiferimentoAudio) {
        letteDurante += sorveglia.aperta
        delegata.decodifica(id, sorgente)
    }

    override fun tutti(id: RegistrazioneId): CampioniAudio {
        letteDurante += sorveglia.aperta
        return delegata.tutti(id)
    }

    override fun campioni(id: RegistrazioneId, intervallo: IntervalloMs): CampioniAudio {
        letteDurante += sorveglia.aperta
        return delegata.campioni(id, intervallo)
    }
}

private class DiarizzatoreSorvegliato(
    private val delegata: Diarizzatore,
    private val sorveglia: SorvegliaTransazione,
    private val letteDurante: MutableList<Boolean>,
) : Diarizzatore {
    override fun diarizza(c: CampioniAudio, numeroPersone: NumeroPersone?): List<Turno> {
        letteDurante += sorveglia.aperta
        return delegata.diarizza(c, numeroPersone)
    }
}

private class AllineatoreSorvegliato(
    private val delegata: Allineatore,
    private val sorveglia: SorvegliaTransazione,
    private val letteDurante: MutableList<Boolean>,
) : Allineatore {
    override fun allinea(campioni: CampioniAudio, turni: List<Turno>): List<SegmentoGrezzo> {
        letteDurante += sorveglia.aperta
        return delegata.allinea(campioni, turni)
    }
}

/** A port fault, simulated (AC-70): never seen by the user — only the fixed `motivo` is. */
private class GuastoDiPortaDiProva(fase: String) : RuntimeException("guasto di prova in $fase")

/** [DecodificatoreAudio] whose `tutti` always throws once decoded (AC-70: decodifica guasta). */
private class DecodificatoreCheFallisceSuTutti(private val delegata: DecodificatoreAudio) : DecodificatoreAudio {
    override fun decodifica(id: RegistrazioneId, sorgente: RiferimentoAudio): Unit = delegata.decodifica(id, sorgente)

    override fun tutti(id: RegistrazioneId): CampioniAudio = throw GuastoDiPortaDiProva("tutti()")

    override fun campioni(id: RegistrazioneId, intervallo: IntervalloMs): CampioniAudio =
        delegata.campioni(id, intervallo)
}

/** [Diarizzatore] that always throws [guasto] (AC-70: diarizzazione guasta; F4: cancellation). */
private class DiarizzatoreCheLancia(
    private val guasto: Exception = GuastoDiPortaDiProva("diarizza()"),
) : Diarizzatore {
    override fun diarizza(c: CampioniAudio, numeroPersone: NumeroPersone?): List<Turno> = throw guasto
}

/** [Allineatore] that always throws [guasto] (AC-70: allineamento guasto; F4: interruption). */
private class AllineatoreCheLancia(
    private val guasto: Exception = GuastoDiPortaDiProva("allinea()"),
) : Allineatore {
    override fun allinea(campioni: CampioniAudio, turni: List<Turno>): List<SegmentoGrezzo> = throw guasto
}

/** [LettoreRegistrazione] whose lookup always throws (AC-70/F-A: a fault, not a miss). */
private class LettoreRegistrazioneCheLancia : LettoreRegistrazione {
    override fun registrazione(id: RegistrazioneId): RegistrazioneVista? =
        throw GuastoDiPortaDiProva("registrazione()")
}

/** [SegnalatoreFase] that throws on [guasta] (F-A), recording every other signal into [registro]. */
private class SegnalatoreCheFallisceSu(
    private val guasta: FaseElaborazione,
    private val registro: SegnalatoreFaseFinta,
) : SegnalatoreFase {
    override fun fase(id: RegistrazioneId, f: FaseElaborazione) {
        if (f == guasta) throw GuastoDiPortaDiProva("fase($f)")
        registro.fase(id, f)
    }

    override fun terminata(id: RegistrazioneId): Unit = registro.terminata(id)
}

/** [Allineatore] that returns ONE Segmento over [intervallo], whatever the audio (INV-7, F5). */
private class AllineatoreFisso(private val intervallo: IntervalloMs) : Allineatore {
    override fun allinea(campioni: CampioniAudio, turni: List<Turno>): List<SegmentoGrezzo> =
        listOf(SegmentoGrezzo(0, intervallo, "parlato"))
}

/** [DecodificatoreAudio] that decodes exactly [quanti] non-silent samples (F5: partial millisecond). */
private class DecodificatoreConCampioni(private val quanti: Int) : DecodificatoreAudio {
    override fun decodifica(id: RegistrazioneId, sorgente: RiferimentoAudio): Unit = Unit

    override fun tutti(id: RegistrazioneId): CampioniAudio = CampioniAudio(FloatArray(quanti) { 0.5f })

    override fun campioni(id: RegistrazioneId, intervallo: IntervalloMs): CampioniAudio = tutti(id)
}

/**
 * [ElaborazioneRepository] whose `salva` of a TERMINAL row (completata or its compensating fallita)
 * runs [conclusione] instead — throwing or refusing (F-B: completion AND compensation both fail).
 */
private class ElaborazioneRepositoryCheRifiutaLaConclusione(
    private val delegata: ElaborazioneRepositoryFinta,
    private val conclusione: (Elaborazione) -> Esito<Unit>,
) : ElaborazioneRepository, Ripristinabile {
    override fun diRegistrazione(id: RegistrazioneId): List<Elaborazione> = delegata.diRegistrazione(id)

    override fun inAttesa(): List<Elaborazione> = delegata.inAttesa()

    override fun inCorso(): List<Elaborazione> = delegata.inCorso()

    override fun salva(e: Elaborazione): Esito<Unit> = if (e.terminale) conclusione(e) else delegata.salva(e)

    override fun istantanea(): () -> Unit = delegata.istantanea()
}

/** [Allineatore] that never produces a Segmento, whatever the Turni (AC-72/F12: zero parlato). */
private class AllineatoreVuoto : Allineatore {
    override fun allinea(campioni: CampioniAudio, turni: List<Turno>): List<SegmentoGrezzo> = emptyList()
}

/** [TrascrittoRepository] whose `salva` always fails (INV-5). */
private class TrascrittoRepositoryGuasta : TrascrittoRepository, Ripristinabile {
    override fun trova(id: RegistrazioneId): Trascritto? = null

    override fun conTrascritto(): List<RegistrazioneId> = emptyList()

    override fun salva(t: Trascritto): Unit = throw GuastoDiProva()

    override fun istantanea(): () -> Unit = {}
}

private class GuastoDiProva : RuntimeException("guasto di prova nel salvataggio del Trascritto")

/**
 * [ElaborazioneRepository] whose `salva` refuses to persist a `completata` row (V1): the Trascritto may
 * already be saved in the SAME transaction — this proves the whole transaction rolls back (INV-5),
 * unlike a [TrascrittoRepositoryGuasta]-only scenario, which stays green even under a two-transaction
 * split.
 */
private class ElaborazioneRepositoryCheRifiutaIlCompletamento(
    private val delegata: ElaborazioneRepositoryFinta,
) : ElaborazioneRepository, Ripristinabile {
    override fun diRegistrazione(id: RegistrazioneId): List<Elaborazione> = delegata.diRegistrazione(id)

    override fun inAttesa(): List<Elaborazione> = delegata.inAttesa()

    override fun inCorso(): List<Elaborazione> = delegata.inCorso()

    override fun salva(e: Elaborazione): Esito<Unit> = if (e.completata) {
        Esito.Errore(ErroreTrascrizione.ElaborazioneGiaCompletata(e.registrazioneId))
    } else {
        delegata.salva(e)
    }

    override fun istantanea(): () -> Unit = delegata.istantanea()
}

/** Simulates [RecuperaElaborazioniInterrotte] running concurrently while this pipeline is mid-flight (F3). */
private class DecodificatoreCheSimulaRecuperoConcorrente(
    private val delegata: DecodificatoreAudio,
    private val elaborazioni: ElaborazioneRepositoryFinta,
    private val elaborazioneId: ElaborazioneId,
    private val registrazioneId: RegistrazioneId,
) : DecodificatoreAudio {
    override fun decodifica(id: RegistrazioneId, sorgente: RiferimentoAudio) {
        elaborazioni.salva(
            unaElaborazione(
                stato = StatoElaborazione.FALLITA,
                id = elaborazioneId,
                registrazioneId = registrazioneId,
                motivo = "interrotta",
            ),
        )
        delegata.decodifica(id, sorgente)
    }

    override fun tutti(id: RegistrazioneId): CampioniAudio = delegata.tutti(id)

    override fun campioni(id: RegistrazioneId, intervallo: IntervalloMs): CampioniAudio =
        delegata.campioni(id, intervallo)
}
