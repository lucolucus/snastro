package snastro.ui

import kotlinx.coroutines.flow.Flow

/**
 * `tec-shell-ui` (owned here): cross-cutting refresh signal (R15) — the screens' read-models are
 * computed-on-read (no event-folded tables in v1), so a presenter re-queries when it sees a
 * [Cambiamento] that could affect what it shows.
 */
interface AggiornamentiVista {
    val cambiamenti: Flow<Cambiamento>
}
