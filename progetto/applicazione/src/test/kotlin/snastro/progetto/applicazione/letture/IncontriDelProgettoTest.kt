package snastro.progetto.applicazione.letture

import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.progetto.applicazione.porte.IncontroRepositoryFinta
import snastro.progetto.applicazione.porte.RegistrazioneRepositoryFinta
import snastro.progetto.dominio.Registrazione
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class IncontriDelProgettoTest {
    private val progettoId = ProgettoId("progetto-1")
    private val registrazioni = RegistrazioneRepositoryFinta()
    private val catalogo = CatalogoRegistrazioni(registrazioni, IncontroRepositoryFinta(registrazioni))
    private val vista = IncontriDelProgetto(registrazioni, catalogo)

    @Test
    fun `INV-I3 un Incontro di una Parte ha i dati della riga di oggi e numParti 1`() {
        registrazioni.salva(una("id-1", "Seduta del 12 marzo", "inc-1", LocalDate.of(2026, 3, 12), durataMs = 90_000L))

        val righe = vista.delProgetto(progettoId)
        val oggi = RegistrazioniDelProgetto(registrazioni).delProgetto(progettoId).single()

        val riga = righe.single()
        assertEquals(oggi.titolo, riga.titolo)
        assertEquals(oggi.dataRegistrazione, riga.data)
        assertEquals(oggi.durataMs, riga.durataMs)
        assertEquals(1, riga.numParti)
        assertEquals(IncontroId("inc-1"), riga.incontroId)
        assertEquals(listOf(RegistrazioneId("id-1")), riga.parti.map { it.registrazioneId })
        assertEquals(listOf(1), riga.parti.map { it.numero })
    }

    @Test
    fun `AC-I38 un Incontro di 3 Parti ha titolo e data della Parte 1, durata somma, Parti ordinate e numerate`() {
        // inserted out of order; Parte 1 is the earliest date
        registrazioni.salva(una("c", "Terza", "inc", LocalDate.of(2026, 3, 14), durataMs = 300L))
        registrazioni.salva(una("a", "Prima", "inc", LocalDate.of(2026, 3, 12), durataMs = 100L))
        registrazioni.salva(una("b", "Seconda", "inc", LocalDate.of(2026, 3, 13), durataMs = 200L))

        val riga = vista.delProgetto(progettoId).single()

        assertEquals("Prima", riga.titolo)
        assertEquals(LocalDate.of(2026, 3, 12), riga.data)
        assertEquals(600L, riga.durataMs)
        assertEquals(3, riga.numParti)
        assertEquals(
            listOf(RegistrazioneId("a") to 1, RegistrazioneId("b") to 2, RegistrazioneId("c") to 3),
            riga.parti.map { it.registrazioneId to it.numero },
        )
    }

    @Test
    fun `AC-I38 gli Incontri sono dal piu recente per data, inclusi quelli multi-Parte`() {
        registrazioni.salva(una("m1", "Mid", "inc-mid", LocalDate.of(2026, 2, 1)))
        registrazioni.salva(una("n1", "New", "inc-new", LocalDate.of(2026, 3, 1)))
        registrazioni.salva(una("n2", "New 2", "inc-new", LocalDate.of(2026, 3, 2)))
        registrazioni.salva(una("o1", "Old", "inc-old", LocalDate.of(2026, 1, 1)))

        assertEquals(
            listOf("inc-new", "inc-mid", "inc-old").map(::IncontroId),
            vista.delProgetto(progettoId).map { it.incontroId },
        )
    }

    @Test
    fun `a parita di data vince l'ultima aggiunta, come oggi`() {
        val d = LocalDate.of(2026, 3, 1)
        registrazioni.salva(una("id-1", "A", "inc-1", d).aggiuntaIl("2026-03-01T10:00:00Z"))
        registrazioni.salva(una("id-2", "B", "inc-2", d).aggiuntaIl("2026-03-01T11:00:00Z"))

        assertEquals(
            listOf(IncontroId("inc-2"), IncontroId("inc-1")),
            vista.delProgetto(progettoId).map { it.incontroId },
        )
    }

    @Test
    fun `AC-I38 un Progetto senza Incontri restituisce lista vuota`() {
        assertEquals(emptyList(), vista.delProgetto(progettoId))
    }

    @Test
    fun `non include gli Incontri di un altro Progetto`() {
        registrazioni.salva(una("altro-1", "X", "inc-1", LocalDate.of(2026, 3, 1)))

        assertEquals(emptyList(), vista.delProgetto(progettoId))
    }

    private fun una(
        id: String,
        titolo: String,
        incontro: String,
        data: LocalDate,
        durataMs: Long = 3_600_000L,
    ): Registrazione =
        Registrazione.aggiungi(
            id = RegistrazioneId(id),
            progettoId = if (id.startsWith("altro")) ProgettoId("altro") else progettoId,
            incontroId = IncontroId(incontro),
            titolo = titolo,
            riferimentoAudio = RiferimentoAudio("audio/$id.m4a"),
            durataMs = durataMs,
            dataRegistrazione = data,
            aggiuntaAlle = Instant.parse("2026-09-23T10:00:00Z"),
        ).aggregato

    private fun Registrazione.aggiuntaIl(istante: String): Registrazione =
        Registrazione.aggiungi(
            id = id,
            progettoId = progettoId,
            incontroId = incontroId,
            titolo = titolo,
            riferimentoAudio = riferimentoAudio,
            durataMs = durataMs,
            dataRegistrazione = dataRegistrazione,
            aggiuntaAlle = Instant.parse(istante),
        ).aggregato
}
