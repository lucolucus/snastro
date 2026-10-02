package snastro.sbobinatura.adattatori.porte

import snastro.kernel.CampioniAudio
import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IncontroId
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
import snastro.progetto.applicazione.comandi.Destinazione
import snastro.progetto.applicazione.eventi.RegistrazioneAggiunta
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni
import snastro.progetto.applicazione.porte.ArchivioAudioFinta
import snastro.progetto.applicazione.porte.IncontroRepositoryFinta
import snastro.progetto.applicazione.porte.InfoAudio
import snastro.progetto.applicazione.porte.ProgettoRepositoryFinta
import snastro.progetto.applicazione.porte.RegistrazioneRepositoryFinta
import snastro.progetto.applicazione.porte.SondaAudioFinta
import snastro.supporto.test.OrologioFinto
import snastro.sbobinatura.applicazione.porte.AmbienteLettoreTrascritto
import snastro.sbobinatura.applicazione.porte.LettoreTrascritto
import snastro.sbobinatura.applicazione.porte.LettoreTrascrittoContratto
import snastro.sbobinatura.applicazione.porte.SegmentoConiato
import snastro.sbobinatura.applicazione.porte.SemeRegistrazione
import snastro.sbobinatura.applicazione.porte.SemeTurno
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
import snastro.trascrizione.applicazione.porte.Turno
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepositoryFinta
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * D2 (dev-architecture-app.md#porta-contratto): [LettoreTrascrittoDaTrascrizione] passes
 * [LettoreTrascrittoContratto] real-on-real (AC-138). Both suppliers are populated exclusively
 * through THEIR OWN commands — Progetto's `CreaProgetto`/`AggiungiRegistrazione`, Trascrizione's
 * `AvviaElaborazione`/`EseguiProssimaElaborazione`/`RiassegnaSegmento` — this test never builds a
 * `Registrazione`/`Progetto`/`Trascritto` itself. The commands run over each supplier's OWN in-memory
 * port fakes (`applicazione` testFixtures): the cross-context dependency rule (ADR 0002, CR-1) allows
 * `sbobinatura:adattatori` to call only `progetto:applicazione` / `trascrizione:applicazione`, never
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
        // Advances at every import, so the import order is a real aggiuntaAlle order (INV-I2), never an id tie-break.
        private val clock = OrologioFinto(Instant.parse("2026-09-23T10:00:00Z"))
        private val generatoreId = GeneratoreIdFinto()

        // Progetto: seeded only through CreaProgettoServizio / AggiungiRegistrazioneServizio.
        private val progetti = ProgettoRepositoryFinta()
        private val registrazioniProgetto = RegistrazioneRepositoryFinta()
        private val incontriProgetto = IncontroRepositoryFinta(registrazioniProgetto)
        private val eventiProgetto =
            DispatcherEventiFinta(UnitaDiLavoroFinta(registrazioniProgetto, progetti, incontriProgetto))
        private val archivio = ArchivioAudioFinta()
        private val catalogo = CatalogoRegistrazioni(registrazioniProgetto, incontriProgetto)

        // Trascrizione: seeded only through AvviaElaborazioneServizio / EseguiProssimaElaborazioneServizio /
        // RiassegnaSegmentoServizio.
        private val elaborazioni = ElaborazioneRepositoryFinta()
        private val trascritti = VociDellIncontroRepositoryFinta()
        private val eventiTrascrizione = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))

        /** Trascrizione's own view of each Registrazione seeded so far (its `LettoreRegistrazione` port). */
        private val registrazioniViste = mutableMapOf<RegistrazioneId, RegistrazioneVista>()

        /** Each Incontro's Parti in PROGETTO's order (INV-I2), copied from its catalogue after each import. */
        private val ordineParti = mutableMapOf<IncontroId, List<RegistrazioneId>>()
        private val lettoreRegistrazione = LettoreRegistrazioneFinta(registrazioniViste, ordineParti)
        private var contatore = 0

        init {
            CreaProgettoServizio(eventiProgetto.unitaDiLavoro, generatoreId, progetti, eventiProgetto)
                .esegui(CreaProgetto("Progetto di prova"))
                .atteso()
        }

        /** On (D-0037): [aggiungiParte] goes through Progetto's own import into the Incontro (I2). */
        override val piuPartiPerIncontro: Boolean = true

        override val lettore: LettoreTrascritto =
            LettoreTrascrittoDaTrascrizione(
                VociDelTrascritto(trascritti, lettoreRegistrazione, UnitaDiLavoroFinta()),
                catalogo,
            )

        override fun aggiungiRegistrazione(seme: SemeRegistrazione): RegistrazioneId =
            importa(seme, Destinazione.NuovoIncontro)

        override fun aggiungiParte(incontroId: IncontroId, seme: SemeRegistrazione): RegistrazioneId =
            importa(seme, Destinazione.Incontro(incontroId))

        /** Progetto's AggiungiRegistrazione to [destinazione], then Trascrizione's own view of the new Parte. */
        private fun importa(seme: SemeRegistrazione, destinazione: Destinazione): RegistrazioneId {
            val percorso = "/sorgenti/${++contatore}/${seme.titolo}.wav"
            archivio.conSorgente(percorso)
            val sonda = SondaAudioFinta(leggibili = mapOf(percorso to InfoAudio(seme.durataMs, seme.dataRegistrazione)))
            clock.avanza(1.seconds)
            val servizio = AggiungiRegistrazioneServizio(
                eventiProgetto.unitaDiLavoro,
                generatoreId,
                clock,
                progetti,
                registrazioniProgetto,
                incontriProgetto,
                sonda,
                archivio,
                eventiProgetto,
            )

            servizio.esegui(
                AggiungiRegistrazione(checkNotNull(progetti.trova()).id, listOf(percorso), destinazione),
            ).atteso()

            val id = eventiProgetto.pubblicati.filterIsInstance<RegistrazioneAggiunta>().last().registrazioneId
            val v = checkNotNull(catalogo.registrazione(id))
            registrazioniViste[id] = RegistrazioneVista(
                registrazioneId = v.registrazioneId,
                progettoId = v.progettoId,
                incontroId = v.incontroId,
                titolo = v.titolo,
                riferimentoAudio = v.riferimentoAudio,
                dataRegistrazione = v.dataRegistrazione,
                durataMs = v.durataMs,
            )
            ordineParti[v.incontroId] = checkNotNull(catalogo.incontro(v.incontroId)).parti.map { it.registrazioneId }
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

            val trascritto = checkNotNull(
                trascritti.trascritto(registrazioneId),
            )
            return turni.map { t ->
                val segmento = trascritto.segmenti.single { it.intervallo == t.intervallo }
                SegmentoConiato(segmento.id, segmento.voceId)
            }
        }

        override fun incontroDi(registrazioneId: RegistrazioneId): IncontroId =
            registrazioniViste.getValue(registrazioneId).incontroId

        override fun fallisciElaborazione(registrazioneId: RegistrazioneId) {
            avvia(registrazioneId)
            // Nessun turno diarizzato -> Trascritto.crea rifiuta con NessunParlatoRilevato (AC-72).
            eseguiPipeline(registrazioneId, DiarizzatoreFinta(emptyList()), AllineatoreFinta())
        }

        override fun riassegna(registrazioneId: RegistrazioneId, segmento: SegmentoId, destinazione: VoceId?): VoceId {
            RiassegnaSegmentoServizio(
                eventiTrascrizione.unitaDiLavoro,
                trascritti,
                lettoreRegistrazione,
                eventiTrascrizione,
            )
                .esegui(RiassegnaSegmento(registrazioneId, segmento, destinazione))
                .atteso()
            return eventiTrascrizione.pubblicati.filterIsInstance<SegmentoRiassegnato>().last().a
        }

        private fun avvia(registrazioneId: RegistrazioneId) {
            AvviaElaborazioneServizio(
                eventiTrascrizione.unitaDiLavoro,
                generatoreId,
                clock,
                lettoreRegistrazione,
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
                lettoreRegistrazione,
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
