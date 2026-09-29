package snastro.sintesi.dominio

import snastro.kernel.Creato
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.RicostituzioneDaPersistenza
import snastro.kernel.mappa
import snastro.sintesi.dominio.StatoRiassunto.FALLITO
import snastro.sintesi.dominio.StatoRiassunto.IN_ATTESA
import snastro.sintesi.dominio.StatoRiassunto.IN_CORSO
import snastro.sintesi.dominio.StatoRiassunto.PRONTO
import java.time.Instant

/**
 * One request to summarise a Registrazione. Owns INV-S1 (lifecycle; content iff `pronto`, motivo iff `fallito`),
 * INV-S4 (`pronto` only through the Verifica delle fonti), INV-S5 (speakers only as Voce references),
 * INV-S7 ([superato] derived from the stored [struttura]) and INV-S10 ([lunghezzaMassima] fixed at request).
 * No deletion method: physical removals are repository operations (ADR 0021 §9).
 */
// Constructor internal, not private: tests exercise the INV-S1 guard without opting in to ricostituisci (CR-15).
@Suppress("LongParameterList") // one parameter per field of the root
public class Riassunto internal constructor(
    public val id: RiassuntoId,
    public val registrazioneId: RegistrazioneId,
    public val argomento: Argomento?,
    public val lunghezzaMassima: LunghezzaMassimaParole,
    public val richiestoAlle: Instant,
    stato: StatoRiassunto,
    avviatoAlle: Instant?,
    motivoFallimento: MotivoFallimento?,
    contenuto: EsitoVerifica?,
    struttura: String?,
) {
    // Backing fields, not `public var … private set`: CR-4's Konsist rule bans any public var in dominio.
    private var _stato = stato
    private var _avviatoAlle = avviatoAlle
    private var _motivoFallimento = motivoFallimento
    private var _contenuto = contenuto
    private var _struttura = struttura

    init {
        require((stato == FALLITO) == (motivoFallimento != null)) { "INV-S1: motivoFallimento esiste sse fallito" }
        require((stato == PRONTO) == (contenuto != null) && (stato == PRONTO) == (struttura != null)) {
            "INV-S1: contenuto e struttura esistono sse pronto"
        }
        require(stato == IN_ATTESA || avviatoAlle != null) { "INV-S1: avviatoAlle esiste da in_corso in poi" }
    }

    /** For the persistence adapter only; everyone else uses the named predicates. */
    public val stato: StatoRiassunto get() = _stato
    public val avviatoAlle: Instant? get() = _avviatoAlle
    public val motivoFallimento: MotivoFallimento? get() = _motivoFallimento

    public val sommario: Sommario? get() = _contenuto?.sommario

    // .toList() (not the raw field): state captive, an accessor never exposes the backing collection.
    public val decisioni: List<Decisione> get() = _contenuto?.decisioni?.toList().orEmpty()
    public val questioniAperte: List<QuestioneAperta> get() = _contenuto?.questioniAperte?.toList().orEmpty()
    public val azioni: List<Azione> get() = _contenuto?.azioni?.toList().orEmpty()
    public val puntiChiave: List<PuntoChiave> get() = _contenuto?.puntiChiave?.toList().orEmpty()

    /** Elements (+ a Sommario) dropped by the Verifica delle fonti; null unless `pronto`. */
    public val omessi: Int? get() = _contenuto?.omessi

    /** [StrutturaTrascritto.chiave] of the structure the content was verified against; null unless `pronto`. */
    public val struttura: String? get() = _struttura

    /** `in_attesa` or `in_corso`. */
    public val aperto: Boolean get() = stato == IN_ATTESA || stato == IN_CORSO
    public val inAttesa: Boolean get() = stato == IN_ATTESA
    public val inCorso: Boolean get() = stato == IN_CORSO
    public val pronto: Boolean get() = stato == PRONTO
    public val fallito: Boolean get() = stato == FALLITO

    /** INV-S7: a `pronto` Riassunto whose structure differs from the [corrente] one; false unless `pronto`. */
    public fun superato(corrente: StrutturaTrascritto): Boolean = pronto && corrente.chiave != struttura

    public fun avvia(alle: Instant): Esito<RiassuntoAvviatoDominio> =
        transizione(da = IN_ATTESA, verso = IN_CORSO) {
            _avviatoAlle = alle
            RiassuntoAvviatoDominio(id, registrazioneId, alle)
        }

    /**
     * INV-S4: verifies [bozza] against [struttura] (the one read for this run). Some content left → `pronto`
     * (kept whole, never truncated: INV-S10); nothing left → `fallito` NESSUN_CONTENUTO_VERIFICABILE.
     */
    public fun completa(bozza: BozzaRiassunto, struttura: StrutturaTrascritto): Esito<ConclusioneRiassunto> {
        if (stato != IN_CORSO) return nonAmmessa(PRONTO)
        val verificato = VerificaDelleFonti(struttura).applica(bozza)
        return if (verificato.vuoto) {
            fallisci(MotivoFallimento.NESSUN_CONTENUTO_VERIFICABILE).mappa { ConclusioneRiassunto.Fallito(it.motivo) }
        } else {
            transizione(da = IN_CORSO, verso = PRONTO) {
                _contenuto = verificato
                _struttura = struttura.chiave
                ConclusioneRiassunto.Pronto(verificato.omessi)
            }
        }
    }

    public fun fallisci(motivo: MotivoFallimento): Esito<RiassuntoFallitoDominio> =
        transizione(da = IN_CORSO, verso = FALLITO) {
            _motivoFallimento = motivo
            RiassuntoFallitoDominio(id, registrazioneId, motivo)
        }

    private inline fun <E> transizione(da: StatoRiassunto, verso: StatoRiassunto, effetto: () -> E): Esito<E> {
        if (stato != da) return nonAmmessa(verso)
        val evento = effetto()
        _stato = verso
        return Esito.Ok(evento)
    }

    private fun nonAmmessa(verso: StatoRiassunto): Esito.Errore =
        Esito.Errore(ErroreSintesi.TransizioneNonAmmessa(stato.codice, verso.codice))

    public companion object {
        public fun richiedi(
            id: RiassuntoId,
            registrazioneId: RegistrazioneId,
            argomento: Argomento?,
            lunghezzaMassima: LunghezzaMassimaParole,
            richiestoAlle: Instant,
        ): Creato<Riassunto, RiassuntoRichiestoDominio> = Creato(
            Riassunto(
                id, registrazioneId, argomento, lunghezzaMassima, richiestoAlle,
                IN_ATTESA, avviatoAlle = null, motivoFallimento = null, contenuto = null, struttura = null,
            ),
            RiassuntoRichiestoDominio(id, registrazioneId, richiestoAlle),
        )

        /**
         * Rebuilds from persisted state (the DB is trusted, nothing re-verified); an INV-S1-inconsistent
         * combination is a programmer error (`require`): content, [omessi] and [struttura] only when `pronto`.
         */
        @RicostituzioneDaPersistenza
        public fun ricostituisci(
            id: RiassuntoId,
            registrazioneId: RegistrazioneId,
            argomento: Argomento?,
            lunghezzaMassima: LunghezzaMassimaParole,
            richiestoAlle: Instant,
            stato: StatoRiassunto,
            avviatoAlle: Instant?,
            motivoFallimento: MotivoFallimento?,
            sommario: Sommario?,
            decisioni: List<Decisione>,
            questioniAperte: List<QuestioneAperta>,
            azioni: List<Azione>,
            puntiChiave: List<PuntoChiave>,
            omessi: Int?,
            struttura: String?,
        ): Riassunto {
            val haElementi = sommario != null || decisioni.isNotEmpty() || questioniAperte.isNotEmpty() ||
                azioni.isNotEmpty() || puntiChiave.isNotEmpty()
            require(omessi != null || !haElementi) { "INV-S1: contenuto senza omessi" }
            // .toList(): the caller's lists (e.g. built by the mapper) are never aliased by the root (state captive).
            val contenuto = omessi?.let {
                EsitoVerifica(
                    sommario,
                    decisioni.toList(),
                    questioniAperte.toList(),
                    azioni.toList(),
                    puntiChiave.toList(),
                    it,
                )
            }
            return Riassunto(
                id, registrazioneId, argomento, lunghezzaMassima, richiestoAlle,
                stato, avviatoAlle, motivoFallimento, contenuto, struttura,
            )
        }
    }
}
