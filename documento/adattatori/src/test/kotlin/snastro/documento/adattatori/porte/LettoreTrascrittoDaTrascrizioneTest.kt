package snastro.documento.adattatori.porte

import snastro.documento.applicazione.porte.AmbienteLettoreTrascritto
import snastro.documento.applicazione.porte.LettoreTrascritto
import snastro.documento.applicazione.porte.LettoreTrascrittoContratto
import snastro.documento.applicazione.porte.SegmentoConiato
import snastro.documento.applicazione.porte.SemeRegistrazione
import snastro.documento.applicazione.porte.SemeTurno
import snastro.kernel.CampioniAudio
import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.comandi.AggiungiRegistrazioneServizio
import snastro.progetto.applicazione.comandi.CreaProgetto
import snastro.progetto.applicazione.comandi.CreaProgettoServizio
import snastro.progetto.applicazione.eventi.RegistrazioneAggiunta
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni
import snastro.progetto.applicazione.porte.ArchivioAudioFinta
import snastro.progetto.applicazione.porte.InfoAudio
import snastro.progetto.applicazione.porte.ProgettoRepositoryFinta
import snastro.progetto.applicazione.porte.RegistrazioneRepositoryFinta
import snastro.progetto.applicazione.porte.SondaAudioFinta
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.comandi.AvviaElaborazioneServizio
import snastro.trascrizione.applicazione.comandi.EseguiProssimaElaborazione
import snastro.trascrizione.applicazione.comandi.EseguiProssimaElaborazioneServizio
import snastro.trascrizione.applicazione.comandi.PortePipeline
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmento
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmentoServizio
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.letture.VociDelTrascritto
import snastro.trascrizione.applicazione.porte.Allineatore
import snastro.trascrizione.applicazione.porte.AllineatoreFinta
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
import java.time.ZoneOffset

/**
 * D2 (dev-architecture-app.md#porta-contratto): [LettoreTrascrittoDaTrascrizione] passes
 * [LettoreTrascrittoContratto] real-on-real (AC-138). Both suppliers are populated exclusively
 * through THEIR OWN commands — Progetto's `CreaProgetto`/`AggiungiRegistrazione`, Trascrizione's
 * `AvviaElaborazione`/`EseguiProssimaElaborazione`/`RiassegnaSegmento` — this test never builds a
 * `Registrazione`/`Progetto`/`Trascritto` itself. The commands run over each supplier's OWN in-memory
 * port fakes (`applicazione` testFixtures): the cross-context dependency rule (ADR 0002, CR-1) allows
 * `documento:adattatori` to call only `progetto:applicazione` / `trascrizione:applicazione`, never
 * their `adattatori`'s SQL repositories — so minted ids are read back from the suppliers' own published
 * events / query API, never from a `dominio` type. The pipeline's ML ports are Trascrizione's OWN
 * fakes too, except the text-bearing [Allineatore]: [AllineatoreFinta] emits a fixed
 * `"voce <n> <inizio>-<fine>"` string, so a private [AllineatoreConTesto] plays back each
 * [SemeTurno.testo] verbatim instead (AC-49's own requirement) — the diarizzazione (Voce numbering)
 * still runs for real through [DiarizzatoreFinta].
 */
class LettoreTrascrittoDaTrascrizioneTest : LettoreTrascrittoContratto() {
    override fun ambiente(): AmbienteLettoreTrascritto = AmbienteReale()

    private class AmbienteReale : AmbienteLettoreTrascritto {
        private val clock = Clock.fixed(Instant.parse("2026-09-23T10:00:00Z"), ZoneOffset.UTC)
        private val generatoreId = GeneratoreIdFinto()

        // Progetto: seeded only through CreaProgettoServizio / AggiungiRegistrazioneServizio.
        private val progetti = ProgettoRepositoryFinta()
        private val registrazioniProgetto = RegistrazioneRepositoryFinta()
        private val eventiProgetto = DispatcherEventiFinta(UnitaDiLavoroFinta(registrazioniProgetto, progetti))
        private val archivio = ArchivioAudioFinta()
        private val catalogo = CatalogoRegistrazioni(registrazioniProgetto)

        // Trascrizione: seeded only through AvviaElaborazioneServizio / EseguiProssimaElaborazioneServizio /
        // RiassegnaSegmentoServizio.
        private val elaborazioni = ElaborazioneRepositoryFinta()
        private val trascritti = TrascrittoRepositoryFinta()
        private val eventiTrascrizione = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))

        /** Trascrizione's own view of each Registrazione seeded so far (its `LettoreRegistrazione` port). */
        private val registrazioniViste = mutableMapOf<RegistrazioneId, RegistrazioneVista>()

        init {
            CreaProgettoServizio(eventiProgetto.unitaDiLavoro, generatoreId, progetti, eventiProgetto)
                .esegui(CreaProgetto("Progetto di prova"))
                .atteso()
        }

        override val lettore: LettoreTrascritto =
            LettoreTrascrittoDaTrascrizione(VociDelTrascritto(trascritti), catalogo)

        override fun aggiungiRegistrazione(seme: SemeRegistrazione): RegistrazioneId {
            val percorso = "/sorgenti/${seme.titolo}.wav"
            archivio.conSorgente(percorso)
            val sonda = SondaAudioFinta(leggibili = mapOf(percorso to InfoAudio(seme.durataMs, seme.dataRegistrazione)))
            val servizio = AggiungiRegistrazioneServizio(
                eventiProgetto.unitaDiLavoro,
                generatoreId,
                clock,
                progetti,
                registrazioniProgetto,
                sonda,
                archivio,
                eventiProgetto,
            )

            servizio.esegui(AggiungiRegistrazione(percorso)).atteso()

            val id = eventiProgetto.pubblicati.filterIsInstance<RegistrazioneAggiunta>().last().registrazioneId
            val v = checkNotNull(catalogo.registrazione(id))
            registrazioniViste[id] = RegistrazioneVista(
                registrazioneId = v.registrazioneId,
                progettoId = v.progettoId,
                titolo = v.titolo,
                riferimentoAudio = v.riferimentoAudio,
                dataRegistrazione = v.dataRegistrazione,
                durataMs = v.durataMs,
            )
            return id
        }

        override fun completaElaborazione(
            registrazioneId: RegistrazioneId,
            turni: List<SemeTurno>,
        ): List<SegmentoConiato> {
            avvia(registrazioneId)
            val testoDi = turni.associate { it.intervallo to it.testo }
            val diarizzatore = DiarizzatoreFinta(turni.map { Turno(it.intervallo, it.voceIndice) })
            eseguiPipeline(registrazioneId, diarizzatore, AllineatoreConTesto(testoDi))

            val trascritto = checkNotNull(trascritti.trova(registrazioneId))
            return turni.map { t ->
                val segmento = trascritto.segmenti.single { it.intervallo == t.intervallo }
                SegmentoConiato(segmento.id, segmento.voceId)
            }
        }

        override fun fallisciElaborazione(registrazioneId: RegistrazioneId) {
            avvia(registrazioneId)
            // Nessun turno diarizzato -> Trascritto.crea rifiuta con NessunParlatoRilevato (AC-72).
            eseguiPipeline(registrazioneId, DiarizzatoreFinta(emptyList()), AllineatoreFinta())
        }

        override fun riassegna(registrazioneId: RegistrazioneId, segmento: SegmentoId, destinazione: VoceId?): VoceId {
            RiassegnaSegmentoServizio(eventiTrascrizione.unitaDiLavoro, trascritti, eventiTrascrizione)
                .esegui(RiassegnaSegmento(registrazioneId, segmento, destinazione))
                .atteso()
            return eventiTrascrizione.pubblicati.filterIsInstance<SegmentoRiassegnato>().last().a
        }

        private fun avvia(registrazioneId: RegistrazioneId) {
            AvviaElaborazioneServizio(
                eventiTrascrizione.unitaDiLavoro,
                generatoreId,
                clock,
                LettoreRegistrazioneFinta(registrazioniViste),
                elaborazioni,
            ).esegui(AvviaElaborazione(registrazioneId)).atteso()
        }

        private fun eseguiPipeline(
            registrazioneId: RegistrazioneId,
            diarizzatore: DiarizzatoreFinta,
            allineatore: Allineatore,
        ) {
            val vista = registrazioniViste.getValue(registrazioneId)
            val decodificatore = DecodificatoreAudioFinta(mapOf(vista.riferimentoAudio to vista.durataMs))
            val pipeline = PortePipeline(
                LettoreRegistrazioneFinta(registrazioniViste),
                decodificatore,
                diarizzatore,
                allineatore,
                SegnalatoreFaseFinta(),
            )
            EseguiProssimaElaborazioneServizio(
                eventiTrascrizione.unitaDiLavoro,
                clock,
                elaborazioni,
                trascritti,
                pipeline,
                eventiTrascrizione,
            ).esegui(EseguiProssimaElaborazione()).atteso()
        }
    }
}

/** [Allineatore] that plays back [testoDi]`[intervallo]` verbatim instead of [AllineatoreFinta]'s fixed text. */
private class AllineatoreConTesto(private val testoDi: Map<IntervalloMs, String>) : Allineatore {
    override fun allinea(campioni: CampioniAudio, turni: List<Turno>): List<SegmentoGrezzo> =
        turni.map { SegmentoGrezzo(it.voceIndice, it.intervallo, testoDi.getValue(it.intervallo)) }
}
