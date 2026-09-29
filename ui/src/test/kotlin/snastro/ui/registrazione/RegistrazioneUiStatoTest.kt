package snastro.ui.registrazione

import androidx.compose.runtime.Composable
import snastro.kernel.VoceId
import snastro.ui.lettore.LettoreUiStato
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val DATA_1: LocalDate = LocalDate.of(2026, 3, 12)

private fun unaCartaDaIdentificare(numero: Int) = CartaVoce(
    voceId = VoceId(numero),
    titolo = "Voce $numero",
    contenuto = ContenutoCarta.DaIdentificare(StatoProposta.Caricamento, galleriaVuota = false),
)

private fun unPannello(daIdentificare: Int) = PannelloVoci(
    carte = (1..daIdentificare).map { unaCartaDaIdentificare(it) },
    parlantiAttivi = emptyList(),
    unioni = emptyList(),
    estrattiDisponibili = true,
    unioneAbilitata = true,
)

@Suppress("LongParameterList") // one parameter per RegistrazioneUiStato.Dati field this table varies
private fun unDati(
    audioDisponibile: Boolean = true,
    pannello: PannelloVoci? = null,
    bannerRitrascrizione: String? = null,
    contenutoRiassunto: (@Composable () -> Unit)? = { },
) = RegistrazioneUiStato.Dati(
    titolo = "Seduta del 12 marzo",
    dataRegistrazione = DATA_1,
    durataMs = 125_000,
    segmenti = emptyList(),
    barra = LettoreUiStato.Inattivo,
    audioDisponibile = audioDisponibile,
    documentoPercorso = null,
    pannello = pannello,
    bannerRitrascrizione = bannerRitrascrizione,
    contenutoRiassunto = contenutoRiassunto,
)

/**
 * AC-S123 (ux-proposal "Banner precedence on S3"): [RegistrazioneUiStato.Dati.bannerSchermata] as a
 * pure, table-driven predicate — no presenter, no Compose, one assertion per row of the precedence
 * table the ux proposal pins.
 */
class RegistrazioneUiStatoTest {
    @Test
    fun `AC-S123 nessuna condizione nessun banner`() {
        assertNull(unDati().bannerSchermata)
    }

    @Test
    fun `AC-S123 solo audio mancante`() {
        assertEquals(BannerSchermata.AudioMancante, unDati(audioDisponibile = false).bannerSchermata)
    }

    @Test
    fun `AC-S123 solo voci da identificare`() {
        assertEquals(
            BannerSchermata.VociDaIdentificare(2),
            unDati(pannello = unPannello(daIdentificare = 2)).bannerSchermata,
        )
    }

    @Test
    fun `AC-S123 solo lettura ha la precedenza su audio mancante e voci da identificare`() {
        val dati = unDati(
            bannerRitrascrizione = "banner",
            audioDisponibile = false,
            pannello = unPannello(daIdentificare = 3),
        )
        assertEquals(BannerSchermata.Ritrascrizione("banner", null), dati.bannerSchermata)
    }

    @Test
    fun `AC-S123 audio mancante ha la precedenza su voci da identificare`() {
        val dati = unDati(audioDisponibile = false, pannello = unPannello(daIdentificare = 3))
        assertEquals(BannerSchermata.AudioMancante, dati.bannerSchermata)
    }

    @Test
    fun `AC-S123 zero voci da identificare non produce banner`() {
        assertNull(unDati(pannello = unPannello(daIdentificare = 0)).bannerSchermata)
    }

    @Test
    fun `AC-S123 superato una richiesta fallita e il download del modello non sono letti qui`() {
        // These conditions are tab-scoped (scheda-riassunto) and never even reach RegistrazioneUiStato —
        // there is no field for them on Dati at all, so an otherwise-clear screen stays banner-free.
        assertNull(unDati(contenutoRiassunto = { }).bannerSchermata)
    }
}
