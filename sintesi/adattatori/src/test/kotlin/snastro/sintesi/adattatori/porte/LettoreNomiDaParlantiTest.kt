package snastro.sintesi.adattatori.porte

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
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
import snastro.parlanti.applicazione.porte.DecodificatoreAudioFinta
import snastro.parlanti.applicazione.porte.EstrattoreImprontaFinta
import snastro.parlanti.applicazione.porte.LettoreRegistrazioneFinta
import snastro.parlanti.applicazione.porte.LettoreVociFinta
import snastro.parlanti.applicazione.porte.ParlanteRepositoryFinta
import snastro.parlanti.applicazione.porte.RegistrazioneVista
import snastro.parlanti.applicazione.porte.VoceVista
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
import snastro.sintesi.applicazione.porte.AmbienteLettoreNomi
import snastro.sintesi.applicazione.porte.LettoreNomi
import snastro.sintesi.applicazione.porte.LettoreNomiContratto
import snastro.sintesi.applicazione.porte.ParlanteSeminato
import snastro.sintesi.applicazione.porte.RegistrazioneSeminata
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * D2 (dev-architecture-app.md#porta-contratto): [LettoreNomiDaParlanti] passes [LettoreNomiContratto]
 * real-on-real (AC-S53). Parlanti is seeded EXCLUSIVELY through ITS OWN commands —
 * `ConfermaAttribuzioneServizio` / `RinominaParlanteServizio` / `EliminaParlanteServizio` — over its
 * OWN in-memory port fakes (`applicazione` testFixtures): ADR 0021 §2's edge table gives
 * `:sintesi:adattatori` no edge to `:parlanti:adattatori` (ADR 0002, CR-1: consumer:adattatori
 * reaches only supplier:applicazione, never supplier:adattatori), so this test — like
 * `sbobinatura:adattatori`'s `LettoreNomiDaParlantiTest` for the identical `nomi-per-*` port shape —
 * seeds Parlanti's REAL command services over its REAL `ParlanteRepositoryFinta`/
 * `AttribuzioneRepositoryFinta` (a legitimate port implementation, dev-architecture-app.md#test:
 * "fakes are mandatory for every port"), never touching a raw Parlanti aggregate or query. Parlanti's
 * OWN consumed ports (`LettoreRegistrazione`/`LettoreVoci`/`DecodificatoreAudio`/`EstrattoreImpronta`)
 * are Parlanti's OWN fakes too — none of them is part of the `nomi-per-sintesi` boundary under test,
 * so the Registrazione/Voci they need are synthesized directly, from the Registrazione and Incontro Progetto
 * minted through ITS commands (the adapter reads the Incontro's Parti from Progetto's `CatalogoRegistrazioni`).
 * This Ambiente's own contract, unlike Sbobinatura's, never promises real Trascritto-minted Voci (see
 * `AmbienteLettoreNomi`'s KDoc).
 *
 * [ParlanteSeminato] stays opaque at Sintesi's own boundary the whole time (ADR 0021 §7, INV-S5): only
 * this environment maps its [ParlanteSeminato.chiave] to the real [ParlanteId] Parlanti minted, read
 * back from the published [ParlanteCreato] event, never from a `Parlante` aggregate itself — no
 * `ParlanteId`/`snastro.parlanti.*` leaks past this file into `:sintesi:applicazione`.
 */
class LettoreNomiDaParlantiTest : LettoreNomiContratto() {
    override fun ambiente(): AmbienteLettoreNomi = AmbienteReale()

    private class AmbienteReale : AmbienteLettoreNomi {
        private val generatoreId = GeneratoreIdFinto()
        private val progettoId = ProgettoId(generatoreId.nuovo())

        private val parlanti = ParlanteRepositoryFinta()
        private val attribuzioni = AttribuzioneRepositoryFinta()
        private val unitaDiLavoro = UnitaDiLavoroFinta(parlanti, attribuzioni)
        private val eventi = DispatcherEventiFinta(unitaDiLavoro)

        /** Parlanti's OWN view of each Registrazione/Voce seeded so far (its consumed ports' fakes). */
        private val registrazioniViste = mutableMapOf<RegistrazioneId, RegistrazioneVista>()
        private val vociViste = mutableMapOf<IncontroId, List<VoceVista>>()

        // Progetto: seeded only through CreaProgettoServizio / AggiungiRegistrazioneServizio (ADR 0033 §4).
        private val clock = Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC)
        private val progetti = ProgettoRepositoryFinta()
        private val registrazioniProgetto = RegistrazioneRepositoryFinta()
        private val incontriProgetto = IncontroRepositoryFinta(registrazioniProgetto)
        private val eventiProgetto =
            DispatcherEventiFinta(UnitaDiLavoroFinta(registrazioniProgetto, progetti, incontriProgetto))
        private val archivio = ArchivioAudioFinta()
        private val catalogo =
            CatalogoRegistrazioni(registrazioniProgetto, incontriProgetto)
        private var contatore = 0

        init {
            CreaProgettoServizio(eventiProgetto.unitaDiLavoro, generatoreId, progetti, eventiProgetto)
                .esegui(CreaProgetto("Progetto di prova")).atteso()
        }

        override val lettore: LettoreNomi =
            LettoreNomiDaParlanti(NomiDelleVoci(attribuzioni, parlanti, unitaDiLavoro))

        private val confermaAttribuzione = ConfermaAttribuzioneServizio(
            eventi.unitaDiLavoro,
            generatoreId,
            LettoreRegistrazioneFinta(registrazioniViste),
            LettoreVociFinta(vociViste),
            parlanti,
            attribuzioni,
            DecodificatoreAudioFinta(),
            EstrattoreImprontaFinta(),
            eventi,
        )
        private val rinominaParlante = RinominaParlanteServizio(eventi.unitaDiLavoro, parlanti, eventi)
        private val eliminaParlante = EliminaParlanteServizio(eventi.unitaDiLavoro, parlanti, eventi)

        override fun aggiungiRegistrazione(voci: Int): RegistrazioneSeminata {
            require(voci >= 1) { "voci deve essere >= 1: $voci" }
            val id = importa(Destinazione.NuovoIncontro)
            return semina(id, checkNotNull(catalogo.registrazione(id)).incontroId, voci)
        }

        /**
         * Progetto's AggiungiRegistrazione into the existing Incontro, whose new Voci are numbered after its existing
         * ones (INV-I4).
         */
        override fun aggiungiParte(incontroId: IncontroId, voci: Int): RegistrazioneSeminata {
            require(voci >= 1) { "voci deve essere >= 1: $voci" }
            return semina(importa(Destinazione.Incontro(incontroId)), incontroId, voci)
        }

        /**
         * Parlanti's view of the new Parte [id] of [incontroId] with [voci] new Voci, MERGED into the Incontro's Voce
         * list: the Voci of the earlier Parti and their per-Registrazione intervals are kept (L97).
         */
        private fun semina(id: RegistrazioneId, incontroId: IncontroId, voci: Int): RegistrazioneSeminata {
            val gia = vociViste[incontroId].orEmpty()
            val refs = (gia.size + 1..gia.size + voci).map { n -> VoceRef(incontroId, VoceId(n)) }
            registrazioniViste[id] = RegistrazioneVista(
                registrazioneId = id,
                incontroId = incontroId,
                progettoId = progettoId,
                titolo = "Registrazione di prova",
                riferimentoAudio = RiferimentoAudio("audio/${id.valore}.wav"),
                dataRegistrazione = LocalDate.of(2026, 9, 23),
                durataMs = DURATA_REGISTRAZIONE_MS,
            )
            vociViste[incontroId] = gia + refs.mapIndexed { i, ref ->
                val inizio = i * 2_000L
                VoceVista(ref, mapOf(id to listOf(IntervalloMs(inizio, inizio + 1_000L))))
            }
            return RegistrazioneSeminata(id, incontroId, refs)
        }

        /** Progetto's AggiungiRegistrazione into [destinazione]. */
        private fun importa(destinazione: Destinazione): RegistrazioneId {
            val idProgetto = checkNotNull(progetti.trova()).id
            val percorso = "/sorgenti/parte-${++contatore}.m4a"
            archivio.conSorgente(percorso)
            val sonda = SondaAudioFinta(leggibili = mapOf(percorso to InfoAudio(60_000L, LocalDate.of(2026, 10, 1))))
            AggiungiRegistrazioneServizio(
                eventiProgetto.unitaDiLavoro,
                generatoreId,
                clock,
                progetti,
                registrazioniProgetto,
                incontriProgetto,
                sonda,
                archivio,
                eventiProgetto,
            ).esegui(AggiungiRegistrazione(idProgetto, listOf(percorso), destinazione)).atteso()
            return eventiProgetto.pubblicati.filterIsInstance<RegistrazioneAggiunta>().last().registrazioneId
        }

        override fun attribuisciANuovo(voce: VoceRef, nome: String): ParlanteSeminato {
            confermaAttribuzione.esegui(ConfermaAttribuzione(voce, ObiettivoAttribuzione.NuovoParlante(nome))).atteso()
            val id = eventi.pubblicati.filterIsInstance<ParlanteCreato>().last().parlanteId
            return ParlanteSeminato(id.valore)
        }

        override fun attribuisci(voce: VoceRef, parlante: ParlanteSeminato) {
            val obiettivo = ObiettivoAttribuzione.ParlanteEsistente(ParlanteId(parlante.chiave))
            confermaAttribuzione.esegui(ConfermaAttribuzione(voce, obiettivo)).atteso()
        }

        override fun rinomina(parlante: ParlanteSeminato, nome: String) {
            rinominaParlante.esegui(RinominaParlante(ParlanteId(parlante.chiave), nome)).atteso()
        }

        override fun elimina(parlante: ParlanteSeminato) {
            eliminaParlante.esegui(EliminaParlante(ParlanteId(parlante.chiave))).atteso()
        }

        private companion object {
            const val DURATA_REGISTRAZIONE_MS = 3_600_000L
        }
    }
}
