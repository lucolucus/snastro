package snastro.progetto.applicazione.comandi

import snastro.kernel.RegistrazioneId
import snastro.kernel.atteso
import snastro.progetto.dominio.OraDiInizio
import java.time.LocalTime

/**
 * [ModificaOraDiInizio] of [registrazioneId] to [ora] (`null` = unknown), built through `OraDiInizio.di` (INV-I14) —
 * an `applicazione`-level fixture so a CROSS-CONTEXT test (e.g. `sintesi:adattatori`'s `LettoreIncontroDaProgettoTest`)
 * never needs `progetto:dominio`'s `OraDiInizio` itself (CR-1: `consumer:adattatori -> supplier:applicazione` only).
 * A fraction of a second fails the test, as `OraDiInizio.di` refuses it.
 */
public fun unaModificaOraDiInizio(registrazioneId: RegistrazioneId, ora: LocalTime?): ModificaOraDiInizio =
    ModificaOraDiInizio(registrazioneId, ora?.let { OraDiInizio.di(it).atteso() })
