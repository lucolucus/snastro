package snastro.progetto.applicazione.letture

import snastro.kernel.ProgettoId
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.progetto.dominio.OrdineDelleRegistrazioni

/**
 * Progetto slice of the S2 list per owning context (ADR 0033 §5): the Incontri of a Progetto, each with its derived
 * title/date/duration and its Parti as ordered and numbered by [CatalogoRegistrazioni.incontro] (never re-ordered
 * here). Plain projection, no domain rule.
 */
public class IncontriDelProgetto(
    private val registrazioni: RegistrazioneRepository,
    private val catalogo: CatalogoRegistrazioni,
) {
    /**
     * The Incontri of [progettoId], newest first by their date (the first Parte's): the same order as today's list
     * of Registrazioni, applied to each Incontro's first Parte, so a 1-part Incontro keeps its row position (INV-I3).
     */
    public fun delProgetto(progettoId: ProgettoId): List<IncontroDelProgettoVista> {
        val perIncontro = registrazioni.delProgetto(progettoId).groupBy { it.incontroId }
        val righe = perIncontro.keys.mapNotNull(catalogo::incontro).associateBy { it.parti.first().registrazioneId }
        val prime = righe.keys.mapNotNull { id -> perIncontro.values.flatten().firstOrNull { it.id == id } }
        return OrdineDelleRegistrazioni.ordina(prime).map { riga(righe.getValue(it.id)) }
    }

    private fun riga(i: IncontroVista): IncontroDelProgettoVista {
        val prima = i.parti.first()
        return IncontroDelProgettoVista(
            incontroId = i.incontroId,
            titolo = prima.titolo,
            data = prima.dataRegistrazione,
            durataMs = i.parti.sumOf { it.durataMs },
            numParti = i.parti.size,
            parti = i.parti,
        )
    }
}
