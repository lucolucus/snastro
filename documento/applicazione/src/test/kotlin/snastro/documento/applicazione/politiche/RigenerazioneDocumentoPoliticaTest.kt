package snastro.documento.applicazione.politiche

import snastro.documento.applicazione.porte.ErroreApplicazioneDocumento
import snastro.documento.applicazione.porte.LettoreNomiFinta
import snastro.documento.applicazione.porte.LettoreTrascritto
import snastro.documento.applicazione.porte.LettoreTrascrittoFinta
import snastro.documento.applicazione.porte.ScrittoreDocumentoFinta
import snastro.documento.applicazione.porte.SegmentoVista
import snastro.documento.applicazione.porte.TrascrittoTesto
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests of [RigenerazioneDocumentoPolitica]: AC-153..158, AC-327 and AC-155bis (rename, since
 * fix-batch-11 — a Registrazione can be renamed, [snastro.progetto.applicazione.eventi]
 * `RegistrazioneRinominata`, which must behave like a date change on `nomeFile`).
 */
class RigenerazioneDocumentoPoliticaTest {
    // --- AC-623: perRegistrazioneEliminata (ADR 0020 §3-§4), remove-only ------------------------

    /** A [LettoreTrascritto] that fails the test if read: the removal never reads the Trascritto. */
    private val lettoreVietato = object : LettoreTrascritto {
        override fun trascritto(id: RegistrazioneId): TrascrittoTesto? = error("perRegistrazioneEliminata non legge")

        override fun registrazioniConTrascritto(): List<RegistrazioneId> = error("perRegistrazioneEliminata non legge")
    }

    @Test
    fun `AC-623 perRegistrazioneEliminata rimuove il documento corrente e i nomi precedenti diversi senza scrivere`() {
        val scrittore = ScrittoreDocumentoFinta()
        listOf(
            "2026-09-12 Riunione.md",
            "2026-09-10 Riunione.md",
            "2026-09-12 Vecchio titolo.md",
            "2026-09-12 Altra.md",
        ).forEach { scrittore.scrivi(it, "testo") }
        val politica = RigenerazioneDocumentoPolitica(lettoreVietato, LettoreNomiFinta(), scrittore)
        val prima = scrittore.operazioni.size

        politica.perRegistrazioneEliminata(
            RegistrazioneId("reg-1"),
            LocalDate.of(2026, 9, 12),
            "Riunione",
            nomiPrecedenti = setOf("2026-09-10 Riunione.md", "2026-09-12 RIUNIONE.md", "2026-09-12 Vecchio titolo.md"),
        ).atteso()

        assertEquals(
            listOf(
                ScrittoreDocumentoFinta.Operazione.Rimosso("2026-09-12 Riunione.md"),
                ScrittoreDocumentoFinta.Operazione.Rimosso("2026-09-10 Riunione.md"),
                ScrittoreDocumentoFinta.Operazione.Rimosso("2026-09-12 Vecchio titolo.md"),
            ),
            scrittore.operazioni.drop(prima),
            "mai una scrittura; il nome che differisce solo per maiuscole e lo stesso file",
        )
        assertEquals(setOf("2026-09-12 Altra.md"), scrittore.documenti.keys, "mai il file di un altra Registrazione")
    }

    @Test
    fun `AC-623 perRegistrazioneEliminata su file gia assenti e Ok - idempotente`() {
        val scrittore = ScrittoreDocumentoFinta()
        val politica = RigenerazioneDocumentoPolitica(lettoreVietato, LettoreNomiFinta(), scrittore)

        repeat(2) {
            politica.perRegistrazioneEliminata(REG_ELIMINATA, DATA_ELIMINATA, "Riunione", emptySet()).atteso()
        }

        assertTrue(scrittore.documenti.isEmpty())
    }

    @Test
    fun `AC-623 perRegistrazioneEliminata con un errore di I-O e ScritturaFallita cosi il chiamante riprova`() {
        val scrittore = ScrittoreDocumentoFinta()
        scrittore.scrivi("2026-09-12 Riunione.md", "testo")
        scrittore.fallisciAllaProssimaRimozione()
        val politica = RigenerazioneDocumentoPolitica(lettoreVietato, LettoreNomiFinta(), scrittore)

        val errore = politica.perRegistrazioneEliminata(REG_ELIMINATA, DATA_ELIMINATA, "Riunione", emptySet())
            .erroreAtteso<ErroreApplicazioneDocumento.ScritturaFallita>()

        assertEquals("2026-09-12 Riunione.md", errore.nomeFile)
        assertEquals(setOf("2026-09-12 Riunione.md"), scrittore.documenti.keys)
    }

    @Test
    fun `AC-153 RigeneraDocumento scrive il markdown della proiezione con il nomeFile corretto`() {
        val id = RegistrazioneId("reg-1")
        val trascritti = LettoreTrascrittoFinta(mapOf(id to unTrascritto(id, titolo = "Riunione")))
        val nomi = LettoreNomiFinta(
            attribuzioni = mapOf(VoceRef(id, VoceId(1)) to PARLANTE),
            nomiParlanti = mapOf(PARLANTE to "Marco"),
        )
        val scrittore = ScrittoreDocumentoFinta()
        val politica = RigenerazioneDocumentoPolitica(trascritti, nomi, scrittore)

        val esito = politica.esegui(RigeneraDocumento(id))

        esito.atteso()
        assertEquals(setOf("2026-09-12 Riunione.md"), scrittore.documenti.keys)
        assertTrue(scrittore.documenti.getValue("2026-09-12 Riunione.md").contains("**Marco**"))
        assertEquals(listOf(ScrittoreDocumentoFinta.Operazione.Scritto("2026-09-12 Riunione.md")), scrittore.operazioni)
    }

    @Test
    fun `AC-154 RigeneraDocumento su una Registrazione senza Trascritto non scrive nulla ed e Ok`() {
        val id = RegistrazioneId("senza-trascritto")
        val scrittore = ScrittoreDocumentoFinta()
        val politica = RigenerazioneDocumentoPolitica(LettoreTrascrittoFinta(), LettoreNomiFinta(), scrittore)

        val esito = politica.esegui(RigeneraDocumento(id, nomeFilePrecedente = "2026-09-12 Vecchio.md"))

        esito.atteso()
        assertTrue(scrittore.operazioni.isEmpty())
    }

    @Test
    fun `AC-155 con una DataRegistrazioneModificata il nuovo file e scritto e poi il vecchio e rimosso`() {
        val id = RegistrazioneId("reg-data")
        // dopo il commit il Trascritto letto riflette gia' la data NUOVA; il titolo non e' cambiato.
        val nuova = unTrascritto(id, titolo = "Riunione", data = LocalDate.of(2026, 9, 20))
        val trascritti = LettoreTrascrittoFinta(mapOf(id to nuova))
        val scrittore = ScrittoreDocumentoFinta()
        val politica = RigenerazioneDocumentoPolitica(trascritti, LettoreNomiFinta(), scrittore)

        politica.perDataRegistrazioneModificata(id, precedente = LocalDate.of(2026, 9, 19)).atteso()

        assertEquals(
            listOf(
                ScrittoreDocumentoFinta.Operazione.Scritto("2026-09-20 Riunione.md"),
                ScrittoreDocumentoFinta.Operazione.Rimosso("2026-09-19 Riunione.md"),
            ),
            scrittore.operazioni,
        )
        assertEquals(setOf("2026-09-20 Riunione.md"), scrittore.documenti.keys)
    }

    @Test
    fun `AC-327 spostare la data di Riunione (2) su Riunione non tocca il file di Riunione`() {
        val riunione2 = RegistrazioneId("riunione-2")
        val scrittore = ScrittoreDocumentoFinta()
        // 'Riunione' ha gia' il suo documento scritto in precedenza (2026-09-12 Riunione.md).
        scrittore.scrivi("2026-09-12 Riunione.md", "# Riunione\n\ncontenuto originale\n")
        val trascritti = LettoreTrascrittoFinta(
            mapOf(riunione2 to unTrascritto(riunione2, titolo = "Riunione (2)", data = LocalDate.of(2026, 9, 12))),
        )
        val politica = RigenerazioneDocumentoPolitica(trascritti, LettoreNomiFinta(), scrittore)

        politica.perDataRegistrazioneModificata(riunione2, precedente = LocalDate.of(2026, 9, 13)).atteso()

        assertEquals(
            setOf("2026-09-12 Riunione.md", "2026-09-12 Riunione (2).md"),
            scrittore.documenti.keys,
        )
        assertEquals("# Riunione\n\ncontenuto originale\n", scrittore.documenti.getValue("2026-09-12 Riunione.md"))
        val rimozioneAltrui = ScrittoreDocumentoFinta.Operazione.Rimosso("2026-09-12 Riunione.md")
        val rimozioneAttesa = ScrittoreDocumentoFinta.Operazione.Rimosso("2026-09-13 Riunione (2).md")
        assertFalse(scrittore.operazioni.contains(rimozioneAltrui))
        assertTrue(scrittore.operazioni.contains(rimozioneAttesa))
    }

    @Test
    fun `AC-327 se precedente e nuova coincidono il file e riscritto e nulla e rimosso`() {
        val id = RegistrazioneId("riunione-2")
        val invariata = unTrascritto(id, titolo = "Riunione (2)", data = LocalDate.of(2026, 9, 12))
        val trascritti = LettoreTrascrittoFinta(mapOf(id to invariata))
        val scrittore = ScrittoreDocumentoFinta()
        val politica = RigenerazioneDocumentoPolitica(trascritti, LettoreNomiFinta(), scrittore)

        politica.perDataRegistrazioneModificata(id, precedente = LocalDate.of(2026, 9, 12)).atteso()

        assertEquals(
            listOf(ScrittoreDocumentoFinta.Operazione.Scritto("2026-09-12 Riunione (2).md")),
            scrittore.operazioni,
        )
    }

    @Test
    fun `AC-155bis (rename) con una RegistrazioneRinominata il nuovo file e scritto e poi il vecchio e rimosso`() {
        val id = RegistrazioneId("reg-titolo")
        // dopo il commit il Trascritto letto riflette gia' il titolo NUOVO; la data non e' cambiata.
        val nuovo = unTrascritto(id, titolo = "Riunione B", data = LocalDate.of(2026, 9, 12))
        val trascritti = LettoreTrascrittoFinta(mapOf(id to nuovo))
        val scrittore = ScrittoreDocumentoFinta()
        val politica = RigenerazioneDocumentoPolitica(trascritti, LettoreNomiFinta(), scrittore)

        politica.perRegistrazioneRinominata(id, precedente = "Riunione A").atteso()

        assertEquals(
            listOf(
                ScrittoreDocumentoFinta.Operazione.Scritto("2026-09-12 Riunione B.md"),
                ScrittoreDocumentoFinta.Operazione.Rimosso("2026-09-12 Riunione A.md"),
            ),
            scrittore.operazioni,
        )
        assertEquals(setOf("2026-09-12 Riunione B.md"), scrittore.documenti.keys)
    }

    @Test
    fun `AC-155bis (rename) una rinomina non tocca mai il file di un'altra Registrazione`() {
        val rinominata = RegistrazioneId("rinominata")
        val scrittore = ScrittoreDocumentoFinta()
        scrittore.scrivi("2026-09-12 Altra Riunione.md", "# Altra Riunione\n\noriginale\n")
        val trascritti = LettoreTrascrittoFinta(
            mapOf(rinominata to unTrascritto(rinominata, titolo = "Riunione Nuova", data = LocalDate.of(2026, 9, 12))),
        )
        val politica = RigenerazioneDocumentoPolitica(trascritti, LettoreNomiFinta(), scrittore)

        politica.perRegistrazioneRinominata(rinominata, precedente = "Riunione Vecchia").atteso()

        assertEquals(
            setOf("2026-09-12 Altra Riunione.md", "2026-09-12 Riunione Nuova.md"),
            scrittore.documenti.keys,
        )
        assertEquals("# Altra Riunione\n\noriginale\n", scrittore.documenti.getValue("2026-09-12 Altra Riunione.md"))
    }

    @Test
    fun `AC-155bis (rename) un cambio di sola maiuscola su filesystem case-insensitive riscrive e non rimuove`() {
        // pulisci() non normalizza il maiuscolo/minuscolo: 'riunione' e 'Riunione' producono nomeFile
        // DIVERSI come stringhe, ma sono lo STESSO file su un filesystem case-insensitive (APFS di
        // default): rimuovere il vecchio nome dopo aver scritto il nuovo cancellerebbe quanto appena
        // scritto. La Politica confronta con ignoreCase e non deve rimuovere nulla in questo caso.
        val id = RegistrazioneId("reg-maiuscolo")
        val attuale = unTrascritto(id, titolo = "Riunione", data = LocalDate.of(2026, 9, 12))
        val trascritti = LettoreTrascrittoFinta(mapOf(id to attuale))
        val scrittore = ScrittoreDocumentoFinta()
        val politica = RigenerazioneDocumentoPolitica(trascritti, LettoreNomiFinta(), scrittore)

        politica.perRegistrazioneRinominata(id, precedente = "riunione").atteso()

        assertEquals(listOf(ScrittoreDocumentoFinta.Operazione.Scritto("2026-09-12 Riunione.md")), scrittore.operazioni)
        assertEquals(setOf("2026-09-12 Riunione.md"), scrittore.documenti.keys)
    }

    @Test
    fun `AC-156 ParlanteRinominato rigenera tutte e sole le Registrazioni con un'Attribuzione a P`() {
        val conAttribuzione1 = RegistrazioneId("con-attribuzione-1")
        val conAttribuzione2 = RegistrazioneId("con-attribuzione-2")
        val senzaAttribuzione = RegistrazioneId("senza-attribuzione")
        val altroParlante = ParlanteId("altro-parlante")
        val trascritti = LettoreTrascrittoFinta(
            mapOf(
                conAttribuzione1 to unTrascritto(conAttribuzione1, titolo = "Uno"),
                conAttribuzione2 to unTrascritto(conAttribuzione2, titolo = "Due"),
                senzaAttribuzione to unTrascritto(senzaAttribuzione, titolo = "Tre"),
            ),
        )
        val nomi = LettoreNomiFinta(
            attribuzioni = mapOf(
                VoceRef(conAttribuzione1, VoceId(1)) to PARLANTE,
                VoceRef(conAttribuzione2, VoceId(1)) to PARLANTE,
                VoceRef(senzaAttribuzione, VoceId(1)) to altroParlante,
            ),
            nomiParlanti = mapOf(PARLANTE to "Marco Rossi", altroParlante to "Anna"),
        )
        val scrittore = ScrittoreDocumentoFinta()
        val politica = RigenerazioneDocumentoPolitica(trascritti, nomi, scrittore)

        politica.perParlanteRinominato(PARLANTE).atteso()

        assertEquals(setOf("2026-09-12 Uno.md", "2026-09-12 Due.md"), scrittore.documenti.keys)
    }

    @Test
    fun `AC-156 ParlantePromosso con nomeCambiato false non scrive nulla`() {
        val id = RegistrazioneId("con-attribuzione")
        val trascritti = LettoreTrascrittoFinta(mapOf(id to unTrascritto(id)))
        val nomi = LettoreNomiFinta(
            attribuzioni = mapOf(VoceRef(id, VoceId(1)) to PARLANTE),
            nomiParlanti = mapOf(PARLANTE to "Marco"),
        )
        val scrittore = ScrittoreDocumentoFinta()
        val politica = RigenerazioneDocumentoPolitica(trascritti, nomi, scrittore)

        politica.perParlantePromosso(PARLANTE, nomeCambiato = false).atteso()

        assertTrue(scrittore.operazioni.isEmpty())
    }

    @Test
    fun `AC-156 ParlantePromosso con nomeCambiato true rigenera le Registrazioni attribuite a P`() {
        val id = RegistrazioneId("con-attribuzione")
        val trascritti = LettoreTrascrittoFinta(mapOf(id to unTrascritto(id)))
        val nomi = LettoreNomiFinta(
            attribuzioni = mapOf(VoceRef(id, VoceId(1)) to PARLANTE),
            nomiParlanti = mapOf(PARLANTE to "Marco"),
        )
        val scrittore = ScrittoreDocumentoFinta()
        val politica = RigenerazioneDocumentoPolitica(trascritti, nomi, scrittore)

        politica.perParlantePromosso(PARLANTE, nomeCambiato = true).atteso()

        assertEquals(setOf("2026-09-12 Riunione.md"), scrittore.documenti.keys)
    }

    @Test
    fun `AC-156 ParlanteEliminato non scrive nulla`() {
        val scrittore = ScrittoreDocumentoFinta()
        val politica = RigenerazioneDocumentoPolitica(LettoreTrascrittoFinta(), LettoreNomiFinta(), scrittore)

        politica.perParlanteEliminato(PARLANTE).atteso()

        assertTrue(scrittore.operazioni.isEmpty())
    }

    @Test
    fun `AC-157 un errore di scrittura diventa Esito Errore`() {
        val id = RegistrazioneId("reg-1")
        val trascritti = LettoreTrascrittoFinta(mapOf(id to unTrascritto(id)))
        val scrittore = ScrittoreDocumentoFinta()
        scrittore.fallisciAllaProssimaScrittura()
        val politica = RigenerazioneDocumentoPolitica(trascritti, LettoreNomiFinta(), scrittore)

        val esito = politica.esegui(RigeneraDocumento(id))

        esito.erroreAtteso<ErroreApplicazioneDocumento.ScritturaFallita>()
        assertTrue(scrittore.documenti.isEmpty())
    }

    @Test
    fun `AC-157 un errore di rimozione diventa Esito Errore ma il nuovo file resta scritto`() {
        val id = RegistrazioneId("reg-data")
        val trascritti = LettoreTrascrittoFinta(mapOf(id to unTrascritto(id, data = LocalDate.of(2026, 9, 20))))
        val scrittore = ScrittoreDocumentoFinta()
        scrittore.fallisciAllaProssimaRimozione()
        val politica = RigenerazioneDocumentoPolitica(trascritti, LettoreNomiFinta(), scrittore)

        val esito = politica.esegui(RigeneraDocumento(id, nomeFilePrecedente = "2026-09-19 Riunione.md"))

        esito.erroreAtteso<ErroreApplicazioneDocumento.ScritturaFallita>()
        assertTrue(scrittore.documenti.containsKey("2026-09-20 Riunione.md"))
    }

    @Test
    fun `AC-157 RigeneraTuttiIDocumenti propaga un errore di scrittura e si ferma`() {
        val prima = RegistrazioneId("prima")
        val seconda = RegistrazioneId("seconda")
        val trascritti = LettoreTrascrittoFinta(
            mapOf(prima to unTrascritto(prima, titolo = "Prima"), seconda to unTrascritto(seconda, titolo = "Seconda")),
        )
        val scrittore = ScrittoreDocumentoFinta()
        scrittore.fallisciAllaProssimaScrittura()
        val politica = RigenerazioneDocumentoPolitica(trascritti, LettoreNomiFinta(), scrittore)

        val esito = politica.esegui(RigeneraTuttiIDocumenti)

        esito.erroreAtteso<ErroreApplicazioneDocumento.ScritturaFallita>()
        assertTrue(scrittore.documenti.isEmpty())
    }

    @Test
    fun `AC-158 RigeneraTuttiIDocumenti rigenera ogni Registrazione con Trascritto`() {
        val prima = RegistrazioneId("prima")
        val seconda = RegistrazioneId("seconda")
        val trascritti = LettoreTrascrittoFinta(
            mapOf(prima to unTrascritto(prima, titolo = "Prima"), seconda to unTrascritto(seconda, titolo = "Seconda")),
        )
        val scrittore = ScrittoreDocumentoFinta()
        val politica = RigenerazioneDocumentoPolitica(trascritti, LettoreNomiFinta(), scrittore)

        politica.esegui(RigeneraTuttiIDocumenti).atteso()

        assertEquals(setOf("2026-09-12 Prima.md", "2026-09-12 Seconda.md"), scrittore.documenti.keys)
    }

    private companion object {
        val PARLANTE = ParlanteId("parlante-1")
        val REG_ELIMINATA = RegistrazioneId("reg-1")
        val DATA_ELIMINATA: LocalDate = LocalDate.of(2026, 9, 12)

        fun unTrascritto(
            id: RegistrazioneId,
            titolo: String = "Riunione",
            data: LocalDate = LocalDate.of(2026, 9, 12),
        ) = TrascrittoTesto(
            registrazioneId = id,
            titolo = titolo,
            dataRegistrazione = data,
            segmenti = listOf(SegmentoVista(SegmentoId(1), VoceId(1), IntervalloMs(0, 1_000), "Ciao.")),
        )
    }
}
