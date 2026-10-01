package snastro.audio

import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * ADR 0040 / AC-364: the recording date and start time of a source file — pure, so the choice is
 * table-tested without FFmpeg or a filesystem.
 * 1. [udtaDate], the box `moov/udta/date` (ISO-8601 WITH an offset), if plausible: ONE instant gives both
 *    values, in the clock's zone — the date and the local time truncated to the second;
 * 2. otherwise the time stays empty and the date is the first PLAUSIBLE of: [creationTime] (the container's
 *    `creation_time` tag as FFmpeg reports it, ISO-8601 in UTC; unparseable = absent), [creazioneFile] (the
 *    file's birth time, `null` where the filesystem has none), [modificaFile] (always there, taken as is).
 * Plausible = strictly after [PRIMA_DATA_PLAUSIBILE] (rejects the 1904 and 1970 epoch zero a device
 * without a set clock writes) and not after the clock's now. `creation_time` and the file times NEVER give the time.
 */
internal fun dataRegistrazione(
    udtaDate: String?,
    creationTime: String?,
    creazioneFile: Instant?,
    modificaFile: Instant,
    clock: Clock,
): DataEOra {
    val adesso = clock.instant()
    val zona = clock.zone
    fun plausibile(istante: Instant?): Instant? =
        istante?.takeIf { it.isAfter(PRIMA_DATA_PLAUSIBILE) && !it.isAfter(adesso) }
    plausibile(udtaDate?.let(::istanteConFuso))?.let { inizio ->
        val locale = inizio.atZone(zona)
        return DataEOra(locale.toLocalDate(), locale.toLocalTime().truncatedTo(ChronoUnit.SECONDS))
    }
    val scelto = plausibile(creationTime?.let(::istanteDaMetadato)) ?: plausibile(creazioneFile) ?: modificaFile
    return DataEOra(scelto.atZone(zona).toLocalDate(), null)
}

/** [valore] as an [Instant] only if it carries an offset (`…Z`, `…+02:00`); otherwise `null`. */
private fun istanteConFuso(valore: String): Instant? = try {
    OffsetDateTime.parse(valore.trim()).toInstant()
} catch (@Suppress("SwallowedException") senzaFuso: DateTimeParseException) {
    null
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
