package snastro.ui.testi

import snastro.sintesi.applicazione.letture.MotivoNonDisponibile
import snastro.sintesi.applicazione.porte.MotivoDownload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The Riassunto tab's pure text builders (AC-S125..S134/S137/S138). [messaggioFallimento] is checked
 * against EVERY current `snastro.sintesi.dominio.MotivoFallimento.codice` — read ONLY by reflection
 * (`Class.forName`, never a source-level `import`): CR-1(b) forbids `:ui` importing it (a plain
 * domain enum, not `ErroreSintesi`), and the Konsist CR-1 check scans every source set by package
 * name, tests included (`Konsist.scopeFromProject()`), so an import here would fail the gate exactly
 * as it would in `main`. Same technique as `MessaggiErroreTest`'s own `transizioneNonAmmessa`. A new
 * entry with no matching branch in `messaggioFallimento` silently falls back to its raw code (its own
 * `else`), so this test is what actually catches a forgotten branch, not the compiler.
 */
class TestiRiassuntoTest {
    @Test
    fun `AC-S125 la dimensione del modello in GB decimali con la virgola`() {
        assertEquals(
            "Per riassumere serve il modello di linguaggio (6,2 GB), da scaricare una volta sola.",
            messaggioModelloNonInstallato(6_169_341_984),
        )
        assertEquals("Scarica il modello (6,2 GB)", etichettaScaricaModello(6_169_341_984))
    }

    @Test
    fun `AC-S126 il progresso del download in GB decimali`() {
        assertEquals(
            "Scarico il modello… 2,1 di 6,2 GB",
            messaggioModelloInDownload(2_100_000_000, 6_169_341_984),
        )
    }

    @Test
    fun `AC-S127 un testo per ognuno dei motivi di download fallito`() {
        assertEquals(
            "La connessione si è interrotta.",
            messaggioDownloadFallito(MotivoDownload.ConnessioneInterrotta, 6_169_341_984),
        )
        assertEquals(
            "Il file scaricato non è integro.",
            messaggioDownloadFallito(MotivoDownload.FileNonIntegro, 6_169_341_984),
        )
        assertEquals(
            "Non c'è abbastanza spazio sul disco (servono 6,2 GB).",
            messaggioDownloadFallito(MotivoDownload.SpazioInsufficiente, 6_169_341_984),
        )
        assertEquals(
            "Non è stato possibile salvare il modello sul disco.",
            messaggioDownloadFallito(MotivoDownload.ScritturaFallita, 6_169_341_984),
        )
    }

    // Pre-release finding #149 (rework, MED): the SpazioInsufficiente message used to hardcode its own
    // byte size — a DIFFERENT `dimensioneModelloByte` here proves it is really a parameter, not a
    // disguised literal (this would still pass "6,2 GB" against the OLD constant even with a bug).
    @Test
    fun `AC-S127 rework SpazioInsufficiente usa la dimensione passata, mai un valore fisso`() {
        assertEquals(
            "Non c'è abbastanza spazio sul disco (servono 1,0 GB).",
            messaggioDownloadFallito(MotivoDownload.SpazioInsufficiente, 1_000_000_000),
        )
    }

    @Test
    fun `AC-S129 un testo per ognuno dei motivi di non disponibilita`() {
        assertEquals(
            "La registrazione è troppo lunga per il riassunto (oltre 1 h 10 circa).",
            messaggioNonDisponibile(MotivoNonDisponibile.TroppoLunga),
        )
        assertEquals(
            "Aspetta la fine della trascrizione.",
            messaggioNonDisponibile(MotivoNonDisponibile.ElaborazioneAperta(1)),
        )
    }

    @Test
    fun `AC-S134 messaggioFallimento copre ogni codice attuale di MotivoFallimento`() {
        val attesi = mapOf(
            "MODELLO_NON_DISPONIBILE" to "Il riassunto non è riuscito: modello non disponibile.",
            "ERRORE_MODELLO" to "Il riassunto non è riuscito: errore del modello.",
            "TROPPO_LUNGA" to "Il riassunto non è riuscito: registrazione troppo lunga.",
            "NESSUN_CONTENUTO_VERIFICABILE" to "Il riassunto non è riuscito: nessun contenuto verificabile.",
            "INTERROTTO" to "Il riassunto non è riuscito: interrotto.",
        )
        val classe = Class.forName("snastro.sintesi.dominio.MotivoFallimento")
        val getCodice = classe.getMethod("getCodice")
        val voci = classe.enumConstants.map { it as Enum<*> }
        assertEquals(
            attesi.keys,
            voci.map { it.name }.toSet(),
            "un membro di MotivoFallimento è cambiato: aggiorna questo test",
        )
        voci.forEach { voce ->
            val codice = getCodice.invoke(voce) as String
            assertEquals(attesi.getValue(voce.name), messaggioFallimento(codice))
        }
    }

    @Test
    fun `AC-S132 il testo degli omessi al singolare al plurale e assente`() {
        assertNull(testoOmessi(0))
        assertEquals("1 elemento omesso perché non trovavo la frase citata.", testoOmessi(1))
        assertEquals("3 elementi omessi perché non trovavo le frasi citate.", testoOmessi(3))
    }

    @Test
    fun `AC-S132 il testo dei metadati con e senza Argomento`() {
        assertEquals("Lunghezza massima: 2000 parole", testoMetadati(null, 2_000))
        assertEquals("Argomento: Via Roquel · Lunghezza massima: 2000 parole", testoMetadati("Via Roquel", 2_000))
    }

    @Test
    fun `AC-S137 il contatore dell Argomento`() {
        assertEquals("0/200", contatoreArgomento(0, 200))
        assertEquals("201/200", contatoreArgomento(201, 200))
    }

    @Test
    fun `AC-S137 l errore dell Argomento usa il limite passato`() {
        assertEquals("Al massimo 200 caratteri.", erroreArgomentoTroppoLungo(200))
    }

    @Test
    fun `AC-S138 l errore della lunghezza massima usa i limiti passati`() {
        assertEquals("Scegli fra 300 e 2500 parole.", erroreLunghezzaMassima(300, 2_500))
    }
}
