package snastro.progetto.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.progetto.applicazione.eventi.RegistrazioneEliminata
import snastro.progetto.applicazione.porte.EliminazioneInSospeso
import snastro.progetto.applicazione.porte.EliminazioniInSospeso
import snastro.progetto.applicazione.porte.EliminazioniInSospesoFinta
import snastro.progetto.applicazione.porte.IncontroRepository
import snastro.progetto.applicazione.porte.IncontroRepositoryFinta
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.progetto.applicazione.porte.RegistrazioneRepositoryFinta
import snastro.progetto.dominio.ErroreProgetto
import snastro.progetto.dominio.Incontro
import snastro.progetto.dominio.Registrazione
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * `EliminaRegistrazione` (ADR 0020 §1-§2, INV-28): one transaction — trova → Registrazione.elimina() →
 * inSospeso.registra → pubblica (the synchronous subscribers run here) → rimuovi. The synchronous subscriber's veto
 * is played by a recording one that answers an Errore: the real one is Trascrizione's
 * `ElaborazioneGiaAperta`, which `:progetto` may not import (CR-1) — the service returns ANY such Errore unchanged.
 */
class EliminaRegistrazioneServizioTest {
    private val id = RegistrazioneId("id-1")
    private val progettoId = ProgettoId("progetto-1")
    private val passi = mutableListOf<String>()
    private val registrazioni = RegistrazioneRepositoryFinta()
    private val inSospeso = EliminazioniInSospesoFinta()
    private val incontri = IncontroRepositoryFinta(registrazioni)
    private val uow = UnitaDiLavoroContata(UnitaDiLavoroFinta(registrazioni, incontri, inSospeso))
    private val dispatcher = DispatcherEventiInMemoria(uow)
    private val sincroni = mutableListOf<EventoPubblicato>()
    private val dopoCommit = mutableListOf<EventoPubblicato>()
    private var veto: Esito<Unit> = Esito.Ok(Unit)

    /** Trascrizione rows still referencing the Registrazione (an open Elaborazione survives a veto). */
    private var elaborazioneAperta = false
    private val servizio = EliminaRegistrazioneServizio(
        dispatcher.unitaDiLavoro,
        RegistrazioniRegistrate(registrazioni, passi) { elaborazioneAperta },
        IncontriRegistrati(incontri, passi),
        InSospesoRegistrate(inSospeso, passi),
        dispatcher,
    )

    init {
        incontri.salva(Incontro.nuovo(IncontroId("incontro-di-id-1"), progettoId))
        incontri.salva(Incontro.nuovo(IncontroId("incontro-di-id-2"), progettoId))
        registrazioni.salva(unaRegistrazione())
        registrazioni.salva(unaRegistrazione(RegistrazioneId("id-2"), "Altra"))
        dispatcher.registraSincrono { e ->
            passi += "pubblica"
            sincroni += e
            veto
        }
        dispatcher.registraDopoCommit { e -> dopoCommit += e }
    }

    @Test
    fun `AC-600 INV-28 in una sola transazione trova registra pubblica e rimuove in quest ordine`() {
        servizio.esegui(EliminaRegistrazione(id)).atteso()

        assertEquals(1, uow.transazioni, "una sola inTransazione")
        assertEquals(
            listOf("trova", "parti", "registra", "pubblica", "rimuovi", "rimuoviIncontro"),
            passi,
        )
        assertNull(incontri.trova(IncontroId("incontro-di-id-1")), "l'Incontro cessa con la sua ultima Parte")
        assertEquals(
            listOf(EliminazioneInSospeso(id, "Seduta rinominata", DATA_SCELTA, RiferimentoAudio("audio/id-1.m4a"))),
            inSospeso.elenco(),
        )
        val atteso = RegistrazioneEliminata(
            id,
            progettoId,
            "Seduta rinominata",
            DATA_SCELTA,
            RiferimentoAudio("audio/id-1.m4a"),
            IncontroId("incontro-di-id-1"),
            incontroCessato = true,
        )
        assertEquals(listOf<EventoPubblicato>(atteso), sincroni)
        assertEquals(listOf<EventoPubblicato>(atteso), dopoCommit)
        assertNull(registrazioni.trova(id))
        assertNotNull(registrazioni.trova(RegistrazioneId("id-2")), "le altre restano")
    }

    @Test
    fun `AC-601 un id sconosciuto restituisce RegistrazioneNonTrovata e non registra pubblica ne rimuove`() {
        val sconosciuta = RegistrazioneId("id-sconosciuto")

        val errore = servizio.esegui(EliminaRegistrazione(sconosciuta))
            .erroreAtteso<ErroreProgetto.RegistrazioneNonTrovata>()

        assertEquals(ErroreProgetto.RegistrazioneNonTrovata(sconosciuta), errore)
        assertEquals(listOf("trova"), passi)
        assertEquals(emptyList(), inSospeso.elenco())
        assertEquals(emptyList(), sincroni + dopoCommit)
        assertEquals(2, registrazioni.delProgetto(progettoId).size)
    }

    @Test
    fun `AC-602 INV-28 il veto di un abbonato sincrono e restituito invariato e non cambia nulla`() {
        val vetoTrascrizione = ErroreDiProva.Fallito("ElaborazioneGiaAperta(id-1)")
        veto = Esito.Errore(vetoTrascrizione)
        // Like SQLite: the open Elaborazione is still there, so the immediate FK makes rimuovi THROW (ADR 0020 §2).
        elaborazioneAperta = true

        val errore = servizio.esegui(EliminaRegistrazione(id)).erroreAtteso<ErroreDiProva.Fallito>()

        assertEquals(vetoTrascrizione, errore)
        assertEquals("Seduta rinominata", assertNotNull(registrazioni.trova(id)).titolo)
        assertEquals(emptyList(), inSospeso.elenco(), "nessuna riga eliminazione_in_sospeso")
        assertEquals(emptyList(), dopoCommit, "nessun abbonato dopo-commit")
        assertNotNull(incontri.trova(IncontroId("incontro-di-id-1")), "l'Incontro resta")
    }

    @Test
    fun `INV-I1 il veto su un ultimo Parte lascia l Incontro e la Parte`() {
        veto = Esito.Errore(ErroreDiProva.Fallito("ElaborazioneGiaAperta(id-1)"))

        servizio.esegui(EliminaRegistrazione(id)).erroreAtteso<ErroreDiProva.Fallito>()

        assertNotNull(registrazioni.trova(id))
        assertNotNull(incontri.trova(IncontroId("incontro-di-id-1")))
    }

    @Test
    fun `AC-I31 la Parte 2 di 3 pubblica incontroCessato falso e l Incontro con le altre Parti resta`() {
        val incontro = IncontroId("incontro-di-id-1")
        registrazioni.salva(unaRegistrazione(RegistrazioneId("id-1b"), "B", incontro))
        registrazioni.salva(unaRegistrazione(RegistrazioneId("id-1c"), "C", incontro))

        servizio.esegui(EliminaRegistrazione(RegistrazioneId("id-1b"))).atteso()

        val evento = sincroni.single() as RegistrazioneEliminata
        assertEquals(false, evento.incontroCessato)
        assertEquals(incontro, evento.incontroId)
        assertEquals(setOf(id, RegistrazioneId("id-1c")), incontri.partiDi(incontro).toSet())
        assertNotNull(incontri.trova(incontro))
        assertEquals(listOf("trova", "parti", "registra", "pubblica", "rimuovi"), passi)
    }

    @Test
    fun `eliminare l ultima Parte di un Incontro mai salvato non fallisce, il suo rimuovi e un no-op`() {
        // The Incontro "incontro-di-id-3" is never saved (the Finta accepts the Parte without it): incontroCessato is
        // still true and IncontroRepository.rimuovi of an absent id changes nothing (its contract).
        registrazioni.salva(unaRegistrazione(RegistrazioneId("id-3"), "Senza riga"))
        servizio.esegui(EliminaRegistrazione(RegistrazioneId("id-3"))).atteso()

        assertNull(registrazioni.trova(RegistrazioneId("id-3")))
        assertEquals("rimuoviIncontro", passi.last())
        assertNotNull(incontri.trova(IncontroId("incontro-di-id-2")), "gli altri Incontri restano")
    }

    @Test
    fun `AC-603 il servizio non tocca file - collaboratori solo transazione repository sospese ed eventi`() {
        val collaboratori = EliminaRegistrazioneServizio::class.java.constructors.single().parameterTypes.toList()

        assertEquals(
            listOf(
                UnitaDiLavoro::class.java,
                RegistrazioneRepository::class.java,
                IncontroRepository::class.java,
                EliminazioniInSospeso::class.java,
                DispatcherEventi::class.java,
            ),
            collaboratori,
        )
    }

    @Test
    fun `AC-604 la seconda eliminazione dello stesso id e RegistrazioneNonTrovata e non ripubblica`() {
        servizio.esegui(EliminaRegistrazione(id)).atteso()

        servizio.esegui(EliminaRegistrazione(id)).erroreAtteso<ErroreProgetto.RegistrazioneNonTrovata>()

        assertEquals(1, sincroni.size)
        assertEquals(1, dopoCommit.size)
        assertEquals(1, inSospeso.elenco().size)
    }

    private fun unaRegistrazione(
        registrazioneId: RegistrazioneId = id,
        titolo: String = "Seduta",
        incontro: IncontroId = IncontroId("incontro-di-${registrazioneId.valore}"),
    ): Registrazione = Registrazione.aggiungi(
        id = registrazioneId,
        progettoId = progettoId,
        incontroId = incontro,
        titolo = titolo,
        riferimentoAudio = RiferimentoAudio("audio/${registrazioneId.valore}.m4a"),
        durataMs = 3_600_000,
        dataRegistrazione = LocalDate.of(2026, 3, 12),
        aggiuntaAlle = Instant.parse("2026-03-12T10:00:00Z"),
    ).aggregato.apply {
        // The event carries the CURRENT titolo and date (AC-613), not those given at creation.
        rinomina("$titolo rinominata").atteso()
        modificaData(DATA_SCELTA).atteso()
    }

    /** Counts the outermost transactions opened on the wrapped unit of work. */
    private class UnitaDiLavoroContata(private val delegata: UnitaDiLavoro) : UnitaDiLavoro {
        var transazioni = 0

        override fun <T> inTransazione(blocco: () -> Esito<T>): Esito<T> {
            transazioni++
            return delegata.inTransazione(blocco)
        }
    }

    /** Records the calls; [rimuovi] throws like the immediate `elaborazione` FK while [elaborazioneAperta]. */
    private class RegistrazioniRegistrate(
        private val delegata: RegistrazioneRepository,
        private val passi: MutableList<String>,
        private val elaborazioneAperta: () -> Boolean,
    ) : RegistrazioneRepository by delegata {
        override fun trova(id: RegistrazioneId): Registrazione? = delegata.trova(id).also { passi += "trova" }

        override fun rimuovi(id: RegistrazioneId) {
            passi += "rimuovi"
            check(!elaborazioneAperta()) { "FOREIGN KEY constraint failed (elaborazione.registrazione_id)" }
            delegata.rimuovi(id)
        }
    }

    private class IncontriRegistrati(
        private val delegata: IncontroRepository,
        private val passi: MutableList<String>,
    ) : IncontroRepository by delegata {
        override fun partiDi(id: IncontroId) = delegata.partiDi(id).also { passi += "parti" }

        override fun rimuovi(id: IncontroId) {
            passi += "rimuoviIncontro"
            delegata.rimuovi(id)
        }
    }

    private class InSospesoRegistrate(
        private val delegata: EliminazioniInSospeso,
        private val passi: MutableList<String>,
    ) : EliminazioniInSospeso by delegata {
        override fun registra(e: EliminazioneInSospeso) {
            passi += "registra"
            delegata.registra(e)
        }
    }

    private companion object {
        val DATA_SCELTA: LocalDate = LocalDate.of(2026, 3, 10)
    }
}
