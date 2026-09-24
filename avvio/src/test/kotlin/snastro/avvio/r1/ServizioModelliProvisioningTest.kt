package snastro.avvio.r1

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.modelli.CatalogoModelli
import snastro.modelli.ErroreModelli
import snastro.modelli.FormatoVoce
import snastro.modelli.ProvisioningModelli
import snastro.modelli.VoceCatalogo
import snastro.ui.modelli.ErroreServizioModelli
import snastro.ui.modelli.LicenzaVista
import snastro.ui.modelli.StatoModelli
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ServizioModelliProvisioningTest {
    @TempDir
    lateinit var cartella: Path

    private val voce = VoceCatalogo(
        id = "vad-prova",
        ruolo = "vad",
        url = "https://example.invalid/vad.onnx",
        sha256 = "00",
        dimensioneByte = 1_000,
        formato = FormatoVoce.FILE,
        licenza = "MIT",
        attribuzione = "Autori di prova",
    )
    private val catalogo = CatalogoModelli(listOf(voce))

    @Test
    fun `AC-329 ogni variante di ErroreModelli e mappata 1 a 1 nella variante omonima di ErroreServizioModelli`() {
        val tabella = listOf(
            ErroreModelli.HashNonValido("m1") to ErroreServizioModelli.HashNonValido("m1"),
            ErroreModelli.ArchivioNonValido("m2", "fuori cartella") to ErroreServizioModelli.ArchivioNonValido("m2"),
            ErroreModelli.ReteAssente to ErroreServizioModelli.ReteAssente,
            ErroreModelli.ScritturaFallita("disco pieno") to ErroreServizioModelli.ScritturaFallita("disco pieno"),
            ErroreModelli.DownloadFallito("HTTP 500") to ErroreServizioModelli.DownloadFallito("HTTP 500"),
        )

        tabella.forEach { (modelli, ui) -> assertEquals(ui, mappaErrore(modelli), "mappatura di $modelli") }
    }

    @Test
    fun `AC-329 un Errore di scarica arriva come StatoModelli Errore mappato`() {
        val servizio = servizio(scarica = { Esito.Errore(ErroreModelli.HashNonValido("vad-prova")) })

        servizio.scarica()

        assertEquals(StatoModelli.Errore(ErroreServizioModelli.HashNonValido("vad-prova")), servizio.stato.value)
    }

    @Test
    fun `AC-329 un ErroreDominio che non e di modelli diventa DownloadFallito, mai un crash`() {
        val servizio = servizio(scarica = { Esito.Errore(ErroreDiProva.Fallito("x")) })

        servizio.scarica()

        assertIs<ErroreServizioModelli.DownloadFallito>(assertIs<StatoModelli.Errore>(servizio.stato.value).errore)
    }

    @Test
    fun `AC-352 una IOException da scarica diventa ScritturaFallita, mai InDownload, e Riprova la ripete`() {
        var chiamate = 0
        var installato = false
        val servizio = servizio(
            pronti = { installato },
            scarica = { progresso ->
                chiamate++
                progresso("vad-prova", 10, 1_000)
                if (chiamate == 1) throw IOException("disco pieno")
                installato = true
                Esito.Ok(Unit)
            },
        )

        servizio.scarica()
        assertEquals(StatoModelli.Errore(ErroreServizioModelli.ScritturaFallita("disco pieno")), servizio.stato.value)

        servizio.scarica() // 'Riprova'
        assertEquals(2, chiamate)
        assertEquals(StatoModelli.Pronti, servizio.stato.value)
    }

    @Test
    fun `AC-352 una InvalidPathException da scarica diventa ScritturaFallita`() {
        val servizio = servizio(scarica = { throw InvalidPathException("x\u0000", "carattere nullo") })

        servizio.scarica()

        assertIs<ErroreServizioModelli.ScritturaFallita>(assertIs<StatoModelli.Errore>(servizio.stato.value).errore)
    }

    @Test
    fun `AC-352 pronti che lancia all avvio da Mancanti sull intero catalogo, l app parte comunque`() {
        val servizio = servizio(pronti = { throw IOException("cartella illeggibile") })

        assertEquals(StatoModelli.Mancanti(1, 1_000), servizio.stato.value)
    }

    @Test
    fun `AC-228 durante scarica lo stato passa per InDownload con i byte del modello`() {
        val visti = mutableListOf<StatoModelli>()
        lateinit var servizio: ServizioModelliProvisioning
        servizio = servizio(scarica = { progresso ->
            progresso("vad-prova", 500, 1_000)
            visti += servizio.stato.value
            Esito.Ok(Unit)
        })

        servizio.scarica()

        assertEquals(listOf<StatoModelli>(StatoModelli.InDownload("vad-prova", 500, 1_000)), visti)
    }

    @Test
    fun `AC-231 licenze elenca ogni voce del catalogo`() {
        assertEquals(listOf(LicenzaVista("vad-prova", "vad", "MIT", "Autori di prova")), servizio().licenze())
    }

    @Test
    fun `sopra ProvisioningModelli reale un catalogo vuoto e subito Pronti, uno non installato e Mancanti`() {
        val vuoto = CatalogoModelli(emptyList())
        assertEquals(
            StatoModelli.Pronti,
            ServizioModelliProvisioning.di(vuoto, ProvisioningModelli(vuoto, cartella)).stato.value,
        )
        assertEquals(
            StatoModelli.Mancanti(1, 1_000),
            ServizioModelliProvisioning.di(catalogo, ProvisioningModelli(catalogo, cartella)).stato.value,
        )
    }

    private fun servizio(
        pronti: () -> Boolean = { false },
        scarica: ((String, Long, Long) -> Unit) -> Esito<Unit> = { Esito.Ok(Unit) },
    ) = ServizioModelliProvisioning(catalogo, pronti, { if (pronti()) emptyList() else listOf(voce) }, scarica)
}
