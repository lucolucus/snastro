# snastro

Definizione del progetto in `.mismagent/` (profilo, architettura, regole del codice, ADR in `decisions/`).

## mismagent
- test: ./gradlew check
- lint: ./gradlew detekt
- max_file_lines: 3100

Nota (scelta dell'utente, 2026-10-08): `max_file_lines` è alto perché il sensore di mm conta anche i test di modulo e
gli header C vendorizzati di llama.cpp (`ggml.h` 3022 righe); la dimensione delle classi la sorveglia detekt in `./gradlew check`.
