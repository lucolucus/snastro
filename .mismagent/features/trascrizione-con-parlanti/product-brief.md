# Product brief — trascrizione-con-parlanti

## Problema
L'utente ha registrazioni di riunioni di lavoro (2–4 persone, italiano e inglese mescolati) e vuole
sapere **chi ha detto cosa**, in un testo leggibile. Gli strumenti esistenti (noScribe, WhisperX,
MacWhisper) trascrivono e separano le voci, ma **ogni registrazione riparte da zero**: i parlanti
vanno rinominati a mano ogni volta, anche quando sono sempre le stesse persone.

## Utente
Una sola persona (l'autore delle registrazioni), su macOS (Apple Silicon), che accumula nel tempo
registrazioni della stessa cerchia di colleghi più ospiti occasionali.

## Valore atteso
- Un **progetto** che ricorda le persone: a ogni nuova registrazione l'app **propone** chi parla
  (candidati in ordine + fascia forte/debole/nessuna + estratto audio da ascoltare), l'utente
  conferma o corregge.
- Un file **.md per registrazione** con il parlato attribuito per nome, sempre coerente con i nomi
  attuali (rigenerato, non modificato a mano).
- Tutto **in locale** con modelli open-source: nessun audio o impronta vocale lascia la macchina.

## Scope (v1)
- App **desktop** multipiattaforma (scelta esplicita dell'utente).
- Creare/aprire un progetto; aggiungere registrazioni nel tempo.
- Separazione delle voci + trascrizione IT/EN.
- Assegnare nomi ai gruppi di voce; proposta automatica per persone già note, anche ospiti
  occasionali ricomparsi (promuovibili a ricorrenti).
- Correggere la separazione: unire gruppi, dividere un gruppo, riassegnare singoli segmenti.
- Rinominare una persona → aggiornamento dei .md esistenti (solo il nome, nessuna ri-analisi).
- Eliminare una persona e le sue impronte vocali (dati biometrici di terzi).

## Fuori scope (v1)
- Capitoli e riassunti con LLM locale → **v2**, sopra il trascritto.
- Modifica del testo trascritto dentro l'app.
- Ri-analisi retroattiva delle vecchie registrazioni quando le impronte migliorano.
- Percentuali di confidenza mostrate come probabilità.

## Esito misurabile
- Su una nuova registrazione con persone già note, l'utente assegna tutti i nomi **confermando
  proposte** invece di digitarli, nella maggior parte dei casi.
- Il .md prodotto attribuisce correttamente gli interventi dopo le correzioni dell'utente.

## Rischi / spike (da validare sugli audio reali in `sample/`)
1. Affidabilità del confronto delle impronte vocali tra dispositivi/stanze diverse (soglia).
2. Scelta del modello di separazione voci: pyannote community-1 vs NVIDIA Streaming Sortformer.
3. Trascrizione con cambio di lingua IT/EN: Parakeet v3 vs Whisper large-v3(-turbo).
4. Impacchettare i modelli Python in un'app desktop multipiattaforma.

## Verdetto challenger
RESHAPE — ristretto al differenziatore (identità persistenti per progetto); capitoli/riassunti
rinviati; forma desktop mantenuta per scelta dell'utente.
