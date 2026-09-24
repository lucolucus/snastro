# Manifest delta — allineamento per turno (ADR 0015)
allineatore: related_adrs +0013 +0014 +0015; gated_by satisfied by ADR 0015.
- REWRITE AC-248: turno > 25 s spezzato con il Vad; nessuna chiamata ASR > 25 s (intervalli Vad > 25 s ritagliati) (regola 3)
- KEEP AC-246, AC-247
- NEW: unione turni stessa voce con distanza < 1000 ms (o sovrapposti); voce diversa in mezzo impedisce l'unione; voci diverse mai unite/tagliate/eliminate (regola 1)
- NEW: turno unito < DURATA_MINIMA_TURNO_MS → nessuna chiamata ASR né SegmentoGrezzo (regola 2; DURATA_MINIMA_TURNO_MS = 500, confermato dall utente 2026-09-24)
- NEW: intervallo Vad < 200 ms → nessuna chiamata ASR (regola 4)
- NEW: testo vuoto/spazi → nessun SegmentoGrezzo; tutti vuoti → lista vuota senza eccezioni (regola 7)
- NEW: un SegmentoGrezzo per turno unito, intervallo del turno anche se spezzato; testo = concatenazione con spazio dei pezzi non vuoti (regole 6, 8)
- NEW: output ordinato per (inizio, voceIndice, fine), deterministico (regola 10)
- NEW: costanti GAP_UNIONE_TURNI_MS=1000, DURATA_MINIMA_TURNO_MS, DURATA_MASSIMA_CHIAMATA_MS=25000, DURATA_MINIMA_CHIAMATA_MS=200 definite una sola volta
esegui-elaborazione: related_adrs +0015
- NEW: Allineatore restituisce lista vuota → fallita 'nessun parlato rilevato', nessun Trascritto (estende AC-72)
- NEW: segmenti grezzi sovrapposti di voci diverse arrivano tutti nel Trascritto (INV-7, regola 9)
riconoscitore-sherpa: NEW [@modelli] modello caricato una volta per Elaborazione, riusato, rilasciato a fine Elaborazione
vad-silero: NEW configurazione Silero threshold 0.5, minSilence 0.25 s, maxSpeech 25 s
