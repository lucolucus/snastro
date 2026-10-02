package snastro.ui.stile

import snastro.kernel.RegistrazioneId
import snastro.ui.formattaDurata

/**
 * AC-S44: one [FonteChip]'s own data — [GruppoFonti]'s input shape, Published-Language-free (a
 * plain `:ui` view type, not `:sintesi:applicazione`'s `FonteVista`): a screen maps its own read
 * model into this before calling [GruppoFonti].
 *
 * Incontro additions (all defaulted: a one-Parte chip is exactly today's `dot + Nome + m:ss`):
 * - [voceId] `null` = no voice part at all (the Segmento vanished, Sintesi stores no Voce per Fonte, D-0042);
 * - [voceNonPresente] (INV-I13) = "Voce n · non più presente", muted, no dot, never a Nome;
 * - [parteEtichetta] = "parte 2", set only on a multi-part Incontro;
 * - [segmentoPresente] `false` = the time part reads "[parte n · ]non più presente" (no minute);
 * - [registrazioneId] + [cliccabile]: the chip plays [inizioMs] of that Parte (AC-I81).
 */
public data class FonteChipDati(
    val voceId: Int?,
    val nome: String?,
    val inizioMs: Long?,
    val parteEtichetta: String? = null,
    val voceNonPresente: Boolean = false,
    val segmentoPresente: Boolean = true,
    val registrazioneId: RegistrazioneId? = null,
    val cliccabile: Boolean = false,
) {
    /** The chip's time part: "12:30", "parte 2 · 12:30", "parte 2 · non più presente" or "non più presente". */
    val tempoTesto: String
        get() {
            val tempo = if (segmentoPresente && inizioMs != null) formattaDurata(inizioMs) else TESTO_NON_PIU_PRESENTE
            return if (parteEtichetta != null) "$parteEtichetta · $tempo" else tempo
        }
}

/** INV-I13: the suffix of everything that no longer exists in the Incontro's current structure. */
public const val TESTO_NON_PIU_PRESENTE: String = "non più presente"
