# Spike contesto-lungo (per Incontro) — evidenza (2026-09-30)

**Domanda.** Il Riassunto di un Incontro di 1,5–3 ore si fa in un unico passaggio? Oppure va spezzato per parte e poi
ricomposto?

**Input.**
- Incontro reale in due parti (New Recording 4 + Via Roquel), trascritto con Numero di persone = 4.
- Parte 2 da sola: 75 min, circa 20k token.
- Concatenato: circa 94 min, circa 24,7k token. Le Voci della parte 2 sono rimappate con l'abbinamento dello spike
  voci-tra-parti.

**Metodo.**
- `./gradlew :avvio:benchmarkRiassunto -Pcampione=<md>`: servizio vero, Qwen3.5 9B q4_K_M, GPU, M3 Pro.
- Il benchmark è **deterministico**: tre giri per input danno token e testo identici, quindi basta un giro per
  controllo.

## Risultati

| Input | Etichette dei parlanti | Tempo | Decisioni / Questioni / Azioni / Punti chiave |
|---|---|---|---|
| parte 2 | `Voce n` | 373–395 s | 18 / 4 / 5 / 5 |
| parte 2 | `Persona A–D` | 116 s | **0 / 0 / 0 / 0** |
| parte 2 | Marco, Anna, Luca, Giulia | 108 s | **0 / 0 / 0 / 0** |
| concatenato 94 min | `Voce n` | **427 s** | **12 / 4 / 4 / 10** |
| concatenato 94 min | `Persona A–D` | 251 s | 0 / 0 / 0 / 18 (6 duplicati) |

In ogni caso: prefill 65–86 s, picco RSS circa 7,1–7,3 GB, nessun problema di contesto.

Un primo 602 s sulla parte 2 era dovuto al carico, perché girava in parallelo con il gate. Non è ripetibile.

## Conclusioni

1. **Un unico passaggio basta per l'Incontro.** 94 min danno un Riassunto completo in 427 s, entro il budget di
   ADR 0026 (≤ 10 min per ora). Non serve spezzare e ricomporre fino ad almeno circa 1,5 h. Oltre, il tempo di
   generazione cresce con la quantità di contenuto, non con il contesto.
2. **Difetto trovato nella Sintesi già rilasciata (non di Incontro).**
   - Quando la legenda del prompt `V<n> = <Nome>` contiene **nomi** invece di `Voce n`, il modello non produce
     **nessun elemento strutturato**: solo il Sommario, con circa 700–850 token generati invece di oltre 5000.
   - Nell'app la legenda usa il Nome attuale del parlante (context-map, Parlanti → Sintesi). Quindi **dare un nome
     alle voci degrada probabilmente il Riassunto di oggi**.
   - Ipotesi da verificare: l'istruzione "Non scrivere mai i nomi dei parlanti" insieme ai nomi reali nella legenda
     spinge il modello a lasciare vuoti gli elementi.
   - Da trattare come spike e correzione a sé, prima del Riassunto per Incontro, che userà ancora di più i Nomi.
     Si lega allo spike aperto `qualita-riassunto`.

**Stato: chiuso per la parte "lunghezza" (un passaggio). Aperto un difetto separato sulla legenda con Nomi.**
