package snastro.parlanti.adattatori.porte

import snastro.kernel.CampioniAudio
import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoRef
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
import snastro.trascrizione.applicazione.porte.Turno
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepositoryFinta
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds

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
        // Advances at every import, so the import order is a real aggiuntaAlle order (INV-I2), never an id tie-break.
        private val clock = OrologioFinto(Instant.parse("2026-09-24T10:00:00Z"))
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
        // UnisciVociServizio / DividiVoceServizio / RiassegnaSegmentoServizio.
        private val elaborazioni = ElaborazioneRepositoryFinta()
        private val trascritti = VociDellIncontroRepositoryFinta()
        private val eventiTrascrizione = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))

        /** Trascrizione's own view of each Registrazione seeded so far (its `LettoreRegistrazione` port). */
        private val registrazioniViste = mutableMapOf<RegistrazioneId, RegistrazioneVista>()
        private var contatore = 0

        init {
            CreaProgettoServizio(eventiProgetto.unitaDiLavoro, generatoreId, progetti, eventiProgetto)
                .esegui(CreaProgetto("Progetto di prova"))
                .atteso()
        }

        override val lettore: LettoreVoci = LettoreVociDaTrascrizione(
            VociDelTrascritto(trascritti, LettoreRegistrazioneFinta(registrazioniViste), UnitaDiLavoroFinta()),
        )

        override fun aggiungiParte(incontroId: IncontroId): RegistrazioneId = importa(Destinazione.Incontro(incontroId))

        override fun aggiungiRegistrazione(): RegistrazioneId = importa(Destinazione.NuovoIncontro)

        /** Progetto's AggiungiRegistrazione to [destinazione], then Trascrizione's own view of the new Parte. */
        private fun importa(destinazione: Destinazione): RegistrazioneId {
            val percorso = "/sorgenti/registrazione-${contatore++}.wav"
            archivio.conSorgente(percorso)
            val sonda = SondaAudioFinta(
                leggibili = mapOf(percorso to InfoAudio(DURATA_REGISTRAZIONE_MS, LocalDate.of(2026, 9, 20))),
            )
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
                incontroId = v.incontroId,
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

            val trascritto = checkNotNull(trascritti.trascritto(registrazioneId))
            val perIndice = trascritto.segmenti.associateBy { it.testo.toInt() }
            return turni.indices.map { i ->
                val segmento = perIndice.getValue(i)
                SegmentoConiato(segmento.id, segmento.voceId)
            }
        }

        // The supplier's own public read API: the Incontro AggiungiRegistrazione minted for the Registrazione.
        override fun incontroDi(registrazioneId: RegistrazioneId): IncontroId =
            registrazioniViste.getValue(registrazioneId).incontroId

        override fun fallisciElaborazione(registrazioneId: RegistrazioneId) {
            avvia(registrazioneId)
            // Nessun turno diarizzato -> Trascritto.crea rifiuta con NessunParlatoRilevato (AC-72).
            eseguiPipeline(registrazioneId, DiarizzatoreFinta(emptyList()), AllineatoreConIndice())
        }

        override fun unisci(incontroId: IncontroId, sopravvive: VoceId, rimossa: VoceId) {
            val registrazioneId = unaParteTrascritta(incontroId)
            UnisciVociServizio(
                eventiTrascrizione.unitaDiLavoro,
                trascritti,
                LettoreRegistrazioneFinta(registrazioniViste),
                eventiTrascrizione,
            )
                .esegui(UnisciVoci(registrazioneId, sopravvive, rimossa, incontroDelleVoci = incontroId))
                .atteso()
        }

        override fun dividi(incontroId: IncontroId, origine: VoceId, segmenti: Set<SegmentoRef>): VoceId {
            // DividiVoce is per Parte: the Segmenti to split all belong to one Parte of the Incontro.
            val registrazioneId = segmenti.map { it.registrazioneId }.distinct().single()
            require(registrazioniViste.getValue(registrazioneId).incontroId == incontroId)
            DividiVoceServizio(
                eventiTrascrizione.unitaDiLavoro,
                trascritti,
                LettoreRegistrazioneFinta(registrazioniViste),
                eventiTrascrizione,
            )
                .esegui(DividiVoce(registrazioneId, origine, segmenti.map { it.segmentoId }.toSet(), incontroId))
                .atteso()
            return eventiTrascrizione.pubblicati.filterIsInstance<VoceDivisa>().last().nuova
        }

        override fun riassegna(segmento: SegmentoRef, destinazione: VoceId?): VoceId {
            RiassegnaSegmentoServizio(
                eventiTrascrizione.unitaDiLavoro,
                trascritti,
                LettoreRegistrazioneFinta(registrazioniViste),
                eventiTrascrizione,
            )
                .esegui(RiassegnaSegmento(segmento.registrazioneId, segmento.segmentoId, destinazione))
                .atteso()
            return eventiTrascrizione.pubblicati.filterIsInstance<SegmentoRiassegnato>().last().a
        }

        override fun conferma(segmento: SegmentoRef) {
            ConfermaSegmentoServizio(
                eventiTrascrizione.unitaDiLavoro,
                trascritti,
                LettoreRegistrazioneFinta(registrazioniViste),
                eventiTrascrizione,
            )
                .esegui(ConfermaSegmento(segmento.registrazioneId, segmento.segmentoId, confermato = true))
                .atteso()
        }

        /** A Parte of [incontroId] with a Trascritto: the commands of the Voci dell'Incontro go through any of them. */
        private fun unaParteTrascritta(incontroId: IncontroId): RegistrazioneId =
            registrazioniViste.values
                .first { it.incontroId == incontroId && trascritti.trascritto(it.registrazioneId) != null }
                .registrazioneId

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
