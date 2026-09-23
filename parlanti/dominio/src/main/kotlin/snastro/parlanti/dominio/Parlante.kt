package snastro.parlanti.dominio

import snastro.kernel.Creato
import snastro.kernel.Esito
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RicostituzioneDaPersistenza
import snastro.kernel.VoceRef

/**
 * Aggregate root of the Parlanti context: owns [INV-13], [INV-14], [INV-18]. The set rule INV-16
 * (unique active [Nome] per Progetto) is NOT checked here (service + index, ADR 0007).
 */
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

    /** The prints, one per [VoceRef] ([INV-14]); a copy. */
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

    /** [INV-14] inserts, or replaces the ONE print of [voceRef]; the others are never touched. */
    public fun registraImpronta(voceRef: VoceRef, impronta: Impronta): Esito<Unit> =
        seModificabile {
            val nuova = ImprontaVocale(voceRef, impronta)
            val indice = _impronte.indexOfFirst { it.voceRef == voceRef }
            if (indice >= 0) _impronte[indice] = nuova else _impronte.add(nuova)
        }

    /** Removes the print of [voceRef], if any (a physical removal: biometric rows, ADR 0009). */
    public fun rimuoviImpronta(voceRef: VoceRef) {
        _impronte.removeAll { it.voceRef == voceRef }
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
        @Suppress("LongParameterList")
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
