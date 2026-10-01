package snastro.sintesi.applicazione.porte

import snastro.kernel.RegistrazioneId
import snastro.sintesi.dominio.SegmentoIngresso
import snastro.sintesi.dominio.StatoParte

// TRANSITION (D-0033): today's callers read ONE Parte (parteUnica) through the per-Parte port; these helpers wrap
// it in the Incontro shapes of ADR 0037. The ordered, numbered reads replace them (riassumi-, esegui-riassunto-,
// riassunto-vista-incontro).

/** The one Parte's number. */
internal const val PRIMA_PARTE: Int = 1

/** This Segmento of the Parte [parte] as the input builder labels it. */
internal fun SegmentoSintesi.inIngresso(parte: RegistrazioneId): SegmentoIngresso =
    SegmentoIngresso(parte, segmentoId, voceId, intervallo.inizioMs, testo)

/**
 * The [StatoParte] of [parte] from today's port, in INV-S6's order: no Trascritto ([segmenti] null) first, then an open
 * Elaborazione. `NON_RIUSCITA` is not readable here (`statoParte` comes with porte-sintesi-incontro).
 */
internal fun LettoreTrascritto.statoParte(parte: RegistrazioneId?, segmenti: List<SegmentoSintesi>?): StatoParte =
    when {
        parte == null || segmenti == null -> StatoParte.DA_TRASCRIVERE
        elaborazioneAperta(parte) -> StatoParte.IN_TRASCRIZIONE
        else -> StatoParte.TRASCRITTA
    }
