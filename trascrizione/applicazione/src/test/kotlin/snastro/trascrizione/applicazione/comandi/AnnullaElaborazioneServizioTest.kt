package snastro.trascrizione.applicazione.comandi

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.trascrizione.applicazione.eventi.ElaborazioneAnnullata
import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.applicazione.porte.ElaborazioneRepositoryFinta
import snastro.trascrizione.applicazione.porte.LettoreRegistrazioneFinta
import snastro.trascrizione.applicazione.porte.RegistrazioneVista
import snastro.trascrizione.applicazione.porte.TrascrittoRepository
import snastro.trascrizione.dominio.Elaborazione
import snastro.trascrizione.dominio.ErroreTrascrizione.ElaborazioneGiaAvviata
import snastro.trascrizione.dominio.ErroreTrascrizione.ElaborazioneNonTrovata
import snastro.trascrizione.dominio.NumeroPersone
import snastro.trascrizione.dominio.StatoElaborazione
import snastro.trascrizione.dominio.unaElaborazione
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** ADR 0018 Amendment (b) §3: AnnullaElaborazione withdraws a never-started (`in_attesa`) Elaborazione. */
class AnnullaElaborazioneServizioTest {
    private val elaborazioni = ElaborazioneRepositoryFinta()
    private val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni))
    private val contata = UnitaDiLavoroContataAnnulla(eventi.unitaDiLavoro)
    private val sincroni = mutableListOf<Pair<EventoPubblicato, Int>>()

    init {
        eventi.registraSincrono { evento ->
            sincroni += evento to contata.transazioni
            Esito.Ok(Unit)
        }
    }

    @Test
    fun `AC-464 una prima trascrizione annullata sparisce e pubblica ElaborazioneAnnullata nella transazione`() {
        elaborazioni.salva(Elaborazione.accoda(IN_CODA, R, DOPO, null).aggregato).atteso()

        servizio().esegui(AnnullaElaborazione(IN_CODA)).atteso()

        assertEquals(emptyList(), elaborazioni.diRegistrazione(R))
        assertEquals(listOf<EventoPubblicato>(ElaborazioneAnnullata(R)), eventi.pubblicati)
        val attesi = listOf<Pair<EventoPubblicato, Int>>(ElaborazioneAnnullata(R) to 1)
        assertEquals(attesi, sincroni, "dentro l unica transazione")
        assertEquals(1, contata.transazioni)
    }

    @Test
    fun `AC-465 una ritrascrizione in coda annullata lascia la completata invariata`() {
        val completata = unaElaborazione(
            StatoElaborazione.COMPLETATA,
            ElaborazioneId("completata"),
            R,
            creataAlle = PRIMA,
            numeroPersone = NumeroPersone.di(3).atteso(),
        )
        elaborazioni.salva(completata).atteso()
        elaborazioni.salva(Elaborazione.accoda(IN_CODA, R, DOPO, null).aggregato).atteso()

        servizio().esegui(AnnullaElaborazione(IN_CODA)).atteso()

        assertEquals(listOf(stato(completata)), elaborazioni.diRegistrazione(R).map(::stato))
        assertEquals(listOf<EventoPubblicato>(ElaborazioneAnnullata(R)), eventi.pubblicati)
    }

    @Test
    fun `AC-465 una Riprova in coda annullata lascia la fallita`() {
        val fallita = unaElaborazione(StatoElaborazione.FALLITA, ElaborazioneId("fallita"), R, creataAlle = PRIMA)
        elaborazioni.salva(fallita).atteso()
        elaborazioni.salva(Elaborazione.accoda(IN_CODA, R, DOPO, null).aggregato).atteso()

        servizio().esegui(AnnullaElaborazione(IN_CODA)).atteso()

        assertEquals(listOf(stato(fallita)), elaborazioni.diRegistrazione(R).map(::stato))
    }

    @Test
    fun `AC-465 AnnullaElaborazione non puo toccare il Trascritto perche non riceve alcun TrascrittoRepository`() {
        val dipendenze = AnnullaElaborazioneServizio::class.java.constructors.single().parameterTypes

        assertTrue(dipendenze.none { TrascrittoRepository::class.java.isAssignableFrom(it) })
    }

    @Test
    fun `AC-466 in_corso completata o fallita sono ElaborazioneGiaAvviata e nulla e scritto o pubblicato`() {
        listOf(StatoElaborazione.IN_CORSO, StatoElaborazione.COMPLETATA, StatoElaborazione.FALLITA).forEach { avviata ->
            val id = ElaborazioneId("elaborazione-$avviata")
            val registrazione = RegistrazioneId("registrazione-$avviata")
            elaborazioni.salva(unaElaborazione(avviata, id, registrazione)).atteso()

            val errore = servizio().esegui(AnnullaElaborazione(id)).erroreAtteso<ElaborazioneGiaAvviata>()

            assertEquals(ElaborazioneGiaAvviata(id), errore)
            assertEquals(listOf(avviata), elaborazioni.diRegistrazione(registrazione).map { it.stato })
        }
        assertEquals(emptyList(), eventi.pubblicati)
        assertEquals(emptyList(), sincroni)
    }

    @Test
    fun `AC-466 un id sconosciuto e ElaborazioneNonTrovata`() {
        val errore = servizio().esegui(AnnullaElaborazione(IN_CODA)).erroreAtteso<ElaborazioneNonTrovata>()

        assertEquals(ElaborazioneNonTrovata(IN_CODA), errore)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-467 se la presa in carico vince prima della cancellazione l errore torna invariato e nulla e pubblicato`() {
        elaborazioni.salva(Elaborazione.accoda(IN_CODA, R, DOPO, null).aggregato).atteso()
        val gara = ElaborazioneRepositoryCheVinceLaPresa(elaborazioni)

        val errore = servizio(gara).esegui(AnnullaElaborazione(IN_CODA)).erroreAtteso<ElaborazioneGiaAvviata>()

        assertEquals(ElaborazioneGiaAvviata(IN_CODA), errore)
        assertEquals(emptyList(), eventi.pubblicati)
        assertEquals(emptyList(), sincroni)
        assertEquals(listOf(IN_CODA), elaborazioni.inAttesa().map { it.id }, "rollback: la riga resta")
    }

    @Test
    fun `AC-468 dopo un annullamento AvviaElaborazione e di nuovo accettata`() {
        elaborazioni.salva(Elaborazione.accoda(IN_CODA, R, DOPO, null).aggregato).atteso()
        servizio().esegui(AnnullaElaborazione(IN_CODA)).atteso()

        AvviaElaborazioneServizio(
            contata,
            GeneratoreIdFinto(),
            OROLOGIO,
            LettoreRegistrazioneFinta(mapOf(R to vista())),
            elaborazioni,
        ).esegui(AvviaElaborazione(R, numeroPersone = 2)).atteso()

        val nuova = elaborazioni.diRegistrazione(R).single()
        assertEquals(StatoElaborazione.IN_ATTESA, nuova.stato)
        assertEquals(2, nuova.numeroPersone?.valore)
    }

    private fun servizio(repository: ElaborazioneRepository = elaborazioni) =
        AnnullaElaborazioneServizio(contata, repository, eventi)

    /** The observable state of an [Elaborazione] (the aggregate has no value equality). */
    private fun stato(e: Elaborazione): List<Any?> =
        listOf(e.id, e.stato, e.numeroPersone, e.creataAlle, e.avviataAlle, e.motivoFallimento)

    private companion object {
        val R = RegistrazioneId("registrazione-1")
        val IN_CODA = ElaborazioneId("in-coda")
        val PRIMA: Instant = Instant.parse("2026-09-24T08:00:00Z")
        val DOPO: Instant = Instant.parse("2026-09-24T09:00:00Z")
        val OROLOGIO: Clock = Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneOffset.UTC)

        fun vista() = RegistrazioneVista(
            registrazioneId = R,
            progettoId = ProgettoId("progetto-1"),
            titolo = "Riunione",
            riferimentoAudio = RiferimentoAudio("audio/registrazione-1.m4a"),
            dataRegistrazione = LocalDate.of(2026, 9, 20),
            durataMs = 1_000L,
        )
    }
}

/**
 * The dispatcher's claim commits between the service's `trova` and its delete (AC-467): the compare-and-delete
 * then answers [ElaborazioneGiaAvviata], exactly as the SQL `eliminaInAttesa` does on a row now `in_corso`.
 */
private class ElaborazioneRepositoryCheVinceLaPresa(
    private val delegata: ElaborazioneRepositoryFinta,
) : ElaborazioneRepository by delegata {
    override fun rimuoviInAttesa(id: ElaborazioneId): Esito<Unit> = Esito.Errore(ElaborazioneGiaAvviata(id))
}

/** Counts the transactions opened through it. */
private class UnitaDiLavoroContataAnnulla(private val delegata: UnitaDiLavoro) : UnitaDiLavoro {
    var transazioni = 0
        private set

    override fun <T> inTransazione(blocco: () -> Esito<T>): Esito<T> {
        transazioni++
        return delegata.inTransazione(blocco)
    }
}
