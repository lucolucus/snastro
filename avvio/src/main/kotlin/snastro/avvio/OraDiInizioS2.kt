package snastro.avvio

import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.poi
import snastro.progetto.applicazione.comandi.ModificaOraDiInizio
import snastro.progetto.dominio.OraDiInizio
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * The start-time edit of S2 (AC-I68, D-0009): the UI hands a [LocalTime] (HH:mm, `null` clears it); the `OraDiInizio`
 * VO (INV-I14, to the second, so any fraction is dropped) is built here, where `:ui` does not see the domain, and the
 * `ModificaOraDiInizio` command is sent through [comando].
 */
internal fun modificaOraDiInizio(
    comando: (ModificaOraDiInizio) -> Esito<Unit>,
    id: RegistrazioneId,
    ora: LocalTime?,
): Esito<Unit> = if (ora == null) {
    comando(ModificaOraDiInizio(id, null))
} else {
    OraDiInizio.di(ora.truncatedTo(ChronoUnit.SECONDS)).poi { comando(ModificaOraDiInizio(id, it)) }
}
