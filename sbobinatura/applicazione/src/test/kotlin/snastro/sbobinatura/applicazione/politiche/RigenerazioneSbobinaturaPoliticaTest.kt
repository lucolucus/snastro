package snastro.sbobinatura.applicazione.politiche

import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.kernel.unIncontroDi
import snastro.sbobinatura.applicazione.porte.ErroreApplicazioneSbobinatura
import snastro.sbobinatura.applicazione.porte.LettoreNomiFinta
import snastro.sbobinatura.applicazione.porte.LettoreTrascritto
import snastro.sbobinatura.applicazione.porte.LettoreTrascrittoFinta
import snastro.sbobinatura.applicazione.porte.ScrittoreSbobinaturaFinta
import snastro.sbobinatura.applicazione.porte.SegmentoVista
import snastro.sbobinatura.applicazione.porte.TrascrittoTesto
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests of [RigenerazioneSbobinaturaPolitica]: AC-153..157, AC-327 and AC-155bis (rename, since
 * fix-batch-11 — a Registrazione can be renamed, [snastro.progetto.applicazione.eventi]
 * `RegistrazioneRinominata`, which must behave like a date change on `nomeFile`). AC-158 (every
 * Registrazione with a Trascritto regenerated at startup) is exercised on its real path — the
 * `AbbonatoSbobinaturaEventi` fan-out sweep, `sbobinatura:adattatori`'s own AC-185 — since the
 * `RigeneraTuttiIDocumenti` fold this file used to test it through was retired as dead code (B51
 * pre-release triage, 2026-09-29).
 */
class RigenerazioneSbobinaturaPoliticaTest {
    // --- AC-623: perRegistrazioneEliminata (ADR 0020 §3-§4), remove-only ------------------------

    /** A [LettoreTrascritto] that fails the test if read: the removal never reads the Trascritto. */
    private val lettoreVietato = object : LettoreTrascritto {
        override fun trascritto(id: RegistrazioneId): TrascrittoTesto? = error("perRegistrazioneEliminata non legge")

        override fun partiConTrascritto(incontroId: IncontroId): List<RegistrazioneId> =
            error("perRegistrazioneEliminata non legge")

        override fun registrazioniConTrascritto(): List<RegistrazioneId> = error("perRegistrazioneEliminata non legge")
    }

    @Test
    fun `AC-623 perRegistrazioneEliminata rimuove la sbobinatura e i nomi precedenti diversi senza scrivere`() {
        val scrittore = ScrittoreSbobinaturaFinta()
        listOf(
            "2026-09-12 Riunione.md",
            "2026-09-10 Riunione.md",
            "2026-09-12 Vecchio titolo.md",
            "2026-09-12 Altra.md",
        ).forEach { scrittore.scrivi(it, "testo") }
        val politica = RigenerazioneSbobinaturaPolitica(lettoreVietato, LettoreNomiFinta(), scrittore)
        val prima = scrittore.operazioni.size

        politica.perRegistrazioneEliminata(
            RegistrazioneId("reg-1"),
            LocalDate.of(2026, 9, 12),
            "Riunione",
            nomiPrecedenti = setOf("2026-09-10 Riunione.md", "2026-09-12 RIUNIONE.md", "2026-09-12 Vecchio titolo.md"),
        ).atteso()

        assertEquals(
            listOf(
                ScrittoreSbobinaturaFinta.Operazione.Rimosso("2026-09-12 Riunione.md"),
                ScrittoreSbobinaturaFinta.Operazione.Rimosso("2026-09-10 Riunione.md"),
                ScrittoreSbobinaturaFinta.Operazione.Rimosso("2026-09-12 Vecchio titolo.md"),
            ),
            scrittore.operazioni.drop(prima),
            "mai una scrittura; il nome che differisce solo per maiuscole e lo stesso file",
        )
        assertEquals(setOf("2026-09-12 Altra.md"), scrittore.sbobinature.keys, "mai il file di un altra Registrazione")
    }

    @Test
    fun `AC-623 perRegistrazioneEliminata su file gia assenti e Ok - idempotente`() {
        val scrittore = ScrittoreSbobinaturaFinta()
        val politica = RigenerazioneSbobinaturaPolitica(lettoreVietato, LettoreNomiFinta(), scrittore)

        repeat(2) {
            politica.perRegistrazioneEliminata(REG_ELIMINATA, DATA_ELIMINATA, "Riunione", emptySet()).atteso()
        }

        assertTrue(scrittore.sbobinature.isEmpty())
    }

    @Test
    fun `AC-623 perRegistrazioneEliminata con un errore di I-O e ScritturaFallita cosi il chiamante riprova`() {
        val scrittore = ScrittoreSbobinaturaFinta()
        scrittore.scrivi("2026-09-12 Riunione.md", "testo")
        scrittore.fallisciAllaProssimaRimozione()
        val politica = RigenerazioneSbobinaturaPolitica(lettoreVietato, LettoreNomiFinta(), scrittore)

        val errore = politica.perRegistrazioneEliminata(REG_ELIMINATA, DATA_ELIMINATA, "Riunione", emptySet())
            .erroreAtteso<ErroreApplicazioneSbobinatura.ScritturaFallita>()

        assertEquals("2026-09-12 Riunione.md", errore.nomeFile)
        assertEquals(setOf("2026-09-12 Riunione.md"), scrittore.sbobinature.keys)
    }

    @Test
    fun `AC-153 RigeneraSbobinatura scrive il markdown della proiezione con il nomeFile corretto`() {
        val id = RegistrazioneId("reg-1")
        val trascritti = LettoreTrascrittoFinta(mapOf(id to unTrascritto(id, titolo = "Riunione")))
        val nomi = LettoreNomiFinta(
            attribuzioni = mapOf(VoceRef(unIncontroDi(id), VoceId(1)) to PARLANTE),
            nomiParlanti = mapOf(PARLANTE to "Marco"),
        )
        val scrittore = ScrittoreSbobinaturaFinta()
        val politica = RigenerazioneSbobinaturaPolitica(trascritti, nomi, scrittore)

        val esito = politica.esegui(RigeneraSbobinatura(id))

        esito.atteso()
        assertEquals(setOf("2026-09-12 Riunione.md"), scrittore.sbobinature.keys)
        assertTrue(scrittore.sbobinature.getValue("2026-09-12 Riunione.md").contains("**Marco**"))
        assertEquals(
            listOf(ScrittoreSbobinaturaFinta.Operazione.Scritto("2026-09-12 Riunione.md")),
            scrittore.operazioni,
        )
    }

    @Test
    fun `AC-154 RigeneraSbobinatura su una Registrazione senza Trascritto non scrive nulla ed e Ok`() {
        val id = RegistrazioneId("senza-trascritto")
        val scrittore = ScrittoreSbobinaturaFinta()
        val politica = RigenerazioneSbobinaturaPolitica(LettoreTrascrittoFinta(), LettoreNomiFinta(), scrittore)

        val esito = politica.esegui(RigeneraSbobinatura(id, nomeFilePrecedente = "2026-09-12 Vecchio.md"))

        esito.atteso()
        assertTrue(scrittore.operazioni.isEmpty())
    }

    @Test
    fun `AC-155 con una DataRegistrazioneModificata il nuovo file e scritto e poi il vecchio e rimosso`() {
        val id = RegistrazioneId("reg-data")
        // dopo il commit il Trascritto letto riflette gia' la data NUOVA; il titolo non e' cambiato.
        val nuova = unTrascritto(id, titolo = "Riunione", data = LocalDate.of(2026, 9, 20))
        val trascritti = LettoreTrascrittoFinta(mapOf(id to nuova))
        val scrittore = ScrittoreSbobinaturaFinta()
        val politica = RigenerazioneSbobinaturaPolitica(trascritti, LettoreNomiFinta(), scrittore)

        politica.perDataRegistrazioneModificata(id, precedente = LocalDate.of(2026, 9, 19)).atteso()

        assertEquals(
            listOf(
                ScrittoreSbobinaturaFinta.Operazione.Scritto("2026-09-20 Riunione.md"),
                ScrittoreSbobinaturaFinta.Operazione.Rimosso("2026-09-19 Riunione.md"),
            ),
            scrittore.operazioni,
        )
        assertEquals(setOf("2026-09-20 Riunione.md"), scrittore.sbobinature.keys)
    }

    @Test
    fun `AC-327 spostare la data di Riunione (2) su Riunione non tocca il file di Riunione`() {
        val riunione2 = RegistrazioneId("riunione-2")
        val scrittore = ScrittoreSbobinaturaFinta()
        // 'Riunione' ha gia' la sua sbobinatura scritta in precedenza (2026-09-12 Riunione.md).
        scrittore.scrivi("2026-09-12 Riunione.md", "# Riunione\n\ncontenuto originale\n")
        val trascritti = LettoreTrascrittoFinta(
            mapOf(riunione2 to unTrascritto(riunione2, titolo = "Riunione (2)", data = LocalDate.of(2026, 9, 12))),
        )
        val politica = RigenerazioneSbobinaturaPolitica(trascritti, LettoreNomiFinta(), scrittore)

        politica.perDataRegistrazioneModificata(riunione2, precedente = LocalDate.of(2026, 9, 13)).atteso()

        assertEquals(
            setOf("2026-09-12 Riunione.md", "2026-09-12 Riunione (2).md"),
            scrittore.sbobinature.keys,
        )
        assertEquals("# Riunione\n\ncontenuto originale\n", scrittore.sbobinature.getValue("2026-09-12 Riunione.md"))
        val rimozioneAltrui = ScrittoreSbobinaturaFinta.Operazione.Rimosso("2026-09-12 Riunione.md")
        val rimozioneAttesa = ScrittoreSbobinaturaFinta.Operazione.Rimosso("2026-09-13 Riunione (2).md")
        assertFalse(scrittore.operazioni.contains(rimozioneAltrui))
        assertTrue(scrittore.operazioni.contains(rimozioneAttesa))
    }

    @Test
    fun `AC-327 se precedente e nuova coincidono il file e riscritto e nulla e rimosso`() {
        val id = RegistrazioneId("riunione-2")
        val invariata = unTrascritto(id, titolo = "Riunione (2)", data = LocalDate.of(2026, 9, 12))
        val trascritti = LettoreTrascrittoFinta(mapOf(id to invariata))
        val scrittore = ScrittoreSbobinaturaFinta()
        val politica = RigenerazioneSbobinaturaPolitica(trascritti, LettoreNomiFinta(), scrittore)

        politica.perDataRegistrazioneModificata(id, precedente = LocalDate.of(2026, 9, 12)).atteso()

        assertEquals(
            listOf(ScrittoreSbobinaturaFinta.Operazione.Scritto("2026-09-12 Riunione (2).md")),
            scrittore.operazioni,
        )
    }

    @Test
    fun `AC-155bis (rename) con una RegistrazioneRinominata il nuovo file e scritto e poi il vecchio e rimosso`() {
        val id = RegistrazioneId("reg-titolo")
        // dopo il commit il Trascritto letto riflette gia' il titolo NUOVO; la data non e' cambiata.
        val nuovo = unTrascritto(id, titolo = "Riunione B", data = LocalDate.of(2026, 9, 12))
        val trascritti = LettoreTrascrittoFinta(mapOf(id to nuovo))
        val scrittore = ScrittoreSbobinaturaFinta()
        val politica = RigenerazioneSbobinaturaPolitica(trascritti, LettoreNomiFinta(), scrittore)

        politica.perRegistrazioneRinominata(id, precedente = "Riunione A").atteso()

        assertEquals(
            listOf(
                ScrittoreSbobinaturaFinta.Operazione.Scritto("2026-09-12 Riunione B.md"),
                ScrittoreSbobinaturaFinta.Operazione.Rimosso("2026-09-12 Riunione A.md"),
            ),
            scrittore.operazioni,
        )
        assertEquals(setOf("2026-09-12 Riunione B.md"), scrittore.sbobinature.keys)
    }

    @Test
    fun `AC-155bis (rename) una rinomina non tocca mai il file di un'altra Registrazione`() {
        val rinominata = RegistrazioneId("rinominata")
        val scrittore = ScrittoreSbobinaturaFinta()
        scrittore.scrivi("2026-09-12 Altra Riunione.md", "# Altra Riunione\n\noriginale\n")
        val trascritti = LettoreTrascrittoFinta(
            mapOf(rinominata to unTrascritto(rinominata, titolo = "Riunione Nuova", data = LocalDate.of(2026, 9, 12))),
        )
        val politica = RigenerazioneSbobinaturaPolitica(trascritti, LettoreNomiFinta(), scrittore)

        politica.perRegistrazioneRinominata(rinominata, precedente = "Riunione Vecchia").atteso()

        assertEquals(
            setOf("2026-09-12 Altra Riunione.md", "2026-09-12 Riunione Nuova.md"),
            scrittore.sbobinature.keys,
        )
        assertEquals("# Altra Riunione\n\noriginale\n", scrittore.sbobinature.getValue("2026-09-12 Altra Riunione.md"))
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
        val scrittore = ScrittoreSbobinaturaFinta()
        val politica = RigenerazioneSbobinaturaPolitica(trascritti, LettoreNomiFinta(), scrittore)

        politica.perRegistrazioneRinominata(id, precedente = "riunione").atteso()

        assertEquals(
            listOf(ScrittoreSbobinaturaFinta.Operazione.Scritto("2026-09-12 Riunione.md")),
            scrittore.operazioni,
        )
        assertEquals(setOf("2026-09-12 Riunione.md"), scrittore.sbobinature.keys)
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
                VoceRef(unIncontroDi(conAttribuzione1), VoceId(1)) to PARLANTE,
                VoceRef(unIncontroDi(conAttribuzione2), VoceId(1)) to PARLANTE,
                VoceRef(unIncontroDi(senzaAttribuzione), VoceId(1)) to altroParlante,
            ),
            nomiParlanti = mapOf(PARLANTE to "Marco Rossi", altroParlante to "Anna"),
        )
        val scrittore = ScrittoreSbobinaturaFinta()
        val politica = RigenerazioneSbobinaturaPolitica(trascritti, nomi, scrittore)

        politica.perParlanteRinominato(PARLANTE).atteso()

        assertEquals(setOf("2026-09-12 Uno.md", "2026-09-12 Due.md"), scrittore.sbobinature.keys)
    }

    @Test
    fun `AC-156 ParlantePromosso con nomeCambiato false non scrive nulla`() {
        val id = RegistrazioneId("con-attribuzione")
        val trascritti = LettoreTrascrittoFinta(mapOf(id to unTrascritto(id)))
        val nomi = LettoreNomiFinta(
            attribuzioni = mapOf(VoceRef(unIncontroDi(id), VoceId(1)) to PARLANTE),
            nomiParlanti = mapOf(PARLANTE to "Marco"),
        )
        val scrittore = ScrittoreSbobinaturaFinta()
        val politica = RigenerazioneSbobinaturaPolitica(trascritti, nomi, scrittore)

        politica.perParlantePromosso(PARLANTE, nomeCambiato = false).atteso()

        assertTrue(scrittore.operazioni.isEmpty())
    }

    @Test
    fun `AC-156 ParlantePromosso con nomeCambiato true rigenera le Registrazioni attribuite a P`() {
        val id = RegistrazioneId("con-attribuzione")
        val trascritti = LettoreTrascrittoFinta(mapOf(id to unTrascritto(id)))
        val nomi = LettoreNomiFinta(
            attribuzioni = mapOf(VoceRef(unIncontroDi(id), VoceId(1)) to PARLANTE),
            nomiParlanti = mapOf(PARLANTE to "Marco"),
        )
        val scrittore = ScrittoreSbobinaturaFinta()
        val politica = RigenerazioneSbobinaturaPolitica(trascritti, nomi, scrittore)

        politica.perParlantePromosso(PARLANTE, nomeCambiato = true).atteso()

        assertEquals(setOf("2026-09-12 Riunione.md"), scrittore.sbobinature.keys)
    }

    @Test
    fun `AC-156 ParlanteEliminato non scrive nulla`() {
        val scrittore = ScrittoreSbobinaturaFinta()
        val politica = RigenerazioneSbobinaturaPolitica(LettoreTrascrittoFinta(), LettoreNomiFinta(), scrittore)

        politica.perParlanteEliminato(PARLANTE).atteso()

        assertTrue(scrittore.operazioni.isEmpty())
    }

    @Test
    fun `AC-157 un errore di scrittura diventa Esito Errore`() {
        val id = RegistrazioneId("reg-1")
        val trascritti = LettoreTrascrittoFinta(mapOf(id to unTrascritto(id)))
        val scrittore = ScrittoreSbobinaturaFinta()
        scrittore.fallisciAllaProssimaScrittura()
        val politica = RigenerazioneSbobinaturaPolitica(trascritti, LettoreNomiFinta(), scrittore)

        val esito = politica.esegui(RigeneraSbobinatura(id))

        esito.erroreAtteso<ErroreApplicazioneSbobinatura.ScritturaFallita>()
        assertTrue(scrittore.sbobinature.isEmpty())
    }

    @Test
    fun `AC-157 un errore di rimozione diventa Esito Errore ma il nuovo file resta scritto`() {
        val id = RegistrazioneId("reg-data")
        val trascritti = LettoreTrascrittoFinta(mapOf(id to unTrascritto(id, data = LocalDate.of(2026, 9, 20))))
        val scrittore = ScrittoreSbobinaturaFinta()
        scrittore.fallisciAllaProssimaRimozione()
        val politica = RigenerazioneSbobinaturaPolitica(trascritti, LettoreNomiFinta(), scrittore)

        val esito = politica.esegui(RigeneraSbobinatura(id, nomeFilePrecedente = "2026-09-19 Riunione.md"))

        esito.erroreAtteso<ErroreApplicazioneSbobinatura.ScritturaFallita>()
        assertTrue(scrittore.sbobinature.containsKey("2026-09-20 Riunione.md"))
    }

    // B51 (pre-release triage, 2026-09-29): `RigeneraTuttiIDocumenti` and this class's `esegui` overload for it
    // were dead in production since the AC-C47 startup-sweep fan-out (`ModuloSbobinatura`/`AbbonatoSbobinaturaEventi`
    // list ids themselves; only these two tests still called it) and are retired. Both exercised
    // `rigeneraOgnuna`'s fold (stop on first error / regenerate every id), shared code also reached by
    // `perParlanteRinominato` below — the "regenerate every match" case is already covered by
    // `AC-156 ParlanteRinominato rigenera tutte e sole le Registrazioni con un'Attribuzione a P` above; the
    // "stop at the first write error" case is preserved here through that same still-alive entry point.
    @Test
    fun `AC-156 ParlanteRinominato propaga un errore di scrittura e si ferma alla prima Registrazione`() {
        val prima = RegistrazioneId("prima")
        val seconda = RegistrazioneId("seconda")
        val trascritti = LettoreTrascrittoFinta(
            mapOf(prima to unTrascritto(prima, titolo = "Prima"), seconda to unTrascritto(seconda, titolo = "Seconda")),
        )
        val nomi = LettoreNomiFinta(
            attribuzioni = mapOf(
                VoceRef(unIncontroDi(prima), VoceId(1)) to PARLANTE,
                VoceRef(unIncontroDi(seconda), VoceId(1)) to PARLANTE,
            ),
            nomiParlanti = mapOf(PARLANTE to "Marco Rossi"),
        )
        val scrittore = ScrittoreSbobinaturaFinta()
        scrittore.fallisciAllaProssimaScrittura()
        val politica = RigenerazioneSbobinaturaPolitica(trascritti, nomi, scrittore)

        val esito = politica.perParlanteRinominato(PARLANTE)

        esito.erroreAtteso<ErroreApplicazioneSbobinatura.ScritturaFallita>()
        assertTrue(scrittore.sbobinature.isEmpty())
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
            incontroId = unIncontroDi(id),
            titolo = titolo,
            dataRegistrazione = data,
            segmenti = listOf(SegmentoVista(SegmentoId(1), VoceId(1), IntervalloMs(0, 1_000), "Ciao.")),
        )
    }
}
