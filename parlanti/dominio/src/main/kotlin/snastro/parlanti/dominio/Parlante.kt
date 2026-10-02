package snastro.parlanti.dominio

import snastro.kernel.Creato
import snastro.kernel.Esito
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RicostituzioneDaPersistenza
import snastro.kernel.VoceRef

/**
 * Aggregate root of the Parlanti context: owns [INV-13], [INV-I8] (prints per (VoceRef, Parte), which amends [INV-14]),
 * [INV-18], and the print re-keying of [INV-21]. The set rule INV-16
 * (unique active [Nome] per Progetto) is NOT checked here (service + index, ADR 0007).
 */
@Suppress("TooManyFunctions") // the root owns every print operation (INV-I8/I8b/21): one command per rule, RC-1
public class Parlante private constructor(
    public val id: ParlanteId,
    public val progettoId: ProgettoId,
    nome: Nome,
    tipo: TipoParlante,
    stato: StatoParlante,
    impronte: List<ImprontaVocale>,
) {
    private var _nome: Nome = nome
    private var _tipo: TipoParlante = tipo
    private var _stato: StatoParlante = stato
    public val nome: Nome get() = _nome
    public val tipo: TipoParlante get() = _tipo
    public val stato: StatoParlante get() = _stato
    private val _impronte: MutableList<ImprontaVocale> = impronte.toMutableList()

    /** The prints, at most one per ([VoceRef], Parte) ([INV-I8]), each recording its Parte; a copy. */
    public val impronte: List<ImprontaVocale> get() = _impronte.toList()

    public val attivo: Boolean get() = stato == StatoParlante.ATTIVO
    public val eliminato: Boolean get() = stato == StatoParlante.ELIMINATO
    public val occasionale: Boolean get() = tipo == TipoParlante.OCCASIONALE
    public val haImpronte: Boolean get() = _impronte.isNotEmpty()

    public fun rinomina(nome: Nome): Esito<ParlanteRinominato> =
        seModificabile {
            _nome = nome
            ParlanteRinominato(id, nome.valore)
        }

    /** [INV-18] only `occasionale` → `ricorrente`; the prints are untouched, the [Nome] changes only if given. */
    public fun promuovi(nome: Nome?): Esito<ParlantePromosso> =
        when {
            eliminato -> Esito.Errore(ErroreParlanti.ParlanteEliminatoNonModificabile(id))
            !occasionale -> Esito.Errore(ErroreParlanti.PromozioneNonAmmessa(id))
            else -> {
                val nomeCambiato = nome != null && nome != _nome
                if (nome != null) _nome = nome
                _tipo = TipoParlante.RICORRENTE
                Esito.Ok(ParlantePromosso(id, _nome.valore, nomeCambiato))
            }
        }

    /** [INV-13] tombstone: purges every print in the same call (ADR 0009), keeps the [Nome]. Terminal. */
    public fun elimina(): Esito<ParlanteEliminato> =
        seModificabile {
            _impronte.clear()
            _stato = StatoParlante.ELIMINATO
            ParlanteEliminato(id)
        }

    /**
     * [INV-I8] inserts, or replaces the ONE print of ([voce], [parte]); prints of the same Voce in other Parti and of
     * other Voci are never touched. [parte] = the Parte (Registrazione) the print was extracted from,
     * [sorgente] = `SorgenteImpronta.chiave`, [modello] = `EstrattoreImpronta.modello` (ADR 0012 (b)).
     */
    public fun aggiungiImpronta(
        voce: VoceRef,
        parte: RegistrazioneId,
        impronta: Impronta,
        sorgente: String,
        modello: String,
    ): Esito<Unit> =
        seModificabile {
            val nuova = ImprontaVocale(voce, impronta, sorgente, modello, parte)
            val indice = _impronte.indexOfFirst { it.voceRef == voce && it.parte == parte }
            if (indice >= 0) _impronte[indice] = nuova else _impronte.add(nuova)
        }

    /**
     * POLICY-ONLY ([INV-21] `unire(A = [a], B = [da])`): each print of [da] is re-keyed onto [a] keeping its Parte,
     * impronta, sorgente and modello (stale by construction, refreshed after commit by `RiallineaImpronte`) — unless [a]
     * already has a print in that Parte: then [a]'s is kept and [da]'s dropped. Covers the inheritance case ([a] holds
     * none). No print for [da], or [da] == [a] → no-op (the former always so for an `eliminato`, [INV-13]).
     */
    public fun riassegnaImpronte(da: VoceRef, a: VoceRef) {
        if (da == a) return // re-keying a Voce onto itself changes nothing (never drop its prints)
        val partiDiA = _impronte.filter { it.voceRef == a }.map { it.parte }.toSet()
        _impronte.removeAll { it.voceRef == da && it.parte in partiDiA }
        _impronte.replaceAll { if (it.voceRef == da) it.copy(voceRef = a) else it }
    }

    /**
     * Removes every print of [voceRef], in every Parte (a physical removal: biometric rows, ADR 0009) — for the
     * removals that end the Voce's link to this Parlante as a whole: its Attribuzione moved to another Parlante, or
     * the Voce ceased (revisione-policy). A per-Parte removal is [rimuoviImpronta] (voce, parte).
     */
    public fun rimuoviImpronta(voceRef: VoceRef) {
        _impronte.removeAll { it.voceRef == voceRef }
    }

    /** Removes the print of ([voce], [parte]), if any; the same Voce keeps its prints of the other Parti. */
    public fun rimuoviImpronta(voce: VoceRef, parte: RegistrazioneId) {
        _impronte.removeAll { it.voceRef == voce && it.parte == parte }
    }

    /**
     * [INV-21] a Revisione emptied the slices of [voce] outside [partiConFetta]: the prints of [voce] sourced from any
     * other Parte have no source left and go; those of [partiConFetta] and of other Voci stay. `true` iff a print went.
     */
    public fun rimuoviImpronteSenzaFetta(voce: VoceRef, partiConFetta: Set<RegistrazioneId>): Boolean =
        _impronte.removeAll { it.voceRef == voce && it.parte !in partiConFetta }

    /** [INV-I8b] removes every print sourced from [parte], of any Voce; the other Parti's prints stay. */
    public fun rimuoviImpronteDellaParte(parte: RegistrazioneId) {
        _impronte.removeAll { it.parte == parte }
    }

    private inline fun <T> seModificabile(modifica: () -> T): Esito<T> =
        if (eliminato) Esito.Errore(ErroreParlanti.ParlanteEliminatoNonModificabile(id)) else Esito.Ok(modifica())

    public companion object {
        public fun crea(
            id: ParlanteId,
            progettoId: ProgettoId,
            nome: Nome,
            tipo: TipoParlante,
        ): Creato<Parlante, ParlanteCreato> =
            Creato(
                Parlante(id, progettoId, nome, tipo, StatoParlante.ATTIVO, emptyList()),
                ParlanteCreato(id, progettoId, nome.valore, tipo),
            )

        /** Rebuilds from persisted state, one parameter per field; re-validates nothing (the DB is trusted). */
        @RicostituzioneDaPersistenza
        public fun ricostituisci(
            id: ParlanteId,
            progettoId: ProgettoId,
            nome: String,
            tipo: TipoParlante,
            stato: StatoParlante,
            impronte: List<ImprontaVocale>,
        ): Parlante = Parlante(id, progettoId, Nome(nome), tipo, stato, impronte)
    }
}
