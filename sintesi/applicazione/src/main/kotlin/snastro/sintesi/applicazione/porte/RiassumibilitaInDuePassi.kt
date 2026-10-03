package snastro.sintesi.applicazione.porte

import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.poi
import snastro.sintesi.dominio.ErroreSintesi
import snastro.sintesi.dominio.IngressoRiassunto
import snastro.sintesi.dominio.LimiteIngresso
import snastro.sintesi.dominio.Riassumibilita

/**
 * [Riassumibilita] in two passes, shared by the Riassumi command and the Riassunto view so they never disagree
 * (ADR 0037 §2, AC-I50): the size estimate needs the whole labelled input (up to ~540k chars on a 3 h Incontro), so
 * [stima] runs only when every other precondition already holds; an [Esito.Errore] of [stima] is the verdict.
 */
internal fun riassumibilitaInDuePassi(
    modelloInstallato: Boolean,
    stati: List<Pair<Int, StatoParteSintesi>>,
    riassuntoAperto: Boolean,
    stima: () -> Esito<Int>,
): Esito<Unit> = Riassumibilita.valuta(modelloInstallato, stati, riassuntoAperto, stimaToken = null)
    .poi { stima() }
    .poi { token -> Riassumibilita.valuta(modelloInstallato, stati, riassuntoAperto, token) }

/**
 * The token estimate of the whole labelled input ([IngressoRiassunto], no names, ADR 0032) over [parti] in order, each
 * Parte's Segmenti read through [segmenti]. A Parte whose Segmenti read `null` (its state said TRASCRITTA, but a
 * concurrent re-run left no Trascritto in this read) is [ErroreSintesi.PartiNonTrascritte], the first one in order:
 * the size check is never skipped, and the command and the view agree on the same reads.
 */
internal fun stimaTokenDi(
    parti: List<ParteSintesi>,
    segmenti: (RegistrazioneId) -> List<SegmentoSintesi>?,
): Esito<Int> {
    val lette = parti.map { p ->
        segmenti(p.registrazioneId)?.map { it.inIngresso(p.registrazioneId) }
            ?: return Esito.Errore(ErroreSintesi.PartiNonTrascritte(p.numero))
    }
    return Esito.Ok(LimiteIngresso.stimaToken(IngressoRiassunto.costruisci(lette).testo))
}
