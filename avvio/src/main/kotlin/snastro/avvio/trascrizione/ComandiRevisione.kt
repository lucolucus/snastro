package snastro.avvio.trascrizione

import snastro.trascrizione.applicazione.comandi.DividiVoceServizio
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmentoServizio
import snastro.trascrizione.applicazione.comandi.UnisciVociServizio

/**
 * The Revisione commands of the open project (S3's Revisione UI), wired with the dispatcher's unit of work
 * (AC-355): their events keep S2 and the Sbobinatura up to date (AC-354/AC-356).
 */
internal class ComandiRevisione(
    val unisciVoci: UnisciVociServizio,
    val dividiVoce: DividiVoceServizio,
    val riassegnaSegmento: RiassegnaSegmentoServizio,
)
