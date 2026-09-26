package snastro.sintesi.dominio

import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId

/**
 * INV-S6, pure (no port, no clock): shared by the Riassumi command and the Riassunto view. Preconditions are
 * evaluated in this order and the first failing one is returned.
 */
public object Riassumibilita {
    /** [stimaToken] is [LimiteIngresso.stimaToken] of the labelled input; null when there is no Trascritto. */
    @Suppress("LongParameterList") // one flag per INV-S6 precondition, pinned (agg-riassunto)
    public fun valuta(
        registrazioneId: RegistrazioneId,
        modelloInstallato: Boolean,
        trascrittoPresente: Boolean,
        elaborazioneAperta: Boolean,
        riassuntoAperto: Boolean,
        stimaToken: Int?,
    ): Esito<Unit> = when {
        !modelloInstallato -> Esito.Errore(ErroreSintesi.ModelloNonInstallato)
        !trascrittoPresente -> Esito.Errore(ErroreSintesi.TrascrittoNonDisponibile(registrazioneId))
        elaborazioneAperta -> Esito.Errore(ErroreSintesi.ElaborazioneGiaAperta(registrazioneId))
        riassuntoAperto -> Esito.Errore(ErroreSintesi.RiassuntoGiaAperto(registrazioneId))
        stimaToken != null && stimaToken > LimiteIngresso.LIMITE_TOKEN ->
            Esito.Errore(ErroreSintesi.RegistrazioneTroppoLunga(stimaToken, LimiteIngresso.LIMITE_TOKEN))
        else -> Esito.Ok(Unit)
    }
}
