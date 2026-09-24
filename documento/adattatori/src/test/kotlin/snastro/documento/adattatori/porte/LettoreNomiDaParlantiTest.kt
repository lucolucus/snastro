package snastro.documento.adattatori.porte

import snastro.documento.applicazione.porte.AmbienteLettoreNomi
import snastro.documento.applicazione.porte.LettoreNomi
import snastro.documento.applicazione.porte.LettoreNomiContratto
import snastro.documento.applicazione.porte.RegistrazioneConiata
import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.parlanti.applicazione.comandi.ConfermaAttribuzione
import snastro.parlanti.applicazione.comandi.ConfermaAttribuzioneServizio
import snastro.parlanti.applicazione.comandi.EliminaParlante
import snastro.parlanti.applicazione.comandi.EliminaParlanteServizio
import snastro.parlanti.applicazione.comandi.ObiettivoAttribuzione
import snastro.parlanti.applicazione.comandi.RinominaParlante
import snastro.parlanti.applicazione.comandi.RinominaParlanteServizio
import snastro.parlanti.applicazione.eventi.ParlanteCreato
import snastro.parlanti.applicazione.letture.NomiDelleVoci
import snastro.parlanti.applicazione.porte.AttribuzioneRepositoryFinta
import snastro.parlanti.applicazione.porte.DecodificatoreAudioFinta as DecodificatoreAudioFintaParlanti
import snastro.parlanti.applicazione.porte.EstrattoreImprontaFinta
import snastro.parlanti.applicazione.porte.LettoreRegistrazioneFinta as LettoreRegistrazioneFintaParlanti
import snastro.parlanti.applicazione.porte.LettoreVociFinta as LettoreVociFintaParlanti
import snastro.parlanti.applicazione.porte.ParlanteRepositoryFinta
import snastro.parlanti.applicazione.porte.RegistrazioneVista as RegistrazioneVistaParlanti
import snastro.parlanti.applicazione.porte.VoceVista as VoceVistaParlanti
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
import snastro.trascrizione.applicazione.porte.AllineatoreFinta
import snastro.trascrizione.applicazione.porte.DecodificatoreAudioFinta as DecodificatoreAudioFintaTrascrizione
import snastro.trascrizione.applicazione.porte.DiarizzatoreFinta
import snastro.trascrizione.applicazione.porte.ElaborazioneRepositoryFinta
import snastro.trascrizione.applicazione.porte.LettoreRegistrazioneFinta as LettoreRegistrazioneFintaTrascrizione
import snastro.trascrizione.applicazione.porte.RegistrazioneVista as RegistrazioneVistaTrascrizione
import snastro.trascrizione.applicazione.porte.SegnalatoreFaseFinta
import snastro.trascrizione.applicazione.porte.TrascrittoRepositoryFinta
import snastro.trascrizione.applicazione.porte.Turno
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * D2 (dev-architecture-app.md#porta-contratto): [LettoreNomiDaParlanti] passes [LettoreNomiContratto]
 * real-on-real (AC-139). THREE suppliers are populated exclusively through THEIR OWN commands —
 * Progetto's `CreaProgetto`/`AggiungiRegistrazione`, Trascrizione's `AvviaElaborazione`/
 * `EseguiProssimaElaborazione` (to mint real Voci, so `VoceRef`s are real), Parlanti's
 * `ConfermaAttribuzione`/`RinominaParlante`/`EliminaParlante` (the actual boundary supplier) — this
 * test never builds a `Registrazione`/`Trascritto`/`Parlante`/`Attribuzione` itself. Every command
 * runs over its OWN in-memory port fakes (`applicazione` testFixtures): the cross-context dependency
 * rule (ADR 0002, CR-1) allows `documento:adattatori` to call only each context's `applicazione`,
 * never their `adattatori`'s SQL repositories — minted ids are read back from each supplier's own
 * published events / query API, never from a `dominio` type. Parlanti's OWN `LettoreRegistrazione` /
 * `LettoreVoci` ports (consumed by ITS commands) are Parlanti's OWN fakes, fed from Progetto's /
 * Trascrizione's real output; the ML ports (`DecodificatoreAudio`, `EstrattoreImpronta`)
 * `ConfermaAttribuzioneServizio` consumes are Parlanti's OWN technical fakes too — neither pair is
 * part of the `nomi-per-documento` boundary under test.
 */
class LettoreNomiDaParlantiTest : LettoreNomiContratto() {
    override fun ambiente(): AmbienteLettoreNomi = AmbienteReale()

    private class AmbienteReale : AmbienteLettoreNomi {
        private val clock = Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneOffset.UTC)
        private val generatoreId = GeneratoreIdFinto()

        // Progetto: seeded only through CreaProgettoServizio / AggiungiRegistrazioneServizio.
        private val progetti = ProgettoRepositoryFinta()
        private val registrazioniProgetto = RegistrazioneRepositoryFinta()
        private val eventiProgetto = DispatcherEventiFinta(UnitaDiLavoroFinta(registrazioniProgetto, progetti))
        private val archivio = ArchivioAudioFinta()
        private val catalogo = CatalogoRegistrazioni(registrazioniProgetto)

        // Trascrizione: seeded only through AvviaElaborazioneServizio / EseguiProssimaElaborazioneServizio
        // (to mint real Voci — Parlanti's Attribuzioni need real VoceRefs).
        private val elaborazioni = ElaborazioneRepositoryFinta()
        private val trascritti = TrascrittoRepositoryFinta()
        private val eventiTrascrizione = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))
        private val registrazioniVisteTrascrizione = mutableMapOf<RegistrazioneId, RegistrazioneVistaTrascrizione>()

        // Parlanti: seeded only through ConfermaAttribuzioneServizio / RinominaParlanteServizio /
        // EliminaParlanteServizio.
        private val parlanti = ParlanteRepositoryFinta()
        private val attribuzioni = AttribuzioneRepositoryFinta()
        private val uowParlanti = UnitaDiLavoroFinta(parlanti, attribuzioni)
        private val eventiParlanti = DispatcherEventiFinta(uowParlanti)

        /** Parlanti's OWN view of each Registrazione/Voce seeded so far (its consumed ports' fakes). */
        private val registrazioniVisteParlanti = mutableMapOf<RegistrazioneId, RegistrazioneVistaParlanti>()
        private val vociVisteParlanti = mutableMapOf<RegistrazioneId, List<VoceVistaParlanti>>()

        private var contatore = 0

        init {
            CreaProgettoServizio(eventiProgetto.unitaDiLavoro, generatoreId, progetti, eventiProgetto)
                .esegui(CreaProgetto("Progetto di prova"))
                .atteso()
        }

        override val lettore: LettoreNomi = LettoreNomiDaParlanti(NomiDelleVoci(attribuzioni, parlanti))

        private val confermaAttribuzione = ConfermaAttribuzioneServizio(
            eventiParlanti.unitaDiLavoro,
            generatoreId,
            LettoreRegistrazioneFintaParlanti(registrazioniVisteParlanti),
            LettoreVociFintaParlanti(vociVisteParlanti),
            parlanti,
            attribuzioni,
            DecodificatoreAudioFintaParlanti(uowParlanti),
            EstrattoreImprontaFinta(unitaDiLavoro = uowParlanti),
            eventiParlanti,
        )
        private val rinominaParlante = RinominaParlanteServizio(eventiParlanti.unitaDiLavoro, parlanti, eventiParlanti)
        private val eliminaParlante = EliminaParlanteServizio(eventiParlanti.unitaDiLavoro, parlanti, eventiParlanti)

        override fun aggiungiRegistrazione(voci: Int): RegistrazioneConiata {
            require(voci >= 1) { "voci deve essere >= 1: $voci" }
            val percorso = "/sorgenti/registrazione-${contatore++}.wav"
            archivio.conSorgente(percorso)
            val sonda = SondaAudioFinta(
                leggibili = mapOf(percorso to InfoAudio(DURATA_REGISTRAZIONE_MS, LocalDate.of(2026, 9, 20))),
            )
            val servizioAggiungi = AggiungiRegistrazioneServizio(
                eventiProgetto.unitaDiLavoro,
                generatoreId,
                clock,
                progetti,
                registrazioniProgetto,
                sonda,
                archivio,
                eventiProgetto,
            )
            servizioAggiungi.esegui(AggiungiRegistrazione(percorso)).atteso()

            val id = eventiProgetto.pubblicati.filterIsInstance<RegistrazioneAggiunta>().last().registrazioneId
            val v = checkNotNull(catalogo.registrazione(id))
            registrazioniVisteTrascrizione[id] = RegistrazioneVistaTrascrizione(
                registrazioneId = v.registrazioneId,
                progettoId = v.progettoId,
                titolo = v.titolo,
                riferimentoAudio = v.riferimentoAudio,
                dataRegistrazione = v.dataRegistrazione,
                durataMs = v.durataMs,
            )
            registrazioniVisteParlanti[id] = RegistrazioneVistaParlanti(
                registrazioneId = v.registrazioneId,
                progettoId = v.progettoId,
                titolo = v.titolo,
                riferimentoAudio = v.riferimentoAudio,
                dataRegistrazione = v.dataRegistrazione,
                durataMs = v.durataMs,
            )

            AvviaElaborazioneServizio(
                eventiTrascrizione.unitaDiLavoro,
                generatoreId,
                clock,
                LettoreRegistrazioneFintaTrascrizione(registrazioniVisteTrascrizione),
                elaborazioni,
            ).esegui(AvviaElaborazione(id)).atteso()

            // `voci` Voci non sovrapposte, ciascuna prima apparizione crescente -> VoceId 1..voci in ordine.
            val turni = (0 until voci).map { i -> Turno(IntervalloMs(i * 2_000L, i * 2_000L + 1_000L), voceIndice = i) }
            val decodificatore = DecodificatoreAudioFintaTrascrizione(mapOf(v.riferimentoAudio to v.durataMs))
            val pipeline = PortePipeline(
                LettoreRegistrazioneFintaTrascrizione(registrazioniVisteTrascrizione),
                decodificatore,
                DiarizzatoreFinta(turni),
                AllineatoreFinta(),
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

            val trascritto = checkNotNull(trascritti.trova(id))
            vociVisteParlanti[id] = trascritto.voci.map { voce ->
                VoceVistaParlanti(VoceRef(id, voce.id), voce.segmenti.map { it.intervallo })
            }
            return RegistrazioneConiata(id, trascritto.voci.map { VoceRef(id, it.id) })
        }

        override fun confermaNuovoParlante(voce: VoceRef, nome: String, occasionale: Boolean): ParlanteId {
            // `occasionale` stays a plain Boolean at this test fixture's own boundary (AmbienteLettoreNomi,
            // documento:applicazione testFixtures): Parlanti's ObiettivoAttribuzione.NuovoParlante still
            // needs its OWN dominio TipoParlante for the occasionale case — referenced by fully-qualified
            // name (no import) so this documento:adattatori file never imports another context's dominio
            // (CR-1); the RICORRENTE branch never needs it at all (NuovoParlante's own default).
            val obiettivo = if (occasionale) {
                ObiettivoAttribuzione.NuovoParlante(nome, snastro.parlanti.dominio.TipoParlante.OCCASIONALE)
            } else {
                ObiettivoAttribuzione.NuovoParlante(nome)
            }
            confermaAttribuzione.esegui(ConfermaAttribuzione(voce, obiettivo)).atteso()
            return eventiParlanti.pubblicati.filterIsInstance<ParlanteCreato>().last().parlanteId
        }

        override fun conferma(voce: VoceRef, parlante: ParlanteId) {
            confermaAttribuzione.esegui(ConfermaAttribuzione(voce, ObiettivoAttribuzione.ParlanteEsistente(parlante)))
                .atteso()
        }

        override fun rinomina(parlante: ParlanteId, nome: String) {
            rinominaParlante.esegui(RinominaParlante(parlante, nome)).atteso()
        }

        override fun elimina(parlante: ParlanteId) {
            eliminaParlante.esegui(EliminaParlante(parlante)).atteso()
        }

        private companion object {
            const val DURATA_REGISTRAZIONE_MS = 3_600_000L
        }
    }
}
