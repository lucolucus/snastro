package snastro.sintesi.adattatori.porte

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.comandi.AggiungiRegistrazioneServizio
import snastro.progetto.applicazione.comandi.CreaProgetto
import snastro.progetto.applicazione.comandi.CreaProgettoServizio
import snastro.progetto.applicazione.comandi.EliminaRegistrazione
import snastro.progetto.applicazione.comandi.EliminaRegistrazioneServizio
import snastro.progetto.applicazione.comandi.ModificaDataRegistrazione
import snastro.progetto.applicazione.comandi.ModificaDataRegistrazioneServizio
import snastro.progetto.applicazione.eventi.RegistrazioneAggiunta
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni
import snastro.progetto.applicazione.porte.ArchivioAudioFinta
import snastro.progetto.applicazione.porte.EliminazioniInSospesoFinta
import snastro.progetto.applicazione.porte.InfoAudio
import snastro.progetto.applicazione.porte.ProgettoRepositoryFinta
import snastro.progetto.applicazione.porte.RegistrazioneRepositoryFinta
import snastro.progetto.applicazione.porte.SondaAudioFinta
import snastro.sintesi.applicazione.porte.AmbienteLettoreIncontro
import snastro.sintesi.applicazione.porte.LettoreIncontro
import snastro.sintesi.applicazione.porte.LettoreIncontroContratto
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * D2 (AC-I204, AC-I28): [LettoreIncontroDaProgetto] passes [LettoreIncontroContratto] real-on-real. Progetto is seeded
 * only through ITS commands (`CreaProgetto`, `AggiungiRegistrazione`, `ModificaDataRegistrazione`,
 * `EliminaRegistrazione`) over its own port fakes; the Incontro of an import is read back through its public read API.
 */
class LettoreIncontroDaProgettoTest : LettoreIncontroContratto() {
    override fun ambiente(): AmbienteLettoreIncontro = AmbienteReale()

    private class AmbienteReale : AmbienteLettoreIncontro {
        private val clock = Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC)
        private val generatoreId = GeneratoreIdFinto()
        private val progetti = ProgettoRepositoryFinta()
        private val registrazioni = RegistrazioneRepositoryFinta()
        private val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(registrazioni, progetti))
        private val archivio = ArchivioAudioFinta()
        private val catalogo = CatalogoRegistrazioni(registrazioni)
        private var contatore = 0

        init {
            CreaProgettoServizio(eventi.unitaDiLavoro, generatoreId, progetti, eventi)
                .esegui(CreaProgetto("Progetto di prova")).atteso()
        }

        override val lettore: LettoreIncontro = LettoreIncontroDaProgetto(catalogo)

        /**
         * Off until the multi-file import into an Incontro (I2, `aggiungi-registrazione-incontro`) lands, with the
         * ordered Parti of `catalogo-incontro`: Progetto's commands cannot give an Incontro a second Parte yet, so the
         * contract's multi-Parte cases are not registered here (D-0037). Switch it on, and implement [aggiungiParte]
         * and [modificaOraDiInizio] through those commands, when they land.
         */
        override val piuPartiPerIncontro: Boolean = false

        override fun importa(data: LocalDate, ora: LocalTime?): RegistrazioneId {
            require(ora == null) { "InfoAudio non porta ancora l'ora di inizio: solo i casi con piu Parti la usano" }
            val percorso = "/sorgenti/parte-${++contatore}.m4a"
            archivio.conSorgente(percorso)
            val sonda = SondaAudioFinta(leggibili = mapOf(percorso to InfoAudio(60_000L, data)))
            AggiungiRegistrazioneServizio(
                eventi.unitaDiLavoro,
                generatoreId,
                clock,
                progetti,
                registrazioni,
                sonda,
                archivio,
                eventi,
            ).esegui(AggiungiRegistrazione(percorso)).atteso()
            return eventi.pubblicati.filterIsInstance<RegistrazioneAggiunta>().last().registrazioneId
        }

        override fun aggiungiParte(incontroId: IncontroId, data: LocalDate, ora: LocalTime?): RegistrazioneId =
            error("una seconda Parte richiede l'import in un Incontro (I2): piuPartiPerIncontro e' false")

        override fun modificaData(registrazioneId: RegistrazioneId, data: LocalDate) {
            ModificaDataRegistrazioneServizio(eventi.unitaDiLavoro, registrazioni, eventi)
                .esegui(ModificaDataRegistrazione(registrazioneId, data)).atteso()
        }

        override fun modificaOraDiInizio(registrazioneId: RegistrazioneId, ora: LocalTime?): Unit =
            error("ModificaOraDiInizio non e' ancora un comando di Progetto: piuPartiPerIncontro e' false")

        override fun incontroDi(registrazioneId: RegistrazioneId): IncontroId =
            checkNotNull(catalogo.registrazione(registrazioneId)).incontroId

        override fun elimina(registrazioneId: RegistrazioneId) {
            EliminaRegistrazioneServizio(eventi.unitaDiLavoro, registrazioni, EliminazioniInSospesoFinta(), eventi)
                .esegui(EliminaRegistrazione(registrazioneId)).atteso()
        }
    }
}
