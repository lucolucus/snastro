Chip di Fonte: dopo il testo di un elemento del Riassunto, indica chi l'ha detto e quando.

- Anatomia: `VoiceTag` (pallino pieno + nome, o anello + «Voce n» in `ink-muted` quando la voce non ha un nome) seguito dal `timecode` dell'inizio della frase citata (`m:ss` / `h:mm:ss`).
- `label` (13/18), `radius-pill`, fondo `sunken`, nessun bordo. **Non interattiva** in v1: niente hover, niente cursore a mano (fuori scope, brief).
- Più `FonteChip` di uno stesso elemento formano un gruppo che va a capo (`GruppoFonti`), gap `space-2` in entrambe le direzioni; il gruppo segue subito il testo, con lo stesso gap `space-2`.
- Il nome è l'unico elemento che può accorciarsi/andare a capo; il `timecode` mantiene sempre la sua misura naturale.
