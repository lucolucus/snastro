La barra audio della pagina di una registrazione: controlli di riproduzione, corsie delle voci, tempo.

- **Corsie:** un segmento colorato `voice-n` per ogni turno di parola, così si vede chi parla lungo tutto l'audio. Prima della trascrizione le corsie sono sostituite dalla forma d'onda.
- **Controlli:** indietro e avanti di 10 s, play/pausa, velocità (1×, 1,25×, 1,5×, 2×). Tempo corrente e durata in `timecode`.
- **Il consumatore fornisce:** durata, posizione, turni `[voce, inizio, fine]`, disponibilità dell'audio.
- **Audio mancante:** controlli attenuati e disabilitati, con una riga in `warning` sotto la barra. Il testo resta leggibile.
