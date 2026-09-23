package snastro.modelli

/**
 * How a [VoceCatalogo]'s downloaded asset is turned into its installed directory (ADR 0008
 * Amendment (c)).
 */
public enum class FormatoVoce {
    /** A k2-fsa `.tar.bz2` release: extracted, a single top-level directory stripped. */
    TAR_BZ2,

    /** A single asset, placed as `<id>/<file name of the url>`. */
    FILE,
}
