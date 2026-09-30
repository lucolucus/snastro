# Product brief — incontro

## Problema
Una riunione vera finisce spesso in **2–3 file**: una pausa, il registratore fermato, la batteria. Il totale
va da 1 a 3 ore. Oggi ogni file è una `Registrazione` separata, e questo ha due conseguenze:
- **Riassunti spezzati.** Ogni parte ha il suo `Riassunto`, e una decisione presa nella parte 1 e ripresa
  nella parte 2 finisce in due riassunti.
- **Persone da riconoscere più volte.** Ogni parte ha le sue `Voce`, quindi la stessa persona va
  riconosciuta in ogni parte, e un ospite non nominato diventa due "Ospite del …".

L'utente vuole l'incontro come unità: **un riassunto e una base di voci comune per tutta la riunione**.

## Utente
Lo stesso di sintesi: una persona su macOS (M3 Pro, 36 GB), riunioni di lavoro con 2–4 persone,
italiano e inglese insieme. Il caso tipico è un incontro in 2–3 parti.

## Valore atteso
- Nel `Progetto` le registrazioni sono raggruppate in **Incontri**. Un Incontro ha le sue parti in ordine
  (le `Registrazione`), e un Incontro con una sola parte appare e funziona **esattamente come oggi**.
- **Voci dell'Incontro.** Le `Voce` appartengono all'Incontro, non alla singola parte: la stessa persona in due
  parti è una sola Voce, che si nomina una volta sola. Diventa la base comune del Riassunto.
- **Un Riassunto per Incontro** su tutte le parti in ordine. Le `Fonte` indicano la parte e il minuto
  ("parte 2 · 12:30"). Il Responsabile e il parlante di un punto sono Voci dell'Incontro.
- **La Sbobinatura resta una per Registrazione**, e usa i nomi delle Voci dell'Incontro.

## Scope (primo rilascio)
- L'entità `Incontro` nel `Progetto`. La migrazione crea un Incontro per ogni `Registrazione` esistente,
  e i Riassunti esistenti passano al loro Incontro.
- **Le parti entrano nell'Incontro solo con l'import**: l'utente crea o apre un Incontro e ci aggiunge i file,
  anche più di uno alla volta. Non esistono gesti di aggancio, separazione o unione. Se una parte è finita
  nell'Incontro sbagliato, la si elimina e la si reimporta (D-0008).
- **Ordine automatico** delle parti per ora di inizio. L'ora va catturata all'import, perché oggi la data è
  senza ora, ed è **modificabile** come la data (D-0009).
- Voci dell'Incontro: unione delle voci tra parti (come si abbinano: spike `voci-tra-parti`), con la
  `Revisione` e l'`Attribuzione` estese all'Incontro.
- Riassunto per Incontro, con queste regole:
  - la Verifica delle fonti è qualificata per parte;
  - un riassunto già "pronto" diventa **superato** quando: una parte viene rivista, ritrascritta (anche con
    una sola parte), riordinata o eliminata, oppure se ne aggiunge una nuova;
  - i numeri delle Voci di un Incontro non si riusano mai, così un riassunto superato non attribuisce una
    frase alla persona sbagliata (D-0007);
  - quando è superato non si rifà mai da solo: c'è "Riassumi di nuovo".
- Il titolo e la data dell'Incontro sono derivati dalla prima parte, con "· N parti". Non sono un nuovo campo
  modificabile.

## Fuori scope (primo rilascio)
- Trascina per riordinare, aggancia o separa parti, unisci incontri, sposta una parte fra incontri.
- Registrazioni sovrapposte o simultanee: due dispositivi sulla stessa riunione non vanno messi nello
  stesso Incontro.
- **Rinomina `Documento` → `Sbobinatura`**: cambio separato, già fatto su `main` prima di questa feature
  ([ADR 0031](../../decisions/0031-rinomina-documento-sbobinatura.md)).
- **I0**, cioè gli anti-pattern noti del codice trasformati in controlli del gate: è un cambio separato,
  fatto prima di questa feature.
- Riassunto di progetto; Sbobinatura per Incontro.

## Esito misurabile
- Su un incontro reale in 2–3 parti, l'utente nomina ogni persona **una volta sola** e ottiene **un
  Riassunto**. In quel Riassunto l'utente riconosce come vere le Decisioni e le Azioni, e nessuna Decisione
  compare due volte perché attraversava due parti.
- Nessuna `Fonte` punta a una parte, a un segmento o a una persona sbagliati.
- Un Incontro con una sola parte è indistinguibile da oggi: stesse schermate, stessi dati dopo la
  migrazione.
- Tempo per riassumere un incontro di 3 ore: misurato dallo spike, entro il budget di ADR 0026, cioè
  ≤ 10 min per ora.

## Rischi / spike
1. **voci-tra-parti** (nuovo): come si uniscono le Voci di parti diverse in Voci dell'Incontro? Le opzioni:
   - abbinamento per impronta (come la `Proposta`) confermato dall'utente;
   - clustering congiunto delle impronte di tutte le parti;
   - solo a mano.

   Va misurata l'accuratezza sugli incontri reali in più parti. Collegato: la `Revisione` fra parti
   (unire una Voce della parte 1 con una della parte 2).
2. **contesto-lungo** (già aperto, sintesi): qualità e tempo del riassunto su un input di 2–3 ore
   concatenato. Va chiuso **prima** di modellare il Riassunto per Incontro. L'input è un incontro reale in
   più parti.
3. **ora-di-inizio**: l'ora di inizio della registrazione si ricava dai metadati del file? Oggi
   `data_registrazione` è solo una data.
4. **Riassunti e privacy**: se si elimina una parte, il Riassunto dell'Incontro diventa superato ma resta
   leggibile, e quindi conserva testo derivato da un audio eliminato. L'utente ha accettato questo costo
   (D-0003). Va verificato con ADR 0020 (Elimina registrazione).

## Verdetto challenger
**RESHAPE.** Le obiezioni principali:
- I1, che solo raggruppava, non dava niente di visibile;
- lo schema del Riassunto è legato a una Registrazione e va riprogettato, non allargato;
- le persone attraverso le parti restavano ambigue;
- il costo cresce con il numero di parti.

L'alternativa più economica proposta era "Unisci registrazioni", cioè un'unica Registrazione con l'audio
concatenato. **L'utente ha scelto l'Incontro persistente.** Per rispondere all'obiezione sulle persone ha
portato le **Voci a livello di Incontro**, così la base è comune. Ha poi tolto dalla feature rinomina e I0, e ha
eliminato i gesti: le parti entrano solo con l'import, in ordine automatico. Scelte e costi sono in [decisions.md](decisions.md).
