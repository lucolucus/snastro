package snastro.audio

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * AC-364: the recording date of a source file — pure, so the choice is table-tested without FFmpeg
 * or a filesystem. The first PLAUSIBLE instant wins, in this order:
 * 1. [creationTime], the container's `creation_time` metadata tag as FFmpeg reports it (e.g. the m4a
 *    `mvhd` box; ISO-8601 in UTC, `2026-09-21T09:30:00.000000Z`); unparseable = absent;
 * 2. [creazioneFile], the file's creation (birth) time — `null` where the filesystem has none;
 * 3. [modificaFile], the file's last-modified time — always there, the last resort, taken as is.
 * Plausible = strictly after [PRIMA_DATA_PLAUSIBILE] (rejects the 1904 and 1970 epoch zero a device
 * without a set clock writes) and not after [adesso]. The winner becomes a date in [zona].
 */
internal fun dataRegistrazione(
    creationTime: String?,
    creazioneFile: Instant?,
    modificaFile: Instant,
    adesso: Instant,
    zona: ZoneId,
): LocalDate {
    fun plausibile(istante: Instant?): Instant? =
        istante?.takeIf { it.isAfter(PRIMA_DATA_PLAUSIBILE) && !it.isAfter(adesso) }
    val scelto = plausibile(creationTime?.let(::istanteDaMetadato)) ?: plausibile(creazioneFile) ?: modificaFile
    return scelto.atZone(zona).toLocalDate()
}

/**
 * The FFmpeg `creation_time` tag as an [Instant]: ISO-8601 with an offset (`…Z`, `…+02:00`), or
 * without one (FFmpeg's older `2026-09-21 09:30:00` form, read as UTC — the tag's own convention).
 */
private fun istanteDaMetadato(valore: String): Instant? {
    val iso = valore.trim().replace(' ', 'T')
    return try {
        OffsetDateTime.parse(iso).toInstant()
    } catch (@Suppress("SwallowedException") senzaFuso: DateTimeParseException) {
        try {
            LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC)
        } catch (@Suppress("SwallowedException") illeggibile: DateTimeParseException) {
            null // an unparseable tag counts as no tag: the next source decides (AC-364)
        }
    }
}

/** 1970-01-01 (end of the day): the 1904 and 1970 epoch zero, and anything before, are not a real date. */
private val PRIMA_DATA_PLAUSIBILE: Instant = Instant.parse("1970-01-01T23:59:59Z")
