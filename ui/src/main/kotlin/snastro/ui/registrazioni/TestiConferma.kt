package snastro.ui.registrazioni

import snastro.ui.testi.MESSAGGIO_CONFERMA_RITRASCRIVI
import snastro.ui.testi.MESSAGGIO_CONFERMA_RITRASCRIVI_PARTE
import snastro.ui.testi.titoloConfermaRitrascrivi
import snastro.ui.testi.titoloConfermaRitrascriviParte

/** The title and body of a row's 'Ritrascrivi' confirmation. */
internal data class TestiConferma(val titolo: String, val messaggio: String)

/**
 * AC-I76 (ADR 0035 §8): a Parte of an Incontro with 2+ Parti ([RigaRegistrazione.parte]) names the Parte and the
 * Incontro and says what is lost/kept; every other row keeps today's text (INV-I3).
 */
internal fun RigaRegistrazione.testiConfermaRitrascrivi(): TestiConferma =
    parte?.let {
        TestiConferma(
            titoloConfermaRitrascriviParte(it.numero, it.titoloIncontro),
            MESSAGGIO_CONFERMA_RITRASCRIVI_PARTE,
        )
    } ?: TestiConferma(titoloConfermaRitrascrivi(titolo), MESSAGGIO_CONFERMA_RITRASCRIVI)
