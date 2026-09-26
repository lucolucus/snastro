package snastro.modelli

import java.nio.file.Path

/**
 * Seam over the two low-level primitives [InstallatoreAsset]'s FILE install needs (ADR 0025 §3):
 * the verified `.part` is MOVED into place, **never copied** — copying a 6.6 GB asset would need 2×
 * its size in free space and minutes of I/O. [InstallatoreAsset] itself owns the policy (try
 * [spostaAtomico], fall back to [sposta] on [java.nio.file.AtomicMoveNotSupportedException]); this
 * interface exists only so a test can force that fallback, which a real filesystem within one
 * store almost never refuses on its own.
 *
 * `public` for the same reason [ProvisioningModelli]'s `cliente`/`timeoutInattivita` constructor
 * parameters are: an injectable technical seam, not a pinned cross-module type.
 */
public interface OperazioniFile {
    /** Same-store atomic rename; throws [java.nio.file.AtomicMoveNotSupportedException] when unsupported. */
    public fun spostaAtomico(sorgente: Path, destinazione: Path)

    /** A plain move — still never a copy — used as the fallback. */
    public fun sposta(sorgente: Path, destinazione: Path)
}
