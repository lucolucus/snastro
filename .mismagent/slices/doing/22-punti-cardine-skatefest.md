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

## Answer
Decisione dell'utente (2026-10-10): **prima la corsa diagnostica w6** (secondo passaggio senza grammatica, stesso
worktree usa e getta `../snastro-h1`). Se un'altra corsa resta ferma nel primo passaggio oltre 5 minuti (di norma 58 s),
interrompila e fermati con un `## Question` (GPU occupata). Annota w6 in `.mismagent/spikes/h1.md` e fermati in ogni
caso con un `## Question` che dica cosa mostra w6 (liste vuote per la grammatica o per il modello) e quale strada ne
segue: liste dal sommario, un passaggio per lista, o rivedere gli esempi. Niente codice di produzione.

## Question
w6 è fatta (`.mismagent/spikes/h1.md` § "w6", punti 11–12). Cosa mostra:
- **Le liste vuote vengono dalla grammatica, non dal modello**: senza grammatica, con lo stesso prompt, il passaggio 2
  scrive 1 decisione, 1 questione, 2 azioni, 5 punti chiave (in markdown, 53 s). Probabile causa, non ancora provata: il
  prompt del passaggio 2 non nomina mai il JSON né le chiavi, quindi la grammatica pigra forza un JSON che il modello non
  si aspettava e sceglie `[]`. Il passaggio 1, che descrive il suo JSON, ha funzionato in tutte le corse.
- **Ma la classificazione resta debole**: D1 esce tra le azioni (fonte 92, giusta), Q1 tra i punti chiave (fonte 40,
  giusta), A1 solo in parte (fonte 36, parla di carte e non della seconda mappa), e l'unica "decisione" è sbagliata
  (fonte 90 dice il contrario). Il punto inventato di EX-14 è assente.

Strade che vedo:
1. **Una corsa in più con il formato detto nel prompt** del passaggio 2 (chiavi e forma di una voce, come fa il
   passaggio 1), grammatica attiva. Toglie probabilmente il vuoto, ma da w6 ci si aspetta lo stesso problema di
   classificazione (D1 tra le azioni). È la prova minima per sapere se basta.
2. **Un passaggio per lista** (decisioni, questioni, azioni; punti chiave a parte o tolti): ogni prompt chiede un solo
   tipo con la sua definizione ed esempi, ciò che serve a mettere D1 tra le decisioni e Q1 tra le questioni. Costa
   ~25 s di prefill per passaggio più la generazione: con 4–5 passaggi circa 3–5 min su skatefest, sotto i 600 s ma
   meno margine per riunioni più lunghe (EX-18).
3. **Liste dal sommario**: il sommario è la parte che funziona (D1, Q1, seconda mappa, Amplify in tutte le corse). w6 non
   dice nulla su questa strada; restano da provare le fonti.
4. **Rivedere gli esempi**: accettare D1 tra le azioni e Q1 tra i punti chiave. w6 dice che il modello vede i punti, ma
   li mette in un'altra lista.

Raccomandazione: **1 e poi 2**. La 1 costa una corsa (~2 min) e dice se, risolto il formato, la classificazione basta;
w6 fa pensare di no, e allora la 2 è la strada che attacca proprio la classificazione, nell'adattatore e senza toccare
`:llama-jni`. In entrambi i casi la struttura (più passaggi, modello tenuto aperto) va nell'ADR che emenda ADR 0026 §3.
Nessun codice di produzione finché una corsa non soddisfa EX-14..EX-17.

## Answer
Decisione dell'utente (2026-10-10): **prova 1 e poi 2**, nel worktree usa e getta, niente codice di produzione.
1. Secondo passaggio con la forma del JSON (chiavi e definizioni) descritta nel prompt, grammatica attiva: le tre
   corse di EX-14, EX-15, EX-17.
2. Un passaggio per lista (decisioni, questioni aperte, azioni, punti chiave), ognuno con la definizione del suo tipo
   e la sua grammatica, dopo il passaggio del sommario: le stesse tre corse, con i tempi totali.
Annota entrambe in `.mismagent/spikes/h1.md` (D1, Q1, A1 nella lista giusta, fonti, voci distinte, punto inventato,
tempi) e fermati con un `## Question` che dice quale strada passa EX-14..EX-17, se una passa, per l'ADR che emenda
ADR 0026 §3. Guardia di 5 minuti su ogni passaggio.

## Question
Prove 1 e 2 fatte (`.mismagent/spikes/h1.md` § "Prove 1 e 2", punti 13–17). **Nessuna delle due passa EX-14..EX-17.**
- **Prova 1** (quattro liste in un passaggio, forma del JSON nel prompt): il vuoto sparisce, ma D1, Q1 e A1 sono tutti
  nella lista giusta solo in 1 corsa su 3 (EX-15). 128–182 s.
- **Prova 2** (un passaggio per lista): D1 tra le decisioni, Q1 tra le questioni e A1 tra le azioni, con fonti dei minuti
  giusti, in **3 corse su 3**; Q3 c'è in EX-17. 237–251 s, sotto i 600 s.
- **In entrambe resta il punto inventato**: il movimento automatico dei poliziotti, che c'è già ed è giudicato
  controintuitivo (s39–s48), esce come deciso o da introdurre in 4 corse su 6 in modo netto (es. prova 2 EX-14:
  "Modificare il comportamento dei poliziotti per renderli automatici e prevedibili"). Nelle altre 2 corse è dubbio
  (prova 1 EX-17: fra le modifiche di D1; prova 2 EX-17: azione "Modificare le carte dei poliziotti per includere il
  movimento automatico"). Questo giudizio è mio e va confermato da te sulle voci citate.

La strada che si avvicina è la **2**: è quella da mettere nell'ADR che emenda ADR 0026 §3 se si va avanti (sommario,
poi un passaggio per lista, modello tenuto aperto nello stesso `riassumi`, ~4 min per ~1 h di audio). Ma oggi fallisce
EX-14 e EX-15 per il punto inventato. Opzioni:
1. **Un'altra prova sulla 2, mirata al passaggio delle decisioni** (e delle azioni): una decisione è un accordo detto
   nella riunione, e una proposta, una regola già esistente o un problema da risolvere non lo sono. In più, il sommario
   del passaggio 1 (che non ha mai avuto il punto inventato) come contesto di quel passaggio. Costo: tre corse, ~13 min.
   Rischio: una definizione tarata su skatefest.
2. **ADR ora sulla 2**, con il punto inventato come limite noto, e EX-14/EX-15 rivisti da te (es. tollerare voci
   "da introdurre" che non dicono "approvato"). Non lo raccomando: EX-14 nomina proprio questo punto.
3. **Rivedere il mio giudizio**: se per te le voci dubbie (o anche quelle "nette") non dicono che il movimento è stato
   approvato, la prova 2 passa EX-14..EX-17 già così.

Raccomandazione: **3 e poi 1**. Prima il tuo giudizio sulle voci citate, che decide se serve altro. Se il punto
inventato c'è, allora la 1: costa tre corse e lascia invariata la struttura della 2, cioè quella da scrivere nell'ADR.
Nessun codice di produzione finché una corsa non soddisfa EX-14..EX-17.

## Answer
Risposta dell'utente (2026-10-10): nella riunione il movimento automatico dei poliziotti verso il giocatore più vicino
era **una proposta nuova**, non la regola esistente. Quindi "soluzione proposta / idea discussa" (punti chiave,
questioni) è corretta; resta inventato solo presentarlo come **deciso o approvato** (una decisione, o un'azione
"implementare …" che lo dà per deciso). Rivaluta le corse delle prove 1 e 2 con questa lettura, senza nuove corse se
non servono, e annota l'esito in `.mismagent/spikes/h1.md`. Se la prova 2 passa EX-14..EX-17: fermati con un
`## Question` per l'architetto (ADR che emenda ADR 0026 §3: sommario + un passaggio per lista, modello aperto, tempi
misurati, effetto sui 600 s di EX-18), segnalando anche le ripetizioni fra liste diverse (EX-16 parla di una lista
sola). Se non passa: la prova con la definizione più stretta di decisione (opzione 1), poi fermati con i numeri.

## Question
Con la tua lettura (movimento automatico = proposta nuova; inventato solo se dato per deciso) le prove 1 e 2 **non
passano**: il punto inventato c'è in 3 corse su 3 in entrambe (`.mismagent/spikes/h1.md` § "Rivalutazione"). Ho fatto
quindi la prova 3, cioè l'opzione 1: prova 2 con decisione e azione definite in modo stretto e il sommario come contesto
di quei due passaggi (§ "Prova 3"). **Non passa nemmeno questa.** I numeri, per EX-14 / EX-15 / EX-17:

| | D1 tra le decisioni | Q1 tra le questioni | A1 tra le azioni | punto inventato | durata |
|---|---|---|---|---|---|
| prova 1 (4 liste, 1 passaggio) | no / sì / sì | sì / sì / sì | parziale / sì / no | sì / sì / sì | 182 / 128 / 136 s |
| prova 2 (1 passaggio per lista) | sì / sì / sì | sì / sì / sì | sì / sì / sì | sì / sì / sì | 237 / 241 / 251 s |
| prova 3 (2 + definizioni strette) | sì / sì / sì | sì / sì / sì | **no / no / no** | dubbio / sì / **no** | 255 / 297 / 253 s |

Q3 c'è in tutte le corse di EX-17. Dentro ogni lista non ci sono doppioni (EX-16), ma lo stesso punto torna in più liste
(prove 2 e 3). La prova 3 toglie il punto inventato in 1 corsa su 3 e perde A1 in 3 su 3: col modello da 9B e un prompt
per lista, separare "proposto" da "deciso" e tenere tutte le azioni vere sembrano tirare in direzioni opposte.

Cosa vedo da qui, per te:
1. **Prova 2 + un filtro dopo le liste**: un passaggio breve che rilegge le sole decisioni (poche, ~300 token) contro la
   trascrizione o il sommario e scarta quelle non concordate. Le azioni restano quelle della prova 2, quindi A1 resta.
   Costo: ~40 s in più, ancora sotto i 600 s. Non provato.
2. **Prova 2 con la sola decisione stretta** (azione com'era nella prova 2): tiene A1. Resta da vedere l'azione
   "implementare il movimento automatico" (prova 2 EX-15, EX-17), che oggi conta come inventato. Una prova di 3 corse.
3. **Rivedere EX-14/EX-15** per accettare il punto inventato come limite noto del modello locale, e passare all'ADR sulla
   prova 2. EX-14 nomina proprio quel punto: è una scelta tua, non tecnica.
4. **Fermare qui R1 "punti cardine"** e tenere solo il sommario del passaggio 1, che non ha mai avuto il punto inventato
   e racconta D1, Q1, A1 e Q3, con le liste in un rilascio successivo.

Raccomandazione: **2 e poi 1**. La 2 è una corsa di 3 riunioni e misura quanto pesa l'azione inventata da sola; la 1
attacca esattamente l'errore che resta (decisioni non concordate) senza toccare le altre liste. In ogni caso la
struttura è quella della prova 2: è quella da mettere nell'ADR che emenda ADR 0026 §3, quando una corsa passa.

## Answer
Decisione dell'utente (2026-10-10): **ultimo giro sul 9B**, poi si sceglie. L'utente vuole un modello piccolo per
riassumere: Qwen3.8-27B e Qwen3.8-Flash-Next (180B totali) sono esclusi, resta Qwen3.5 9B.
Prova 4, usa e getta: variante 2 (sommario + un passaggio per lista) con la definizione stretta **solo** per le
decisioni (le azioni con la definizione della prova 2, così A1 resta), più un breve passaggio che ricontrolla ogni
decisione sulle sue fonti e toglie quelle che le fonti non sostengono come accordo. EX-14, EX-15, EX-17; guardia di 5
minuti per passaggio; tempi totali. Annota in `.mismagent/spikes/h1.md`. Se passa EX-14..EX-17: `## Question` per
l'ADR dell'architetto (emenda ADR 0026 §3). Se non passa: `## Question` con i numeri e le due sole strade rimaste,
"limite noto" (ADR sulla variante 2, EX-14/15 aggiornati) o "solo sommario in R1".

## Question
Prova 4 fatta, ultimo giro su Qwen3.5 9B (`.mismagent/spikes/h1.md` § "Prova 4" e "Bilancio"). **Non passa
EX-14..EX-17**: passa EX-14, non EX-15 né EX-17.
- La verifica delle decisioni scarta 15 decisioni su 17: tutte quelle inventate, ma anche D1 in EX-15 e EX-17 (D1 resta
  solo dove è scritta "è stato fissato un incontro").
- Con le azioni della prova 2, l'azione inventata resta in EX-15 ("Implementare la modifica per la carta 'poliziotto'
  che si muove automaticamente") ed EX-17 ("Modificare le carte dei poliziotti per includere il movimento automatico").
- Q1, A1 e Q3 sono stabili; 286–305 s a corsa.

In nessuna delle quattro prove D1 tra le decisioni e "niente inventato" stanno insieme in tutte e tre le corse. Le due
strade rimaste:

1. **Limite noto**: si costruisce la prova 2 (sommario + un passaggio per lista, modello aperto, ~4 min per ~1 h di
   audio, ADR che emenda ADR 0026 §3). Si rivedono EX-14/EX-15 in modo da ammettere che decisioni e azioni possano
   dare per deciso ciò che era solo proposto, avvisando chi legge (es. "verifica le decisioni sulla trascrizione").
   D1, Q1 e A1 escono nella lista giusta in 3 corse su 3; il punto inventato anche, in 3 su 3. La prova 4 è
   l'alternativa con meno decisioni inventate ma con D1 perso in 2 su 3.
2. **Solo sommario in R1**: il riassunto di R1 è il solo sommario (il passaggio 1, ~1 min). Decisioni, questioni e
   azioni escono vuote o nascoste finché non c'è un modo affidabile; EX-14..EX-17 vanno riscritti sul sommario (D1, Q1,
   A1 e Q3 raccontati, niente dato per deciso), e EX-16 non si applica. Il sommario ha passato questo in tutte le corse
   viste (w1–w3, prove 1–4: stesso passaggio 1). La struttura a passaggi resta pronta per un rilascio successivo.

Raccomandazione: **2**. È l'unica uscita che in tutte le corse ha i punti cardine e non inventa: per un riassunto da
leggere al posto della riunione, un punto falso fra le decisioni costa più di una lista mancante. La 1 va bene se per te
avere le liste conta più del rischio, ma allora EX-14 va riscritto proprio sul punto che nomina. In entrambi i casi la
scelta cambia gli esempi di R1, quindi è tua prima che dell'architetto.
