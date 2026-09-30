# Spike voci-tra-parti — evidenza (2026-09-30)

**Domanda.** Le Voci di parti diverse di un Incontro si possono unire in Voci dell'Incontro con il meccanismo che
esiste già (opzione a: la `Proposta` per impronta, confermata dall'utente)?

**Input.** Un incontro reale in due file contigui (conferma dell'utente), 4 persone in entrambe le parti:
- parte 1 `New Recording 4.m4a`, 18 min;
- parte 2 `Via Roquel.m4a`, 75 min.

**Metodo.** `SpikeIncontroTest` (`:avvio`, `@Tag("modelli")`, opt-in via `SNASTRO_SPIKE_INCONTRO_DIR`, facoltativo
`SNASTRO_SPIKE_PERSONE`):
1. usa la composizione dell'app con i modelli reali;
2. trascrive entrambe le parti;
3. nomina ogni Voce della parte 1 come un nuovo Parlante ricorrente (così ciascuna produce un'`ImprontaVocale`);
4. chiede la `Proposta` dell'app per ogni Voce della parte 2.

Nessun codice ML nuovo.

## Risultati

| Esecuzione | Voci parte 1 | Voci parte 2 | Abbinamenti FORTE | Esito |
|---|---|---|---|---|
| Numero di persone automatico | 2 (vere: 4) | 5 (vere: 4) | 4 su 5, ciascuna Voce della parte 1 presa due volte | inutilizzabile: la diarizzazione sbaglia già il conteggio |
| Numero di persone = 4 | 4 | 4 | **4 su 4, biunivoco** (P2 V1→P1 V3, V2→V1, V3→V2, V4→V4) | ogni Voce della parte 2 ha un solo FORTE, tutti diversi; gli altri DEBOLE o NESSUNA |

Tempi (M3 Pro), con Numero di persone = 4:
- parte 1: 18 min trascritti in 61 s;
- parte 2: 75 min trascritti in 221 s;
- spike completo: 285 s.

Il testo è coerente con l'abbinamento. La P1 V3 dice "per quello che diceva Mauro" e la P2 V1 "lancio una spazzatura
contro Mauro": nessuna delle due è Mauro, e le due voci sono state abbinate tra loro.

Rapporti completi: `snastro-wt/spike-incontro/esito/rapporto.md` e `…/esito-persone-4/rapporto.md` (fuori dal repository,
con l'audio).

## Conclusioni per il modello

1. **L'opzione (a) regge**, a una condizione: un **Numero di persone corretto per ogni parte**. Con il conteggio
   automatico nessuna strategia di unione può funzionare, perché le Voci di partenza sono sbagliate. Per un Incontro:
   - il Numero di persone va chiesto **una volta per l'Incontro** e usato per ogni parte (o proposto dalla prima);
   - una parte può comunque avere meno persone.
2. **Unione proposta, mai automatica.** L'app propone le coppie FORTE uno a uno e l'utente le conferma con un gesto.
   Serve la verifica umana perché il punteggio non conosce la verità.
3. **Da prevedere:** una Voce della parte 2 che ha due Candidati FORTE, oppure un FORTE già preso da un'altra Voce.
   Qui non è successo, ma in quel caso si ricade sulla scelta manuale.
4. **Verità di fondo non ancora verificata a orecchio.** L'utente conferma che le persone sono 4 in entrambe le parti
   ma non ha ancora ascoltato gli estratti delle coppie. Va fatto prima della chiusura formale e dell'ADR.

**Stato: evidenza raccolta, chiusura in attesa della verifica a orecchio dell'utente.**
