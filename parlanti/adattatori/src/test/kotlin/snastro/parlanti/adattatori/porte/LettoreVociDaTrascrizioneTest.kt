package snastro.parlanti.adattatori.porte

import snastro.kernel.CampioniAudio
import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.parlanti.applicazione.porte.AmbienteLettoreVoci
import snastro.parlanti.applicazione.porte.LettoreVoci
import snastro.parlanti.applicazione.porte.LettoreVociContratto
import snastro.parlanti.applicazione.porte.SegmentoConiato
import snastro.parlanti.applicazione.porte.SemeTurno
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
import snastro.trascrizione.applicazione.comandi.ConfermaSegmento
import snastro.trascrizione.applicazione.comandi.ConfermaSegmentoServizio
import snastro.trascrizione.applicazione.comandi.DividiVoce
import snastro.trascrizione.applicazione.comandi.DividiVoceServizio
import snastro.trascrizione.applicazione.comandi.EseguiProssimaElaborazione
import snastro.trascrizione.applicazione.comandi.EseguiProssimaElaborazioneServizio
import snastro.trascrizione.applicazione.comandi.PortePipeline
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmento
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmentoServizio
import snastro.trascrizione.applicazione.comandi.UnisciVoci
import snastro.trascrizione.applicazione.comandi.UnisciVociServizio
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.eventi.VoceDivisa
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

/**
 * D2 (dev-architecture-app.md#porta-contratto): [LettoreVociDaTrascrizione] passes
 * [LettoreVociContratto] real-on-real (AC-137). Both Progetto (scaffolding: a Registrazione's
 * catalogue row) and Trascrizione (the actual supplier of `voci-per-parlanti`) are populated
 * exclusively through THEIR OWN commands — Progetto's `CreaProgetto`/`AggiungiRegistrazione`,
 * Trascrizione's `AvviaElaborazione`/`EseguiProssimaElaborazione`/`UnisciVoci`/`DividiVoce`/
 * `RiassegnaSegmento`/`ConfermaSegmento` (AC-524) — this test never builds a `Registrazione`/
 * `Trascritto` itself. The commands
 * run over each supplier's OWN in-memory port fakes (`applicazione` testFixtures): the cross-context
 * dependency rule (ADR 0002, CR-1) allows `parlanti:adattatori` to call only `progetto:applicazione` /
 * `trascrizione:applicazione`, never their `adattatori`'s SQL repositories — so minted ids are read
 * back from the suppliers' own published events / query API, never from a `dominio` type.
 *
 * [AllineatoreConIndice] tags every Segmento with the ORIGINAL index of its seeding [SemeTurno] (as
 * `testo`, never read by [LettoreVoci]) purely so [completaElaborazione] can correlate the real
 * `Trascritto`'s minted ids back to the caller's list even when two turni share the very same
 * interval (AC-46's overlap/duplicate scenarios) — matching by interval alone would be ambiguous.
 */
class LettoreVociDaTrascrizioneTest : LettoreVociContratto() {
    override fun ambiente(): AmbienteLettoreVoci = AmbienteReale()

    private class AmbienteReale : AmbienteLettoreVoci {
        private val clock = Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneOffset.UTC)
        private val generatoreId = GeneratoreIdFinto()

        // Progetto: seeded only through CreaProgettoServizio / AggiungiRegistrazioneServizio.
        private val progetti = ProgettoRepositoryFinta()
        private val registrazioniProgetto = RegistrazioneRepositoryFinta()
        private val eventiProgetto = DispatcherEventiFinta(UnitaDiLavoroFinta(registrazioniProgetto, progetti))
        private val archivio = ArchivioAudioFinta()
        private val catalogo = CatalogoRegistrazioni(registrazioniProgetto)

        // Trascrizione: seeded only through AvviaElaborazioneServizio / EseguiProssimaElaborazioneServizio /
        // UnisciVociServizio / DividiVoceServizio / RiassegnaSegmentoServizio.
        private val elaborazioni = ElaborazioneRepositoryFinta()
        private val trascritti = TrascrittoRepositoryFinta()
        private val eventiTrascrizione = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))

        /** Trascrizione's own view of each Registrazione seeded so far (its `LettoreRegistrazione` port). */
        private val registrazioniViste = mutableMapOf<RegistrazioneId, RegistrazioneVista>()
        private var contatore = 0

        init {
            CreaProgettoServizio(eventiProgetto.unitaDiLavoro, generatoreId, progetti, eventiProgetto)
                .esegui(CreaProgetto("Progetto di prova"))
                .atteso()
        }

        override val lettore: LettoreVoci = LettoreVociDaTrascrizione(VociDelTrascritto(trascritti))

        override fun aggiungiRegistrazione(): RegistrazioneId {
            val percorso = "/sorgenti/registrazione-${contatore++}.wav"
            archivio.conSorgente(percorso)
            val sonda = SondaAudioFinta(
                leggibili = mapOf(percorso to InfoAudio(DURATA_REGISTRAZIONE_MS, LocalDate.of(2026, 9, 20))),
            )
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
            val diarizzatore = DiarizzatoreFinta(turni.map { Turno(it.intervallo, it.voceIndice) })
            eseguiPipeline(registrazioneId, diarizzatore, AllineatoreConIndice())

            val trascritto = checkNotNull(trascritti.trova(registrazioneId))
            val perIndice = trascritto.segmenti.associateBy { it.testo.toInt() }
            return turni.indices.map { i ->
                val segmento = perIndice.getValue(i)
                SegmentoConiato(segmento.id, segmento.voceId)
            }
        }

        override fun fallisciElaborazione(registrazioneId: RegistrazioneId) {
            avvia(registrazioneId)
            // Nessun turno diarizzato -> Trascritto.crea rifiuta con NessunParlatoRilevato (AC-72).
            eseguiPipeline(registrazioneId, DiarizzatoreFinta(emptyList()), AllineatoreConIndice())
        }

        override fun unisci(registrazioneId: RegistrazioneId, sopravvive: VoceId, rimossa: VoceId) {
            UnisciVociServizio(eventiTrascrizione.unitaDiLavoro, trascritti, eventiTrascrizione)
                .esegui(UnisciVoci(registrazioneId, sopravvive, rimossa))
                .atteso()
        }

        override fun dividi(registrazioneId: RegistrazioneId, origine: VoceId, segmenti: Set<SegmentoId>): VoceId {
            DividiVoceServizio(eventiTrascrizione.unitaDiLavoro, trascritti, eventiTrascrizione)
                .esegui(DividiVoce(registrazioneId, origine, segmenti))
                .atteso()
            return eventiTrascrizione.pubblicati.filterIsInstance<VoceDivisa>().last().nuova
        }

        override fun riassegna(registrazioneId: RegistrazioneId, segmento: SegmentoId, destinazione: VoceId?): VoceId {
            RiassegnaSegmentoServizio(eventiTrascrizione.unitaDiLavoro, trascritti, eventiTrascrizione)
                .esegui(RiassegnaSegmento(registrazioneId, segmento, destinazione))
                .atteso()
            return eventiTrascrizione.pubblicati.filterIsInstance<SegmentoRiassegnato>().last().a
        }

        override fun conferma(registrazioneId: RegistrazioneId, segmento: SegmentoId) {
            ConfermaSegmentoServizio(eventiTrascrizione.unitaDiLavoro, trascritti, eventiTrascrizione)
                .esegui(ConfermaSegmento(registrazioneId, segmento, confermato = true))
                .atteso()
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

        private companion object {
            /** Comfortably above every fineMs the contract seeds (max 31 000 ms) and the >= 60 000 ms floor. */
            const val DURATA_REGISTRAZIONE_MS = 3_600_000L
        }
    }
}

/** [Allineatore] that tags each Segmento with the ORIGINAL index of its seeding Turno (see class doc). */
private class AllineatoreConIndice : Allineatore {
    override fun allinea(campioni: CampioniAudio, turni: List<Turno>): List<SegmentoGrezzo> =
        turni.mapIndexed { i, t -> SegmentoGrezzo(t.voceIndice, t.intervallo, i.toString()) }
}
