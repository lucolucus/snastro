package snastro.sintesi.applicazione.porte

import snastro.kernel.RegistrazioneId

/**
 * In-memory [LettoreTrascritto] over fixed Published Language data (passes [LettoreTrascrittoContratto]).
 * [trascritti] holds each Registrazione's current Trascritto, [aperte] the Registrazioni whose latest
 * Elaborazione is in_attesa or in_corso, [fallite] those whose latest Elaborazione is fallita. The segmenti are
 * returned by segmentoId whatever order they were given in, like the supplier.
 */
public class LettoreTrascrittoFinta(
    private val trascritti: Map<RegistrazioneId, List<SegmentoSintesi>> = emptyMap(),
    private val aperte: Set<RegistrazioneId> = emptySet(),
    private val fallite: Set<RegistrazioneId> = emptySet(),
) : LettoreTrascritto {
    override fun segmenti(r: RegistrazioneId): List<SegmentoSintesi>? =
        trascritti[r]?.sortedBy { it.segmentoId.numero }

    override fun statoParte(r: RegistrazioneId): StatoParteSintesi = when (r) {
        in aperte -> StatoParteSintesi.IN_TRASCRIZIONE
        in trascritti -> StatoParteSintesi.TRASCRITTA
        in fallite -> StatoParteSintesi.NON_RIUSCITA
        else -> StatoParteSintesi.DA_TRASCRIVERE
    }
}
