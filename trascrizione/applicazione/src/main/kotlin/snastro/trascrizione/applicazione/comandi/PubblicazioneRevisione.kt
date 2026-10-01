// The root's Revisione events (`EventoRevisione`, :trascrizione:dominio) as their Published Language copies.
@file:Suppress("MatchingDeclarationName", "Filename")

package snastro.trascrizione.applicazione.comandi

import snastro.trascrizione.applicazione.eventi.SegmentoConfermato
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.eventi.VoceDivisa
import snastro.trascrizione.applicazione.eventi.VociUnite
import snastro.trascrizione.dominio.EventoRevisione

internal fun EventoRevisione.VociUnite.pubblicato(): VociUnite = VociUnite(incontroId, sopravvissuta, rimossa)

internal fun EventoRevisione.VoceDivisa.pubblicato(): VoceDivisa = VoceDivisa(incontroId, origine, nuova, spostati)

internal fun EventoRevisione.SegmentoRiassegnato.pubblicato(): SegmentoRiassegnato =
    SegmentoRiassegnato(incontroId, segmento, da, a, daRimossa, aNuova)

internal fun EventoRevisione.SegmentoConfermato.pubblicato(): SegmentoConfermato =
    SegmentoConfermato(incontroId, segmento, confermato)
