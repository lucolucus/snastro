package snastro.avvio.r1

import snastro.trascrizione.applicazione.comandi.DividiVoceServizio
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmentoServizio
import snastro.trascrizione.applicazione.comandi.UnisciVociServizio

/**
 * The Revisione commands of the open project, wired with `eventi.unitaDiLavoro` (AC-355). R1 has no
 * Revisione UI (S3 is read-only, delta 2026-09-24-packaging): they stay wired so their events keep
 * S2 and the Documento up to date (AC-354/AC-356), and the R2 screen plugs in without re-wiring.
 */
internal class ComandiRevisione(
    val unisciVoci: UnisciVociServizio,
    val dividiVoce: DividiVoceServizio,
    val riassegnaSegmento: RiassegnaSegmentoServizio,
)
