package snastro.audio

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

/** AC-364: the choice of the recording date, table-tested on the pure [dataRegistrazione]. */
class DataRegistrazioneTest {
    private val roma: ZoneId = ZoneId.of("Europe/Rome")
    private val adesso: Instant = Instant.parse("2026-09-23T12:00:00Z")
    private val relogio: Clock = Clock.fixed(adesso, roma)
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
            val data = dataRegistrazione(null, riga.creationTime, riga.creazioneFile, modifica, relogio).data
            assertEquals(riga.attesa, data, riga.caso)
        }
    }

    @Test
    fun `AC-364 un istante uguale ad adesso e plausibile`() {
        assertEquals(settembre(23), dataRegistrazione(null, adesso.toString(), null, modifica, relogio).data)
    }
}

/** ADR 0040 / AC-I52, AC-I53: `moov/udta/date` gives the date AND the start time from one instant. */
class DataEOraDaUdtaDateTest {
    private val roma: ZoneId = ZoneId.of("Europe/Rome")
    private val adesso: Instant = Instant.parse("2026-10-01T12:00:00Z")
    private val relogio: Clock = Clock.fixed(adesso, roma)
    private val creationTime = "2026-09-22T22:05:47Z" // the real part-2 case: says 23/09 in Rome
    private val nascita: Instant = Instant.parse("2026-09-30T08:00:00Z") // the copy: 30 Sep
    private val modifica: Instant = Instant.parse("2026-09-20T08:00:00Z")

    private fun sonda(udta: String?, creation: String? = creationTime, nascitaFile: Instant? = nascita) =
        dataRegistrazione(udta, creation, nascitaFile, modifica, relogio)

    @Test
    fun `AC-I52 udta date da data e ora locali, non quelle di creation_time`() {
        val esito = sonda("2026-09-21T20:44:22Z")
        assertEquals(DataEOra(LocalDate.of(2026, 9, 21), LocalTime.of(22, 44, 22)), esito)
    }

    @Test
    fun `AC-I52 vicino a mezzanotte la data e l'ora sono locali e coerenti`() {
        assertEquals(
            DataEOra(LocalDate.of(2026, 9, 22), LocalTime.of(0, 30, 5)),
            sonda("2026-09-21T22:30:05Z"),
        )
        assertEquals(
            DataEOra(LocalDate.of(2026, 9, 21), LocalTime.of(23, 59, 59)),
            sonda("2026-09-21T23:59:59+02:00"),
        )
    }

    @Test
    fun `AC-I52 i secondi frazionari sono troncati`() {
        assertEquals(LocalTime.of(22, 22, 13), sonda("2026-09-21T20:22:13.987Z").ora)
    }

    @Test
    fun `AC-I53 udta date assente, senza fuso, illeggibile o implausibile lascia l'ora vuota e la data della catena`() {
        val casi = listOf(
            "assente" to null,
            "senza fuso" to "2026-09-21T20:44:22",
            "illeggibile" to "ieri sera",
            "vuoto" to "",
            "epoch 1970" to "1970-01-01T00:00:00Z",
            "prima del 1970-01-01T23:59:59Z" to "1970-01-01T23:59:58Z",
            "1904" to "1904-01-01T00:00:00Z",
            "nel futuro" to "2026-10-01T12:00:01Z",
        )
        casi.forEach { (caso, udta) ->
            assertEquals(DataEOra(LocalDate.of(2026, 9, 23), null), sonda(udta), caso)
        }
    }

    @Test
    fun `AC-I53 la catena resta quella di AC-364 e l'ora non viene mai da creation_time o dai file`() {
        assertEquals(DataEOra(LocalDate.of(2026, 9, 30), null), sonda(null, creation = null))
        assertEquals(DataEOra(LocalDate.of(2026, 9, 20), null), sonda(null, creation = null, nascitaFile = null))
    }

    @Test
    fun `AC-I53 un udta date uguale ad adesso e plausibile`() {
        assertEquals(LocalTime.of(14, 0, 0), sonda(adesso.toString()).ora)
    }
}
