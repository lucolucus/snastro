package snastro.sintesi.applicazione.comandi

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.sintesi.applicazione.eventi.LunghezzaMassimaRiassuntoModificata
import snastro.sintesi.applicazione.porte.LunghezzaMassimaRiassuntoRepositoryFinta
import snastro.sintesi.applicazione.porte.RiassuntoRepositoryFinta
import snastro.sintesi.applicazione.porte.conAvvio
import snastro.sintesi.applicazione.porte.conCompletamento
import snastro.sintesi.applicazione.porte.statoOsservabile
import snastro.sintesi.applicazione.porte.unRiassunto
import snastro.sintesi.applicazione.porte.unaStruttura
import snastro.sintesi.dominio.BozzaElemento
import snastro.sintesi.dominio.BozzaRiassunto
import snastro.sintesi.dominio.ErroreSintesi
import snastro.sintesi.dominio.LunghezzaMassimaParole
import snastro.sintesi.dominio.RiassuntoId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AC-S90/S91; INV-S10. `ModificaLunghezzaMassimaRiassunto` delegates the range to
 * `LunghezzaMassimaRiassunto.modifica`, itself delegating to `LunghezzaMassimaParole.di` (INV-S9), and never
 * touches a Riassunto (INV-S10, ADR 0021 §3).
 */
class ModificaLunghezzaMassimaRiassuntoServizioTest {
    @Test
    fun `AC-S90 1500 senza riga viene salvato, un secondo cambio a 1800 aggiorna la stessa riga`() {
        val a = unAmbiente()

        a.servizio.esegui(ModificaLunghezzaMassimaRiassunto(PROGETTO, 1500)).atteso()

        assertEquals(LunghezzaMassimaParole.di(1500).atteso(), a.lunghezze.trova(PROGETTO).parole)
        assertEquals(listOf(LunghezzaMassimaRiassuntoModificata(PROGETTO)), a.eventi.pubblicati)

        a.servizio.esegui(ModificaLunghezzaMassimaRiassunto(PROGETTO, 1800)).atteso()

        assertEquals(LunghezzaMassimaParole.di(1800).atteso(), a.lunghezze.trova(PROGETTO).parole)
        assertEquals(
            listOf(LunghezzaMassimaRiassuntoModificata(PROGETTO), LunghezzaMassimaRiassuntoModificata(PROGETTO)),
            a.eventi.pubblicati,
        )
    }

    @Test
    fun `AC-S90 un cambio al valore gia salvato pubblica comunque (nessun caso speciale sul no-op)`() {
        val a = unAmbiente()
        a.servizio.esegui(ModificaLunghezzaMassimaRiassunto(PROGETTO, 1500)).atteso()

        a.servizio.esegui(ModificaLunghezzaMassimaRiassunto(PROGETTO, 1500)).atteso()

        assertEquals(LunghezzaMassimaParole.di(1500).atteso(), a.lunghezze.trova(PROGETTO).parole)
        assertEquals(
            listOf(LunghezzaMassimaRiassuntoModificata(PROGETTO), LunghezzaMassimaRiassuntoModificata(PROGETTO)),
            a.eventi.pubblicati,
        )
    }

    @Test
    fun `AC-S91 299 e 2501 rifiutano con LunghezzaMassimaFuoriIntervallo, niente scritto e nessun evento`() {
        listOf(299, 2501).forEach { fuori ->
            val a = unAmbiente()

            val errore = a.servizio.esegui(ModificaLunghezzaMassimaRiassunto(PROGETTO, fuori))
                .erroreAtteso<ErroreSintesi.LunghezzaMassimaFuoriIntervallo>()

            assertEquals(ErroreSintesi.LunghezzaMassimaFuoriIntervallo(fuori, 300, 2500), errore)
            assertEquals(LunghezzaMassimaParole.PREDEFINITA, a.lunghezze.trova(PROGETTO).parole.valore)
            assertTrue(a.eventi.pubblicati.isEmpty())
        }
    }

    @Test
    fun `AC-S91 299 e 2501 rifiutati non toccano un 1500 gia salvato, non solo la riga vuota PREDEFINITA`() {
        // The test above only proves "nothing written" on an EMPTY row: it cannot tell a written PREDEFINITA
        // apart from none at all. This seeds a real 1500 first and proves it survives each rejected value.
        val a = unAmbiente()
        a.servizio.esegui(ModificaLunghezzaMassimaRiassunto(PROGETTO, 1500)).atteso()

        listOf(299, 2501).forEach { fuori ->
            val errore = a.servizio.esegui(ModificaLunghezzaMassimaRiassunto(PROGETTO, fuori))
                .erroreAtteso<ErroreSintesi.LunghezzaMassimaFuoriIntervallo>()

            assertEquals(ErroreSintesi.LunghezzaMassimaFuoriIntervallo(fuori, 300, 2500), errore)
            assertEquals(LunghezzaMassimaParole.di(1500).atteso(), a.lunghezze.trova(PROGETTO).parole, "$fuori")
            assertEquals(
                listOf(LunghezzaMassimaRiassuntoModificata(PROGETTO)),
                a.eventi.pubblicati,
                "$fuori: solo la pubblicazione del salvataggio iniziale",
            )
        }
    }

    @Test
    fun `INV-S10 un in_attesa un in_corso e un pronto mantengono il proprio tetto, il pronto resta non superato`() {
        val struttura = unaStruttura(1 to 1)
        val inAttesa = unRiassunto("r-attesa", REGISTRAZIONE_1, parole = 500)
        val inCorso = unRiassunto("r-corso", REGISTRAZIONE_2, parole = 800).conAvvio()
        val pronto = unRiassunto("r-pronto", REGISTRAZIONE_3, parole = 1200)
            .conAvvio()
            .conCompletamento(unaBozzaMinima(), struttura)
        val riassunti = RiassuntoRepositoryFinta()
        listOf(inAttesa, inCorso, pronto).forEach { riassunti.salva(it).atteso() }
        val id = listOf(inAttesa.id, inCorso.id, pronto.id)
        val prima = stati(riassunti, id)
        val prontoSuperatoPrima = checkNotNull(riassunti.trova(pronto.id)).superato(struttura)
        val a = unAmbiente(riassunti = riassunti)

        a.servizio.esegui(ModificaLunghezzaMassimaRiassunto(PROGETTO, 1900)).atteso()

        assertEquals(prima, stati(riassunti, id))
        assertEquals(prontoSuperatoPrima, checkNotNull(riassunti.trova(pronto.id)).superato(struttura))
    }

    private fun stati(riassunti: RiassuntoRepositoryFinta, id: List<RiassuntoId>): List<List<Any?>> =
        id.map { checkNotNull(riassunti.trova(it)).statoOsservabile() }

    private fun unaBozzaMinima(): BozzaRiassunto = BozzaRiassunto(
        sommario = null,
        decisioni = listOf(BozzaElemento("Decisione.", listOf(1), null)),
        questioniAperte = emptyList(),
        azioni = emptyList(),
        puntiChiave = emptyList(),
    )

    private data class Ambiente(
        val servizio: ModificaLunghezzaMassimaRiassuntoServizio,
        val lunghezze: LunghezzaMassimaRiassuntoRepositoryFinta,
        val eventi: DispatcherEventiFinta,
    )

    /** A fresh set of fakes wired into a [ModificaLunghezzaMassimaRiassuntoServizio]; [riassunti] shares the same
     * (fake) unit of work, as the real composition's SQLite transaction would (INV-S10 is a same-DB guarantee). */
    private fun unAmbiente(
        lunghezze: LunghezzaMassimaRiassuntoRepositoryFinta = LunghezzaMassimaRiassuntoRepositoryFinta(),
        riassunti: RiassuntoRepositoryFinta = RiassuntoRepositoryFinta(),
    ): Ambiente {
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(lunghezze, riassunti))
        val servizio = ModificaLunghezzaMassimaRiassuntoServizio(eventi.unitaDiLavoro, lunghezze, eventi)
        return Ambiente(servizio, lunghezze, eventi)
    }

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val REGISTRAZIONE_1 = RegistrazioneId("registrazione-1")
        val REGISTRAZIONE_2 = RegistrazioneId("registrazione-2")
        val REGISTRAZIONE_3 = RegistrazioneId("registrazione-3")
    }
}
