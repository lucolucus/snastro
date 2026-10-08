# Examples

R0 approvato dall'utente il 2026-10-08. Valori reali: i progetti in `~/Documents/snastro/`.

| id | given | when | then | rule | req | release |
|----|-------|------|------|------|-----|---------|
| EX-1 | lista con "skatefest", "mangu 2", "test"; nessun progetto aperto | Elimina su "test", digita `test`, conferma | la cartella `test.snastro` non esiste più; la lista mostra solo "skatefest" e "mangu 2" | cancellazione definitiva, conferma col nome | R0 elimina dalla lista | R0 |
| EX-2 | come EX-1 | Elimina su "test", digita `Test` (o `tes`) | il pulsante di conferma resta disattivo; niente cancellato | il nome deve corrispondere esattamente | R0 conferma | R0 |
| EX-3 | come EX-1 | Elimina su "test", poi Annulla | niente cancellato, la lista è invariata | si può tornare indietro fino alla conferma | R0 conferma | R0 |
| EX-4 | "skatefest" aperto, nessuna elaborazione in corso o in coda | Elimina progetto dal progetto aperto, digita `skatefest`, conferma | il progetto si chiude, si torna alla lista, `skatefest.snastro` non esiste più e non è in lista | se aperto: chiudi, torna al menu, cancella | R0 elimina l'aperto | R0 |
| EX-5 | "skatefest" aperto, trascrizione dell'incontro del 2026-10-07 in corso | Elimina progetto | rifiutato: "C'è un'elaborazione in corso: aspetta che finisca prima di eliminare il progetto."; niente chiuso, niente cancellato | niente in corso o in coda | R0 rifiuto | R0 |
| EX-6 | "mangu 2" aperto in un'altra finestra dell'app | Elimina su "mangu 2" dalla lista di questa finestra | rifiutato: "Questo progetto è aperto in un'altra finestra."; niente cancellato | il lock (`.lock`) è di un'altra istanza | R0 rifiuto | R0 |
| EX-7 | `test.snastro` spostata nel Finder in `~/Desktop/` dopo l'ultima apertura | si apre la lista | la scheda "test" è segnata "Cartella non trovata", con l'azione "Togli dalla lista" e senza "Elimina" | progetto non trovato = stato problematico | R0 non trovato | R0 |
| EX-8 | come EX-7 | Togli dalla lista su "test" | "test" sparisce dalla lista; `~/Desktop/test.snastro` resta intatta | togliere dalla lista non cancella niente | R0 non trovato | R0 |
| EX-9 | `skatefest.snastro` contiene anche `appunti.pdf` messo dall'utente | Elimina "skatefest", conferma | cancellati `progetto.db` (e i suoi file `-wal`/`-shm`), `audio/`, `cache/`, `sbobinature/`, `.lock`; resta `skatefest.snastro/appunti.pdf`; messaggio: "Il progetto è stato eliminato. Nella cartella ~/Documents/snastro/skatefest.snastro sono rimasti file non creati da snastro."; la voce sparisce dalla lista | si cancella solo ciò che ha creato l'app; i link simbolici non si seguono | R0 sicurezza | R0 |
| EX-10 | durante la cancellazione di "skatefest" un file di `audio/` non si può cancellare | — | messaggio: "Il progetto è stato eliminato solo in parte: resta la cartella ~/Documents/snastro/skatefest.snastro."; la voce sparisce comunque dalla lista | esito parziale mostrato | R0 fallimento | R0 |
| EX-11 | "mangu 2" chiuso; nel suo DB un riassunto è rimasto `in_attesa` (il progetto era stato chiuso prima che partisse) | Elimina su "mangu 2", digita `mangu 2`, conferma | `mangu 2.snastro` cancellata, la voce sparisce dalla lista | un progetto chiuso non ha niente in corso: la coda sparisce con lui | R0 elimina dalla lista | R0 |
| EX-12 | "test" segnato "Cartella non trovata" perché stava su un disco esterno staccato | si riattacca il disco e si riapre la lista | "test" torna una scheda normale, si apre e si elimina come le altre | il controllo si rifà a ogni apertura della lista | R0 non trovato | R0 |
| EX-13 | come EX-7 ("test" non trovato) | si prova ad aprire "test" | avviso "Cartella non trovata: ~/Documents/snastro/test.snastro"; resta nella lista | stesso avviso dello stato problematico | R0 non trovato | R0 |

R1 approvato dall'utente il 2026-10-08 (dopo lo spike H1, `.mismagent/spikes/h1.md`). Caso di riferimento: `skatefest.snastro`,
incontro del 2026-10-07, sbobinatura `sbobinature/2026-10-07 SKATEFEST.md` (orari come `mm:ss` di quel file).
Punti cardine attesi, confermati dall'utente: **D1** fra due-tre settimane ci si ritrova e si fa un test con le modifiche
(11:15, 12:58); **D2** una modifica alla volta (10:38); **Q1** come far capire ai giocatori il movimento automatico del
poliziotto, percorso disegnato o colori (5:29–7:42); **Q2** come cambiare la fine della partita / fase chill; **Q3** se
spostare il sito ad Amplify (14:40–21:48); **A1** rifare la seconda mappa tutti e quattro bilanciando le posizioni (5:10).
Inventato, da non riportare mai: "approvato il movimento automatico dei poliziotti verso il più vicino".

| id | given | when | then | rule | req | release |
|----|-------|------|------|------|-----|---------|
| EX-14 | skatefest, argomento "Parliamo di skatefest, gioco già definitivo da proporre a qualche editore ma forse che richiede qualche modifica, per esempio al comportamento dei polizitti oppure della fase chill. servono i punti" | Riassumi | riassunto pronto; tra le decisioni c'è D1, tra le questioni aperte Q1, tra le azioni A1 (anche con altre parole), ognuno con fonti nelle righe di quei minuti; nessun elemento dice che il movimento automatico dei poliziotti è stato approvato | i punti cardine presenti nella riunione compaiono; niente inventato | R1 punti cardine | R1 |
| EX-15 | skatefest, nessun argomento | Riassumi | come EX-14: D1, Q1 e A1 presenti con le fonti; il punto inventato assente | i punti non dipendono dall'argomento | R1 punti cardine | R1 |
| EX-16 | il riassunto di EX-14 | l'utente lo legge | in nessuna lista due elementi hanno lo stesso testo, e nessuna coppia dice la stessa cosa con altre parole (giudizio dell'utente; oggi, spike v2: 14 questioni di cui 3 distinte) | ogni punto una volta sola | R1 punti cardine | R1 |
| EX-17 | skatefest, argomento "Parliamo solo del sito e dell'hosting" | Riassumi | Q3 (Amplify) c'è, e ci sono anche D1, Q1 e A1, che col filtro di oggi sarebbero "fuori tema" | l'argomento è contesto, non filtro: non toglie mai punti | R1 argomento | R1 |
| EX-18 | un incontro in cui il modello comincia a ripetersi (spike v6: skatefest a quota 0.8, ~27 900 parole in ~40 minuti) | Riassumi | il riassunto finisce entro 600 s (limite di AC-S153) in stato fallito, con "Il modello si è ripetuto: riprova." e il pulsante Riprova; un riassunto pronto precedente resta visibile | la generazione si ferma presto quando il modello si ripete | R1 ciclo | R1 |
| EX-19 | un incontro di sole chiacchiere senza decisioni né compiti (es. 3 minuti sul meteo e sul pranzo) | Riassumi | riassunto pronto con il sommario; decisioni, questioni aperte e azioni vuote | niente inventato: liste vuote ammesse quando la riunione non ne ha | R1 punti cardine | R1 |
