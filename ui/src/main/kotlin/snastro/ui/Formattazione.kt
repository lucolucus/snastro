package snastro.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MS_PER_SECONDO = 1000L
private const val SECONDI_PER_MINUTO = 60L
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
