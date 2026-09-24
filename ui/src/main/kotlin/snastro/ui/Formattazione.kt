package snastro.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MS_PER_SECONDO = 1000L
private const val SECONDI_PER_MINUTO = 60L
private const val SECONDI_PER_ORA = 3600L
private const val BYTE_PER_MB = 1024.0 * 1024.0
private val FORMATO_DATA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * AC-557 (supersedes AC-179's duration half; AC-203's "3:12" now matches): under one hour `m:ss`
 * with NO leading zero on minutes ("1:15", "3:12", "59:59"); from one hour on, `h:mm:ss`
 * ("1:00:00", "1:15:03"). Seconds truncate to whole, as before.
 */
fun formattaDurata(durataMs: Long): String {
    val secondiTotali = durataMs / MS_PER_SECONDO
    val ore = secondiTotali / SECONDI_PER_ORA
    val minuti = (secondiTotali % SECONDI_PER_ORA) / SECONDI_PER_MINUTO
    val secondi = secondiTotali % SECONDI_PER_MINUTO
    return if (ore > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", ore, minuti, secondi)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minuti, secondi)
    }
}

/** AC-557: the prose form of a duration ("52 min", "1 h 04 min") — dates stay `formattaData`, unchanged. */
fun formattaDurataEstesa(durataMs: Long): String {
    val secondiTotali = durataMs / MS_PER_SECONDO
    val ore = secondiTotali / SECONDI_PER_ORA
    val minuti = (secondiTotali % SECONDI_PER_ORA) / SECONDI_PER_MINUTO
    return if (ore > 0) String.format(Locale.ROOT, "%d h %02d min", ore, minuti) else "$minuti min"
}

/** AC-179: "dd/MM/yyyy". */
fun formattaData(data: LocalDate): String = data.format(FORMATO_DATA)

/** AC-227/228: a byte count as "<n,n> MB" — the whole catalogue (ADR 0008/0013/0014) sits in the
 * hundreds of MB, so a single unit is enough (frugality rung 6: no GB tier, no thousands separator). */
fun formattaByte(byte: Long): String = String.format(Locale.ROOT, "%.1f MB", byte / BYTE_PER_MB)
