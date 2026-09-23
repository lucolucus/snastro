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
import snastro.trascrizione.applicazione.eventi.ElaborazioneFallita
import snastro.trascrizione.applicazione.porte.Allineatore
import snastro.trascrizione.applicazione.porte.AllineatoreFinta
import snastro.trascrizione.applicazione.porte.DecodificatoreAudio
import snastro.trascrizione.applicazione.porte.DecodificatoreAudioFinta
import snastro.trascrizione.applicazione.porte.Diarizzatore
import snastro.trascrizione.applicazione.porte.DiarizzatoreFinta
import snastro.trascrizione.applicazione.porte.ElaborazioneRepositoryFinta
import snastro.trascrizione.applicazione.porte.FaseElaborazione.ALLINEAMENTO
import snastro.trascrizione.applicazione.porte.FaseElaborazione.DECODIFICA
import snastro.trascrizione.applicazione.porte.FaseElaborazione.DIARIZZAZIONE
import snastro.trascrizione.applicazione.porte.FaseElaborazione.TRASCRIZIONE
import snastro.trascrizione.applicazione.porte.LettoreRegistrazioneFinta
import snastro.trascrizione.applicazione.porte.RegistrazioneVista
import snastro.trascrizione.applicazione.porte.SegmentoGrezzo
import snastro.trascrizione.applicazione.porte.SegnalatoreFaseFinta
import snastro.trascrizione.applicazione.porte.TrascrittoRepository
import snastro.trascrizione.applicazione.porte.TrascrittoRepositoryFinta
import snastro.trascrizione.applicazione.porte.Turno
import snastro.trascrizione.dominio.Elaborazione
import snastro.trascrizione.dominio.Trascritto
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
    }

    @Test
    fun `AC-70 un errore di porta in qualunque fase diventa fallita con motivo semplice e nessun Trascritto`() {
        val id = RegistrazioneId("registrazione-1")
        val riferimento = RiferimentoAudio("audio/registrazione-1.m4a")
        val elaborazioni = ElaborazioneRepositoryFinta()
        val trascritti = TrascrittoRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))
        val segnalatore = SegnalatoreFaseFinta()
        val registrazioni = LettoreRegistrazioneFinta(mapOf(id to unaVista(id, riferimento, DURATA)))
        val decodificatoreGuasto = DecodificatoreAudioFinta(sorgenti = emptyMap()) // decodifica() sempre fallisce
        val servizio = servizio(
            eventi.unitaDiLavoro,
            eventi,
            elaborazioni,
            trascritti,
            pipeline(registrazioni = registrazioni, decodificatore = decodificatoreGuasto, segnalatore = segnalatore),
        )
        elaborazioni.salva(unaInAttesa(id)).atteso()

        servizio.esegui(EseguiProssimaElaborazione).atteso()

        val salvata = elaborazioni.diRegistrazione(id).single()
        assertTrue(salvata.fallita)
        val motivo = checkNotNull(salvata.motivoFallimento)
        assertTrue(motivo.isNotBlank(), "il motivo deve essere in parole semplici, non vuoto")
        assertEquals(listOf(DECODIFICA), segnalatore.fasi(id), "nessuna fase successiva alla decodifica guasta")
        assertEquals(listOf(id), segnalatore.terminate)
        assertEquals(emptyList(), trascritti.conTrascritto())
        assertEquals(
            listOf(ElaborazioneAvviata(id, OROLOGIO.instant()), ElaborazioneFallita(id, motivo)),
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
    fun `INV-5 se il salvataggio del Trascritto fallisce, Elaborazione non completata e nessun Trascritto`() {
        val id = RegistrazioneId("registrazione-1")
        val riferimento = RiferimentoAudio("audio/registrazione-1.m4a")
        val elaborazioni = ElaborazioneRepositoryFinta()
        val trascrittiGuasti = TrascrittoRepositoryGuasta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascrittiGuasti))
        val registrazioni = LettoreRegistrazioneFinta(mapOf(id to unaVista(id, riferimento, DURATA)))
        val decodificatore = DecodificatoreAudioFinta(mapOf(riferimento to DURATA))
        val servizio = servizio(
            eventi.unitaDiLavoro,
            eventi,
            elaborazioni,
            trascrittiGuasti,
            pipeline(registrazioni = registrazioni, decodificatore = decodificatore),
        )
        elaborazioni.salva(unaInAttesa(id)).atteso()

        assertFailsWith<GuastoDiProva> { servizio.esegui(EseguiProssimaElaborazione) }

        val salvata = elaborazioni.diRegistrazione(id).single()
        assertFalse(salvata.completata, "il salvataggio del Trascritto e' fallito: non deve risultare completata")
        assertFalse(salvata.fallita, "il rollback lascia l'Elaborazione com'era prima del commit finale (in_corso)")
        assertEquals(emptyList(), trascrittiGuasti.conTrascritto())
    }

    private fun servizio(
        uow: UnitaDiLavoro,
        eventi: DispatcherEventiFinta,
        elaborazioni: ElaborazioneRepositoryFinta,
        trascritti: TrascrittoRepository,
        pipeline: PortePipeline,
    ): EseguiProssimaElaborazioneServizio =
        EseguiProssimaElaborazioneServizio(uow, OROLOGIO, elaborazioni, trascritti, pipeline, eventi)

    private fun pipeline(
        registrazioni: LettoreRegistrazioneFinta = LettoreRegistrazioneFinta(),
        decodificatore: DecodificatoreAudio = DecodificatoreAudioFinta(emptyMap()),
        diarizzatore: Diarizzatore = DiarizzatoreFinta(),
        allineatore: Allineatore = AllineatoreFinta(),
        segnalatore: SegnalatoreFaseFinta = SegnalatoreFaseFinta(),
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
        Elaborazione.accoda(ElaborazioneId("elab-${id.valore}"), id, creataAlle).aggregato

    private companion object {
        const val DURATA = 2_000L
        val OROLOGIO: Clock = Clock.fixed(Instant.parse("2026-09-23T10:10:00Z"), ZoneOffset.UTC)
        val CREATA_VECCHIA: Instant = Instant.parse("2026-09-23T09:00:00Z")
        val CREATA_RECENTE: Instant = Instant.parse("2026-09-23T09:05:00Z")
        val PROGETTO = ProgettoId("progetto-1")
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
    override fun diarizza(c: CampioniAudio): List<Turno> {
        letteDurante += sorveglia.aperta
        return delegata.diarizza(c)
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

/** [TrascrittoRepository] whose `salva` always fails (INV-5). */
private class TrascrittoRepositoryGuasta : TrascrittoRepository, Ripristinabile {
    override fun trova(id: RegistrazioneId): Trascritto? = null

    override fun conTrascritto(): List<RegistrazioneId> = emptyList()

    override fun salva(t: Trascritto): Unit = throw GuastoDiProva()

    override fun istantanea(): () -> Unit = {}
}

private class GuastoDiProva : RuntimeException("guasto di prova nel salvataggio del Trascritto")
