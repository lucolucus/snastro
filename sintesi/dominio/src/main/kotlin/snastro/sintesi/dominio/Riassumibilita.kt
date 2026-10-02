package snastro.sintesi.dominio

import snastro.kernel.Esito

/**
 * INV-I9 (amends INV-S6), pure (no port, no clock): shared by the Riassumi command and the Riassunto view.
 * Preconditions are evaluated in this order and the first failing one is returned: the model, then the first blocking
 * Parte by its number (INV-I2 order), then an open Riassunto, then the size of the whole input.
 */
public object Riassumibilita {
    /**
     * [stati] are `numero to stato` for every Parte of the Incontro (never empty: an Incontro has ≥ 1 Parte; each
     * numero once);
     * [stimaToken] is [LimiteIngresso.stimaToken] of the whole labelled input, null when it was not built.
     */
    public fun valuta(
        modelloInstallato: Boolean,
        stati: List<Pair<Int, StatoParte>>,
        riassuntoAperto: Boolean,
        stimaToken: Int?,
    ): Esito<Unit> {
        require(stati.isNotEmpty()) { "un Incontro ha almeno una Parte" }
        require(stati.map { it.first }.toSet().size == stati.size) { "numero di Parte ripetuto: $stati" }
        val bloccante = stati.sortedBy { it.first }.firstNotNullOfOrNull { (parte, stato) -> bloccoDi(parte, stato) }
        return when {
            !modelloInstallato -> Esito.Errore(ErroreSintesi.ModelloNonInstallato)
            bloccante != null -> Esito.Errore(bloccante)
            riassuntoAperto -> Esito.Errore(ErroreSintesi.RiassuntoGiaAperto())
            stimaToken != null && stimaToken > LimiteIngresso.LIMITE_TOKEN ->
                Esito.Errore(ErroreSintesi.IngressoTroppoLungo(stimaToken, LimiteIngresso.LIMITE_TOKEN))
            else -> Esito.Ok(Unit)
        }
    }

    /** Why Parte [parte] blocks the Riassunto, null when it is [StatoParte.TRASCRITTA]. */
    private fun bloccoDi(parte: Int, stato: StatoParte): ErroreSintesi? = when (stato) {
        StatoParte.DA_TRASCRIVERE -> ErroreSintesi.PartiNonTrascritte(parte)
        StatoParte.IN_TRASCRIZIONE -> ErroreSintesi.ElaborazioneGiaAperta(parte)
        StatoParte.NON_RIUSCITA -> ErroreSintesi.PartiFallite(parte)
        StatoParte.TRASCRITTA -> null
    }
}
