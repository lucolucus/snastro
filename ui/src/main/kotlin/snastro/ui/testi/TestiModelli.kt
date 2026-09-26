package snastro.ui.testi

import snastro.ui.modelli.StatoModelloFacoltativo
import java.util.Locale

/** S5 · Modelli screen labels, Italian (dev-architecture `#presenter`: UI strings live in `snastro.ui.testi`). */
const val ETICHETTA_SCARICA: String = "Scarica"
const val ETICHETTA_LICENZE: String = "Licenze dei modelli e librerie"

/** AC-578: the `BannerSn` title for a failed download — the message itself stays [ModelliUiStato.Errore.messaggio]. */
const val ETICHETTA_DOWNLOAD_NON_RIUSCITO: String = "Download non riuscito"

/** AC-227: "N modelli da scaricare" (singular for 1, like [etichettaRegistrazioni]). */
fun etichettaModelliMancanti(numero: Int): String =
    if (numero == 1) "1 modello da scaricare" else "$numero modelli da scaricare"

/** AC-228: "Download in corso: <modelloId>". */
fun etichettaDownloadInCorso(modelloId: String): String = "Download in corso: $modelloId"

private const val BYTE_PER_GB = 1_000_000_000.0

/**
 * AC-S33/AC-S34 (ADR 0025 §8): the optional LLM model is GB-scale, unlike the required catalogue's
 * MB-scale [snastro.ui.formattaByte] (which stays binary/dot-decimal, ADR 0008/0013/0014) — this one
 * is SI-decimal (10^9) with one decimal and the Italian comma, e.g. "6,2" for 6 169 341 984.
 */
fun formattaGigabyte(byte: Long): String = String.format(Locale.ITALY, "%.1f", byte / BYTE_PER_GB)

/**
 * AC-S33: the sidebar-foot line for the optional model — only while ANY entry of [statiFacoltativi]
 * is downloading (`NonInstallato`/`Installato`/`Errore` show no line here: the Riassunto tab is where
 * those are actionable, ADR 0025 §4). v1 has exactly one optional entry (the Sintesi LLM, product
 * brief), so the first `InDownload` found is enough.
 */
fun etichettaModelloLinguisticoPiede(statiFacoltativi: Map<String, StatoModelloFacoltativo>): String? {
    val inDownload = statiFacoltativi.values.filterIsInstance<StatoModelloFacoltativo.InDownload>().firstOrNull()
        ?: return null
    return "Modello di linguaggio: ${formattaGigabyte(inDownload.scaricatiByte)} di " +
        "${formattaGigabyte(inDownload.totaliByte)} GB"
}
