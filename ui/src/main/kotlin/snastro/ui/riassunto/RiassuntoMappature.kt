package snastro.ui.riassunto

import snastro.sintesi.applicazione.letture.AzioneVista
import snastro.sintesi.applicazione.letture.ElementoVista
import snastro.sintesi.applicazione.letture.FonteVista
import snastro.sintesi.applicazione.letture.ParteTestoVista
import snastro.sintesi.applicazione.letture.PuntoChiaveVista
import snastro.sintesi.applicazione.letture.RiassuntoMostrato
import snastro.sintesi.applicazione.letture.TestoConVociVista
import snastro.ui.stile.FonteChipDati
import snastro.ui.testi.testoMetadati
import snastro.ui.testi.testoOmessi

/**
 * Pure mapping [RiassuntoMostrato] (`riassunto-vista`) → [ContenutoUi] (AC-S132/S133) — `internal`
 * top-level functions, not presenter methods, so [SchedaRiassuntoRenderCheckTest]'s own rich fixture
 * (AC-S140: ≥ 12 Decisioni, 5 Fonti on one element, a 40-char Nome, an unattributed Voce) can build a
 * [ContenutoUi] the same way [RiassuntoPresenter] does, without a second, drifting copy of this logic.
 */
internal fun contenutoUi(m: RiassuntoMostrato): ContenutoUi = ContenutoUi(
    superato = m.superato,
    sommario = m.sommario?.let(::testoConVoci)?.takeIf { it.isNotBlank() },
    decisioni = m.decisioni.map(::elementoUi),
    azioni = m.azioni.map(::azioneUi),
    questioniAperte = m.questioniAperte.map(::elementoUi),
    puntiChiave = m.puntiChiave.map(::puntoChiaveUi),
    omessiTesto = testoOmessi(m.omessi),
    metadatiTesto = testoMetadati(m.argomento, m.lunghezzaMassimaParole),
)

private fun elementoUi(e: ElementoVista) = ElementoUi(testoConVoci(e.testo), fontiUi(e.fonti))

private fun azioneUi(a: AzioneVista) = AzioneUi(testoConVoci(a.testo), fontiUi(a.fonti), a.responsabile)

private fun puntoChiaveUi(p: PuntoChiaveVista) = PuntoChiaveUi(testoConVoci(p.testo), fontiUi(p.fonti), p.parlante)

/** Pre-release finding #117 (stile-sintesi): `GruppoFonti` keeps caller order — sorted HERE. */
private fun fontiUi(fonti: List<FonteVista>): List<FonteChipDati> =
    // TRANSITION (D-0037): a Fonte whose Segmento vanished (INV-I13) has no minute nor Voce; its chip
    // ("parte n · non più presente") comes with scheda-riassunto-incontro.
    fonti.mapNotNull { f -> f.voce?.let { v -> f.inizioMs?.let { FonteChipDati(v.voceId, v.nome, it) } } }
        .sortedBy { it.inizioMs }

/** ux-proposal: speaker tokens inside prose render as plain text (current Nome or "Voce n"), never
 * coloured — collapsing to a `String` here means the view does zero more decision-making on it. */
private fun testoConVoci(testo: TestoConVociVista): String = testo.joinToString("") { parte ->
    when (parte) {
        is ParteTestoVista.Testo -> parte.testo
        is ParteTestoVista.Voce -> parte.voce.nome ?: parte.voce.etichetta
    }
}
