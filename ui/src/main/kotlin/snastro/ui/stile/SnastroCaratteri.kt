package snastro.ui.stile

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/**
 * AC-554: the three OFL families bundled as static TTF in `resources/font/` (no network at
 * runtime) — Instrument Sans (interface), Source Serif 4 (reading), JetBrains Mono (data/timecode).
 * Each family's `<Nome>-OFL.txt` sits next to its TTFs.
 */
public val CarattereInterfaccia: FontFamily = FontFamily(
    Font("font/InstrumentSans-Regular.ttf", FontWeight.Normal),
    Font("font/InstrumentSans-Medium.ttf", FontWeight.Medium),
    Font("font/InstrumentSans-SemiBold.ttf", FontWeight.SemiBold),
)

public val CarattereLettura: FontFamily = FontFamily(
    Font("font/SourceSerif4-Regular.ttf", FontWeight.Normal),
    Font("font/SourceSerif4-SemiBold.ttf", FontWeight.SemiBold),
)

public val CarattereDati: FontFamily = FontFamily(
    Font("font/JetBrainsMono-Medium.ttf", FontWeight.Medium),
)
