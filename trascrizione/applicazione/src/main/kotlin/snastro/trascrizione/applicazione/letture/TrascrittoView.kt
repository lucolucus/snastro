package snastro.trascrizione.applicazione.letture

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import java.time.LocalDate

/**
 * Read-model `trascritto-view` (S3, `TrascrittoQuery.vista`): the Trascrizione slice of the
 * `schermata-parte` screen's data, for ONE Parte of an Incontro. Parlante attribution (`nome`, `tipoParlante`,
 * `parlanteId`) is joined in by the presenter from `identificazione-voci`, never here (R1 split, ux-proposal).
 * [parti] is the switcher (every Parte of the Incontro in order), [solaLettura] says a re-run is open on some Parte,
 * [voci] holds only the Voci with Segmenti in this Parte. A view is always of a Parte of its Incontro: [parti] names
 * this Parte as number [numeroParte] (a 1-Parte Incontro: `numeroParte = 1`, `parti` with this one Parte), refused
 * otherwise (`require`, programmer error).
 */
public data class TrascrittoView(
    val registrazioneId: RegistrazioneId,
    val incontroId: IncontroId,
    val titolo: String,
    val dataRegistrazione: LocalDate,
    val durataMs: Long,
    val segmenti: List<SegmentoTrascrittoView>,
    val voci: List<VoceTrascrittoView>,
    val numeroParte: Int,
    val parti: List<ParteRef>,
    val solaLettura: RitrascrizioneInCorso? = null,
) {
    init {
        require(ParteRef(registrazioneId, numeroParte) in parti) {
            "$registrazioneId non e' la Parte $numeroParte di $parti"
        }
    }
}

/** One Parte of the Incontro in [TrascrittoView.parti]: [numero] is 1..N in the Incontro's order. */
public data class ParteRef(val registrazioneId: RegistrazioneId, val numero: Int)

/** A re-run is open on a Parte of the Incontro; [parte] is its number, `null` for a 1-Parte Incontro (as before). */
public data class RitrascrizioneInCorso(val parte: Int?)
