package snastro.audio

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

/** AC-364: the choice of the recording date, table-tested on the pure [dataRegistrazione]. */
class DataRegistrazioneTest {
    private val roma: ZoneId = ZoneId.of("Europe/Rome")
    private val adesso: Instant = Instant.parse("2026-09-23T12:00:00Z")
    private val nascita: Instant = Instant.parse("2026-09-23T08:00:00Z") // the copy: 23 Sep
    private val modifica: Instant = Instant.parse("2026-09-20T08:00:00Z")

    private fun settembre(giorno: Int): LocalDate = LocalDate.of(2026, 9, giorno)

    private data class Riga(
        val caso: String,
        val creationTime: String?,
        val creazioneFile: Instant?,
        val attesa: LocalDate,
    )

    private val tabella = listOf(
        Riga("metadato m4a plausibile", "2026-09-21T09:30:00.000000Z", nascita, settembre(21)),
        Riga("metadato UTC convertito nel fuso locale", "2026-09-21T22:30:00.000000Z", nascita, settembre(22)),
        Riga("metadato con offset", "2026-09-21T00:30:00+02:00", nascita, settembre(21)),
        Riga("metadato senza fuso letto in UTC", "2026-09-21 22:30:00", nascita, settembre(22)),
        Riga("metadato assente: nascita del file", null, nascita, settembre(23)),
        Riga("epoch zero 1904: nascita del file", "1904-01-01T00:00:00.000000Z", nascita, settembre(23)),
        Riga("epoch zero 1970: nascita del file", "1970-01-01T00:00:00.000000Z", nascita, settembre(23)),
        Riga("metadato nel futuro: nascita del file", "2027-01-01T00:00:00Z", nascita, settembre(23)),
        Riga("metadato illeggibile: nascita del file", "ieri sera", nascita, settembre(23)),
        Riga("metadato vuoto: nascita del file", "", nascita, settembre(23)),
        Riga("nessuna nascita: ultima modifica", null, null, settembre(20)),
        Riga("nascita epoch zero: ultima modifica", null, Instant.EPOCH, settembre(20)),
        Riga("nascita nel futuro: ultima modifica", null, Instant.parse("2026-12-01T00:00:00Z"), settembre(20)),
        Riga("tutto implausibile: ultima modifica", "1904-01-01T00:00:00Z", Instant.EPOCH, settembre(20)),
    )

    @Test
    fun `AC-364 metadato creation_time, poi nascita, poi ultima modifica, solo se plausibili`() {
        tabella.forEach { riga ->
            val data = dataRegistrazione(riga.creationTime, riga.creazioneFile, modifica, adesso, roma)
            assertEquals(riga.attesa, data, riga.caso)
        }
    }

    @Test
    fun `AC-364 un istante uguale ad adesso e plausibile`() {
        assertEquals(settembre(23), dataRegistrazione(adesso.toString(), null, modifica, adesso, roma))
    }
}
