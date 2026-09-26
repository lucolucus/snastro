package snastro.sintesi.applicazione.letture

import java.time.Instant

/**
 * AC-S106: the Registrazione's open (non-`pronto`, non-`fallito`) Riassunto, if any. No queue position
 * here (ADR 0023 §4, arbitrated): the presenter joins it in from `PosizioniNellaCoda`.
 */
public sealed interface RichiestaApertaVista {
    public data class InAttesa(val richiestoAlle: Instant) : RichiestaApertaVista

    public data class InCorso(val avviatoIl: Instant) : RichiestaApertaVista
}
