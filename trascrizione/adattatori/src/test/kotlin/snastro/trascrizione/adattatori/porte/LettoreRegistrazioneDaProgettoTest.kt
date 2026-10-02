package snastro.trascrizione.adattatori.porte

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.comandi.AggiungiRegistrazioneServizio
import snastro.progetto.applicazione.comandi.CreaProgetto
import snastro.progetto.applicazione.comandi.CreaProgettoServizio
import snastro.progetto.applicazione.comandi.Destinazione
import snastro.progetto.applicazione.comandi.ModificaDataRegistrazione
import snastro.progetto.applicazione.comandi.ModificaDataRegistrazioneServizio
import snastro.progetto.applicazione.eventi.ProgettoCreato
import snastro.progetto.applicazione.eventi.RegistrazioneAggiunta
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni
import snastro.progetto.applicazione.porte.ArchivioAudioFinta
import snastro.progetto.applicazione.porte.IncontroRepositoryFinta
import snastro.progetto.applicazione.porte.InfoAudio
import snastro.progetto.applicazione.porte.ProgettoRepositoryFinta
import snastro.progetto.applicazione.porte.RegistrazioneRepositoryFinta
import snastro.progetto.applicazione.porte.SondaAudioFinta
import snastro.supporto.test.OrologioFinto
import snastro.trascrizione.applicazione.porte.AmbienteLettoreRegistrazione
import snastro.trascrizione.applicazione.porte.LettoreRegistrazione
import snastro.trascrizione.applicazione.porte.LettoreRegistrazioneContratto
import snastro.trascrizione.applicazione.porte.SemeRegistrazione
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds

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
        // Advances at every import, so the import order is a real aggiuntaAlle order (INV-I2), never an id tie-break.
        private val clock = OrologioFinto(Instant.parse("2026-09-23T10:00:00Z"))
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

        override fun semina(seme: SemeRegistrazione): RegistrazioneId {
            importa(listOf(seme))
            return eventi.pubblicati.filterIsInstance<RegistrazioneAggiunta>().last().registrazioneId
        }

        // The supplier's own public read API: the id AggiungiRegistrazione minted through GeneratoreId.
        override fun incontroDi(id: RegistrazioneId): IncontroId = checkNotNull(catalogo.registrazione(id)).incontroId

        override fun modificaData(id: RegistrazioneId, data: LocalDate) {
            ModificaDataRegistrazioneServizio(eventi.unitaDiLavoro, registrazioni, eventi)
                .esegui(ModificaDataRegistrazione(id, data)).atteso()
        }

        /** On (D-0037): [seminaIncontro] is Progetto's own multi-file import into ONE new Incontro (I2). */
        override val piuPartiPerIncontro: Boolean = true

        override fun seminaIncontro(semi: List<SemeRegistrazione>): IncontroId {
            importa(semi)
            return eventi.pubblicati.filterIsInstance<RegistrazioneAggiunta>().last().incontroId
        }

        // Progetto's own order (OrdineDelleParti), read through its public read API.
        override fun ordineDelleParti(incontroId: IncontroId): List<RegistrazioneId> =
            checkNotNull(catalogo.incontro(incontroId)).parti.map { it.registrazioneId }

        /** One AggiungiRegistrazione of [semi], in this order, as the Parti of ONE new Incontro. */
        private fun importa(semi: List<SemeRegistrazione>) {
            val percorsi = semi.associateBy { "/sorgenti/${++contatore}/${it.titolo}.${it.estensione}" }
            percorsi.keys.forEach(archivio::conSorgente)
            val sonda = SondaAudioFinta(
                leggibili = percorsi.mapValues { (_, seme) -> InfoAudio(seme.durataMs, seme.dataRegistrazione) },
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
            ).esegui(AggiungiRegistrazione(progettoId, percorsi.keys.toList(), Destinazione.NuovoIncontro)).atteso()
        }
    }
}
