# Cosa vede l'utente, momento per momento

Ogni momento risponde a tre domande: **cosa vuole sapere l'utente**, **cosa gli mostriamo** e **qual è l'unica azione principale** (un solo bottone `primary` per schermata).

## 1 · Primo avvio: i modelli non ci sono
- **Vuole sapere:** perché non può ancora trascrivere, quanto deve scaricare, se i suoi dati restano sul Mac.
- **Mostriamo:** la dimensione totale, un `ProgressBar` per modello (MB scaricati / totali), la licenza di ogni modello. In caso di errore compare un `Banner` di tipo error che dice cosa è successo e offre «Riprova».
- **Azione:** «Scarica». Nel frattempo l'app resta usabile: si può importare, non trascrivere.

## 2 · Nessun progetto
- **Mostriamo:** un `EmptyState` con «Nuovo progetto» e «Apri progetto…», più i progetti recenti (nome, numero di registrazioni, ultima attività).

## 3 · Progetto vuoto
- **Mostriamo:** la `DropZone` grande al centro, con il testo «Resta dov'è: snastro lo legge senza mandarlo da nessuna parte».
- **Azione:** «Importa audio…», oppure il trascinamento del file sulla finestra (la drop zone passa allo stato `over`).

## 4 · Ho appena importato un file
- **Vuole sapere:** se il file è quello giusto, quanto dura, quanto ci vorrà a trascriverlo.
- **Mostriamo:** la riga compare subito in cima con titolo, data (modificabile), durata e lo stato «Da trascrivere». Sulla riga ci sono il campo «Quante persone parlano?» e «Trascrivi», più la **stima del tempo** («~7 min»). Aprendo la registrazione (`ScreenImported`) si può già ascoltarla con la forma d'onda, controllare i dati del file e avviare la trascrizione.
- **Azione:** «Trascrivi».

## 5 · In coda
- **Mostriamo:** `StatusChip` queued «In coda · 2ª» e «Annulla» (non si perde nulla).

## 6 · In elaborazione
- **Vuole sapere:** se sta andando avanti, a che punto è, quanto manca, se può fare altro nel frattempo.
- **Mostriamo:** sulla riga il chip running con la fase e il tempo trascorso. Nella pagina (`ScreenProcessing`) il `PhaseProgress` con le quattro fasi nominate, il tempo trascorso e la stima rimanente. L'ascolto resta disponibile. Non mostriamo percentuali finte.
- **Azione:** nessuna obbligatoria. Si può ascoltare o tornare alla lista.

## 7 · Trascritta, ma le voci non hanno nome
- **Vuole sapere:** chi ha parlato, se l'app ha riconosciuto qualcuno, cosa manca.
- **Mostriamo:** il chip warn «Da identificare» sulla riga e i pallini delle voci (pieni i nominati, vuoti gli altri). Nella pagina (`ScreenRecording`) c'è un `Banner` info «1 voce da identificare · Identifica», poi il testo e il riassunto con la quota di parlato per voce. La scheda Voci (`ScreenIdentify`) mostra le proposte («Sembra Paolo · forte»), l'estratto da ascoltare e «È Paolo».
- **Azione:** «Identifica», che apre la scheda Voci.

## 8 · Trascritta e identificata
- **Vuole sapere:** di cosa si è parlato e chi ha detto cosa, senza rileggere un'ora di testo.
- **Mostriamo:** la scheda Riassunto (`RecordingSummary`). Oggi contiene durata, minuti di parlato, persone e quota di parlato per persona, calcolabili subito dal trascritto. Con la v2 si aggiungono il riassunto in prosa, le decisioni e le cose da fare con chi se ne occupa. Il documento `.md` si apre con «Apri documento».
- **Azione:** leggere e ascoltare. Cliccando una frase la si ascolta da lì.

## 9 · Non riuscita
- **Mostriamo:** sulla riga il motivo in parole semplici e in `danger`, il campo persone precompilato e «Riprova». Nella pagina lo stesso, in un `Banner` error.

## 10 · Ritrascrizione
- **Mostriamo:** prima un `Dialog` di conferma che spiega cosa si perde. Poi il chip «Ritrascrizione in coda / in corso». La pagina resta leggibile con un `Banner` warn: modifiche disabilitate fino al termine.

## 11 · Il progetto nel tempo
- **Vuole sapere:** cosa resta da fare, quanto materiale ha, chi c'è di solito, di cosa si parla.
- **Mostriamo:** la home del progetto (`ScreenProject`). Prima la card «Da fare» (da trascrivere, da identificare, non riuscite, ognuna con il suo bottone). Poi il riassunto del progetto: registrazioni, ore, persone e ospiti, presenze per persona, e con la v2 i temi ricorrenti. Infine l'elenco.

## Proposte che cambiano il comportamento attuale

Sono da confermare prima di implementarle:
1. **La pagina della registrazione si apre in ogni stato**, non solo quando esiste un trascritto (oggi S3 si apre solo in quel caso). Prima della trascrizione si ascolta e si avvia. Durante mostra le fasi.
2. **Stima del tempo di trascrizione**, calcolata dalla durata e dal rapporto misurato su questo Mac (R1: 60 min in meno di 10 min).
3. **Scheda Riassunto nella pagina della registrazione**. I dati di fatto (durata, parlato, quota per persona) sono disponibili subito. La prosa, le decisioni e le cose da fare arrivano con la v2, e fino ad allora compaiono solo come esempio.
4. **Home del progetto con «Da fare» e riassunto** sopra l'elenco. «Presenze» significa in quante registrazioni compare ogni persona.
5. **Corsie delle voci nella barra audio**: mostrano chi parla lungo tutta la registrazione. Cliccando una corsia si salta a quel punto.
