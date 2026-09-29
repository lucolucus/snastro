package snastro.avvio

import snastro.ui.DestinazioneShell
import snastro.ui.ShellPresenter
import snastro.ui.progetti.ProgettiPresenter

/**
 * ADR 0030 §4 (block c2-contenuto-app-base): the ONE place [ShellPresenter] is built. Every composition
 * root — R0's own `ContenutoApp` ([Main.kt]) and `ContenutoAppCondiviso` (`snastro.avvio.r1`, R1/R2/R3's
 * shared body) — only passes its own `SEZIONI_SHELL_*` constant as [sezioni]; the presenter is otherwise
 * wired identically over [grafo]'s scope/dispatcher/session.
 */
internal fun costruisciShellPresenter(grafo: GrafoR0, sezioni: Set<DestinazioneShell>): ShellPresenter =
    ShellPresenter(grafo.scope, grafo.io, grafo.sessione, sezioni)

/**
 * ADR 0030 §4 (block c2-contenuto-app-base): the ONE place [ProgettiPresenter] is built, reused by every
 * composition root's own S1 (`contenutoSenzaProgetto`) the same way.
 */
internal fun costruisciProgettiPresenter(grafo: GrafoR0): ProgettiPresenter =
    ProgettiPresenter(grafo.scope, grafo.io, grafo.elencoProgetti, grafo.sessione)
