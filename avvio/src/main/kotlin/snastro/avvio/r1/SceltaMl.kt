package snastro.avvio.r1

/**
 * Which ML adapters the pipeline uses ([SelezioneAdattatoriMl]): `-Dsnastro.ml=finte` forces
 * [FINTE] (the `--smoke` run); anything else is [REALI].
 */
internal enum class SceltaMl {
    FINTE,
    REALI,
    ;

    companion object {
        const val PROPRIETA = "snastro.ml"

        fun da(valore: String?): SceltaMl = if (valore.equals("finte", ignoreCase = true)) FINTE else REALI

        fun daSistema(): SceltaMl = da(System.getProperty(PROPRIETA))
    }
}
