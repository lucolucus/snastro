# Brief — modifiche dopo v1.5.1: elimina progetto, punti del riassunto, lunghezza in percentuale, ricerca

## Problema
Quattro cose, emerse usando l'app dopo il primo rilascio pubblico:

1. **Un progetto non si può eliminare dall'app.** Oggi "un progetto vive finché l'utente non cancella la cartella"
   (ADR 0010, `.mismagent/decisions/0010-cartella-progetto-autonoma.md`). L'utente deve andare nel Finder.
2. **Il riassunto può uscire senza punti.** Riassunto di `skatefest.snastro` (letto dal DB il 2026-10-08, su una copia):
   stato `pronto`, sommario di 1464 caratteri, **0 righe in `riassunto_elemento`**. Quindi zero decisioni, questioni,
   azioni e punti chiave. Eppure il sommario stesso dice "Il gruppo ha concordato di implementare queste modifiche
   entro tre settimane", e l'argomento scritto dall'utente finisce con "servono i punti". Il prompt chiede
   "se non ce ne sono, lascia la lista vuota: non inventare" (`sintesi/adattatori/.../PromptRiassunto.kt`).
3. **La lunghezza si imposta in parole fisse.** Il tetto è per progetto, da 300 a 10 000 parole, default 2000
   (`LunghezzaMassimaParole.kt:10-13`). L'obiettivo chiesto al modello è già il 40% delle parole trascritte, tra 250
   e il tetto (`MisuraRisposta.kt:14-30`). L'utente vuole impostare **una percentuale del parlato**, non un numero
   di parole, con un limite di sicurezza più alto. Su skatefest: 3584 parole trascritte, quindi obiettivo ≈ 1400
   parole; tetto impostato a 10 000; ne sono uscite circa 230 nel sommario e nessun elemento. Il modello è rimasto
   molto sotto l'obiettivo.
4. **Non c'è modo di ritrovare dove si è parlato di una cosa** fra i tanti incontri di un progetto.

## Utenti e contesto
Una sola persona su macOS (M3 Pro, 36 GB, dal profilo). Riunioni di lavoro di 2–4 persone in italiano e inglese,
spesso con progetti che durano nel tempo (es. skatefest: un gioco da proporre agli editori).

## Valore
- Togliere un progetto senza uscire dall'app.
- Un riassunto che riporta sempre i punti cardine: il motivo per cui lo si legge.
- Una lunghezza che segue quanto si è parlato, senza toccare l'impostazione a ogni riunione.
- Ritrovare una frase o un argomento in tutto il progetto, e vederne il contesto.

## In scope
- **Elimina progetto** (parole dell'utente):
  - "si cancella del tutto", niente Cestino;
  - non è possibile mentre ci sono trascrizioni o elaborazioni in corso su quel progetto;
  - se il progetto è aperto, si torna al menu e lo si cancella.
- **Punti cardine sempre presenti** nel riassunto. Caso di riferimento: skatefest.
- **Lunghezza come percentuale del parlato** impostabile, con un default. Il tetto di sicurezza resta 10 000.
  Si fa solo dopo che le prove di Q1 mostrano che la quota cambia davvero quanto scrive il modello.
- **Prove su skatefest prima di tutto** (Q1): senza argomento, senza "non inventare", con la risposta grezza
  registrata prima del parsing, e con quota 0.4, 0.6 e 0.8.
- **Ricerca per parole in tutto il progetto** da una barra (FTS5, già previsto da ADR 0022). L'utente sceglie se
  cercare in tutto o solo nei riassunti. Mostra un'anteprima con un po' di contesto (chi parla, il minuto);
  cliccando si arriva al punto (confermato dall'utente: "esatto").

## Out of scope
- Recuperare un progetto eliminato (cestino, undo).
- Cercare in più progetti insieme.
- Modifiche al formato del riassunto oltre ai punti e alla lunghezza.
- Ricerca per concetti (embedding): rimandata. Si valuta solo se, dopo l'uso, le parole non bastano.
- Tetto di sicurezza oltre 10 000 parole.

## Costo della ricerca per concetti (risposta alla domanda "quanto costa un modello?")
- **Soldi: zero.** Esempio: Qwen3-Embedding-0.6B, Apache-2.0, "100+ lingue" (l'italiano non è nominato), GGUF Q8_0
  di 639 MB, supportato da llama.cpp (https://huggingface.co/Qwen/Qwen3-Embedding-0.6B-GGUF).
- **Costo vero: lavoro e disco.**
  - circa 640 MB di download in più, sul modello già facoltativo di ADR 0025;
  - esporre gli embedding da `:llama-jni`: oggi c'è solo l'header `llama.h`, nessuna funzione JNI di embedding
    trovata (grep del 2026-10-08);
  - un indice da calcolare per ogni trascrizione, con tempi non misurati.
- **Assunzioni da verificare con uno spike**: qualità sull'italiano parlato e trascritto male; tempo di
  indicizzazione di un'ora di riunione sull'M3 Pro.

## Domande aperte
- **Q1.** Perché skatefest ha 0 elementi? Possibili cause: il modello obbedisce a "non inventare" e all'argomento
  ("riassumi solo ciò che riguarda…"), oppure un difetto nel parsing o nella grammatica scarta gli elementi.
  Va riprodotto prima di cambiare il prompt.
- **Q2 (risolta, vedi sotto).** "Sempre i punti" vuol dire almeno N punti chiave (quanti?), o tutte le liste piene quando la trascrizione
  ne contiene?
- **Q3.** Il default della percentuale: il 40% di oggi o un altro valore? E il nuovo tetto di sicurezza? Alzarlo
  riduce lo spazio per l'ingresso (`LimiteIngresso.kt`: 35 512 `max_tokens` al tetto di 10 000).
- **Q4.** La percentuale resta un'impostazione per progetto, come oggi il tetto, o diventa globale?
- **Q5 (risolta: solo parole).** Concetti già nel primo rilascio, o solo parole?
- **Q6 (risolta, vedi sotto).** Si elimina anche un progetto chiuso, dalla lista, o solo quello aperto? E uno aperto in un'altra
  istanza (`.lock`)?

## Decisione dopo il challenger (2026-10-08)
Verdetto: **RESHAPE**. L'utente accetta il ridisegno ("si tutto"):
- punti 2 e 3 trattati come un unico difetto da capire prima (Q1); tetto a 10 000; concetti rimandati;
- elimina progetto: anche dalla lista dei progetti chiusi (Q6), cancellazione solo di una cartella che contiene
  `progetto.db`, senza seguire i link simbolici, conferma digitando il nome, tenendo il lock;
- punti cardine (Q2): "i punti non devono essere inventati". Devono comparire quando la riunione li contiene;
  nessun minimo fisso, liste vuote ammesse per riunioni che davvero non ne hanno;
- ricerca: il salto al punto nell'incontro resta dentro, va stimato a parte (oggi non esiste uno scorrimento a un
  segmento nel codice).

## First release
- **R0 — Elimina progetto** (timeline A, eventi 0–5): elimina dalla lista o dal progetto aperto (che si chiude e torna
  alla lista), rifiuto se c'è qualcosa in corso/in coda o il progetto è aperto altrove o la cartella non contiene
  `progetto.db`, conferma digitando il nome, cancellazione definitiva tenendo il lock e senza seguire link simbolici,
  esito parziale mostrato; progetto "non trovato" segnato in lista e rimovibile dalla lista.
- In parallelo, spike: H1 (skatefest: 0 punti e lunghezza), H8–H9 (FTS5).
- Dopo: **R1 — punti e lunghezza** (percentuale per progetto, personalizzabile per incontro; tetto 10 000);
  **R2 — ricerca per parole** con anteprima e salto al punto.

## Open questions
Nessuna per R0. Risolte il 2026-10-08 ("ok va bene tutto"):
- R0-1: progetto chiuso con lavori in coda nel DB → si elimina (EX-11).
- R0-2: file estranei → si cancellano solo i contenuti creati dall'app, il resto rimane e lo si dice (EX-9).
- R0-3: "Elimina" nel menu "⋯" della scheda in lista e nel menu dell'intestazione del progetto aperto.
- R0-4: "non trovato" ricontrollato a ogni apertura della lista; torna normale se la cartella ricompare (EX-12, EX-13).
