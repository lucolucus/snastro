package snastro.progetto.adattatori.persistenza

import org.junit.jupiter.api.Test
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.atteso
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.databaseInMemoria
import snastro.progetto.applicazione.porte.EliminazioneInSospeso
import snastro.progetto.applicazione.porte.EliminazioniInSospesoContratto
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals

/** D2 (dev-architecture-app.md#porta-contratto): the contract passes real-on-real (AC-615 via AC-616). */
class EliminazioniInSospesoSqlTest : EliminazioniInSospesoContratto() {
    override fun ambiente(): Ambiente {
        val db = databaseInMemoria()
        // The contract needs each registra at a strictly later time: a clock that ticks 1 ms per read.
        val orologio = OrologioCheAvanza(Instant.parse("2026-09-25T10:00:00Z"))
        return object : Ambiente {
            override val inSospeso = EliminazioniInSospesoSql(db, orologio)
            override val unitaDiLavoro = UnitaDiLavoroSql(db)
        }
    }

    @Test
    fun `AC-616 eliminata_alle e l'istante del Clock in epoch millis e data_registrazione una data ISO`() {
        val db = databaseInMemoria()
        val istante = Instant.parse("2026-09-25T10:15:30.123Z")
        val inSospeso = EliminazioniInSospesoSql(db, Clock.fixed(istante, ZoneOffset.UTC))

        UnitaDiLavoroSql(db).inTransazione {
            inSospeso.registra(unaEliminazione("reg-1"))
            Esito.Ok(Unit)
        }.atteso()

        val riga = db.eliminazioneInSospesoQueries.elenco().executeAsOne()
        assertEquals(istante.toEpochMilli(), riga.eliminata_alle)
        assertEquals("2026-02-28", riga.data_registrazione)
        assertEquals(listOf(unaEliminazione("reg-1")), inSospeso.elenco())
    }

    @Test
    fun `AC-616 a parita di eliminata_alle elenco ordina per registrazioneId`() {
        val db = databaseInMemoria()
        val inSospeso = EliminazioniInSospesoSql(db, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))

        UnitaDiLavoroSql(db).inTransazione {
            listOf("reg-c", "reg-a", "reg-b").forEach { inSospeso.registra(unaEliminazione(it)) }
            Esito.Ok(Unit)
        }.atteso()

        assertEquals(listOf("reg-a", "reg-b", "reg-c"), inSospeso.elenco().map { it.registrazioneId.valore })
    }

    private fun unaEliminazione(id: String) = EliminazioneInSospeso(
        registrazioneId = RegistrazioneId(id),
        titolo = "Seduta $id",
        dataRegistrazione = LocalDate.of(2026, 2, 28),
        riferimentoAudio = RiferimentoAudio("audio/$id.m4a"),
    )

    /** A [Clock] that moves forward 1 ms at every read. */
    private class OrologioCheAvanza(private var adesso: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = adesso.also { adesso = adesso.plus(Duration.ofMillis(1)) }
    }
}
