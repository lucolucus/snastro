Kind: feature
Release: R1
Examples: EX-14, EX-15, EX-16
Base: c42c2740bfe837b6f9a0109775d8ce921eafed08

Riassumendo skatefest, con o senza argomento, decisioni, questioni e azioni riportano i punti cardine della riunione
(D1, Q1, A1) con le fonti, una volta sola ciascuno e senza il punto inventato. Il come lo sceglie l'architetto (spike H1).

## Question
Quale approccio usa il riassunto per avere i punti cardine (EX-14..EX-16)? Nessun ADR o nota dell'architetto lo ha
ancora scelto (ultimo ADR: 0041; `design-notes.md:13` lo lascia "a chi sceglie dopo H1"), e lo spike H1 §5 mostra che
ritoccare il prompt nel passaggio unico di oggi non basta: con seme e temperatura fissi (42, 0.2) varianti vicine danno
0 decisioni (v7) o 40 voci ripetute fino al limite della grammatica (v8), e senza filtro il modello inventa (v2:
"approvato il meccanismo di movimento automatico"). Le tre strade che lo spike indica cambiano tutte una struttura
costosa da rovesciare, quindi non le scelgo io:

1. **Estrazione dei punti in un passaggio a parte** (passaggio 1: sommario; passaggio 2: solo le quattro liste, con la
   loro grammatica). Resta tutto in `:sintesi` (adattatore `ModelloLinguisticoLlama`, `PromptRiassunto`,
   `GrammaticaRisposta`/`RispostaV1`), ma cambia ADR 0026 §3 ("genera UNA volta", nessun modello tenuto caldo):
   o si paga l'apertura due volte (10–15 s a freddo) o l'adattatore tiene il modello aperto fra i due passaggi.
   Raddoppia il prefill (~4300 parole di skatefest) e il tempo; interagisce col limite di 600 s di EX-18 (slice 23).
   Non provato nello spike.
2. **Penalità di ripetizione** nel campionamento: nuovo campo in `Sampling` di `:llama-jni` (oggi solo
   temperature/topK/topP/seed, `Sampling.kt:11-16`) e nello shim C (`llama_sampler_init_penalties`). È l'API della
   libreria separata (ADR 0027, versione propria). Riduce i cicli (EX-16, EX-18) ma da sola non garantisce né liste
   non vuote (v7) né l'assenza del punto inventato. Non provato.
3. **Arresto al primo elemento ripetuto**: serve un flusso di token o un callback sul testo parziale da `:llama-jni`
   (oggi `generate` vede il testo solo alla fine, `LlamaModel.kt:24`). Cambio d'API più grande del 2; aiuta EX-16/EX-18,
   non EX-14/EX-15.

Strade economiche, tutte dentro l'adattatore e reversibili, ma senza prova che bastino: togliere i doppioni di testo
identico dopo il parsing (copre solo una parte di EX-16, non le parafrasi né EX-14/15); un tetto di voci basso nella
grammatica (es. 6) invece di obiettivo ÷ 100; liste prima del sommario nello schema.

Raccomandazione: **1** (passaggio a parte per le liste, modello tenuto aperto fra i due passaggi dentro lo stesso
`riassumi`), deciso con un ADR che emenda 0026 §3, e preceduto da 2–3 corse vere su skatefest (con e senza argomento)
nel worktree `../snastro-h1` per vedere se D1, Q1, A1 escono senza il punto inventato. È l'unica strada che attacca la
causa vista nello spike (un solo JSON che deve fare sommario e liste insieme, con un modello da 9B) senza toccare la
libreria separata; 2 può aggiungersi dopo, per EX-18, se l'architetto vuole. Se l'architetto preferisce prima le strade
economiche, chiedo che la slice dica quale, così la provo sul modello vero (`benchmarkRiassunto`) prima del codice.

## Answer
Decisione dell'utente (2026-10-09): **strada 1, due passaggi, ma prima provati**.
1. Prima di qualunque codice: 2–3 corse vere su skatefest nel worktree usa e getta `../snastro-h1` (branch `spike/h1`,
   mai unito) con i due passaggi (1: sommario; 2: solo le quattro liste con la loro grammatica), modello aperto fra i
   due: con l'argomento di EX-14, senza argomento (EX-15), con l'argomento di EX-17. Annota risultati e tempi in
   `.mismagent/spikes/h1.md` (nuova sezione "Due passaggi"), con il conteggio di voci distinte e la presenza di D1, Q1,
   A1 e del punto inventato.
2. Se le corse soddisfano EX-14..EX-16: fermati con un nuovo `## Question` che chiede all'architetto l'ADR che emenda
   ADR 0026 §3 (generazione in due passaggi, modello tenuto aperto dentro lo stesso `riassumi`, effetto sul limite di
   600 s di EX-18). Si costruisce dopo l'ADR.
3. Se non le soddisfano: fermati con un `## Question` che riporta i numeri; si torna dall'utente.

## Question
Le corse dei due passaggi (risposta qui sopra, punto 3) **non soddisfano EX-14..EX-17**: si torna dall'utente.
Numeri in `.mismagent/spikes/h1.md` § "Due passaggi" (w1–w6):
- w1 (argomento EX-14): 127 s; decisioni 0, questioni 0, azioni 3, punti chiave 5, nessun doppione. D1 c'è ma tra le
  **azioni** (fonti giuste: 90, 92, 107, 108); Q1 solo come punto chiave confuso; A1 assente; punto inventato assente.
- w2 (senza argomento, EX-15) e w3 (argomento EX-17): 85 s e 90 s, **quattro liste vuote** (30 token in tutto).
- Varianti su EX-15: w4 (il sommario del passaggio 1 nel prompt delle liste) e w5 (senza "non inventare"): ancora
  quattro liste vuote.
- Il **sommario** del passaggio 1 è buono in tutte le corse: racconta D1, Q1, la seconda mappa e Amplify, senza il punto
  inventato. Il tempo non è un problema (85–127 s contro i 600 s di EX-18).
- w6 (passaggio 2 senza grammatica, per capire se il vuoto viene dalla grammatica pigra o dal modello) non è stata
  ottenuta: due tentativi bloccati oltre 15 min nel passaggio 1 con la GPU occupata da un altro processo.

Strade che vedo:
1. **Diagnosi prima di decidere**: ripetere w6 a GPU libera (~2 min). Se senza grammatica il modello scrive le liste,
   il vuoto viene dalla grammatica (es. spazi/a capo che la grammatica compatta rifiuta) e i due passaggi si possono
   ancora salvare con una grammatica più tollerante; se scrive liste vuote anche lì, il passaggio 2 così non funziona.
2. **Liste estratte dal sommario**: il passaggio 2 legge il sommario (non la trascrizione) e ne trae le voci, poi
   cerca le fonti. Il sommario contiene già i punti; resta da provare se le fonti vengono giuste.
3. **Una lista per passaggio** (decisioni, questioni, azioni separate): più passaggi brevi e più tempo (~25 s di
   prefill ciascuno), prompt più mirato a ciascun tipo.
4. Rivedere le attese: accettare D1 tra le azioni o Q1 tra i punti chiave ("anche con altre parole" di EX-14 non copre
   la lista sbagliata).

Raccomandazione: **1**, perché costa una corsa e decide se il problema è la grammatica (rimediabile dentro
l'adattatore) o il modello; poi 2 se il modello sceglie davvero liste vuote. Nessun codice di produzione finché non
c'è una corsa che soddisfa EX-14..EX-17.
