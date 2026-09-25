package snastro.progetto.applicazione.comandi

import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.progetto.applicazione.porte.ArchivioAudio
import snastro.progetto.applicazione.porte.ArchivioAudioFinta
import snastro.progetto.applicazione.porte.EliminazioneInSospeso
import snastro.progetto.applicazione.porte.EliminazioniInSospeso
import snastro.progetto.applicazione.porte.EliminazioniInSospesoFinta
import snastro.progetto.applicazione.porte.PuliziaDerivatiRegistrazione
import snastro.progetto.applicazione.porte.PuliziaDerivatiRegistrazioneFinta
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** `CompletaEliminazioniRegistrazioni` (ADR 0020 §4): the crash recovery of the file removals, at project open. */
class CompletaEliminazioniRegistrazioniServizioTest {
    private val passi = mutableListOf<String>()
    private val inSospeso = EliminazioniInSospesoFinta()
    private val uow = UnitaDiLavoroFinta(inSospeso)
    private val file = ArchivioAudioFinta()
    private val derivati = PuliziaDerivatiRegistrazioneFinta()
    private val servizio = CompletaEliminazioniRegistrazioniServizio(
        uow,
        InSospesoRegistrate(inSospeso, passi, uow),
        ArchivioRegistrato(file, passi),
        DerivatiRegistrati(derivati, passi),
    )
    private val a = unaEliminazione("reg-a")
    private val b = unaEliminazione("reg-b")

    init {
        listOf(a, b).forEach { e ->
            inSospeso.registra(e)
            file.conFileNelProgetto(e.riferimentoAudio.percorsoRelativo, byteArrayOf(1))
        }
    }

    @Test
    fun `AC-605 per ogni riga in ordine scarta l audio pulisce i derivati e conclude in una transazione propria`() {
        servizio.esegui(CompletaEliminazioniRegistrazioni).atteso()

        assertEquals(
            listOf(
                "scarta reg-a", "pulisci reg-a", "concludi reg-a in transazione",
                "scarta reg-b", "pulisci reg-b", "concludi reg-b in transazione",
            ),
            passi,
        )
        assertEquals(listOf(a, b), derivati.chiamate)
        assertEquals(emptyList(), inSospeso.elenco())
        assertEquals(emptySet(), file.archiviati)
    }

    @Test
    fun `AC-606 una pulizia fallita lascia la riga in sospeso senza bloccare le altre ne l apertura`() {
        derivati.falliscaUnaVoltaPer(a.registrazioneId)

        servizio.esegui(CompletaEliminazioniRegistrazioni).atteso()

        assertEquals(listOf(a), inSospeso.elenco(), "A resta, B e conclusa")
        assertEquals(listOf("pulisci reg-a", "pulisci reg-b", "concludi reg-b in transazione"), passi.filterNot { it.startsWith("scarta") })

        servizio.esegui(CompletaEliminazioniRegistrazioni).atteso()

        assertEquals(emptyList(), inSospeso.elenco(), "guasto rientrato: anche A e conclusa")
    }

    @Test
    fun `AC-607 idempotente - due esecuzioni o file gia spariti danno lo stesso esito e senza righe nessuna chiamata`() {
        file.scarta(a.riferimentoAudio)

        servizio.esegui(CompletaEliminazioniRegistrazioni).atteso()
        assertEquals(emptyList(), inSospeso.elenco())
        assertNull(file.contenuto(a.riferimentoAudio.percorsoRelativo))
        passi.clear()

        servizio.esegui(CompletaEliminazioniRegistrazioni).atteso()

        assertEquals(emptyList(), passi, "nessuna riga: zero chiamate ad archivio e derivati")
        assertEquals(listOf(a, b), derivati.chiamate)
    }

    private fun unaEliminazione(id: String) =
        EliminazioneInSospeso(RegistrazioneId(id), "Seduta $id", LocalDate.of(2026, 9, 25), RiferimentoAudio("audio/$id.m4a"))

    private class ArchivioRegistrato(private val delegata: ArchivioAudio, private val passi: MutableList<String>) :
        ArchivioAudio by delegata {
        override fun scarta(r: RiferimentoAudio) {
            passi += "scarta ${r.percorsoRelativo.removePrefix("audio/").removeSuffix(".m4a")}"
            delegata.scarta(r)
        }
    }

    private class DerivatiRegistrati(
        private val delegata: PuliziaDerivatiRegistrazione,
        private val passi: MutableList<String>,
    ) : PuliziaDerivatiRegistrazione {
        override fun pulisci(e: EliminazioneInSospeso): Esito<Unit> {
            passi += "pulisci ${e.registrazioneId.valore}"
            return delegata.pulisci(e)
        }
    }

    private class InSospesoRegistrate(
        private val delegata: EliminazioniInSospeso,
        private val passi: MutableList<String>,
        private val uow: UnitaDiLavoroFinta,
    ) : EliminazioniInSospeso by delegata {
        override fun concludi(id: RegistrazioneId) {
            passi += "concludi ${id.valore}" + if (uow.transazioneAperta) " in transazione" else " FUORI transazione"
            delegata.concludi(id)
        }
    }
}
