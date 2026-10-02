package snastro.progetto.applicazione.comandi

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.progetto.applicazione.eventi.OraDiInizioModificata
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.progetto.applicazione.porte.RegistrazioneRepositoryFinta
import snastro.progetto.dominio.ErroreProgetto
import snastro.progetto.dominio.OraDiInizio
import snastro.progetto.dominio.Registrazione
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ModificaOraDiInizioServizioTest {
    private val id = RegistrazioneId("id-1")
    private val incontroId = IncontroId("incontro-di-id-1")
    private val registrazioni = RegistrazioneRepositoryFinta().apply {
        salva(
            Registrazione.aggiungi(
                id = id,
                progettoId = ProgettoId("progetto-1"),
                incontroId = incontroId,
                titolo = "Seduta del 12 marzo",
                riferimentoAudio = RiferimentoAudio("audio/id-1.m4a"),
                durataMs = 3_600_000,
                dataRegistrazione = LocalDate.of(2026, 3, 12),
                aggiuntaAlle = Instant.parse("2026-03-12T10:00:00Z"),
            ).aggregato,
        )
    }
    private val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(registrazioni))
    private var scritture = 0
    private val servizio = ModificaOraDiInizioServizio(
        eventi.unitaDiLavoro,
        object : RegistrazioneRepository by registrazioni {
            override fun salva(r: Registrazione) {
                scritture++
                registrazioni.salva(r)
            }
        },
        eventi,
    )

    private fun ora(testo: String): OraDiInizio = assertNotNull(OraDiInizio.di(testo).atteso())

    @Test
    fun `su un'ora vuota la memorizza e pubblica OraDiInizioModificata con precedente null`() {
        servizio.esegui(ModificaOraDiInizio(id, ora("10:25:00"))).atteso()

        assertEquals(ora("10:25:00"), assertNotNull(registrazioni.trova(id)).oraDiInizio)
        assertEquals(
            listOf(OraDiInizioModificata(id, incontroId, null, LocalTime.of(10, 25))),
            eventi.pubblicati,
        )
    }

    @Test
    fun `null azzera un'ora impostata e pubblica l'evento`() {
        servizio.esegui(ModificaOraDiInizio(id, ora("10:25:00"))).atteso()

        servizio.esegui(ModificaOraDiInizio(id, null)).atteso()

        assertNull(assertNotNull(registrazioni.trova(id)).oraDiInizio)
        assertEquals(
            OraDiInizioModificata(id, incontroId, LocalTime.of(10, 25), null),
            eventi.pubblicati.last(),
        )
    }

    @Test
    fun `lo stesso valore e' Ok senza evento`() {
        servizio.esegui(ModificaOraDiInizio(id, ora("10:25:00"))).atteso()
        val dopoLaPrima = eventi.pubblicati.size

        assertEquals(Esito.Ok(Unit), servizio.esegui(ModificaOraDiInizio(id, ora("10:25:00"))))
        assertEquals(dopoLaPrima, eventi.pubblicati.size)
        assertEquals(1, scritture, "la seconda non scrive")
    }

    @Test
    fun `l'ora vuota su un'ora vuota non pubblica e non scrive nulla`() {
        servizio.esegui(ModificaOraDiInizio(id, null)).atteso()

        assertEquals(emptyList(), eventi.pubblicati)
        assertEquals(0, scritture)
    }

    @Test
    fun `su una Registrazione inesistente restituisce RegistrazioneNonTrovata`() {
        val sconosciuta = RegistrazioneId("id-sconosciuto")

        val errore = servizio.esegui(ModificaOraDiInizio(sconosciuta, ora("10:25:00")))
            .erroreAtteso<ErroreProgetto.RegistrazioneNonTrovata>()

        assertEquals(sconosciuta, errore.id)
        assertEquals(emptyList(), eventi.pubblicati)
        assertEquals(0, scritture)
    }

    @Test
    fun `un'ora non valida e' rifiutata da OraDiInizio_di prima del comando e non scrive nulla`() {
        listOf("24:00:00", "12:60:00", "").forEach {
            OraDiInizio.di(it).erroreAtteso<ErroreProgetto.OraDiInizioNonValida>()
        }
        OraDiInizio.di(LocalTime.of(10, 25, 0, 1)).erroreAtteso<ErroreProgetto.OraDiInizioNonValida>()

        assertNull(assertNotNull(registrazioni.trova(id)).oraDiInizio)
        assertEquals(emptyList(), eventi.pubblicati)
    }
}
