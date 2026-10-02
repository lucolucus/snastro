package snastro.parlanti.adattatori.porte

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.parlanti.applicazione.porte.AmbienteLettoreRegistrazione
import snastro.parlanti.applicazione.porte.LettoreRegistrazione
import snastro.parlanti.applicazione.porte.LettoreRegistrazioneContratto
import snastro.parlanti.applicazione.porte.SemeRegistrazione
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.comandi.AggiungiRegistrazioneServizio
import snastro.progetto.applicazione.comandi.CreaProgetto
import snastro.progetto.applicazione.comandi.CreaProgettoServizio
import snastro.progetto.applicazione.comandi.Destinazione
import snastro.progetto.applicazione.comandi.EliminaRegistrazione
import snastro.progetto.applicazione.comandi.EliminaRegistrazioneServizio
import snastro.progetto.applicazione.comandi.ModificaDataRegistrazione
import snastro.progetto.applicazione.comandi.ModificaDataRegistrazioneServizio
import snastro.progetto.applicazione.eventi.ProgettoCreato
import snastro.progetto.applicazione.eventi.RegistrazioneAggiunta
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni
import snastro.progetto.applicazione.porte.ArchivioAudioFinta
import snastro.progetto.applicazione.porte.EliminazioniInSospesoFinta
import snastro.progetto.applicazione.porte.IncontroRepositoryFinta
import snastro.progetto.applicazione.porte.InfoAudio
import snastro.progetto.applicazione.porte.ProgettoRepositoryFinta
import snastro.progetto.applicazione.porte.RegistrazioneRepositoryFinta
import snastro.progetto.applicazione.porte.SondaAudioFinta
import snastro.supporto.test.OrologioFinto
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds

/**
 * D2 (dev-architecture-app.md#porta-contratto): [LettoreRegistrazioneDaProgetto] passes
 * [LettoreRegistrazioneContratto] real-on-real (AC-136). The supplier is populated exclusively
 * through ITS OWN commands (`CreaProgetto`, `AggiungiRegistrazione`, `ModificaDataRegistrazione`) —
 * this test never builds a `Registrazione`/`Progetto` itself. The commands run over Progetto's own
 * in-memory port fakes (`RegistrazioneRepositoryFinta`, `ProgettoRepositoryFinta`, `SondaAudioFinta`,
 * `ArchivioAudioFinta`, all `progetto:applicazione` testFixtures): the cross-context dependency rule
 * (ADR 0002, CR-1) allows `parlanti:adattatori` to call only `progetto:applicazione`, never
 * `progetto:adattatori`'s SQL repositories — so the minted ids are read back from Progetto's own
 * published events (`ProgettoCreato`, `RegistrazioneAggiunta`), never from a `dominio` type.
 */
class LettoreRegistrazioneDaProgettoTest : LettoreRegistrazioneContratto() {
    override fun ambiente(): AmbienteLettoreRegistrazione = AmbienteReale()

    private class AmbienteReale : AmbienteLettoreRegistrazione {
        // Advances at every import, so the import order is a real aggiuntaAlle order (INV-I2), never an id tie-break.
        private val clock = OrologioFinto(Instant.parse("2026-09-24T10:00:00Z"))
        private val generatoreId = GeneratoreIdFinto()
        private val progetti = ProgettoRepositoryFinta()
        private val registrazioni = RegistrazioneRepositoryFinta()
        private val incontri = IncontroRepositoryFinta(registrazioni)
        private val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(registrazioni, progetti, incontri))
        private val archivio = ArchivioAudioFinta()
        private val catalogo = CatalogoRegistrazioni(registrazioni, incontri)
        private var contatore = 0

        override val progettoId: ProgettoId = run {
            CreaProgettoServizio(eventi.unitaDiLavoro, generatoreId, progetti, eventi)
                .esegui(CreaProgetto("Progetto di prova")).atteso()
            eventi.pubblicati.filterIsInstance<ProgettoCreato>().single().progettoId
        }

        override val lettore: LettoreRegistrazione = LettoreRegistrazioneDaProgetto(catalogo)

        /** On (D-0037): [aggiungiParte] goes through Progetto's own import into the Incontro (I2). */
        override val piuPartiPerIncontro: Boolean = true

        override fun semina(seme: SemeRegistrazione): RegistrazioneId = importa(seme, Destinazione.NuovoIncontro)

        override fun aggiungiParte(incontroId: IncontroId, seme: SemeRegistrazione): RegistrazioneId =
            importa(seme, Destinazione.Incontro(incontroId))

        private fun importa(seme: SemeRegistrazione, destinazione: Destinazione): RegistrazioneId {
            val percorso = "/sorgenti/${++contatore}/${seme.titolo}.${seme.estensione}"
            archivio.conSorgente(percorso)
            val sonda = SondaAudioFinta(
                leggibili = mapOf(percorso to InfoAudio(seme.durataMs, seme.dataRegistrazione)),
            )
            clock.avanza(1.seconds)
            AggiungiRegistrazioneServizio(
                eventi.unitaDiLavoro,
                generatoreId,
                clock,
                progetti,
                registrazioni,
                incontri,
                sonda,
                archivio,
                eventi,
            ).esegui(AggiungiRegistrazione(progettoId, listOf(percorso), destinazione)).atteso()

            return eventi.pubblicati.filterIsInstance<RegistrazioneAggiunta>().last().registrazioneId
        }

        // The supplier's own public read API: the id AggiungiRegistrazione minted through GeneratoreId.
        override fun incontroDi(id: RegistrazioneId): IncontroId = checkNotNull(catalogo.registrazione(id)).incontroId

        override fun elimina(id: RegistrazioneId) {
            EliminaRegistrazioneServizio(eventi.unitaDiLavoro, registrazioni, incontri, EliminazioniInSospesoFinta(), eventi)
                .esegui(EliminaRegistrazione(id)).atteso()
        }

        override fun modificaData(id: RegistrazioneId, data: LocalDate) {
            ModificaDataRegistrazioneServizio(eventi.unitaDiLavoro, registrazioni, eventi)
                .esegui(ModificaDataRegistrazione(id, data)).atteso()
        }
    }
}
