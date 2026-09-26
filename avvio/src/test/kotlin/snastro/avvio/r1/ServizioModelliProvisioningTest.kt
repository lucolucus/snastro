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
import snastro.ui.modelli.StatoModelloFacoltativo
import snastro.ui.stile.LICENZE_CARATTERI
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
            // ADR 0025 §3/tec-modelli-ui-facoltativo: no longer a ScritturaFallita stopgap (:ui now
            // has its own dedicated variant, AC-S34).
            ErroreModelli.SpazioInsufficiente(6_600_000_000) to
                ErroreServizioModelli.SpazioInsufficiente(6_600_000_000),
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
    fun `AC-S32 pronti che lancia conta solo le voci obbligatorie, mai una facoltativa`() {
        val facoltativa = voce.copy(id = "llm-facoltativo", dimensioneByte = 6_200_000_000, obbligatoria = false)
        val catalogoConFacoltativa = CatalogoModelli(listOf(voce, facoltativa))
        val servizio = ServizioModelliProvisioning(
            catalogoConFacoltativa,
            { throw IOException("cartella illeggibile") },
            { throw IOException("cartella illeggibile") },
            { Esito.Ok(Unit) },
            { false },
            { _, _ -> Esito.Ok(Unit) },
        )

        assertEquals(StatoModelli.Mancanti(1, voce.dimensioneByte), servizio.stato.value)
    }

    @Test
    fun `AC-S76 statoFacoltativi e seminato una volta per ogni voce facoltativa del catalogo, mai una obbligatoria`() {
        val facoltativa = voce.copy(id = "llm-facoltativo", dimensioneByte = 6_200_000_000, obbligatoria = false)
        val catalogoConFacoltativa = CatalogoModelli(listOf(voce, facoltativa))
        val servizio = ServizioModelliProvisioning(
            catalogoConFacoltativa,
            { true },
            { emptyList() },
            { Esito.Ok(Unit) },
            { false },
            { _, _ -> Esito.Ok(Unit) },
        )

        assertEquals(
            mapOf("llm-facoltativo" to StatoModelloFacoltativo.NonInstallato(6_200_000_000)),
            servizio.statoFacoltativi.value,
        )
    }

    @Test
    fun `AC-S76 una voce facoltativa gia installata semina Installato`() {
        val facoltativa = voce.copy(id = "llm-facoltativo", obbligatoria = false)
        val catalogoConFacoltativa = CatalogoModelli(listOf(facoltativa))
        val servizio = ServizioModelliProvisioning(
            catalogoConFacoltativa,
            { true },
            { emptyList() },
            { Esito.Ok(Unit) },
            { true },
            { _, _ -> Esito.Ok(Unit) },
        )

        assertEquals(StatoModelloFacoltativo.Installato, servizio.statoFacoltativi.value["llm-facoltativo"])
    }

    @Test
    fun `AC-S73 durante scaricaFacoltativo lo stato passa per InDownload con i byte del modello`() {
        val facoltativa = voce.copy(id = "llm-facoltativo", dimensioneByte = 1_000, obbligatoria = false)
        val catalogoConFacoltativa = CatalogoModelli(listOf(facoltativa))
        val visti = mutableListOf<StatoModelloFacoltativo?>()
        lateinit var servizio: ServizioModelliProvisioning
        servizio = ServizioModelliProvisioning(
            catalogoConFacoltativa,
            { true },
            { emptyList() },
            { Esito.Ok(Unit) },
            { false },
            { _, progresso ->
                progresso(500, 1_000)
                visti += servizio.statoFacoltativi.value["llm-facoltativo"]
                Esito.Ok(Unit)
            },
        )

        servizio.scaricaFacoltativo("llm-facoltativo")

        assertEquals(listOf<StatoModelloFacoltativo?>(StatoModelloFacoltativo.InDownload(500, 1_000)), visti)
    }

    @Test
    fun `AC-352 finding 53 una IOException da scaricaFacoltativo diventa Errore ScritturaFallita, mai un crash`() {
        val facoltativa = voce.copy(id = "llm-facoltativo", obbligatoria = false)
        val catalogoConFacoltativa = CatalogoModelli(listOf(facoltativa))
        val servizio = ServizioModelliProvisioning(
            catalogoConFacoltativa,
            { true },
            { emptyList() },
            { Esito.Ok(Unit) },
            { false },
            { _, _ -> throw IOException("disco pieno") },
        )

        servizio.scaricaFacoltativo("llm-facoltativo")

        val stato = servizio.statoFacoltativi.value["llm-facoltativo"]
        assertEquals(
            StatoModelloFacoltativo.Errore(ErroreServizioModelli.ScritturaFallita("disco pieno")),
            stato,
        )
    }

    @Test
    fun `AC-S75 dopo un riavvio un part parziale legge NonInstallato, scaricaFacoltativo lo riprende da dove era`() {
        val facoltativa = voce.copy(id = "llm-facoltativo", dimensioneByte = 6_000, obbligatoria = false)
        val catalogoConFacoltativa = CatalogoModelli(listOf(facoltativa))
        // "restart": a fresh instance, as a new app launch would build — a `.part` alone never counts
        // as installed (ADR 0008 (c)/AC-333), so `installata` still reads false here.
        var chiamate = 0
        val servizio = ServizioModelliProvisioning(
            catalogoConFacoltativa,
            { true },
            { emptyList() },
            { Esito.Ok(Unit) },
            { false },
            { _, progresso ->
                chiamate++
                progresso(2_000, 6_000) // resumes from the `.part`'s own bytes, never from 0
                Esito.Ok(Unit)
            },
        )
        assertEquals(StatoModelloFacoltativo.NonInstallato(6_000), servizio.statoFacoltativi.value["llm-facoltativo"])

        servizio.scaricaFacoltativo("llm-facoltativo")

        // `:avvio` never resets or restarts the download itself — it delegates the by-id `scarica`
        // call to `:modelli` UNCHANGED (ONE call, resuming from wherever the `.part` left off is
        // entirely `ProvisioningModelli`'s own tested resumability, ADR 0008 (c)/AC-131/AC-337).
        assertEquals(1, chiamate)
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
        assertEquals(
            listOf(LicenzaVista("vad-prova", "vad", "MIT", "Autori di prova")) + LICENZE_CARATTERI,
            servizio().licenze(),
        )
    }

    @Test
    fun `AC-556 licenze include anche i tre caratteri OFL in coda alla lista dei modelli`() {
        val licenze = servizio().licenze()

        assertEquals(LICENZE_CARATTERI, licenze.takeLast(LICENZE_CARATTERI.size))
    }

    @Test
    fun `AC-S76 licenze omette una voce facoltativa non installata, la include una volta installata`() {
        val facoltativa = voce.copy(id = "llm-facoltativo", ruolo = "llm", obbligatoria = false)
        val catalogoConFacoltativa = CatalogoModelli(listOf(voce, facoltativa))
        var installata = false
        val servizio = ServizioModelliProvisioning(
            catalogoConFacoltativa,
            { true },
            { emptyList() },
            { Esito.Ok(Unit) },
            { installata },
            { _, _ -> Esito.Ok(Unit) },
        )

        assertEquals(
            listOf(LicenzaVista("vad-prova", "vad", "MIT", "Autori di prova")) + LICENZE_CARATTERI,
            servizio.licenze(),
        )

        installata = true

        assertEquals(
            listOf(
                LicenzaVista("vad-prova", "vad", "MIT", "Autori di prova"),
                LicenzaVista("llm-facoltativo", "llm", "MIT", "Autori di prova"),
            ) + LICENZE_CARATTERI,
            servizio.licenze(),
        )
    }

    @Test
    fun `AC-S76 licenze non lancia se installata fallisce con IOException, la voce facoltativa resta omessa`() {
        val facoltativa = voce.copy(id = "llm-facoltativo", obbligatoria = false)
        val catalogoConFacoltativa = CatalogoModelli(listOf(voce, facoltativa))
        val servizio = ServizioModelliProvisioning(
            catalogoConFacoltativa,
            { true },
            { emptyList() },
            { Esito.Ok(Unit) },
            { throw IOException("cartella illeggibile") },
            { _, _ -> Esito.Ok(Unit) },
        )

        assertEquals(
            listOf(LicenzaVista("vad-prova", "vad", "MIT", "Autori di prova")) + LICENZE_CARATTERI,
            servizio.licenze(),
        )
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
    ) = ServizioModelliProvisioning(
        catalogo,
        pronti,
        { if (pronti()) emptyList() else listOf(voce) },
        scarica,
        { false },
        { _, _ -> Esito.Ok(Unit) },
    )
}
