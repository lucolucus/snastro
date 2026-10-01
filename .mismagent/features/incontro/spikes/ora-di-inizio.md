# Spike ora-di-inizio — evidenza (2026-10-01)

**Domanda.** L'`OraDiInizio` di una Registrazione (l'ora in cui è partita la registrazione) si può leggere dai metadati
dei file reali dell'utente? Quale campo usare, quanto è affidabile, e quando un valore va scartato (meglio vuoto che
sbagliato)?

**Input.** Lo stesso incontro reale di [voci-tra-parti](voci-tra-parti.md), in due file contigui (conferma dell'utente).
Entrambi vengono da Memo Vocali su iPhone (`©too` = `com.apple.VoiceMemos (iPhone Version 27.0 (Build 24A437))`) e
sono arrivati sul Mac via AirDrop (`com.apple.quarantine` = `sharingd`):
- parte 1 `New Recording 4.m4a`, 18:55, AAC 48 kHz 63 kb/s;
- parte 2 `Via Roquel.m4a`, 75:11, AAC 44,1 kHz 225 kb/s.

Ci sono due copie: `snastro/sample/` (ricevuta via AirDrop il 23/09 alle 00:06–00:08) e
`snastro-wt/spike-incontro/audio/` (copiata il 30/09 alle 17:46).

Verità nota: la parte 1 viene prima della parte 2. Se sono contigue, la parte 2 parte almeno 18:55 dopo l'inizio della
parte 1. La data e l'ora esatte non sono note: vanno confermate dall'utente.

**Metodo.** Il prototipo `SpikeOraDiInizioTest` si trova in `:audio`, sul branch `spike/ora-di-inizio`, con
`@Tag("spike")`. Si lancia a mano, fuori dal gate:
`SNASTRO_SPIKE_ORA_DIR=<dir> [SNASTRO_SPIKE_ORA_ORDINE=a|b] ./gradlew :audio:spikeOraDiInizio`.

Per ogni file legge tre gruppi di campi:
1. **i metadati di FFmpeg**, cioè quello che `SondaFfmpeg` vede già (ADR 0005);
2. **i box ISO-BMFF**, letti a mano: `mvhd`/`mdhd` `creation_time` (secondi dal 1904, UTC), `moov/udta/date`,
   `©day`, la chiave `mdta` `com.apple.quicktime.creationdate`, e il chunk `bext` dei WAV;
3. **i tempi del file**: `birth`, `mtime` e `mtime − durata`.

Ogni valore è riportato in UTC e nell'ora locale (`Europe/Rome`). Il prototipo applica poi la regola proposta e stampa
l'ordine e lo scarto fra gli inizi. Nessuna dipendenza nuova.

Il prototipo ha visto 0 risultati da `mdls`, perché Spotlight non indicizza queste cartelle: niente
`kMDItemRecordingDate`.

## Risultati

Ora locale = Europe/Rome (CEST, UTC+2).

| File | Campo | Valore (UTC) | Ora locale | Torna con la verità? |
|---|---|---|---|---|
| parte 1 | `moov/udta/date` (Memo Vocali) | 2026-09-21T20:22:13Z | 21/09 22:22:13 | **sì** |
| parte 1 | `mvhd`/`mdhd` `creation_time` (= `creation_time` di FFmpeg) | 2026-09-21T20:22:13Z | 21/09 22:22:13 | sì (uguale a `date`) |
| parte 1 | `mvhd` `modification_time` | 2026-09-22T22:06:18Z | 23/09 00:06:18 | no: è il momento della condivisione |
| parte 1 | file `birth` = `mtime` (sample) | 2026-09-22T22:06:18Z | 23/09 00:06:18 | no: ricezione AirDrop |
| parte 1 | `mtime − durata` (sample) | 2026-09-22T21:47:22Z | 22/09 23:47:22 | no |
| parte 1 | `mtime − durata` (copia del 30/09) | 2026-09-30T15:27:55Z | 30/09 17:27:55 | no: la copia azzera i tempi |
| parte 2 | `moov/udta/date` (Memo Vocali) | 2026-09-21T20:44:22Z | 21/09 22:44:22 | **sì**: 22:09 dopo la parte 1, cioè 3:14 di pausa dopo la sua fine |
| parte 2 | `mvhd`/`mdhd` `creation_time` (= `creation_time` di FFmpeg) | 2026-09-22T22:05:47Z | **23/09 00:05:47** | **NO**: riscritto 2 min prima della condivisione, oltre un giorno dopo |
| parte 2 | `mvhd` `modification_time` | 2026-09-22T22:07:53Z | 23/09 00:07:53 | no |
| parte 2 | file `birth` = `mtime` (sample) | 2026-09-22T22:07:53Z | 23/09 00:07:53 | no |
| parte 2 | `mtime − durata` (sample) | 2026-09-22T20:52:41Z | 22/09 22:52:41 | no, e **ordine sbagliato**: viene prima della parte 1 (23:47) |
| parte 2 | `mtime − durata` (copia del 30/09) | 2026-09-30T14:31:40Z | 30/09 16:31:40 | no, e **ordine sbagliato** |
| entrambi | `©day`, `mdta com.apple.quicktime.creationdate` | assenti | — | — |
| entrambi | WAV `bext` | non applicabile (nessun WAV nel campione) | — | non verificato |

FFmpeg **non espone** `udta/date`. La mappa dei metadati contiene solo `creation_time` (che viene da `mvhd`),
`encoder`, `voice-memo-uuid`, `major_brand`, `minor_version` e `compatible_brands`. Per leggere `date` serve quindi un
lettore di box, che sono poche decine di righe.

**Ordine con la regola proposta:** parte 1 (22:22:13), poi parte 2 (22:44:22), su entrambe le copie: **corretto**. Lo
scarto fra gli inizi è 22:09, cioè 18:55 di durata della parte 1 più 3:14 di pausa. Questo è compatibile con "fermo e
riparto", ma i 3 minuti di pausa vanno confermati dall'utente.

## Conclusioni per il modello

1. **Regola di lettura proposta: `moov/udta/date`, altrimenti vuoto.**
   - `date` è ISO-8601 con fuso (`…Z`) e lo scrive Memo Vocali all'inizio della registrazione.
   - Lettura "inizio" e non "fine": se fosse la fine, le due parti si sovrapporrebbero di 56 minuti, il che è impossibile
     per due registrazioni dello stesso telefono.
   - Si converte nell'ora locale della macchina che importa, perché `OraDiInizio` è un orario da parete senza fuso
     (ADR 0033).
   - Plausibilità come per `DataRegistrazione`: dopo il 1970 e non nel futuro, altrimenti vuoto.
   - Se c'è anche `com.apple.quicktime.creationdate`, che ha il suo offset, può essere un secondo candidato. Però non
     compare in nessun file reale, quindi non è verificato.
2. **`mvhd` `creation_time` non è affidabile.** Sulla parte 2 è stato riscritto al momento della condivisione: oltre
   un giorno dopo, e anche con una data diversa. Usato da solo, sarebbe proprio il caso "valore che NON dobbiamo
   credere". Probabile causa: la parte 2 è stata ricodificata (bitrate e frequenza diversi dalla parte 1, per esempio
   per un ritaglio o un "migliora registrazione"). È un'ipotesi, non verificata.
   - Quando c'è `udta/date`, `mvhd` va ignorato.
   - Quando `udta/date` manca (altri registratori), `mvhd` è l'unico campo che resta. Va adottato solo dopo un campione
     reale di quel dispositivo; fino ad allora, vuoto.
3. **I tempi del file non servono mai.** AirDrop e la copia li azzerano, e `mtime − durata` dà l'**ordine sbagliato**
   sull'Incontro reale in tutte e due le copie. Restano esclusi anche come ripiego.
4. **Ripiego** (già fissato dal modello): `OraDiInizio` vuota, ultima nell'ordine, poi l'ordine di import, poi l'id
   ([INV-I2]). L'utente la corregge con `ModificaOraDiInizio`.
5. **Conseguenza su `DataRegistrazione`, oltre questo spike.** L'AC-364 di oggi prende la data dal `creation_time` di
   FFmpeg, cioè da `mvhd`. Per la parte 2 dà quindi **23/09** invece di **21/09**.
   - Con la regola proposta la parte 2 avrebbe data 23/09 e ora 22:44: una coppia incoerente.
   - `OrdineDelleParti` ordina prima per `DataRegistrazione`. Qui l'ordine esce giusto per caso, ma con una parte 2
     ricodificata prima della parte 1 verrebbe sbagliato.
   - Proposta: data e ora prese dallo **stesso istante** (`udta/date`, poi `mvhd` per la sola data come oggi). È una
     modifica di `SondaFfmpeg`/`dataRegistrazione` (AC-364) che va decisa nell'ADR.

## Cosa manca per chiudere (criterio di chiusura)

- **Altri formati e dispositivi dell'utente.** Il campione copre solo Memo Vocali su iPhone, `.m4a`, via AirDrop.
  Restano da verificare, se l'utente li usa:
  - Memo Vocali su Mac o via iCloud;
  - registratori Android (`mvhd` / `©day`);
  - registratori portatili WAV (`bext`: data e ora locali senza fuso, quindi da prendere così come sono);
  - QuickTime Player (`com.apple.quicktime.creationdate`);
  - file esportati dall'app «File» o da un'email, invece che via AirDrop.

  Il prototipo legge già `bext` e `mdta`, ma il parser non è provato su file reali.
- **Conferma dell'utente** della verità di fondo: l'incontro è del 21/09 sera, verso le 22:22 e 22:44 ora italiana?
  C'è stata una pausa di circa 3 minuti tra le parti?
- **L'ADR** con la regola di lettura e con la decisione su `DataRegistrazione` (punto 5).

**Stato: evidenza raccolta su un dispositivo (iPhone/Memo Vocali). La chiusura attende la conferma dell'utente e gli
eventuali altri formati.**
