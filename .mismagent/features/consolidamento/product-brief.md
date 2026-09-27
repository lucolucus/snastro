# Product brief — consolidamento

## Perché
Dopo R3 (Sintesi) la revisione di design del codice (`analisi-design.md`, 2026-09-27) ha trovato un nucleo
esagonale sano e bordi disordinati. Il debito si concentra in tre punti che ADR 0028–0030 decidono:

1. **Pattern tecnici reinventati blocco per blocco.** Due worker "canale conflated + backoff" identici, senza log e con
   `runCatching` attorno a `CancellationException`/`Error`; sette scope costruiti a mano; una coda che inghiotte ogni
   `Throwable`; circa 14 copie private di `attendiFinche` e circa 40 `Thread.sleep` nei test, da cui i flake noti
   (AC-417, 459, 479, 536). → [ADR 0028](../../decisions/0028-librerie-tecniche-supporto.md): `:supporto` e `:supporto-test`.
2. **Ogni transazione è `BEGIN IMMEDIATE`, anche le letture**, e due `trova` multi-tabella leggono senza snapshot.
   I lettori si mettono in coda dietro gli scrittori fino a 5 s. → [ADR 0029](../../decisions/0029-lettura-coerente-deferred.md):
   la porta `LetturaCoerente` (DEFERRED + `query_only`).
3. **La radice di composizione è stratificata per release (R0→R3)** anche se si spedisce solo R3: shell triplicate,
   downcast a catena, adattatori istanziati più volte, ordine degli abbonati implicito, presenter con parametri
   nullable usati come feature flag. → [ADR 0030](../../decisions/0030-composizione-unica-per-contesto.md): una sola
   composizione per contesto; R0–R2 ritirate come codice.

## Valore atteso
- **Nessun cambiamento visibile all'utente**: l'app R3 si comporta come oggi.
- Gate più affidabile: i flake noti diventano deterministici, nessun test si blocca.
- Letture che non aspettano gli scrittori; nessuna lettura strappata di un aggregato multi-tabella.
- Errori dei worker e della coda segnalati, mai inghiottiti né ritentati in silenzio.
- La parte di codice più toccata da ogni feature (la composizione) diventa lineare: un ordine dichiarato, niente cast.
- Chiude in gruppo una parte delle voci aperte di `features/sintesi/pre-release.md` (mappa nei blocchi).

## Fuori scopo
- Nuove funzioni per l'utente; nuovi contesti; modifiche al modello di dominio.
- Gli altri passi dell'analisi (Published Language senza dominio, eventi `PubblicazioneEventi`, testFixtures fuori da
  `:avvio` M2/S5, errori a tre livelli, nativi): restano per dopo.

## Metodo
Rilasci funzionali visibili invariati: questo è il rilascio `R3c`, dimostrato da app invariata + gate verde + voci del
pre-release chiuse. Un blocco = un PR con `./gradlew check` verde.
