package snastro.sintesi.applicazione.politiche

import com.lemonappdev.konsist.api.Konsist
import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.Esito
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IntervalloMs
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.sintesi.applicazione.eventi.RiassuntoEliminato
import snastro.sintesi.applicazione.eventi.RiassuntoRichiesto
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguistico
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguisticoFinta
import snastro.sintesi.applicazione.porte.LettoreTrascritto
import snastro.sintesi.applicazione.porte.LettoreTrascrittoFinta
import snastro.sintesi.applicazione.porte.LunghezzaMassimaRiassuntoRepositoryFinta
import snastro.sintesi.applicazione.porte.RiassuntoRepository
import snastro.sintesi.applicazione.porte.RiassuntoRepositoryFinta
import snastro.sintesi.applicazione.porte.SegmentoSintesi
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico
import snastro.sintesi.applicazione.porte.conAvvio
import snastro.sintesi.applicazione.porte.conCompletamento
import snastro.sintesi.applicazione.porte.conFallimento
import snastro.sintesi.applicazione.porte.unRiassunto
import snastro.sintesi.applicazione.porte.unaStruttura
import snastro.sintesi.dominio.Argomento
import snastro.sintesi.dominio.BozzaElemento
import snastro.sintesi.dominio.BozzaRiassunto
import snastro.sintesi.dominio.ErroreSintesi
import snastro.sintesi.dominio.LimiteIngresso
import snastro.sintesi.dominio.LunghezzaMassimaParole
import snastro.sintesi.dominio.LunghezzaMassimaRiassunto
import snastro.sintesi.dominio.Riassunto
import snastro.sintesi.dominio.RiassuntoId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ApplicaSostituzioneTrascrittoSintesiPolitica] against the port fakes (D1, ADR 0018 §5, ADR 0021 §6).
 * Every scenario runs [ApplicaSostituzioneTrascrittoSintesiPolitica.applica] wrapped in the shared
 * `UnitaDiLavoro`'s [snastro.kernel.UnitaDiLavoro.inTransazione], exactly as the completion transaction of
 * the re-run does it: [DispatcherEventiFinta.pubblicati] only ever holds events of a COMMITTED wrap, so
 * seeing (or not seeing) them there proves what the policy queued, not just what it called.
 */
class ApplicaSostituzioneTrascrittoSintesiPoliticaTest {
    @Test
    fun `AC-S92 rimuove pronto e fallito, riaccoda con argomento e cap correnti, pubblica entrambi gli eventi`() {
        val a = unAmbiente()
        val cappata = LunghezzaMassimaRiassunto.predefinita(PROGETTO).also { it.modifica(1500).atteso() }
        a.lunghezze.salva(cappata).atteso()
        val pronto = unRiassunto("pronto-1", REGISTRAZIONE, argomento = "A1", richiestoAlle = T1)
            .conAvvio()
            .conCompletamento(unaBozza(), unaStruttura(1 to 1))
        a.riassunti.salva(pronto).atteso()
        val fallito = unRiassunto("fallito-1", REGISTRAZIONE, argomento = "A2", richiestoAlle = T2)
            .conAvvio()
            .conFallimento()
        a.riassunti.salva(fallito).atteso()

        a.eventi.unitaDiLavoro.inTransazione { a.politica.applica(REGISTRAZIONE) }.atteso()

        val righe = a.riassunti.diRegistrazione(REGISTRAZIONE)
        assertEquals(1, righe.size, "solo il nuovo in_attesa resta")
        val nuovo = righe.single()
        assertEquals(RiassuntoId("id-1"), nuovo.id)
        assertTrue(nuovo.inAttesa)
        assertEquals(Argomento.di("A2").atteso(), nuovo.argomento, "l'argomento del piu recente (t2 > t1)")
        assertEquals(LunghezzaMassimaParole.di(1500).atteso(), nuovo.lunghezzaMassima)
        assertEquals(CLOCK.instant(), nuovo.richiestoAlle)
        assertEquals(listOf(RiassuntoEliminato(REGISTRAZIONE), RiassuntoRichiesto(REGISTRAZIONE)), a.eventi.pubblicati)
    }

    @Test
    fun `INV-S8 rimuove un Riassunto in ogni stato possibile, incluso in_attesa e in_corso`() {
        listOf(
            unRiassunto("r-in-attesa", REGISTRAZIONE),
            unRiassunto("r-in-corso", REGISTRAZIONE).conAvvio(),
            unRiassunto("r-fallito", REGISTRAZIONE).conAvvio().conFallimento(),
            unRiassunto("r-pronto", REGISTRAZIONE).conAvvio().conCompletamento(unaBozza(), unaStruttura(1 to 1)),
        ).forEach { riassunto ->
            val a = unAmbiente()
            a.riassunti.salva(riassunto).atteso()

            a.eventi.unitaDiLavoro.inTransazione { a.politica.applica(REGISTRAZIONE) }.atteso()

            assertNull(a.riassunti.trova(riassunto.id), "${riassunto.stato}")
        }
    }

    @Test
    fun `INV-S10 il riaccodo usa il cap CORRENTE del Progetto, non quello del Riassunto rimosso`() {
        val a = unAmbiente()
        a.riassunti.salva(unRiassunto("vecchio", REGISTRAZIONE, parole = 300)).atteso()
        val capCorrente = LunghezzaMassimaRiassunto.predefinita(PROGETTO).also { it.modifica(2500).atteso() }
        a.lunghezze.salva(capCorrente).atteso()

        a.eventi.unitaDiLavoro.inTransazione { a.politica.applica(REGISTRAZIONE) }.atteso()

        val nuovo = a.riassunti.diRegistrazione(REGISTRAZIONE).single()
        assertEquals(LunghezzaMassimaParole.di(2500).atteso(), nuovo.lunghezzaMassima)
    }

    @Test
    fun `AC-S94 r senza Riassunto restituisce Ok senza creare ne pubblicare nulla`() {
        val a = unAmbiente()

        a.eventi.unitaDiLavoro.inTransazione { a.politica.applica(REGISTRAZIONE) }.atteso()

        assertEquals(emptyList(), a.riassunti.diRegistrazione(REGISTRAZIONE))
        assertEquals(emptyList(), a.eventi.pubblicati)
    }

    @Test
    fun `AC-S95 modello non installato o oltre il limite rimuove ma non crea, solo RiassuntoEliminato pubblicato`() {
        val oltreIlLimite = "a".repeat(LimiteIngresso.LIMITE_TOKEN * 3)
        listOf(
            unAmbiente(disponibilita = DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.NonInstallato(1))),
            unAmbiente(
                trascritti = LettoreTrascrittoFinta(
                    mapOf(REGISTRAZIONE to listOf(unSegmentoSintesi(testo = oltreIlLimite))),
                ),
            ),
        ).forEach { a ->
            a.riassunti.salva(unRiassunto("esistente", REGISTRAZIONE)).atteso()

            a.eventi.unitaDiLavoro.inTransazione { a.politica.applica(REGISTRAZIONE) }.atteso()

            assertEquals(emptyList(), a.riassunti.diRegistrazione(REGISTRAZIONE))
            assertEquals(listOf(RiassuntoEliminato(REGISTRAZIONE)), a.eventi.pubblicati)
        }
    }

    @Test
    fun `AC-S96 legge il Trascritto NUOVO tramite segmenti con la transazione aperta, mai elaborazioneAperta`() {
        val riassunti = RiassuntoRepositoryFinta()
        val lunghezze = LunghezzaMassimaRiassuntoRepositoryFinta()
        val transazione = UnitaDiLavoroFinta(riassunti, lunghezze)
        val eventi = DispatcherEventiFinta(transazione)
        val trascrittiSpia = LettoreTrascrittoSpia(
            LettoreTrascrittoFinta(mapOf(REGISTRAZIONE to listOf(unSegmentoSintesi()))),
            transazione,
        )
        val politica = ApplicaSostituzioneTrascrittoSintesiPolitica(
            GeneratoreIdFinto(),
            CLOCK,
            PROGETTO,
            riassunti,
            lunghezze,
            trascrittiSpia,
            DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.Installato),
            eventi,
        )
        riassunti.salva(unRiassunto("esistente", REGISTRAZIONE)).atteso()

        eventi.unitaDiLavoro.inTransazione { politica.applica(REGISTRAZIONE) }.atteso()

        assertTrue(trascrittiSpia.letture.isNotEmpty())
        assertTrue(trascrittiSpia.letture.all { it }, "${trascrittiSpia.letture}")
        assertEquals(0, trascrittiSpia.chiamateElaborazioneAperta, "restretto a modello+limite: mai letto")
    }

    @Test
    fun `AC-S97 il costruttore non ha un collaboratore ModelloLinguistico`() {
        val scope = Konsist.scopeFromPackage(PACCHETTO, "sintesi/applicazione", "main")
        val costruttore = scope.classes().single { it.name == "ApplicaSostituzioneTrascrittoSintesiPolitica" }
            .primaryConstructor

        val tipi = checkNotNull(costruttore) { "ApplicaSostituzioneTrascrittoSintesiPolitica" }
            .parameters.map { it.type.text }

        assertEquals(
            listOf(
                "GeneratoreId",
                "Clock",
                "ProgettoId",
                "RiassuntoRepository",
                "LunghezzaMassimaRiassuntoRepository",
                "LettoreTrascritto",
                "DisponibilitaModelloLinguistico",
                "DispatcherEventi",
            ),
            tipi,
        )
    }

    @Test
    fun `AC-S97 un Errore da rimuoviDiRegistrazione e restituito invariato, niente rimosso ne pubblicato`() {
        val a = unAmbiente()
        a.riassunti.salva(unRiassunto("esistente", REGISTRAZIONE)).atteso()
        val riassuntiGuasti = object : RiassuntoRepository by a.riassunti {
            override fun rimuoviDiRegistrazione(r: RegistrazioneId): Esito<Int> =
                Esito.Errore(ErroreSintesi.TrascrittoNonDisponibile(r))
        }
        val politicaGuasta = ApplicaSostituzioneTrascrittoSintesiPolitica(
            GeneratoreIdFinto(),
            CLOCK,
            PROGETTO,
            riassuntiGuasti,
            a.lunghezze,
            a.trascritti,
            a.disponibilita,
            a.eventi,
        )

        val esito = a.eventi.unitaDiLavoro.inTransazione { politicaGuasta.applica(REGISTRAZIONE) }

        esito.erroreAtteso<ErroreSintesi.TrascrittoNonDisponibile>()
        assertEquals(1, a.riassunti.diRegistrazione(REGISTRAZIONE).size, "rollback: la riga originale resta")
        assertEquals(emptyList(), a.eventi.pubblicati)
    }

    @Test
    fun `AC-S97 un Errore da salva nel riaccodo e restituito invariato, rollback totale`() {
        val a = unAmbiente()
        a.riassunti.salva(unRiassunto("esistente", REGISTRAZIONE)).atteso()
        val riassuntiGuasti = object : RiassuntoRepository by a.riassunti {
            override fun salva(r: Riassunto): Esito<Unit> =
                Esito.Errore(ErroreSintesi.RiassuntoGiaAperto(REGISTRAZIONE))
        }
        val politicaGuasta = ApplicaSostituzioneTrascrittoSintesiPolitica(
            GeneratoreIdFinto(),
            CLOCK,
            PROGETTO,
            riassuntiGuasti,
            a.lunghezze,
            a.trascritti,
            a.disponibilita,
            a.eventi,
        )

        val esito = a.eventi.unitaDiLavoro.inTransazione { politicaGuasta.applica(REGISTRAZIONE) }

        esito.erroreAtteso<ErroreSintesi.RiassuntoGiaAperto>()
        assertEquals(
            listOf("esistente"),
            a.riassunti.diRegistrazione(REGISTRAZIONE).map { it.id.valore },
            "rollback: la riga originale rimossa da rimuoviDiRegistrazione torna",
        )
        assertEquals(emptyList(), a.eventi.pubblicati)
    }

    private class LettoreTrascrittoSpia(
        private val delega: LettoreTrascritto,
        private val transazione: UnitaDiLavoroFinta,
    ) : LettoreTrascritto {
        val letture = mutableListOf<Boolean>()
        var chiamateElaborazioneAperta: Int = 0
            private set

        override fun segmenti(r: RegistrazioneId): List<SegmentoSintesi>? {
            letture += transazione.transazioneAperta
            return delega.segmenti(r)
        }

        override fun elaborazioneAperta(r: RegistrazioneId): Boolean {
            chiamateElaborazioneAperta++
            return delega.elaborazioneAperta(r)
        }
    }

    private data class Ambiente(
        val politica: ApplicaSostituzioneTrascrittoSintesiPolitica,
        val riassunti: RiassuntoRepositoryFinta,
        val lunghezze: LunghezzaMassimaRiassuntoRepositoryFinta,
        val trascritti: LettoreTrascritto,
        val disponibilita: DisponibilitaModelloLinguistico,
        val eventi: DispatcherEventiFinta,
    )

    /** A fresh set of fakes wired into a [ApplicaSostituzioneTrascrittoSintesiPolitica]; every guard passes unless
     * overridden here. */
    private fun unAmbiente(
        trascritti: LettoreTrascritto = LettoreTrascrittoFinta(mapOf(REGISTRAZIONE to listOf(unSegmentoSintesi()))),
        disponibilita: DisponibilitaModelloLinguistico =
            DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.Installato),
    ): Ambiente {
        val riassunti = RiassuntoRepositoryFinta()
        val lunghezze = LunghezzaMassimaRiassuntoRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(riassunti, lunghezze))
        val politica = ApplicaSostituzioneTrascrittoSintesiPolitica(
            GeneratoreIdFinto(),
            CLOCK,
            PROGETTO,
            riassunti,
            lunghezze,
            trascritti,
            disponibilita,
            eventi,
        )
        return Ambiente(politica, riassunti, lunghezze, trascritti, disponibilita, eventi)
    }

    private fun unaBozza(): BozzaRiassunto = BozzaRiassunto(
        sommario = null,
        decisioni = listOf(BozzaElemento("Decisione di prova.", listOf(1), null)),
        questioniAperte = emptyList(),
        azioni = emptyList(),
        puntiChiave = emptyList(),
    )

    private fun unSegmentoSintesi(
        segmentoId: Int = 1,
        voceId: Int = 1,
        inizioMs: Long = 0,
        fineMs: Long = 1_000,
        testo: String = "Testo di prova.",
    ): SegmentoSintesi {
        val intervallo = IntervalloMs(inizioMs, fineMs)
        return SegmentoSintesi(SegmentoId(segmentoId), VoceId(voceId), intervallo, testo)
    }

    private companion object {
        const val PACCHETTO = "snastro.sintesi.applicazione.politiche"
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
        val PROGETTO = ProgettoId("progetto-1")
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"), ZoneOffset.UTC)
        val T1: Instant = Instant.parse("2026-09-25T09:00:00Z")
        val T2: Instant = Instant.parse("2026-09-25T09:30:00Z")
    }
}
