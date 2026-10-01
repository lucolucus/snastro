package snastro.parlanti.adattatori.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.parlanti.applicazione.porte.LettoreRegistrazione
import snastro.parlanti.applicazione.porte.ParteDiIncontroParlanti
import snastro.parlanti.applicazione.porte.RegistrazioneVista
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni

/**
 * [LettoreRegistrazione] over Progetto's public read API [CatalogoRegistrazioni] (boundary
 * `registrazione-per-parlanti`, ADR 0002): calls the supplier's `registrazione(id)` and maps its
 * answer field by field into this context's own [RegistrazioneVista] — delegates, never re-decides.
 *
 * ONE-PARTE TRANSITION for [parti] (D-0032, D-0037): Progetto's `parti` is still the unordered read of ADR 0033 §4.1
 * until `catalogo-incontro`, and the order of the Parti is decided only in Progetto's domain (ADR 0033 §7), never
 * here. So an Incontro with ONE Parte is answered as Parte 1, and one with more FAILS CLOSED (an
 * [IllegalStateException], never an order made up here): no Incontro has a second Parte before the I2 import.
 */
public class LettoreRegistrazioneDaProgetto(
    private val catalogo: CatalogoRegistrazioni,
) : LettoreRegistrazione {
    override fun registrazione(id: RegistrazioneId): RegistrazioneVista? =
        catalogo.registrazione(id)?.let { vista ->
            RegistrazioneVista(
                registrazioneId = vista.registrazioneId,
                progettoId = vista.progettoId,
                incontroId = vista.incontroId,
                titolo = vista.titolo,
                riferimentoAudio = vista.riferimentoAudio,
                dataRegistrazione = vista.dataRegistrazione,
                durataMs = vista.durataMs,
            )
        }

    override fun parti(incontroId: IncontroId): List<ParteDiIncontroParlanti>? {
        val parti = catalogo.parti(incontroId) ?: return null
        check(parti.size == 1) {
            "Incontro ${incontroId.valore} con ${parti.size} parti: ordine delle parti non ancora pubblicato da " +
                "Progetto (transizione a una parte fino a catalogo-incontro)"
        }
        return catalogo.registrazione(parti.single())
            ?.let { listOf(ParteDiIncontroParlanti(it.registrazioneId, 1, it.dataRegistrazione)) }
    }
}
