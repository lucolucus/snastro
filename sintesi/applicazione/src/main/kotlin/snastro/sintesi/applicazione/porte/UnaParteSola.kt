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

/** The [StatoParte] of the one Parte (ADR 0033 §4); no Parte (unknown or ceased Incontro) is DA_TRASCRIVERE. */
internal fun LettoreTrascritto.statoDi(parte: RegistrazioneId?): StatoParte =
    parte?.let(::statoParte) ?: StatoParte.DA_TRASCRIVERE
