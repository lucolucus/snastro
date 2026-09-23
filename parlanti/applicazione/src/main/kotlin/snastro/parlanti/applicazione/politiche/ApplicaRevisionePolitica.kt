package snastro.parlanti.applicazione.politiche

import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.mappa
import snastro.kernel.poi
import snastro.parlanti.applicazione.porte.AttribuzioneRepository
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.applicazione.porte.LettoreVoci
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.Parlante

/**
 * Policy `ApplicaRevisione` ([INV-21], [INV-25], ADR 0012): reacts to a Trascrizione Revisione
 * (`VociUnite` / `VoceDivisa` / `SegmentoRiassegnato`) inside the SAME transaction as the publishing
 * command. It never imports those published events (`:parlanti:applicazione` may not depend on
 * `:trascrizione:applicazione`, `architecture.md` edges table): `abbonato-revisione-parlanti`
 * (`:parlanti:adattatori`) subscribes to them and translates each into the call below.
 *
 * Every rule goes through [Parlante] / `Attribuzione` (RC-1): this class only orchestrates
 * lookups, re-derives a print from the CURRENT Segmenti of a `Voce` ([LettoreVoci] + audio ports) and
 * applies the [INV-25] cross-aggregate cessation of an `occasionale` left without any Attribuzione.
 * A `Parlante` referenced by a still-persisted `Attribuzione` but missing from [ParlanteRepository],
 * or a `Voce` no longer present in [LettoreVoci], is a data-integrity fault (`check`), not an
 * expected business error (ADR 0003): the Trascritto was just saved, in this same transaction, by
 * the command that published the event.
 */
public class ApplicaRevisionePolitica(
    private val parlanti: ParlanteRepository,
    private val attribuzioni: AttribuzioneRepository,
    private val lettoreVoci: LettoreVoci,
    private val decodificatore: DecodificatoreAudio,
    private val estrattore: EstrattoreImpronta,
) {
    /**
     * `VociUnite`: [rimossa] disappears into [sopravvissuta]. [rimossa] loses its Attribuzione and
     * derived print; if [sopravvissuta] is attributed its print is re-derived (its Segmenti changed) —
     * this is how "A's Attribuzione wins" when A and B were attributed to different Parlanti: B's is
     * simply dropped, A's is untouched but re-derived from the merged Segmenti.
     */
    public fun applicaVociUnite(registrazioneId: RegistrazioneId, sopravvissuta: VoceId, rimossa: VoceId): Esito<Unit> =
        rimuoviSePresente(VoceRef(registrazioneId, rimossa))
            .poi { riderivaSePresente(VoceRef(registrazioneId, sopravvissuta)) }

    /**
     * `VoceDivisa`: the new `Voce` (`nuova`) starts without Attribuzione — no call needed: no
     * Attribuzione row exists yet for it. [origine] keeps its Attribuzione, if any, with its print
     * re-derived from its current (reduced) Segmenti.
     */
    public fun applicaVoceDivisa(registrazioneId: RegistrazioneId, origine: VoceId): Esito<Unit> =
        riderivaSePresente(VoceRef(registrazioneId, origine))

    /**
     * `SegmentoRiassegnato`: the source [da] either loses its Attribuzione+print ([daRimossa], left
     * without any Segmento) or keeps it re-derived; the destination [a] either starts without
     * Attribuzione ([aNuova], a brand-new `Voce`) or, if it already existed and is attributed, has its
     * print re-derived (it gained a Segmento).
     */
    public fun applicaSegmentoRiassegnato(
        registrazioneId: RegistrazioneId,
        da: VoceId,
        a: VoceId,
        daRimossa: Boolean,
        aNuova: Boolean,
    ): Esito<Unit> {
        val perDa = VoceRef(registrazioneId, da)
        val esitoDa = if (daRimossa) rimuoviSePresente(perDa) else riderivaSePresente(perDa)
        return esitoDa.poi {
            if (aNuova) Esito.Ok(Unit) else riderivaSePresente(VoceRef(registrazioneId, a))
        }
    }

    /** [INV-21] the removed [voceRef] loses its Attribuzione and derived print; cascades [INV-25]. */
    private fun rimuoviSePresente(voceRef: VoceRef): Esito<Unit> {
        val attribuzione = attribuzioni.trova(voceRef) ?: return Esito.Ok(Unit)
        attribuzioni.rimuovi(voceRef)
        val parlante = checkNotNull(parlanti.trova(attribuzione.parlanteId)) {
            "Attribuzione($voceRef) punta al Parlante inesistente ${attribuzione.parlanteId}"
        }
        parlante.rimuoviImpronta(voceRef)
        return parlanti.salva(parlante).mappa { cessaSeOccasionaleSenzaAttribuzioni(parlante) }
    }

    /** [INV-21] a surviving/changed attributed [voceRef] gets its print re-derived from its CURRENT Segmenti. */
    private fun riderivaSePresente(voceRef: VoceRef): Esito<Unit> {
        val attribuzione = attribuzioni.trova(voceRef) ?: return Esito.Ok(Unit)
        val parlante = checkNotNull(parlanti.trova(attribuzione.parlanteId)) {
            "Attribuzione($voceRef) punta al Parlante inesistente ${attribuzione.parlanteId}"
        }
        val voci = checkNotNull(lettoreVoci.voci(voceRef.registrazioneId)) {
            "nessun Trascritto per ${voceRef.registrazioneId} durante una Revisione in corso"
        }
        val voce = checkNotNull(voci.firstOrNull { it.voceRef == voceRef }) {
            "Voce $voceRef assente tra le Voci correnti durante una Revisione in corso"
        }
        val impronta: Impronta = estrattore.estrai(decodificatore.campioni(voceRef.registrazioneId, voce.intervalli))
        return parlante.registraImpronta(voceRef, impronta).poi { parlanti.salva(parlante) }
    }

    /** [INV-25] an `occasionale` left without any Attribuzione ceases to exist; a `ricorrente` is kept. */
    private fun cessaSeOccasionaleSenzaAttribuzioni(parlante: Parlante) {
        if (parlante.attivo && parlante.occasionale && attribuzioni.diParlante(parlante.id).isEmpty()) {
            parlanti.rimuovi(parlante.id)
        }
    }
}
