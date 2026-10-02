package snastro.progetto.applicazione.letture

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.porte.IncontroRepository
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.progetto.dominio.OrdineDelleParti
import snastro.progetto.dominio.ParteDaOrdinare

/**
 * The Progetto context's public read API (AC-97): a plain projection over [RegistrazioneRepository],
 * no domain rule (the [snastro.progetto.dominio.Registrazione] aggregate stays the only owner of
 * INV-1/INV-2). Other contexts' adapters (`registrazione-da-progetto-tr`, `-pa`, `-doc`, future
 * blocks) call it directly — the allowed `consumer:adattatori -> supplier:applicazione` edge (CR-1).
 */
public class CatalogoRegistrazioni(
    private val registrazioni: RegistrazioneRepository,
    private val incontri: IncontroRepository,
) {
    /** The Registrazione [id] as a [RegistrazioneVista], or `null` if the catalogue does not know it. */
    public fun registrazione(id: RegistrazioneId): RegistrazioneVista? =
        registrazioni.trova(id)?.let { r ->
            RegistrazioneVista(
                registrazioneId = r.id,
                progettoId = r.progettoId,
                incontroId = r.incontroId,
                titolo = r.titolo,
                riferimentoAudio = r.riferimentoAudio,
                dataRegistrazione = r.dataRegistrazione,
                oraDiInizio = r.oraDiInizio?.valore,
                durataMs = r.durataMs,
            )
        }

    /**
     * The Incontro [id] with its Parti ordered and numbered by `OrdineDelleParti` (INV-I2, the only place the order is
     * computed). `null` for an unknown Incontro or one that ceased with its last Parte. A Parte deleted between the two
     * reads (`partiDi`, then `trova`) is left out: it is gone, like it would be one read later.
     */
    public fun incontro(id: IncontroId): IncontroVista? {
        val parti = incontri.partiDi(id).mapNotNull(registrazioni::trova)
        if (parti.isEmpty()) return null
        val numeri = OrdineDelleParti.ordina(
            parti.map { ParteDaOrdinare(it.id, it.dataRegistrazione, it.oraDiInizio, it.aggiuntaAlle) },
        )
        val perId = parti.associateBy { it.id }
        return IncontroVista(
            incontroId = id,
            progettoId = parti.first().progettoId,
            parti = numeri.map { (rid, numero) ->
                val r = perId.getValue(rid)
                ParteVista(rid, numero, r.titolo, r.dataRegistrazione, r.oraDiInizio?.valore, r.durataMs)
            },
        )
    }

    /**
     * The ids of the Parti of the Incontro [incontroId], UNORDERED by contract (callers never rely on the order: they
     * come as the repository returns them, not ordered by [incontro]). `null` as [incontro].
     */
    public fun parti(incontroId: IncontroId): List<RegistrazioneId>? = incontri.partiDi(incontroId).ifEmpty { null }
}
