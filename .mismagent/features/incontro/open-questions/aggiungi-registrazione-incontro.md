# aggiungi-registrazione-incontro — BOUNCED (contract vs ADR check)

Worker HEAD c85950cc (WIP), worktree /Users/lucaparsani/projects/snastro-wt/incontro-blocchi/aggiungi-registrazione-incontro.

ADR 0033 §2 pins `AggiungiRegistrazione(progettoId, file: List<Path>, destinazione)` with `java.nio.file.Path`.
ADR 0002's check `adr-0002-nucleo-senza-tecnologia.sh` and Konsist rule CR-2 forbid `java.nio.file.` in every `*/applicazione`; the gate fails on that import.

- A (worker's recommendation): `file: List<String>`, like today's `percorsoSorgente: String` and the SondaAudio/ArchivioAudio ports; amend ADR 0033 §2 and the pin.
- B: keep `List<Path>` and add an exception for this one type to the ADR 0002 check and CR-2.

All other work is done; the swap plus one full gate run remain.
