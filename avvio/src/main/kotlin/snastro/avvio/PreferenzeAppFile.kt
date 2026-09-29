package snastro.avvio

import snastro.ui.impostazioni.Preferenze
import snastro.ui.impostazioni.PreferenzeApp
import snastro.ui.impostazioni.TemaApp
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

private const val CHIAVE_TEMA = "tema"
private const val CHIAVE_CARTELLA_PROGETTI = "cartellaProgetti"

/**
 * [PreferenzeApp] over a small `.properties` [file] in the per-user app-data folder (AC-348's, next to the project
 * registry — never inside a project folder). [leggi] never fails: a missing, unreadable or partly invalid file gives
 * the defaults for what it cannot read. [salva] writes a sibling temp file and moves it over [file], so a crash
 * mid-write never leaves a truncated file behind.
 */
internal class PreferenzeAppFile(private val file: Path) : PreferenzeApp {
    override fun leggi(): Preferenze {
        val proprieta = Properties()
        try {
            if (Files.isRegularFile(file)) Files.newBufferedReader(file).use { proprieta.load(it) }
        } catch (
            @Suppress("SwallowedException") e: IOException,
        ) {
            return Preferenze()
        }
        return Preferenze(
            tema = TemaApp.entries.firstOrNull { it.name == proprieta.getProperty(CHIAVE_TEMA) } ?: TemaApp.SISTEMA,
            cartellaProgetti = proprieta.getProperty(CHIAVE_CARTELLA_PROGETTI)?.takeIf { it.isNotBlank() },
        )
    }

    override fun salva(preferenze: Preferenze) {
        val proprieta = Properties()
        proprieta.setProperty(CHIAVE_TEMA, preferenze.tema.name)
        preferenze.cartellaProgetti?.let { proprieta.setProperty(CHIAVE_CARTELLA_PROGETTI, it) }
        Files.createDirectories(file.toAbsolutePath().parent)
        val temporaneo = file.resolveSibling("${file.fileName}.tmp")
        Files.newBufferedWriter(temporaneo).use { proprieta.store(it, "snastro — preferenze") }
        Files.move(temporaneo, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}

/** The app's preferences file inside [cartellaRegistro] (the per-user app-data folder). */
internal fun preferenzeAppFile(cartellaRegistro: Path): PreferenzeApp =
    PreferenzeAppFile(cartellaRegistro.resolve("preferenze.properties"))
