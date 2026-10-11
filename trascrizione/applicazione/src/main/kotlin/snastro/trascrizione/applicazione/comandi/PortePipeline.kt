package snastro.trascrizione.applicazione.comandi

import snastro.trascrizione.applicazione.porte.Allineatore
import snastro.trascrizione.applicazione.porte.DecodificatoreAudio
import snastro.trascrizione.applicazione.porte.Diarizzatore
import snastro.trascrizione.applicazione.porte.LettoreRegistrazione
import snastro.trascrizione.applicazione.porte.SegnalatoreFase
import snastro.trascrizione.applicazione.porte.SegnalatoreGuasti

/**
 * Every port the local pipeline reads (ADR 0004) while running one Elaborazione OUTSIDE any
 * transaction, grouped so [EseguiProssimaElaborazioneServizio]'s constructor stays short; not a port
 * itself, no boundary of its own — wiring only (`:avvio`). [guasti] takes the cause of every port fault the
 * Elaborazione fails with (ADR 0042).
 */
public class PortePipeline(
    public val registrazioni: LettoreRegistrazione,
    public val decodificatore: DecodificatoreAudio,
    public val diarizzatore: Diarizzatore,
    public val allineatore: Allineatore,
    public val segnalatore: SegnalatoreFase,
    public val guasti: SegnalatoreGuasti,
)
