Snastro trascrive le riunioni e ricorda chi parla. Il prodotto è **il testo di chi ha detto cosa**, quindi l'interfaccia intorno resta sobria. Il colore è riservato alle voci delle persone e ai pochi momenti in cui l'app ti chiede qualcosa.

## Principi

- **Prima quello che mi serve, poi il resto.** In ogni schermata metti in alto ciò che richiede un'azione: da trascrivere, voci senza nome, errori. Numeri e liste vengono dopo (vedi `ProjectSummary`, `Banner`).
- **Il testo si legge, l'interfaccia si usa.** Il parlato è in `transcript` e i riassunti in `abstract`, entrambi con il carattere da lettura. Tutto il resto (bottoni, stati, metadati) usa il carattere d'interfaccia. Non mescolarli.
- **Il colore appartiene alle voci.** `voice-1` … `voice-8` sono gli unici colori vivi di una schermata. `accent` è uno solo e marca l'azione principale, la testina di riproduzione e ciò che è in corso. Non usarlo per decorare.
- **Lo stato si vede dalla forma, non solo dal colore.** Una voce senza nome ha il pallino vuoto (`sn-dot ring`), una persona nominata il pallino pieno. Ogni stato di elaborazione ha la sua icona e la sua parola (`StatusChip`). Nessuna informazione passa dal solo colore.
- **Tutto in locale, detto una volta.** La promessa di privacy compare dove conta (import, trascrizione, piede della barra laterale). Non ripeterla ovunque.

## Contenuti e tono

- Tutta l'interfaccia è in italiano. Dai del **tu**, con frasi brevi e attive. L'app parla in prima persona solo quando fa qualcosa per te: «Sposterò 23 frasi», «le conto io».
- Chiama le cose come le chiama l'utente: *registrazione*, *voce*, *persona*, *frase*, *riassunto*. Evita *segmento*, *diarizzazione* ed *embedding*. Le fasi si chiamano «Preparazione audio», «Separazione voci», «Trascrizione», «Allineamento».
- Un bottone dice cosa succede: «Trascrivi», «Applica», «È Paolo», «Ritrascrivi». La conferma usa lo stesso verbo: «23 frasi spostate».
- Un errore dice cosa è successo e cosa fare: «Il file audio non si apre (formato non riconosciuto)», più «Riprova». Niente scuse, niente codici.
- Le maiuscole seguono l'italiano: solo la prima lettera della frase e i nomi propri. Le etichette `overline` sono maiuscole via CSS, mai scritte in maiuscolo nel testo.
- Niente emoji, né come icone né come decorazione. I glifi ▶ ⏸ vanno sostituiti dalle icone del set.

## Numeri, tempi e date

- **Timecode e durate** sono in `timecode` (mono, cifre tabulari). Sotto l'ora si scrive `m:ss` («3:12», «48:03»), dall'ora in su `h:mm:ss` («1:04:12»). Niente zeri iniziali sulle ore («01:15» diventa «1:15»). Questo chiude la divergenza AC-179/AC-203.
- **Nella prosa e nei riassunti** le durate si scrivono per esteso: «1 h 04 min», «52 min».
- **Le date** sono `gg/mm/aaaa`. Nelle frasi si usa la forma lunga: «dal 3 febbraio 2026».
- **Somiglianza delle voci:** mai un numero o una percentuale. Si usano solo le fasce `forte`, `debole` e `nessuna` (`FasciaMeter`).
- **Percentuali** solo per la quota di parlato (`SpeakerShare`), arrotondate all'intero.

## Colore

- Fondi, dal più esterno all'interno: `ground` è la finestra e la barra laterale, `surface` l'area di contenuto, `raised` card, player, menu e dialoghi. `sunken` serve per i pozzetti (traccia della barra audio, drop zone, parte vuota dei misuratori).
- Testo: `ink` per il testo principale, `ink-muted` per metadati e didascalie. Entrambi superano 4,5:1 su `surface` e `raised` in chiaro e in scuro. `ink-faint` è solo per disabilitati e placeholder.
- Linee: `line` per i separatori decorativi. `line-strong` per i bordi dei controlli da trovare (campi, bottoni secondari), almeno 3:1.
- `accent` (ambra nastro, il colore del nastro magnetico) si usa **solo come riempimento**: bottone principale, play, parte già ascoltata della barra audio, pallino «in corso». Il testo sopra è `on-accent`, scuro in entrambi i temi, perché il bianco sull'ambra non si legge (2,9:1). Al passaggio del mouse il riempimento diventa `accent-hover`, più chiaro. Testo e icone in accento su `surface`, `raised` o `accent-soft` usano `accent-ink` (link come Annulla e Salta, check del completato, puntina, icona della sezione attiva, testina). `accent-soft` è il fondo di ciò che sta suonando o è selezionato. `focus` è l'anello di focus: 2px pieni con 2px di distacco.
- Stati: `danger` + `danger-soft` (cremisi) per ciò che è fallito o distruttivo, `warning` + `warning-soft` (blu acciaio) per ciò che ti limita senza fallire. Sono volutamente lontani dall'ambra, così un problema non si confonde con il colore del marchio. `warning` copre la sola lettura durante una ritrascrizione, il file audio mancante e le voci da identificare. Il completato non ha un colore suo: è lo stato normale, quindi `ink-muted` con l'icona check in `accent-ink`.
- Voci: il colore dipende dal numero della voce (`voice-n`) e resta stabile per tutta la vita della trascrizione. La persona nominata eredita il colore della sua voce in quella registrazione. Il colore di voce va su pallini, barre di quota e corsie della barra audio, **mai sul testo**: il nome accanto resta in `ink`. Dopo l'ottava voce la palette ricomincia da `voice-1`.

## Tipografia

- Tre famiglie, tutte con licenza OFL e da includere nell'app come file: **Instrument Sans** (`ui`), **Source Serif 4** (`reading`), **JetBrains Mono** (`mono`).
- Scala dell'interfaccia: `display` (26/32, uno per schermata), `title` (18/24), `heading` (14/20, titoli di riga e nomi), `body` (14/20), `label` (13/18, bottoni e chip), `caption` (12/16, metadati), `overline` (11/14, etichette di gruppo), `figure` (22/26, i pochi numeri di un riassunto).
- Lettura: `transcript` (16/26) e `abstract` (15/24), con una misura massima di `reading-measure` (68ch).
- Dati: `timecode` (12/16, mono 500).

## Spazi, forme, profondità

- La griglia è da 4px: `space-1` 4 · `space-2` 8 · `space-3` 12 · `space-4` 16 · `space-5` 24 · `space-6` 32 · `space-7` 48. Le schermate hanno `space-5` in verticale e `space-6` di margine laterale. Card e player hanno `space-4` di padding. Fra le sezioni c'è `space-5`.
- Raggi: `radius-control` (6) per bottoni, campi e chip · `radius-card` (10) per card, player, banner e drop zone · `radius-dialog` (14) per menu e dialoghi · `radius-pill` per chip di stato, play e pallini.
- Le card si distinguono con un filo `line`, senza ombra. `shadow-pop` è solo per menu, dialoghi e la barra di selezione, cioè ciò che sta sopra il contenuto.
- Misure: controlli `control-m` (34) e `control-s` (28, dentro righe e card). Icone `icon-m` (20) e `icon-s` (16). Barra laterale `sidebar-width` (232). Pannello destro `panel-width` (320).

## Layout

- La finestra ha due colonne: barra laterale (progetto + Registrazioni + Parlanti; stato dei modelli in fondo) e contenuto.
- La **home del progetto** (`ScreenProject`) mostra titolo e azione Importa, poi due card affiancate («Da fare» e il riassunto del progetto), poi l'elenco delle registrazioni.
- La **pagina della registrazione** esiste in ogni stato (`ScreenImported`, `ScreenProcessing`, `ScreenRecording`, `ScreenIdentify`). Contiene intestazione, barra audio a tutta larghezza, poi il corpo a sinistra e il pannello destro (`panel-width`) con le schede Riassunto / Voci.

## Movimento

- Poco e funzionale. Il pallino dello stato «in corso» pulsa (1,6 s). Hover e pressione cambiano solo il colore, in 120 ms. Menu e dialoghi appaiono con una dissolvenza di 150 ms.
- Con `prefers-reduced-motion` (o «Riduci movimento» su macOS) non si anima nulla.

## Iconografia

- Il set è in **assets/Icons**: 29 icone a tratto, griglia 24px, tratto 1,75, estremi arrotondati. Play e pausa sono piene. Sostituiscono i glifi ▶ ⏸ e le emoji attuali.
- Le icone si colorano come il testo che accompagnano: `ink`, `ink-muted` a riposo nella barra laterale, `accent-ink` sulla voce di menu attiva. Nel web `sn-icon i-<nome>` usa `currentColor`. In Compose ogni SVG diventa un `ImageVector` colorato con `tint`.
- Accanto a un'etichetta usa `icon-s` (bottoni, chip, righe). Da sole usa `icon-m` (barra laterale, bottoni-icona). Un bottone con sola icona ha sempre il tooltip.
- **Non c'è un logo.** Il nome si scrive in `ui` 600. L'icona `reel` (bobina) indica il progetto nella barra laterale e può fare da base per l'icona dell'app.

## Implementazione in Compose

- `SnastroTema` sostituisce il Material di default. Serve un `ColorScheme` chiaro e uno scuro presi da questi token (primary = `accent`, onPrimary = `on-accent`, background = `ground`, surface = `surface`, surfaceContainer = `raised`, outline = `line-strong`, outlineVariant = `line`, error = `danger`) e una `CompositionLocal` per `voice-1…8` e per i colori che Material non ha (`accent-ink`, `accent-soft`, `warning`, `warning-soft`).
- La tipografia va in `Typography` e in una `CompositionLocal` per `transcript`, `abstract` e `timecode`. I font sono file in `resources`.
- `Palette.kt` (i colori delle voci) passa ai valori `voice-n` di questo sistema, con la variante scura.
- Il tema chiaro o scuro segue quello di macOS.
