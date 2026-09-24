package snastro.ui.stile

import snastro.ui.modelli.LicenzaVista

/**
 * AC-556: the three bundled OFL fonts (AC-554), in the shape the S5 licences screen already uses
 * ([LicenzaVista]). NOT wired into the assembled S5 list here — that composition happens in
 * `:avvio` (`ServizioModelliProvisioning.licenze()`,
 * `avvio/src/main/kotlin/snastro/avvio/r1/ServizioModelliProvisioning.kt`), which reads `:modelli`'s
 * catalogue and is outside this block's `:ui` boundary (golden rule: never cross a boundary from a
 * worker dispatch). Whoever next touches that composition (wave 16, or a dedicated `:avvio` task)
 * appends `LICENZE_CARATTERI` to its returned list.
 */
public val LICENZE_CARATTERI: List<LicenzaVista> = listOf(
    LicenzaVista(
        nome = "Instrument Sans",
        ruolo = "Carattere dell'interfaccia",
        licenza = "SIL OFL 1.1",
        attribuzione = "The Instrument Sans Project Authors",
    ),
    LicenzaVista(
        nome = "Source Serif 4",
        ruolo = "Carattere di lettura",
        licenza = "SIL OFL 1.1",
        attribuzione = "Adobe (Frank Grießhammer)",
    ),
    LicenzaVista(
        nome = "JetBrains Mono",
        ruolo = "Carattere dei tempi",
        licenza = "SIL OFL 1.1",
        attribuzione = "The JetBrains Mono Project Authors",
    ),
)
