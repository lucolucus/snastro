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
import snastro.progetto.applicazione.comandi.Destinazione
import snastro.progetto.applicazione.comandi.EliminaRegistrazione
import snastro.progetto.applicazione.comandi.EliminaRegistrazioneServizio
import snastro.progetto.applicazione.comandi.ModificaDataRegistrazione
import snastro.progetto.applicazione.comandi.ModificaDataRegistrazioneServizio
import snastro.progetto.applicazione.comandi.ModificaOraDiInizioServizio
import snastro.progetto.applicazione.comandi.unaModificaOraDiInizio
import snastro.progetto.applicazione.eventi.RegistrazioneAggiunta
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni
import snastro.progetto.applicazione.porte.ArchivioAudioFinta
import snastro.progetto.applicazione.porte.EliminazioniInSospesoFinta
import snastro.progetto.applicazione.porte.IncontroRepositoryFinta
import snastro.progetto.applicazione.porte.InfoAudio
import snastro.progetto.applicazione.porte.ProgettoRepositoryFinta
import snastro.progetto.applicazione.porte.RegistrazioneRepositoryFinta
import snastro.progetto.applicazione.porte.SondaAudioFinta
import snastro.sintesi.applicazione.porte.AmbienteLettoreIncontro
import snastro.sintesi.applicazione.porte.LettoreIncontro
import snastro.sintesi.applicazione.porte.LettoreIncontroContratto
import snastro.sintesi.applicazione.porte.ParteSintesi
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * D2 (AC-I204, AC-I28): [LettoreIncontroDaProgetto] passes [LettoreIncontroContratto] real-on-real. Progetto is seeded
 * only through ITS commands (`CreaProgetto`, `AggiungiRegistrazione`, `ModificaDataRegistrazione`,
 * `ModificaOraDiInizio`, `EliminaRegistrazione`) over its own port fakes; the Incontro of an import is read back
 * through its public read API.
 */
class LettoreIncontroDaProgettoTest : LettoreIncontroContratto() {
    override fun ambiente(): AmbienteLettoreIncontro = AmbienteReale()

    /**
     * AC-I64: an Incontro of several Parti, imported through Progetto's own `AggiungiRegistrazione` into it, comes back
     * ordered and numbered by Progetto (date, then start time with the empty one last, then import order) and an
     * eliminated Parte is renumbered away: the adapter serves the ordered Parti and no longer fails closed on more
     * than one. The contract's own multi-Parte cases run here too (start time edited through Progetto's
     * `unaModificaOraDiInizio` fixture, CR-1).
     */
    @Test
    fun `AC-I64 un Incontro di piu Parti torna in ordine e numerato e una Parte eliminata si rinumera`() {
        val a = AmbienteReale()
        val senzaOra = a.importa(data = LocalDate.of(2026, 10, 1))
        val incontro = a.incontroDi(senzaOra)
        val giornoPrima = a.aggiungiParte(incontro, data = LocalDate.of(2026, 9, 30))
        val alleNove = a.aggiungiParte(incontro, data = LocalDate.of(2026, 10, 1), ora = LocalTime.of(9, 0))
        val alleOtto = a.aggiungiParte(incontro, data = LocalDate.of(2026, 10, 1), ora = LocalTime.of(8, 0))
        val altroIncontro = a.importa()

        assertEquals(
            listOf(giornoPrima, alleOtto, alleNove, senzaOra).mapIndexed { i, r -> ParteSintesi(r, i + 1) },
            a.lettore.parti(incontro),
        )
        assertEquals(listOf(ParteSintesi(altroIncontro, 1)), a.lettore.parti(a.incontroDi(altroIncontro)))

        a.elimina(alleOtto)

        assertEquals(
            listOf(giornoPrima, alleNove, senzaOra).mapIndexed { i, r -> ParteSintesi(r, i + 1) },
            a.lettore.parti(incontro),
        )
    }

    private class AmbienteReale : AmbienteLettoreIncontro {
        private val clock = Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC)
        private val generatoreId = GeneratoreIdFinto()
        private val progetti = ProgettoRepositoryFinta()
        private val registrazioni = RegistrazioneRepositoryFinta()
        private val incontri = IncontroRepositoryFinta(registrazioni)
        private val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(registrazioni, progetti, incontri))
        private val archivio = ArchivioAudioFinta()
        private val catalogo = CatalogoRegistrazioni(registrazioni, incontri)
        private var contatore = 0

        init {
            CreaProgettoServizio(eventi.unitaDiLavoro, generatoreId, progetti, eventi)
                .esegui(CreaProgetto("Progetto di prova")).atteso()
        }

        override val lettore: LettoreIncontro = LettoreIncontroDaProgetto(catalogo)

        override fun importa(data: LocalDate, ora: LocalTime?): RegistrazioneId =
            importaIn(Destinazione.NuovoIncontro, data, ora)

        override fun aggiungiParte(incontroId: IncontroId, data: LocalDate, ora: LocalTime?): RegistrazioneId =
            importaIn(Destinazione.Incontro(incontroId), data, ora)

        private fun importaIn(destinazione: Destinazione, data: LocalDate, ora: LocalTime?): RegistrazioneId {
            val percorso = "/sorgenti/parte-${++contatore}.m4a"
            archivio.conSorgente(percorso)
            val sonda = SondaAudioFinta(leggibili = mapOf(percorso to InfoAudio(60_000L, data, ora)))
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
            ).esegui(AggiungiRegistrazione(checkNotNull(progetti.trova()).id, listOf(percorso), destinazione)).atteso()
            return eventi.pubblicati.filterIsInstance<RegistrazioneAggiunta>().last().registrazioneId
        }

        override fun modificaData(registrazioneId: RegistrazioneId, data: LocalDate) {
            ModificaDataRegistrazioneServizio(eventi.unitaDiLavoro, registrazioni, eventi)
                .esegui(ModificaDataRegistrazione(registrazioneId, data)).atteso()
        }

        // Progetto's own command; the OraDiInizio is built by Progetto's applicazione fixture (CR-1).
        override fun modificaOraDiInizio(registrazioneId: RegistrazioneId, ora: LocalTime?) {
            ModificaOraDiInizioServizio(eventi.unitaDiLavoro, registrazioni, eventi)
                .esegui(unaModificaOraDiInizio(registrazioneId, ora)).atteso()
        }

        override fun incontroDi(registrazioneId: RegistrazioneId): IncontroId =
            checkNotNull(catalogo.registrazione(registrazioneId)).incontroId

        override fun elimina(registrazioneId: RegistrazioneId) {
            EliminaRegistrazioneServizio(
                eventi.unitaDiLavoro,
                registrazioni,
                incontri,
                EliminazioniInSospesoFinta(),
                eventi,
            )
                .esegui(EliminaRegistrazione(registrazioneId)).atteso()
        }
    }
}
