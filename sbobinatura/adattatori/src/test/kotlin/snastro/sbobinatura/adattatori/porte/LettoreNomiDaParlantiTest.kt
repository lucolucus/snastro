package snastro.sbobinatura.adattatori.porte

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IncontroId
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
import snastro.parlanti.applicazione.comandi.unObiettivoOccasionale
import snastro.parlanti.applicazione.eventi.ParlanteCreato
import snastro.parlanti.applicazione.letture.NomiDelleVoci
import snastro.parlanti.applicazione.porte.AttribuzioneRepositoryFinta
import snastro.parlanti.applicazione.porte.EstrattoreImprontaFinta
import snastro.parlanti.applicazione.porte.ParlanteRepositoryFinta
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
import snastro.sbobinatura.applicazione.porte.AmbienteLettoreNomi
import snastro.sbobinatura.applicazione.porte.LettoreNomi
import snastro.sbobinatura.applicazione.porte.LettoreNomiContratto
import snastro.sbobinatura.applicazione.porte.RegistrazioneConiata
import snastro.supporto.test.OrologioFinto
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.comandi.AvviaElaborazioneServizio
import snastro.trascrizione.applicazione.comandi.EseguiProssimaElaborazione
import snastro.trascrizione.applicazione.comandi.EseguiProssimaElaborazioneServizio
import snastro.trascrizione.applicazione.comandi.PortePipeline
import snastro.trascrizione.applicazione.letture.VociDelTrascritto
import snastro.trascrizione.applicazione.porte.AllineatoreFinta
import snastro.trascrizione.applicazione.porte.DiarizzatoreFinta
import snastro.trascrizione.applicazione.porte.ElaborazioneRepositoryFinta
import snastro.trascrizione.applicazione.porte.SegnalatoreFaseFinta
import snastro.trascrizione.applicazione.porte.Turno
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepositoryFinta
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds
import snastro.parlanti.applicazione.porte.DecodificatoreAudioFinta as DecodificatoreAudioFintaParlanti
import snastro.parlanti.applicazione.porte.LettoreRegistrazioneFinta as LettoreRegistrazioneFintaParlanti
import snastro.parlanti.applicazione.porte.LettoreVociFinta as LettoreVociFintaParlanti
import snastro.parlanti.applicazione.porte.RegistrazioneVista as RegistrazioneVistaParlanti
import snastro.parlanti.applicazione.porte.VoceVista as VoceVistaParlanti
import snastro.trascrizione.applicazione.porte.DecodificatoreAudioFinta as DecodificatoreAudioFintaTrascrizione
import snastro.trascrizione.applicazione.porte.LettoreRegistrazioneFinta as LettoreRegistrazioneFintaTrascrizione
import snastro.trascrizione.applicazione.porte.RegistrazioneVista as RegistrazioneVistaTrascrizione

/**
 * D2 (dev-architecture-app.md#porta-contratto): [LettoreNomiDaParlanti] passes [LettoreNomiContratto]
 * real-on-real (AC-139). THREE suppliers are populated exclusively through THEIR OWN commands —
 * Progetto's `CreaProgetto`/`AggiungiRegistrazione`, Trascrizione's `AvviaElaborazione`/
 * `EseguiProssimaElaborazione` (to mint real Voci, so `VoceRef`s are real), Parlanti's
 * `ConfermaAttribuzione`/`RinominaParlante`/`EliminaParlante` (the actual boundary supplier) — this
 * test never builds a `Registrazione`/`Trascritto`/`Parlante`/`Attribuzione` itself. Every command
 * runs over its OWN in-memory port fakes (`applicazione` testFixtures): the cross-context dependency
 * rule (ADR 0002, CR-1) allows `sbobinatura:adattatori` to call only each context's `applicazione`,
 * never their `adattatori`'s SQL repositories — minted ids are read back from each supplier's own
 * published events / query API, never from a `dominio` type. Parlanti's OWN `LettoreRegistrazione` /
 * `LettoreVoci` ports (consumed by ITS commands) are Parlanti's OWN fakes, fed from Progetto's /
 * Trascrizione's real output; the ML ports (`DecodificatoreAudio`, `EstrattoreImpronta`)
 * `ConfermaAttribuzioneServizio` consumes are Parlanti's OWN technical fakes too — neither pair is
 * part of the `nomi-per-sbobinatura` boundary under test.
 */
class LettoreNomiDaParlantiTest : LettoreNomiContratto() {
    override fun ambiente(): AmbienteLettoreNomi = AmbienteReale()

    private class AmbienteReale : AmbienteLettoreNomi {
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

        // Trascrizione: seeded only through AvviaElaborazioneServizio / EseguiProssimaElaborazioneServizio
        // (to mint real Voci — Parlanti's Attribuzioni need real VoceRefs).
        private val elaborazioni = ElaborazioneRepositoryFinta()
        private val trascritti = VociDellIncontroRepositoryFinta()
        private val eventiTrascrizione = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))
        private val registrazioniVisteTrascrizione = mutableMapOf<RegistrazioneId, RegistrazioneVistaTrascrizione>()

        /** Trascrizione's own read of the Voci dell'Incontro: what Parlanti's `LettoreVoci` sees across the Parti. */
        private val vociDelTrascritto = VociDelTrascritto(
            trascritti,
            LettoreRegistrazioneFintaTrascrizione(registrazioniVisteTrascrizione),
            UnitaDiLavoroFinta(),
        )

        // Parlanti: seeded only through ConfermaAttribuzioneServizio / RinominaParlanteServizio /
        // EliminaParlanteServizio.
        private val parlanti = ParlanteRepositoryFinta()
        private val attribuzioni = AttribuzioneRepositoryFinta()
        private val uowParlanti = UnitaDiLavoroFinta(parlanti, attribuzioni)
        private val eventiParlanti = DispatcherEventiFinta(uowParlanti)

        /** Parlanti's OWN view of each Registrazione/Voce seeded so far (its consumed ports' fakes). */
        private val registrazioniVisteParlanti = mutableMapOf<RegistrazioneId, RegistrazioneVistaParlanti>()
        private val vociVisteParlanti = mutableMapOf<IncontroId, List<VoceVistaParlanti>>()

        private var contatore = 0

        init {
            CreaProgettoServizio(eventiProgetto.unitaDiLavoro, generatoreId, progetti, eventiProgetto)
                .esegui(CreaProgetto("Progetto di prova"))
                .atteso()
        }

        override val lettore: LettoreNomi = LettoreNomiDaParlanti(
            NomiDelleVoci(attribuzioni, parlanti, uowParlanti),
        )

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
            return completaElaborazione(aggiungiRegistrazioneProgetto(Destinazione.NuovoIncontro), voci)
        }

        override fun aggiungiParte(incontroId: IncontroId, voci: Int): RegistrazioneConiata {
            require(voci >= 1) { "voci deve essere >= 1: $voci" }
            return completaElaborazione(aggiungiRegistrazioneProgetto(Destinazione.Incontro(incontroId)), voci)
        }

        /** Progetto: CreaProgetto (già in [init]) + AggiungiRegistrazione, poi le due viste dello stesso dato. */
        private fun aggiungiRegistrazioneProgetto(destinazione: Destinazione): RegistrazioneId {
            val percorso = "/sorgenti/registrazione-${contatore++}.wav"
            archivio.conSorgente(percorso)
            val sonda = SondaAudioFinta(
                leggibili = mapOf(percorso to InfoAudio(DURATA_REGISTRAZIONE_MS, LocalDate.of(2026, 9, 20))),
            )
            clock.avanza(1.seconds)
            val servizioAggiungi = AggiungiRegistrazioneServizio(
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
            servizioAggiungi.esegui(
                AggiungiRegistrazione(checkNotNull(progetti.trova()).id, listOf(percorso), destinazione),
            ).atteso()

            val id = eventiProgetto.pubblicati.filterIsInstance<RegistrazioneAggiunta>().last().registrazioneId
            val v = checkNotNull(catalogo.registrazione(id))
            registrazioniVisteTrascrizione[id] = RegistrazioneVistaTrascrizione(
                registrazioneId = v.registrazioneId,
                progettoId = v.progettoId,
                incontroId = v.incontroId,
                titolo = v.titolo,
                riferimentoAudio = v.riferimentoAudio,
                dataRegistrazione = v.dataRegistrazione,
                durataMs = v.durataMs,
            )
            registrazioniVisteParlanti[id] = RegistrazioneVistaParlanti(
                registrazioneId = v.registrazioneId,
                progettoId = v.progettoId,
                incontroId = v.incontroId,
                titolo = v.titolo,
                riferimentoAudio = v.riferimentoAudio,
                dataRegistrazione = v.dataRegistrazione,
                durataMs = v.durataMs,
            )
            return id
        }

        /** Trascrizione: AvviaElaborazione + EseguiProssimaElaborazione su `voci` Voci non sovrapposte. */
        private fun completaElaborazione(id: RegistrazioneId, voci: Int): RegistrazioneConiata {
            AvviaElaborazioneServizio(
                eventiTrascrizione.unitaDiLavoro,
                generatoreId,
                clock,
                LettoreRegistrazioneFintaTrascrizione(registrazioniVisteTrascrizione),
                elaborazioni,
            ).esegui(AvviaElaborazione(id)).atteso()

            // Ciascuna prima apparizione crescente -> VoceId 1..voci nell'ordine dato.
            val turni = (0 until voci).map { i -> Turno(IntervalloMs(i * 2_000L, i * 2_000L + 1_000L), voceIndice = i) }
            val vista = registrazioniVisteTrascrizione.getValue(id)
            val decodificatore = DecodificatoreAudioFintaTrascrizione(mapOf(vista.riferimentoAudio to vista.durataMs))
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

            val trascritto = checkNotNull(trascritti.trascritto(id))
            // Every Voce of the Incontro with its intervals in each Parte (Trascrizione's own read).
            val incontro = trascritto.incontroId
            vociVisteParlanti[incontro] = checkNotNull(vociDelTrascritto.voci(incontro))
                .map { VoceVistaParlanti(it.voceRef, it.intervalliPerParte) }
            return RegistrazioneConiata(
                id,
                trascritto.incontroId,
                trascritto.voci.map { VoceRef(trascritto.incontroId, it.id) },
            )
        }

        override fun confermaNuovoParlante(voce: VoceRef, nome: String, occasionale: Boolean): ParlanteId {
            // `occasionale` stays a plain Boolean at this test fixture's own boundary (AmbienteLettoreNomi,
            // sbobinatura:applicazione testFixtures): the OCCASIONALE case goes through Parlanti's OWN
            // `unObiettivoOccasionale` applicazione-level fixture (L653), so this sbobinatura:adattatori
            // file never references `parlanti:dominio`'s TipoParlante itself (CR-1); the RICORRENTE
            // branch never needs it at all (NuovoParlante's own default).
            val obiettivo = if (occasionale) {
                unObiettivoOccasionale(nome)
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
