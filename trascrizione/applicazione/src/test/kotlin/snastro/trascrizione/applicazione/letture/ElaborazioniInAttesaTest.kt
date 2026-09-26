package snastro.trascrizione.applicazione.letture

import snastro.kernel.ElaborazioneId
import snastro.kernel.RegistrazioneId
import snastro.kernel.atteso
import snastro.trascrizione.applicazione.porte.ElaborazioneRepositoryFinta
import snastro.trascrizione.dominio.StatoElaborazione.COMPLETATA
import snastro.trascrizione.dominio.StatoElaborazione.FALLITA
import snastro.trascrizione.dominio.StatoElaborazione.IN_ATTESA
import snastro.trascrizione.dominio.StatoElaborazione.IN_CORSO
import snastro.trascrizione.dominio.unaElaborazione
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AC-S23 (boundary `elaborazioni-in-coda`, consumer-driven: `avvio-coda-condivisa`'s Elaborazione
 * source, ADR 0023 §1): [ElaborazioniInAttesa.elenco] carries only what the shared queue needs —
 * `in_attesa` rows, FIFO, primitive ids.
 */
class ElaborazioniInAttesaTest {
    private val elaborazioni = ElaborazioneRepositoryFinta()
    private val coda = ElaborazioniInAttesa(elaborazioni)

    @Test
    fun `AC-S23 elenca solo le in_attesa, FIFO per creataAlle poi id, con elaborazioneId come stringa`() {
        val prima = RegistrazioneId("registrazione-1")
        val seconda = RegistrazioneId("registrazione-2")
        // salvate fuori ordine: l'elenco deve comunque uscire FIFO
        elaborazioni.salva(unaElaborazione(IN_ATTESA, idDi("el-2"), seconda, creataAlle = t(1))).atteso()
        elaborazioni.salva(unaElaborazione(IN_ATTESA, idDi("el-1"), prima, creataAlle = t(0))).atteso()
        elaborazioni.salva(unaElaborazione(IN_CORSO, idDi("el-corso"), RegistrazioneId("registrazione-corso")))
            .atteso()
        elaborazioni.salva(unaElaborazione(COMPLETATA, idDi("el-completata"), RegistrazioneId("registrazione-c")))
            .atteso()
        elaborazioni.salva(unaElaborazione(FALLITA, idDi("el-fallita"), RegistrazioneId("registrazione-f"))).atteso()

        val elenco = coda.elenco()

        assertEquals(
            listOf(
                ElaborazioneInCoda("el-1", prima, t(0)),
                ElaborazioneInCoda("el-2", seconda, t(1)),
            ),
            elenco,
            "solo le in_attesa, in ordine FIFO — in_corso/completata/fallita non appaiono",
        )
    }

    @Test
    fun `AC-S23 a parita di creataAlle il pareggio e per id, come inAttesa del repository`() {
        elaborazioni.salva(
            unaElaborazione(IN_ATTESA, idDi("el-b"), RegistrazioneId("registrazione-b"), creataAlle = t(0)),
        ).atteso()
        elaborazioni.salva(
            unaElaborazione(IN_ATTESA, idDi("el-a"), RegistrazioneId("registrazione-a"), creataAlle = t(0)),
        ).atteso()

        assertEquals(listOf("el-a", "el-b"), coda.elenco().map { it.elaborazioneId })
    }

    @Test
    fun `AC-S23 un annullamento (rimuoviInAttesa) fa sparire la riga dall elenco`() {
        val registrazione = RegistrazioneId("registrazione-1")
        elaborazioni.salva(unaElaborazione(IN_ATTESA, idDi("el-1"), registrazione, creataAlle = t(0))).atteso()
        assertEquals(1, coda.elenco().size)

        elaborazioni.rimuoviInAttesa(idDi("el-1")).atteso()

        assertEquals(emptyList(), coda.elenco())
    }

    private companion object {
        fun idDi(valore: String) = ElaborazioneId(valore)
        fun t(secondi: Long): Instant = Instant.parse("2026-09-23T10:00:00Z").plusSeconds(secondi)
    }
}
