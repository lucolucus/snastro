package snastro.sintesi.applicazione.porte

import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.poi
import snastro.sintesi.dominio.IngressoRiassunto
import snastro.sintesi.dominio.LimiteIngresso
import snastro.sintesi.dominio.Riassumibilita

/**
 * [Riassumibilita] in two passes, shared by the Riassumi command and the Riassunto view so they never disagree
 * (ADR 0037 §2, AC-I50): the size estimate needs the whole labelled input (up to ~540k chars on a 3 h Incontro), so
 * [stima] runs only when every other precondition already holds.
 */
internal fun riassumibilitaInDuePassi(
    modelloInstallato: Boolean,
    stati: List<Pair<Int, StatoParteSintesi>>,
    riassuntoAperto: Boolean,
    stima: () -> Int?,
): Esito<Unit> = Riassumibilita.valuta(modelloInstallato, stati, riassuntoAperto, stimaToken = null)
    .poi { Riassumibilita.valuta(modelloInstallato, stati, riassuntoAperto, stima()) }

/**
 * The token estimate of the whole labelled input ([IngressoRiassunto], no names, ADR 0032) over [parti] in order, each
 * Parte's Segmenti read through [segmenti]; `null` at the first Parte with no Trascritto (the input is not built).
 */
internal fun stimaTokenDi(parti: List<ParteSintesi>, segmenti: (RegistrazioneId) -> List<SegmentoSintesi>?): Int? {
    val lette = parti.map { p -> segmenti(p.registrazioneId)?.map { it.inIngresso(p.registrazioneId) } ?: return null }
    return LimiteIngresso.stimaToken(IngressoRiassunto.costruisci(lette).testo)
}
