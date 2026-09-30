---
scope: global
status: accepted
supersedes: null   # partial: ADR 0021 §4's legend `V<n> = <Nome | Voce n>` becomes `V<n> = Voce n` for the model run; context-map Parlanti → Sintesi amended in place.
closes_spike: null
decided: 2026-10-01 · user (asked to fix the defect found by spike contesto-lungo of feature `incontro`) · Claude (option H2 over H1, on measured evidence)
enforced_by: []   # by construction: IngressoRiassunto.costruisci takes no Nomi at all; plus ComposizioneSintesiTest (a named Voce never reaches the model)
---
# 0032 — The model never sees a Nome: the Riassunto input legend is always `Voce n`

## Context
Spike `contesto-lungo` (feature `incontro`, 2026-09-30) found the defect. The model is Qwen3.5 9B q4_K_M, and the
benchmark is deterministic, so the same input gives the same output.

The model was given the same 75-minute transcript three times, changing only the speaker legend
`V<n> = <Nome | Voce n>`:

| Legend | Decisioni / Questioni / Azioni / Punti chiave | Tokens generated |
|---|---|---|
| `Voce 1…4` | 18 / 4 / 5 / 5 | 5329 |
| `Persona A…D` | 0 / 0 / 0 / 0 | 847 |
| `Marco, Anna, Luca, Giulia` | 0 / 0 / 0 / 0 | 710 |

With names in the legend, the model wrote only the Sommario. `EseguiProssimoRiassuntoServizio` built the legend from
the **current** Nomi (ADR 0021 §4, AC-S85). So once the user named the speakers, which the app invites them to do,
every later Riassunto lost its Decisioni, Azioni and Questioni aperte.

The first attempted fix (H1) reworded the prompt rule "Non scrivere mai i nomi dei parlanti". The elements came back,
but with 0 Decisioni and 20 invented, generic Questioni aperte, which was worse.

## Decision
- The model run builds its input with the legend `V<n> = Voce n` for every Voce, whatever its `Attribuzione`.
  `EseguiProssimoRiassuntoServizio` no longer reads `LettoreNomi`, and `IngressoRiassunto.costruisci` has no
  parameter for names, so no caller (the Incontro Riassunto included) can put one back.
- Nomi are applied only when the Riassunto is shown (`{V<n>}` tokens, `Responsabile`, `PuntoChiave` speaker). The
  display resolution through Sintesi's `LettoreNomi` is unchanged.
- The input is now byte-identical to the measured good case, so the output is the same with or without names.

## Rejected options
- **H1, reword the prompt.** Measured worse, as described above.
- **Names in the lines instead of the legend.** It would make the prompt still more sensitive to names. It would also
  put Nomi, which are personal data of third parties, into more of the input, for no measured gain.

## Consequences
- **Lost:** when someone says "Mauro deve fare X", the model can no longer link "Mauro" to a Voce. It then sets
  `Responsabile` to null or picks the speaker, and the user corrects this through the display. Accepted.
- **Renames:** a renamed Parlante now changes neither the input nor the output of a new Riassunto. The existing
  rule is unchanged: a rename is not `superato`.
- **Incontro:** the Riassunto per Incontro follows the same rule. Its legend lists the Incontro's Voci as `Voce n`.
