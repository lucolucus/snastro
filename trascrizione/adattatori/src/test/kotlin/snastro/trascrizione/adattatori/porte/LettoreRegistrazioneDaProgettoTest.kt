package snastro.trascrizione.adattatori.porte

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.comandi.AggiungiRegistrazioneServizio
import snastro.progetto.applicazione.comandi.CreaProgetto
import snastro.progetto.applicazione.comandi.CreaProgettoServizio
import snastro.progetto.applicazione.comandi.ModificaDataRegistrazione
import snastro.progetto.applicazione.comandi.ModificaDataRegistrazioneServizio
import snastro.progetto.applicazione.eventi.ProgettoCreato
import snastro.progetto.applicazione.eventi.RegistrazioneAggiunta
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni
import snastro.progetto.applicazione.porte.ArchivioAudioFinta
import snastro.progetto.applicazione.porte.InfoAudio
import snastro.progetto.applicazione.porte.ProgettoRepositoryFinta
import snastro.progetto.applicazione.porte.RegistrazioneRepositoryFinta
import snastro.progetto.applicazione.porte.SondaAudioFinta
import snastro.trascrizione.applicazione.porte.AmbienteLettoreRegistrazione
import snastro.trascrizione.applicazione.porte.LettoreRegistrazione
import snastro.trascrizione.applicazione.porte.LettoreRegistrazioneContratto
import snastro.trascrizione.applicazione.porte.SemeRegistrazione
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * D2 (dev-architecture-app.md#porta-contratto): [LettoreRegistrazioneDaProgetto] passes
 * [LettoreRegistrazioneContratto] real-on-real (AC-135). The supplier is populated exclusively
 * through ITS OWN commands (`CreaProgetto`, `AggiungiRegistrazione`, `ModificaDataRegistrazione`) —
 * this test never builds a `Registrazione`/`Progetto` itself. The commands run over Progetto's own
 * in-memory port fakes (`RegistrazioneRepositoryFinta`, `ProgettoRepositoryFinta`, `SondaAudioFinta`,
 * `ArchivioAudioFinta`, all `progetto:applicazione` testFixtures): the cross-context dependency rule
 * (ADR 0002, CR-1) allows `trascrizione:adattatori` to call only `progetto:applicazione`, never
 * `progetto:adattatori`'s SQL repositories — so the minted ids are read back from Progetto's own
 * published events (`ProgettoCreato`, `RegistrazioneAggiunta`), never from a `dominio` type.
 */
class LettoreRegistrazioneDaProgettoTest : LettoreRegistrazioneContratto() {
    override fun ambiente(): AmbienteLettoreRegistrazione = AmbienteReale()

    private class AmbienteReale : AmbienteLettoreRegistrazione {
        private val clock = Clock.fixed(Instant.parse("2026-09-23T10:00:00Z"), ZoneOffset.UTC)
        private val generatoreId = GeneratoreIdFinto()
        private val progetti = ProgettoRepositoryFinta()
        private val registrazioni = RegistrazioneRepositoryFinta()
        private val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(registrazioni, progetti))
        private val archivio = ArchivioAudioFinta()

        override val progettoId: ProgettoId = run {
            CreaProgettoServizio(eventi.unitaDiLavoro, generatoreId, progetti, eventi)
                .esegui(CreaProgetto("Progetto di prova")).atteso()
            eventi.pubblicati.filterIsInstance<ProgettoCreato>().single().progettoId
        }

        override val lettore: LettoreRegistrazione =
            LettoreRegistrazioneDaProgetto(CatalogoRegistrazioni(registrazioni))

        override fun semina(seme: SemeRegistrazione): RegistrazioneId {
            val percorso = "/sorgenti/${seme.titolo}.${seme.estensione}"
            archivio.conSorgente(percorso)
            val sonda = SondaAudioFinta(
                leggibili = mapOf(percorso to InfoAudio(seme.durataMs, seme.dataRegistrazione)),
            )
            val servizio = AggiungiRegistrazioneServizio(
                eventi.unitaDiLavoro,
                generatoreId,
                clock,
                progetti,
                registrazioni,
                sonda,
                archivio,
                eventi,
            )

            servizio.esegui(AggiungiRegistrazione(percorso)).atteso()

            return eventi.pubblicati.filterIsInstance<RegistrazioneAggiunta>().last().registrazioneId
        }

        override fun modificaData(id: RegistrazioneId, data: LocalDate) {
            ModificaDataRegistrazioneServizio(eventi.unitaDiLavoro, registrazioni, eventi)
                .esegui(ModificaDataRegistrazione(id, data)).atteso()
        }
    }
}
