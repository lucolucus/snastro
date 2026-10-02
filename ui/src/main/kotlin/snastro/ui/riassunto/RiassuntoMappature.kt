package snastro.ui.riassunto

import snastro.sintesi.applicazione.letture.AzioneVista
import snastro.sintesi.applicazione.letture.ElementoVista
import snastro.sintesi.applicazione.letture.FonteVista
import snastro.sintesi.applicazione.letture.ParteTestoVista
import snastro.sintesi.applicazione.letture.PuntoChiaveVista
import snastro.sintesi.applicazione.letture.RiassuntoMostrato
import snastro.sintesi.applicazione.letture.TestoConVociVista
import snastro.sintesi.applicazione.letture.VoceVista
import snastro.ui.stile.FonteChipDati
import snastro.ui.stile.TESTO_NON_PIU_PRESENTE
import snastro.ui.testi.testoMetadati
import snastro.ui.testi.testoOmessi

/**
 * Pure mapping [RiassuntoMostrato] (`riassunto-vista`) → [ContenutoUi] (AC-S132/S133) — `internal`
 * top-level functions, not presenter methods, so [SchedaRiassuntoRenderCheckTest]'s own rich fixture
 * (AC-S140: ≥ 12 Decisioni, 5 Fonti on one element, a 40-char Nome, an unattributed Voce) can build a
 * [ContenutoUi] the same way [RiassuntoPresenter] does, without a second, drifting copy of this logic.
 */
internal fun contenutoUi(m: RiassuntoMostrato, numParti: Int = 1): ContenutoUi = ContenutoUi(
    superato = m.superato,
    sommario = m.sommario?.let(::testoConVoci)?.takeIf { it.isNotBlank() },
    decisioni = m.decisioni.map { elementoUi(it, numParti) },
    azioni = m.azioni.map { azioneUi(it, numParti) },
    questioniAperte = m.questioniAperte.map { elementoUi(it, numParti) },
    puntiChiave = m.puntiChiave.map { puntoChiaveUi(it, numParti) },
    omessiTesto = testoOmessi(m.omessi),
    metadatiTesto = testoMetadati(m.argomento, m.lunghezzaMassimaParole),
)

private fun elementoUi(e: ElementoVista, numParti: Int) = ElementoUi(testoConVoci(e.testo), fontiUi(e.fonti, numParti))

private fun azioneUi(a: AzioneVista, numParti: Int) =
    AzioneUi(testoConVoci(a.testo), fontiUi(a.fonti, numParti), a.responsabile)

private fun puntoChiaveUi(p: PuntoChiaveVista, numParti: Int) =
    PuntoChiaveUi(testoConVoci(p.testo), fontiUi(p.fonti, numParti), p.parlante)

/**
 * Pre-release finding #117 (stile-sintesi): `GruppoFonti` keeps caller order — sorted HERE, by Parte then minute
 * (a Fonte with no Parte or no minute last). INV-I13/D-0042: a Fonte whose Segmento vanished, or whose Parte was
 * eliminated, is still a chip ("parte n · non più presente" / "non più presente", never clickable), and a Voce no
 * longer present never carries a Nome. On a one-Parte Incontro the chip is today's: no "parte n", not clickable
 * (INV-I3).
 */
private fun fontiUi(fonti: List<FonteVista>, numParti: Int): List<FonteChipDati> =
    fonti.map { f ->
        val multi = numParti > 1
        val presente = f.segmentoPresente && f.numeroParte != null && f.inizioMs != null
        FonteChipDati(
            voceId = f.voce?.voceId,
            nome = f.voce?.takeIf { it.presente }?.nome,
            inizioMs = f.inizioMs,
            parteEtichetta = if (multi) f.numeroParte?.let { "parte $it" } else null,
            voceNonPresente = f.voce?.presente == false,
            segmentoPresente = presente,
            registrazioneId = f.registrazioneId,
            cliccabile = multi && presente,
        ) to (f.numeroParte ?: Int.MAX_VALUE)
    }.sortedWith(compareBy({ it.second }, { it.first.inizioMs ?: Long.MAX_VALUE })).map { it.first }

/** ux-proposal: speaker tokens inside prose render as plain text (current Nome or "Voce n"), never
 * coloured — collapsing to a `String` here means the view does zero more decision-making on it. */
private fun testoConVoci(testo: TestoConVociVista): String = testo.joinToString("") { parte ->
    when (parte) {
        is ParteTestoVista.Testo -> parte.testo
        is ParteTestoVista.Voce -> nomeInTesto(parte.voce)
    }
}

/** INV-I13: a Voce no longer in the structure reads "Voce n · non più presente", never a Nome. */
private fun nomeInTesto(voce: VoceVista): String =
    if (voce.presente) voce.nome ?: voce.etichetta else "${voce.etichetta} · $TESTO_NON_PIU_PRESENTE"
