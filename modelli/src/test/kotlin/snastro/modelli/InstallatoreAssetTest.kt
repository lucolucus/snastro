package snastro.modelli

import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * [InstallatoreAsset] real-on-real: `.tar.bz2` archives built programmatically (AC-330/AC-332) —
 * no HTTP involved, [ScaricamentoAsset] already proved the download+verify half (AC-129/AC-337).
 */
class InstallatoreAssetTest {
    private val cartella: Path = Files.createTempDirectory("installatore-asset-test")

    @Test
    fun `AC-330 estrae ogni voce e rimuove la cartella di primo livello comune`() {
        val encoder = "dati encoder".toByteArray()
        val tokens = "dati tokens".toByteArray()
        val archivio = ArchivioDiProva.tarBz2("top/encoder.onnx" to encoder, "top/tokens.txt" to tokens)
        val voce = unaVoceTar("modello", archivio)
        scriviParziale(voce, archivio)

        val esito = InstallatoreAsset(voce, cartella).installa()

        esito.atteso()
        val installata = cartella.resolve("modello")
        assertContentEquals(encoder, Files.readAllBytes(installata.resolve("encoder.onnx")))
        assertContentEquals(tokens, Files.readAllBytes(installata.resolve("tokens.txt")))
        assertEquals(voce.sha256, Files.readString(installata.resolve(".sha256")).trim())
        assertFalse(Files.exists(cartella.resolve("modello.part")))
        assertFalse(Files.exists(cartella.resolve("modello.tmp-0")))
    }

    @Test
    fun `AC-331 formato FILE sposta il file verificato con il nome dell url dentro la directory`() {
        val contenuto = "pesi del modello".toByteArray()
        val voce = unaVoceFile("singolo", contenuto, "silero_vad.onnx")
        scriviParziale(voce, contenuto)

        val esito = InstallatoreAsset(voce, cartella).installa()

        esito.atteso()
        assertContentEquals(contenuto, Files.readAllBytes(cartella.resolve("singolo/silero_vad.onnx")))
        assertFalse(Files.exists(cartella.resolve("singolo.part")))
    }

    @Test
    fun `AC-332 una voce che esce dalla destinazione con doppio punto e rifiutata e nulla resta`() {
        val archivio = ArchivioDiProva.tarBz2("../evil" to "malintenzionato".toByteArray())
        val voce = unaVoceTar("modello", archivio)
        scriviParziale(voce, archivio)

        val esito = InstallatoreAsset(voce, cartella).installa()

        val errore = esito.erroreAtteso<ErroreModelli.ArchivioNonValido>()
        assertEquals("modello", errore.modelloId)
        assertFalse(Files.exists(cartella.resolve("modello")), "nulla e' rinominato al posto della destinazione")
        assertFalse(Files.exists(cartella.resolve("modello.tmp-0")), "nessuna directory temporanea resta")
    }

    @Test
    fun `AC-332 un percorso assoluto e neutralizzato dal tar, ma una fuga profonda con doppio punto e rifiutata`() {
        // Commons Compress spoglia lo slash iniziale gia' alla costruzione dell'entry: "/etc/passwd"
        // diventa "etc/passwd" e, una volta letto da TarArchiveInputStream, non e' piu' un
        // tentativo di fuga — l'unico segmento "etc" e' trattato come cartella di primo livello da
        // rimuovere (come per ogni archivio a voce singola) e "passwd" e' installato innocuamente.
        val innocuo = ArchivioDiProva.tarBz2("/etc/passwd" to "contenuto".toByteArray())
        val voceInnocua = unaVoceTar("innocuo", innocuo)
        scriviParziale(voceInnocua, innocuo)
        InstallatoreAsset(voceInnocua, cartella).installa().atteso()
        assertContentEquals("contenuto".toByteArray(), Files.readAllBytes(cartella.resolve("innocuo/passwd")))

        // Il vettore di fuga reale ed equivalente in un tar e' una sequenza di '..' che risale
        // oltre la destinazione (mai spogliata dal formato): rifiutata.
        val archivio = ArchivioDiProva.tarBz2("a/../../../etc/passwd" to "malintenzionato".toByteArray())
        val voce = unaVoceTar("modello", archivio)
        scriviParziale(voce, archivio)

        val esito = InstallatoreAsset(voce, cartella).installa()

        esito.erroreAtteso<ErroreModelli.ArchivioNonValido>()
        assertFalse(Files.exists(cartella.resolve("modello")))
        assertFalse(Files.exists(cartella.resolve("modello.tmp-0")))
    }

    @Test
    fun `AC-332 un link simbolico e rifiutato`() {
        val archivio = ArchivioDiProva.tarBz2ConSymlink("link", "/etc/passwd")
        val voce = unaVoceTar("modello", archivio)
        scriviParziale(voce, archivio)

        val esito = InstallatoreAsset(voce, cartella).installa()

        esito.erroreAtteso<ErroreModelli.ArchivioNonValido>()
        assertFalse(Files.exists(cartella.resolve("modello")))
        assertFalse(Files.exists(cartella.resolve("modello.tmp-0")))
    }

    @Test
    fun `AC-332 un link fisico e rifiutato`() {
        val archivio = ArchivioDiProva.tarBz2ConHardLink("link", "bersaglio")
        val voce = unaVoceTar("modello", archivio)
        scriviParziale(voce, archivio)

        val esito = InstallatoreAsset(voce, cartella).installa()

        esito.erroreAtteso<ErroreModelli.ArchivioNonValido>()
        assertFalse(Files.exists(cartella.resolve("modello")))
        assertFalse(Files.exists(cartella.resolve("modello.tmp-0")))
    }

    @Test
    fun `AC-333 sostituisce una directory installata precedente rinominandola da parte e poi eliminandola`() {
        val vecchia = cartella.resolve("modello")
        Files.createDirectories(vecchia)
        Files.writeString(vecchia.resolve("vecchio.onnx"), "vecchio contenuto")
        Files.writeString(vecchia.resolve(ProtocolloInstallazione.MARCATORE), "hash-vecchio")
        val contenuto = "nuovo contenuto".toByteArray()
        val voce = unaVoceFile("modello", contenuto, "nuovo.onnx")
        scriviParziale(voce, contenuto)

        val esito = InstallatoreAsset(voce, cartella).installa()

        esito.atteso()
        assertContentEquals(contenuto, Files.readAllBytes(cartella.resolve("modello/nuovo.onnx")))
        assertFalse(Files.exists(cartella.resolve("modello/vecchio.onnx")))
        Files.newDirectoryStream(cartella).use { flusso ->
            val resta = flusso.any { it.fileName.toString().contains(".old-") }
            assertFalse(resta, "la vecchia directory rinominata da parte e' stata eliminata")
        }
    }

    private fun scriviParziale(voce: VoceCatalogo, contenuto: ByteArray) {
        Files.write(cartella.resolve("${voce.id}.part"), contenuto)
    }

    private fun unaVoceTar(id: String, archivio: ByteArray): VoceCatalogo = VoceCatalogo(
        id = id,
        ruolo = "asr",
        url = "http://esempio.invalid/modelli/$id.tar.bz2",
        sha256 = sha256Esadecimale(archivio),
        dimensioneByte = archivio.size.toLong(),
        formato = FormatoVoce.TAR_BZ2,
        licenza = "MIT",
        attribuzione = "k2-fsa",
    )

    private fun unaVoceFile(id: String, contenuto: ByteArray, nomeFile: String): VoceCatalogo = VoceCatalogo(
        id = id,
        ruolo = "vad",
        url = "http://esempio.invalid/modelli/$id/$nomeFile",
        sha256 = sha256Esadecimale(contenuto),
        dimensioneByte = contenuto.size.toLong(),
        formato = FormatoVoce.FILE,
        licenza = "MIT",
        attribuzione = "k2-fsa",
    )

    private fun sha256Esadecimale(bytes: ByteArray): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
}
