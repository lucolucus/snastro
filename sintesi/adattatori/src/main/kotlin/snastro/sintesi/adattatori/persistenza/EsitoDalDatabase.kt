package snastro.sintesi.adattatori.persistenza

import snastro.kernel.Esito

/**
 * Trusts the DB (CR-15): a smart constructor re-run on an already-validated stored value (`Argomento.di`,
 * `LunghezzaMassimaParole.di`) is always [Esito.Ok] here; `messaggio` only ever surfaces on data corruption
 * (mirrors `LunghezzaMassimaRiassunto.predefinita`'s own `as? Esito.Ok` + `checkNotNull` idiom).
 */
internal fun <T> Esito<T>.dalDatabase(messaggio: String): T = (this as? Esito.Ok)?.valore ?: error(messaggio)
