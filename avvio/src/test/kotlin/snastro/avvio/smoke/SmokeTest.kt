package snastro.avvio.smoke

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.ElaborazioneId
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.SegmentoId
import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.kernel.mappa
import snastro.parlanti.adattatori.persistenza.AttribuzioneRepositorySql
import snastro.parlanti.adattatori.persistenza.ParlanteRepositorySql
import snastro.parlanti.adattatori.porte.LettoreRegistrazioneDaProgetto
import snastro.parlanti.adattatori.porte.LettoreVociDaTrascrizione
import snastro.parlanti.applicazione.comandi.ConfermaAttribuzione
import snastro.parlanti.applicazione.comandi.ConfermaAttribuzioneServizio
import snastro.parlanti.applicazione.comandi.ObiettivoAttribuzione
import snastro.parlanti.applicazione.porte.DecodificatoreAudioFinta
import snastro.parlanti.applicazione.porte.EstrattoreImprontaFinta
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.apriDatabaseProgetto
import snastro.progetto.adattatori.persistenza.IncontroRepositorySql
import snastro.progetto.adattatori.persistenza.ProgettoRepositorySql
import snastro.progetto.adattatori.persistenza.RegistrazioneRepositorySql
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni
import snastro.progetto.dominio.Incontro
import snastro.progetto.dominio.NomeProgetto
import snastro.progetto.dominio.OraDiInizio
import snastro.progetto.dominio.Progetto
import snastro.progetto.dominio.Registrazione
import snastro.sintesi.adattatori.persistenza.RiassuntoRepositorySql
import snastro.sintesi.applicazione.porte.conAvvio
import snastro.sintesi.applicazione.porte.conCompletamento
import snastro.sintesi.applicazione.porte.unRiassunto
import snastro.sintesi.applicazione.porte.unaStruttura
import snastro.sintesi.dominio.BozzaElemento
import snastro.sintesi.dominio.BozzaRiassunto
import snastro.sintesi.dominio.StrutturaIncontro
import snastro.trascrizione.adattatori.persistenza.ElaborazioneRepositorySql
import snastro.trascrizione.adattatori.persistenza.VociDellIncontroRepositorySql
import snastro.trascrizione.applicazione.letture.VociDelTrascritto
import snastro.trascrizione.dominio.SegmentoIniziale
import snastro.trascrizione.dominio.StatoElaborazione
import snastro.trascrizione.dominio.VociDellIncontro
import snastro.trascrizione.dominio.unaElaborazione
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import snastro.trascrizione.adattatori.porte.LettoreRegistrazioneDaProgetto as LettoreRegistrazioneTrascrizione

/**
 * AC-237 + AC-351 + AC-357: `--smoke <fixture-dir>` opens the fixture project (>=1 Registrazione already
 * imported, one of them with a completed Trascritto whose Voce 1 is named 'Anna') and saves S1, S2 (the
 * smoke waits for the identification badge '2 voci · 1 da identificare'), S3 (the Voci panel, Voce 1's
 * Nome shown), S3 with the Riassunto tab selected (AC-S151: the fixture's pronto Riassunto), S4 (the shell's
 * Parlanti section) and S5 — headless, on the ML Finte: no sherpa natives,
 * no models. fix-batch-16 LOW-1: S5 renders the REAL catalogue over an empty cache ('Mancanti',
 * 'Scarica'). The fixture here is built DIRECTLY via the SQL repositories + domain factories (never
 * `AggiungiRegistrazioneServizio`'s real FFmpeg probe/copy pipeline — this proves the smoke MECHANISM,
 * not audio import), and its Parlante through the REAL `ConfermaAttribuzione` over the Finte decoder and
 * extractor, so this test needs no native library and stays in the default gate.
 */
class SmokeTest {
    @TempDir
    lateinit var cartella: Path

    @Test
    fun `AC-237 AC-351 AC-357 AC-S151 AC-I88 AC-I91 smoke salva ogni schermata del fixture a 2 Parti, senza nativi`() {
        val cartellaFixture = cartella.resolve("Fixture.snastro")
        costruisciProgettoFixture(cartellaFixture)
        SCHERMATE.forEach { Files.deleteIfExists(Path.of("build/smoke/$it.png")) }
        // AC-C90: lo smoke non installa MAI il file handler di produzione (mai scrittura sulla cartella reale).
        val gestoriPrimaDelloSmoke = Logger.getLogger("snastro").handlers.size

        eseguiSmoke(cartellaFixture.toString())

        SCHERMATE.forEach { nome ->
            val png = Path.of("build/smoke/$nome.png")
            assertTrue(Files.exists(png) && Files.size(png) > 0, "screenshot di $nome mancante o vuoto: $png")
        }
        assertEquals(
            gestoriPrimaDelloSmoke,
            Logger.getLogger("snastro").handlers.size,
            "AC-C90: eseguiSmoke non deve mai installare un FileHandler sul logger \"snastro\"",
        )
    }

    /** A valid `.snastro` folder with a Progetto and one Registrazione — SQL only, no FFmpeg. */
    private fun costruisciProgettoFixture(cartellaProgetto: Path) {
        Files.createDirectories(cartellaProgetto.resolve("audio"))
        Files.createDirectories(cartellaProgetto.resolve("sbobinature"))
        Files.createDirectories(cartellaProgetto.resolve("cache/audio"))
        // La sorgente non serve alla decodifica FFmpeg qui (S2 controlla solo l'esistenza del file
        // per `disponibile`, non riproduce nulla durante lo smoke) — un file segnaposto basta.
        Files.write(cartellaProgetto.resolve("audio/rec-1.wav"), byteArrayOf(0))

        val db = apriDatabaseProgetto(cartellaProgetto.toFile())
        val progetti = ProgettoRepositorySql(db.database)
        val registrazioni = RegistrazioneRepositorySql(db.database)

        val nome = NomeProgetto.di("Progetto Fixture").atteso()
        val progetto = Progetto.crea(ProgettoId("fixture-progetto"), nome)
        progetti.salva(progetto.aggregato)

        val incontroId = IncontroId("incontro-di-fixture-registrazione")
        val prima = unaParte(1, progetto.aggregato.id, incontroId, "Riunione di prova", 60_000)
        IncontroRepositorySql(db.database).salva(Incontro.nuovo(incontroId, progetto.aggregato.id))
        registrazioni.salva(prima)

        // AC-I91: the Incontro has a SECOND Parte (later in the day), so S2 shows a multi-part row.
        Files.write(cartellaProgetto.resolve("audio/rec-2.wav"), byteArrayOf(0))
        val seconda = unaParte(2, progetto.aggregato.id, incontroId, "Riunione di prova (dopo la pausa)", 45_000)
        registrazioni.salva(seconda)

        // AC-351: a completed Elaborazione + its Trascritto per Parte (Parte 1: Voce 1 and 2; Parte 2: Voce 3 and 4).
        val radice = VociDellIncontro.crea(incontroId)
        trascrivi(db.database, radice, prima, SEGMENTI_PARTE_1)
        trascrivi(db.database, radice, seconda, SEGMENTI_PARTE_2)
        val catalogo = CatalogoRegistrazioni(registrazioni, IncontroRepositorySql(db.database))
        val lettore = LettoreRegistrazioneTrascrizione(catalogo)
        VociDellIncontroRepositorySql(db.database, UnitaDiLavoroSql(db.database), lettore).salva(radice)

        // AC-357: Voce 1 is 'Anna' (S2 badge '4 voci · 3 da identificare', S3 Nome, one S4 row).
        confermaAttribuzione(db.database, registrazioni).esegui(
            ConfermaAttribuzione(VoceRef(incontroId, VoceId(1)), ObiettivoAttribuzione.NuovoParlante("Anna")),
        ).atteso()
        aggiungiRiassunto(db.database, incontroId, prima.id, seconda.id)
        db.chiudi()
    }

    /** The [numero]th Parte of the fixture's Incontro: starts at 9 + [numero] o'clock, audio/rec-[numero].wav. */
    private fun unaParte(
        numero: Int,
        progetto: ProgettoId,
        incontro: IncontroId,
        titolo: String,
        durataMs: Long,
    ): Registrazione = Registrazione.aggiungi(
        id = RegistrazioneId(if (numero == 1) "fixture-registrazione" else "fixture-registrazione-$numero"),
        progettoId = progetto,
        incontroId = incontro,
        titolo = titolo,
        riferimentoAudio = RiferimentoAudio("audio/rec-$numero.wav"),
        durataMs = durataMs,
        dataRegistrazione = LocalDate.parse("2026-01-01"),
        aggiuntaAlle = Instant.parse("2026-01-01T10:0${numero - 1}:00Z"),
        oraDiInizio = OraDiInizio.di(LocalTime.of(9 + numero, 0)).atteso(),
    ).aggregato

    /** [parte]'s COMPLETATA Elaborazione and its Segmenti in the Incontro's Voci [radice]. */
    private fun trascrivi(
        database: SnastroDatabase,
        radice: VociDellIncontro,
        parte: Registrazione,
        segmenti: List<SegmentoIniziale>,
    ) {
        ElaborazioneRepositorySql(database).salva(
            unaElaborazione(StatoElaborazione.COMPLETATA, ElaborazioneId("elaborazione-${parte.id.valore}"), parte.id),
        ).atteso()
        radice.completaParte(parte.id, segmenti, durataMs = parte.durataMs).atteso()
    }

    /**
     * AC-S151/AC-I91: a pronto Riassunto of the whole Incontro (Sintesi's own SQL repository and root transitions),
     * its Fonti on both Parti (labels 1..3 = Parte 1, 4..6 = Parte 2: the Voci of Parte 2 are Voce 3 and Voce 4).
     */
    private fun aggiungiRiassunto(
        database: SnastroDatabase,
        incontroId: IncontroId,
        prima: RegistrazioneId,
        seconda: RegistrazioneId,
    ) {
        val uow = UnitaDiLavoroSql(database)
        val riassunti = RiassuntoRepositorySql(database, uow)
        val riassunto = unRiassunto("fixture-riassunto", incontroId, argomento = "punto sul progetto").conAvvio()
        uow.inTransazione { riassunti.salva(riassunto) }.atteso()
        riassunto.conCompletamento(
            BOZZA_FIXTURE,
            StrutturaIncontro(
                listOf(
                    prima to unaStruttura(1 to 1, 2 to 2, 3 to 1),
                    seconda to unaStruttura(1 to 3, 2 to 4, 3 to 3),
                ),
            ),
            (1..3).map { SegmentoRef(prima, SegmentoId(it)) } + (1..3).map { SegmentoRef(seconda, SegmentoId(it)) },
        )
        uow.inTransazione { riassunti.concludi(riassunto).mappa { } }.atteso()
    }

    private fun confermaAttribuzione(
        database: SnastroDatabase,
        registrazioni: RegistrazioneRepositorySql,
    ): ConfermaAttribuzioneServizio {
        val unitaDiLavoroSql = UnitaDiLavoroSql(database)
        val eventi = DispatcherEventiInMemoria(unitaDiLavoroSql)
        return ConfermaAttribuzioneServizio(
            eventi.unitaDiLavoro,
            GeneratoreIdFinto(),
            LettoreRegistrazioneDaProgetto(CatalogoRegistrazioni(registrazioni, IncontroRepositorySql(database))),
            LettoreVociDaTrascrizione(
                VociDelTrascritto(
                    VociDellIncontroRepositorySql(
                        database,
                        unitaDiLavoroSql,
                        LettoreRegistrazioneTrascrizione(
                            CatalogoRegistrazioni(registrazioni, IncontroRepositorySql(database)),
                        ),
                    ),
                    LettoreRegistrazioneTrascrizione(
                        CatalogoRegistrazioni(registrazioni, IncontroRepositorySql(database)),
                    ),
                ),
            ),
            ParlanteRepositorySql(database, unitaDiLavoroSql),
            AttribuzioneRepositorySql(database),
            DecodificatoreAudioFinta(),
            EstrattoreImprontaFinta(),
            eventi,
        )
    }

    private companion object {
        val SEGMENTI_PARTE_1 = listOf(
            SegmentoIniziale(0, IntervalloMs(0, 4_000), "Buongiorno a tutti, iniziamo con il punto sul progetto."),
            SegmentoIniziale(1, IntervalloMs(4_500, 9_000), "Grazie. Da parte mia ci sono due aggiornamenti."),
            SegmentoIniziale(0, IntervalloMs(9_500, 12_000), "Perfetto, partiamo dal primo."),
        )

        val SEGMENTI_PARTE_2 = listOf(
            SegmentoIniziale(0, IntervalloMs(0, 5_000), "Riprendiamo dopo la pausa, dal budget."),
            SegmentoIniziale(1, IntervalloMs(5_500, 9_000), "Il budget regge, andiamo avanti."),
            SegmentoIniziale(0, IntervalloMs(9_500, 12_000), "Allora chiudiamo la decisione."),
        )

        val SCHERMATE = listOf("s1", "s2", "s2-espansa", "s3-parte-1", "s3-parte-2", "s3-riassunto", "s4", "s5")

        val BOZZA_FIXTURE = BozzaRiassunto(
            sommario = "{V1} apre la riunione e {V2} porta due aggiornamenti; dopo la pausa {V3} riprende dal budget.",
            decisioni = listOf(BozzaElemento("Si parte dal primo aggiornamento.", listOf(3), null)),
            questioniAperte = listOf(BozzaElemento("Il secondo aggiornamento resta da discutere.", listOf(2), null)),
            azioni = listOf(BozzaElemento("{V2} presenta il primo aggiornamento.", listOf(2), 2)),
            puntiChiave = listOf(
                BozzaElemento("Il punto sul progetto.", listOf(1), 1),
                BozzaElemento("Il budget regge.", listOf(5), 4),
            ),
        )
    }
}
