package snastro.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MS_PER_SECONDO = 1000L
private const val SECONDI_PER_MINUTO = 60L
private const val BYTE_PER_MB = 1024.0 * 1024.0
private val FORMATO_DATA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** AC-179: "mm:ss" — minutes are NOT capped at 59 (no hour component), seconds truncate to whole. */
fun formattaDurata(durataMs: Long): String {
    val secondiTotali = durataMs / MS_PER_SECONDO
    val minuti = secondiTotali / SECONDI_PER_MINUTO
    val secondi = secondiTotali % SECONDI_PER_MINUTO
    return String.format(Locale.ROOT, "%02d:%02d", minuti, secondi)
}

/** AC-179: "dd/MM/yyyy". */
fun formattaData(data: LocalDate): String = data.format(FORMATO_DATA)

/** AC-227/228: a byte count as "<n,n> MB" — the whole catalogue (ADR 0008/0013/0014) sits in the
 * hundreds of MB, so a single unit is enough (frugality rung 6: no GB tier, no thousands separator). */
fun formattaByte(byte: Long): String = String.format(Locale.ROOT, "%.1f MB", byte / BYTE_PER_MB)
