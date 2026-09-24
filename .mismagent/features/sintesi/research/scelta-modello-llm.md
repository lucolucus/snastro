# Spike: scelta del modello LLM locale per la Sintesi

Data: 2026-09-25 · Esito: **Qwen3.5 9B (q4_K_M) scelto dall'utente** · Consumer: ADR di scelta modello/runtime (model), voce catalogo `:modelli`.

## Domanda
Quale LLM locale, su un M3 Pro con 36 GB di RAM, riassume in italiano riunioni IT/EN trascritte da snastro entro circa 5 minuti per un'ora di audio? Deve citare fonti valide (id di segmento e parlante).

## Metodo
- Input: i Documenti reali esportati da R1/R2 (`Via Roquel`, 4 parlanti, 10.7k parole; `New Recording 4`, 2.5k parole). Ogni turno riceve l'etichetta `[sN Parlante m:ss]`.
- Harness: Ollama 0.34.3, usato solo come banco di misura. Il runtime nell'app resta aperto, vedi lo spike runtime. `num_ctx` 32768, temperatura 0.2, `think: false`, output vincolato da uno schema JSON (riassunto, punti_chiave, decisioni, azioni, ognuno con `fonti`).
- Validazione automatica: ogni fonte deve essere un id esistente; il parlante di un punto deve comparire tra i parlanti delle sue fonti; il responsabile di un'azione deve essere un parlante noto oppure "nessuno".
- Script: [spike-llm/spike_sintesi.py](spike-llm/spike_sintesi.py). Gli output non sono versionati perché contengono il contenuto di riunioni private.

## Misure

| | Qwen3.5 9B q4_K_M | Gemma 4 12B q4_K_M |
|---|---|---|
| Licenza | Apache 2.0 | Apache 2.0 |
| Disco | 6.6 GB | 7.6 GB |
| Via Roquel: token in / out | 24 137 / 1 363 | 25 204 / 795 |
| Via Roquel: lettura + scrittura = totale | 85 s + 69 s = **175 s** | 189 s + 58 s = 251 s |
| New Recording 4: totale | **46 s** | 72 s |
| Citazioni / problemi strutturali | 116 / 0 | 34 / 0 |

## Risultati
- Un'ora di trascritto vale circa **25k token**, non i 12–15k stimati prima. Entra in 32k di contesto; oltre circa 1h15 servono spezzatura e ricomposizione.
- Validità strutturale al 100% su entrambi i modelli, grazie all'output con schema e alle fonti come id. Questo non prova la correttezza del contenuto.
- Qwen è più ricco e concreto. I difetti da correggere nel prompt e nel modello:
  - mette cose "da valutare" tra le decisioni;
  - duplica le stesse fonti tra punti chiave e decisioni;
  - su New Recording 4 il riassunto è di una sola frase.
- Gemma è più prudente, ma più povero di decisioni e azioni.
- Scelta: Qwen3.5 9B, decisa dall'utente.

## Aperto
- Runtime nell'app (llama.cpp via JNI oppure sidecar `llama-server`). L'utente ha escluso un Ollama installato dall'utente.
- Filtro fuori tema guidato da un contesto breve dato dall'utente: non ancora misurato.
- Giudizio semantico (decisioni vere, perse o inventate) su 2 registrazioni: da fare dall'utente.
