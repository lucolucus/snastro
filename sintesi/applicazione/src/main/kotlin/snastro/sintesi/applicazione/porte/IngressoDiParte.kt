package snastro.sintesi.applicazione.porte

import snastro.kernel.RegistrazioneId
import snastro.sintesi.dominio.SegmentoIngresso

/** This Segmento of the Parte [parte] as the input builder labels it. */
internal fun SegmentoSintesi.inIngresso(parte: RegistrazioneId): SegmentoIngresso =
    SegmentoIngresso(parte, segmentoId, voceId, intervallo.inizioMs, testo)
