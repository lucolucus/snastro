package snastro.ui.riassunto

import snastro.sintesi.applicazione.letture.VoceVista
import snastro.ui.stile.FonteChipDati

/**
 * State of S3's Riassunto tab (ux-proposal states 1..12) — presenter-owned ([RiassuntoPresenter]),
 * rendered by [SchedaRiassunto]. [Dati.contenuto] (the shown Riassunto) and [Dati.areaAzione] (the
 * bottom action area) are independent axes: a Riassunto can stay visible while the action area shows
 * the model download, the queued/running status, a failed download or "not available" (ux rows 1/5/
 * 6/7/10) — this is why they are separate fields rather than one big enum of 12 cases.
 */
sealed interface RiassuntoUiStato {
    /** AC-S136 (state 12): the view is local (no network) — brief skeleton lines only. */
    data object Caricamento : RiassuntoUiStato

    @Suppress("LongParameterList") // one field per ux-proposal datum this tab shows (states 1..10)
    data class Dati(
        val modello: ModelloUi,
        val richiesta: RichiestaUi?,
        val fallimentoTesto: String?,
        val nonDisponibileTesto: String?,
        val contenuto: ContenutoUi?,
        val argomento: ArgomentoUiStato,
        val lunghezzaMassima: LunghezzaMassimaUiStato,
        val messaggioErrore: String? = null,
    ) : RiassuntoUiStato {
        /**
         * The ONE bottom action-area variant, by PRECEDENCE (ux-proposal's own layout: model line at
         * top, replacing the whole body when no Riassunto is shown) — a pure computed property, the
         * same pattern as [snastro.ui.registrazione.RegistrazioneUiStato.Dati.bannerSchermata]: it can
         * never drift from the fields above and is table-testable on plain [Dati] fixtures
         * ([RiassuntoUiStatoTest]).
         *
         * D-0014 (pre-release finding #156, rework): [nonDisponibileTesto] now outranks
         * [fallimentoTesto] — an ENABLED "Riprova" the recording being unavailable makes certain to
         * be refused is worse than the DISABLED "Riassumi" + caption [AreaAzione.NonDisponibile]
         * already shows.
         */
        val areaAzione: AreaAzione
            get() = when {
                modello is ModelloUi.NonInstallato ->
                    AreaAzione.ScaricaModello(modello.messaggio, modello.etichettaBottone)
                modello is ModelloUi.InDownload -> AreaAzione.Scaricando(modello.testo, modello.avanzamento)
                modello is ModelloUi.DownloadFallito -> AreaAzione.DownloadFallito(modello.messaggio)
                richiesta is RichiestaUi.InAttesa -> AreaAzione.InCoda(richiesta.posizione)
                richiesta is RichiestaUi.InCorso -> AreaAzione.InCorso(richiesta.trascorsoMs)
                nonDisponibileTesto != null -> AreaAzione.NonDisponibile(nonDisponibileTesto)
                fallimentoTesto != null -> AreaAzione.Fallito(fallimentoTesto)
                else -> AreaAzione.Azionabile(nuovo = contenuto != null)
            }
    }
}

/** AC-S125..S127: [RiassuntoVista.modello] mapped into ready-to-render text (dev-architecture `#presenter`). */
sealed interface ModelloUi {
    data class NonInstallato(val messaggio: String, val etichettaBottone: String) : ModelloUi

    data class InDownload(val testo: String, val avanzamento: Float) : ModelloUi

    data class DownloadFallito(val messaggio: String) : ModelloUi

    data object Installato : ModelloUi
}

/** AC-S130/S131: [RiassuntoVista.richiestaAperta] mapped into ready-to-render fields (pre-release
 * finding #152, rework: raw [posizione]/[trascorsoMs] rather than a pre-joined string, so the view
 * can feed [snastro.ui.stile.TipoChipStato.InCoda]/[snastro.ui.stile.TipoChipStato.InCorso] directly). */
sealed interface RichiestaUi {
    /** `null` = absent from the queue's snapshot ([snastro.ui.coda.PosizioniCoda] KDoc). */
    data class InAttesa(val posizione: Int?) : RichiestaUi

    data class InCorso(val trascorsoMs: Long) : RichiestaUi
}

/**
 * The bottom action area (ux-proposal "Action area"), by precedence — see [RiassuntoUiStato.Dati.areaAzione].
 * [Azionabile] is states 4/8/9: Argomento + lunghezza massima + button ("Riassumi" when [Azionabile.nuovo]
 * is `false`, "Riassumi di nuovo" otherwise). [Fallito] (state 10) is its own case: "Riprova", never
 * "Riassumi di nuovo".
 */
sealed interface AreaAzione {
    data class ScaricaModello(val messaggio: String, val etichettaBottone: String) : AreaAzione

    data class Scaricando(val testo: String, val avanzamento: Float) : AreaAzione

    data class DownloadFallito(val messaggio: String) : AreaAzione

    data class InCoda(val posizione: Int?) : AreaAzione

    data class InCorso(val trascorsoMs: Long) : AreaAzione

    data class Fallito(val messaggio: String) : AreaAzione

    data class NonDisponibile(val messaggio: String) : AreaAzione

    data class Azionabile(val nuovo: Boolean) : AreaAzione
}

/**
 * AC-S132/S133: the shown `pronto` Riassunto, [RiassuntoMostrato] mapped into ready-to-render text —
 * speaker tokens already resolved to plain text (ux: "never coloured", so no [ParteTestoVista.Voce]
 * span survives past the presenter) and [ElementoUi]/[AzioneUi]/[PuntoChiaveUi]'s `fonti` already
 * sorted by time ([FonteChipDati], `stile-sintesi`'s own shape — `GruppoFonti` keeps caller order,
 * L713/pre-release finding #117: this presenter is the caller that sorts).
 */
data class ContenutoUi(
    val superato: Boolean,
    val sommario: String?,
    val decisioni: List<ElementoUi>,
    val azioni: List<AzioneUi>,
    val questioniAperte: List<ElementoUi>,
    val puntiChiave: List<PuntoChiaveUi>,
    val omessiTesto: String?,
    val metadatiTesto: String,
)

data class ElementoUi(val testo: String, val fonti: List<FonteChipDati>)

data class AzioneUi(val testo: String, val fonti: List<FonteChipDati>, val responsabile: VoceVista?)

data class PuntoChiaveUi(val testo: String, val fonti: List<FonteChipDati>, val parlante: VoceVista?)

/** AC-S137: the Argomento field's own state — [contatore] "n/200", [errore] only over the limit. */
data class ArgomentoUiStato(val valore: String, val contatore: String, val errore: String?)

/** AC-S138: the per-Progetto lunghezza massima inline editor. */
sealed interface LunghezzaMassimaUiStato {
    data class Testo(val parole: Int) : LunghezzaMassimaUiStato

    data class Modifica(val valore: String, val errore: String?) : LunghezzaMassimaUiStato

    /** "Salvato", shown for 2 s ([RiassuntoPresenter]) before reverting to [Testo]. */
    data class Salvato(val parole: Int) : LunghezzaMassimaUiStato
}
