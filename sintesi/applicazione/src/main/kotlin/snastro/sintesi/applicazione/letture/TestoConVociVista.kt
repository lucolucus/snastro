package snastro.sintesi.applicazione.letture

/**
 * AC-S103: a decoded text, its speakers resolved to a [VoceVista] (CURRENT Nome, or unattributed) —
 * see [RiassuntoVisteLettura]. A list, not a wrapper: the view renders it by folding over the parts
 * in order, e.g. `[Voce(Marco), Testo(" apre, "), Voce(etichetta = "Voce 3", nome = null), ...]`.
 */
public typealias TestoConVociVista = List<ParteTestoVista>

/** One part of a [TestoConVociVista]. */
public sealed interface ParteTestoVista {
    public data class Testo(val testo: String) : ParteTestoVista

    public data class Voce(val voce: VoceVista) : ParteTestoVista
}
