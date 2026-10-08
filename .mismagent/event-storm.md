# Event storm — elimina progetto, punti del riassunto, lunghezza in percentuale, ricerca

Brief: `.mismagent/brief.md`. Proposta del 2026-10-08, da percorrere con l'utente.

## Timeline
| # | event | command | actor | policy | read model | undo |
|---|-------|---------|-------|--------|------------|------|
| **A. Elimina progetto** |
| 0 | Progetto non trovato | — | sistema | all'apertura della lista, una cartella che non c'è più è segnata in stato problematico | lista progetti | l'utente può togliere la voce dalla lista (niente cancellato sul disco) |
| 1 | Eliminazione progetto richiesta | Elimina progetto (dalla lista o dal progetto aperto) | utente | se il progetto è aperto, si chiude e si torna alla lista | lista progetti (`ElencoProgetti`, AC-198) | sì, annulla la conferma |
| 2 | Eliminazione progetto rifiutata | — | sistema | se c'è una trascrizione/elaborazione/riassunto in corso o in coda, oppure il progetto è aperto in un'altra istanza (`.lock`), oppure la cartella non contiene `progetto.db` | messaggio col motivo | — |
| 3 | Eliminazione progetto confermata | Conferma digitando il nome | utente | — | finestra di conferma | no |
| 4 | Progetto eliminato | — | sistema | cartella cancellata tenendo il lock, senza seguire link simbolici; la voce sparisce dalla lista | lista progetti | **no**: non resta niente (scelta dell'utente: "si cancella del tutto") |
| 5 | Eliminazione progetto fallita a metà | — | sistema | si mostra cosa è rimasto; la voce sparisce comunque dalla lista | messaggio | — |
| **B. Riassunto: punti e lunghezza** |
| 6 | Percentuale del parlato modificata | Modifica percentuale (del progetto, o personalizzata per un incontro) | utente | vale per i prossimi riassunti (dell'incontro, se personalizzata); quello fatto resta finché non si rigenera | incontro | sì, una nuova modifica |
| 7 | Riassunto richiesto | Riassumi incontro (con argomento facoltativo) | utente | entra nella coda condivisa (ADR 0023); obiettivo = percentuale × parole del parlato, max 10 000 | incontro | sì, annullabile finché in coda (oggi) |
| 8 | Riassunto pronto | — | modello locale | ogni punto cardine presente nella riunione compare tra decisioni/questioni/azioni/punti chiave, con le fonti; niente inventato | riassunto dell'incontro | rigenerare = nuovo riassunto |
| 9 | Indice di ricerca aggiornato | — | sistema | a ogni trascrizione o riassunto salvato, rigenerato o eliminato | — | segue il testo |
| **C. Ricerca** |
| 10 | Ricerca eseguita | Cerca (testo + "tutto" / "solo riassunti") | utente | — | elenco risultati: frase trovata con un po' di contesto, incontro, parte, chi parla, minuto | — |
| 11 | Risultato aperto | Apri risultato | utente | si apre l'incontro e si scorre al punto trovato (sbobinatura o riassunto); l'audio non parte | sbobinatura / riassunto dell'incontro | si torna ai risultati, che restano |

## Hotspots
- H1: perché il riassunto di skatefest ha 0 elementi e circa 230 parole contro un obiettivo di circa 1400? Prove su
  una copia: senza argomento, senza "non inventare", con la risposta grezza registrata prima del parsing,
  con quota 0.4/0.6/0.8 — spike (lo fa Claude, l'utente giudica il risultato).
- H2: l'argomento oggi è un filtro tematico ("riassumi solo ciò che riguarda…"), ma l'utente ci ha scritto
  un'istruzione ("servono i punti"). Serve un campo istruzioni separato, o l'argomento cambia significato? — utente, dopo H1.
- H3: il default della percentuale: 40% come oggi o un altro? — utente, dopo H1.
- H4: RISOLTA — la percentuale resta **per progetto** (default dell'incontro), con la possibilità di **personalizzarla per un singolo incontro** (utente).
- H5: RISOLTA — sì, il riassunto fatto resta finché l'utente non lo rigenera.
- H6: RISOLTA — solo testo: si scorre al punto, l'audio non parte.
- H7: RISOLTA — sì, tornando indietro la ricerca e i risultati restano.
- H8: FTS5 su italiano trascritto male: "decisioni" trova "decisione"? Prefisso automatico? Accenti? — spike su skatefest.
- H9: FTS5 è disponibile nel sqlite-jdbc in uso? — spike (una query su una copia del DB).
- H10: RISOLTA — una cartella che non si trova più appare in lista in stato problematico ("non trovato"); da lì
  l'utente può toglierla dalla lista senza cancellare niente sul disco.
- H11: RISOLTA — sì, nei risultati compaiono i nomi delle Voci, non `{V<n>}`.

## Language
- **punti cardine** (utente) = gli elementi del riassunto: decisioni, questioni aperte, azioni, punti chiave (Sintesi).
  Attenzione: "punti chiave" è anche il nome di una sola delle quattro liste.
- **percentuale del parlato**: quota delle parole trascritte usata come obiettivo di lunghezza (oggi `QUOTA_DEL_TRASCRITTO`, fisso 0.4).
- **tetto / limite di sicurezza**: la lunghezza massima in parole (oggi `LunghezzaMassimaParole`, 300–10 000), resta 10 000.
- **argomento**: oggi un filtro tematico (Sintesi); l'utente lo usa anche come istruzione (vedi H2).
- **eliminare**: per una registrazione/parte esiste già con le sue regole (ADR 0020, 0024, 0038); per il progetto è
  cancellazione della cartella intera, senza recupero.
