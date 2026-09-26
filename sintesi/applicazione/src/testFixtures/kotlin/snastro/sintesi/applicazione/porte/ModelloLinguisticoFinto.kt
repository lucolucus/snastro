package snastro.sintesi.applicazione.porte

import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoroFinta

/**
 * Scripted [ModelloLinguistico] (ADR 0021 §4).
 * - Throws [IllegalStateException] when invoked while [unitaDiLavoro] has a transaction open: the
 *   ADR 0012 (b) guard every `esegui-riassunto` test relies on (AC-S14).
 * - Records the [ultimaRichiesta] (AC-S15).
 * - Answers [Esito.Errore] with `Annullato` at once when [riassumi]'s `annullato()` is true or the thread
 *   is interrupted; otherwise the scripted outcome ([rispondi] / [fallisci]), [RISPOSTA_PREDEFINITA] by default.
 * - Never validates Fonti: a scripted answer may carry invalid ids on purpose ([INV-S4] is the root's).
 */
public class ModelloLinguisticoFinto(private val unitaDiLavoro: UnitaDiLavoroFinta) : ModelloLinguistico {
    private var esito: Esito<RispostaModello> = Esito.Ok(RISPOSTA_PREDEFINITA)

    /** The last request received, or null if never invoked. */
    public var ultimaRichiesta: RichiestaRiassunto? = null
        private set

    /** Scripts the answer of the next calls. */
    public fun rispondi(risposta: RispostaModello) {
        esito = Esito.Ok(risposta)
    }

    /** Scripts the failure of the next calls. */
    public fun fallisci(errore: ErroreApplicazioneSintesi) {
        esito = Esito.Errore(errore)
    }

    override fun riassumi(richiesta: RichiestaRiassunto, annullato: () -> Boolean): Esito<RispostaModello> {
        check(!unitaDiLavoro.transazioneAperta) { "riassumi invocato dentro una transazione (ADR 0012 (b))" }
        ultimaRichiesta = richiesta
        val annullata = annullato() || Thread.currentThread().isInterrupted
        return if (annullata) Esito.Errore(ErroreApplicazioneSintesi.Annullato) else esito
    }

    public companion object {
        /** A complete answer over [ModelloLinguisticoContratto.INGRESSO], every speaker as `{V<n>}`. */
        public val RISPOSTA_PREDEFINITA: RispostaModello = RispostaModello(
            sommario = "{V1} e {V2} fissano il combattimento a turni.",
            decisioni = listOf(ElementoRisposta("Il combattimento resta a turni.", listOf(1))),
            questioniAperte = listOf(ElementoRisposta("Quanti nemici per stanza.", listOf(3))),
            azioni = listOf(AzioneRisposta("{V2} prepara il prototipo entro venerdi.", listOf(2), responsabile = 2)),
            puntiChiave = listOf(PuntoChiaveRisposta("Il ritmo del combattimento.", listOf(1, 3), parlante = 1)),
        )
    }
}
