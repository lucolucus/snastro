package snastro.sintesi.dominio

import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId

/**
 * The Parti of an Incontro in INV-I2 order, each with the [StrutturaTrascritto] read for it (null: no Trascritto)
 * (ADR 0037 §5). The Verifica delle fonti checks a Fonte against it (INV-I10); [chiave] decides `superato` (INV-I11).
 */
public data class StrutturaIncontro(val parti: List<Pair<RegistrazioneId, StrutturaTrascritto?>>) {
    init {
        require(parti.map { it.first }.toSet().size == parti.size) { "Parte ripetuta nella struttura dell'Incontro" }
    }

    /**
     * `<registrazioneId>=<StrutturaTrascritto.chiave or empty>` per Parte in order, joined by `;`
     * (e.g. `r1=1:1,2:2;r2=`): exact and collision-free, a registrazioneId is a UUID text with no `=` or `;`. One
     * Parte gives the 7.sqm re-encoding `<id>=<old struttura>` (AC-I17, INV-I3).
     */
    public val chiave: String
        get() = parti.joinToString(SEPARATORE_PARTI) { (r, s) ->
            "${r.valore}$SEPARATORE_CHIAVE${s?.chiave.orEmpty()}"
        }

    /** The Voci of the Incontro as read: every Voce of every Parte's structure. */
    public val voci: Set<VoceId> get() = parti.flatMap { it.second?.voci.orEmpty() }.toSet()

    /** [ref]'s Parte is in this structure and [ref]'s segmentoId is a Segmento of that Parte's Trascritto. */
    public fun contiene(ref: SegmentoRef): Boolean = voceDi(ref) != null

    /** The Voce of the Segmento [ref], null when [contiene] is false. */
    public fun voceDi(ref: SegmentoRef): VoceId? = strutturaDi(ref.registrazioneId)?.voceDi(ref.segmentoId)

    /** Only the Parti that had a Trascritto: what a `pronto` Riassunto records (ADR 0037 §5 "Recorded"). */
    internal fun conTrascritto(): StrutturaIncontro = StrutturaIncontro(parti.filter { it.second != null })

    private fun strutturaDi(r: RegistrazioneId): StrutturaTrascritto? = parti.firstOrNull { it.first == r }?.second

    private companion object {
        const val SEPARATORE_PARTI = ";"
        const val SEPARATORE_CHIAVE = "="
    }
}
