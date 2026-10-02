package snastro.progetto.applicazione.letture

import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.progetto.applicazione.porte.IncontroRepositoryFinta
import snastro.progetto.applicazione.porte.RegistrazioneRepositoryFinta
import snastro.progetto.dominio.OraDiInizio
import snastro.progetto.dominio.Registrazione
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CatalogoRegistrazioniTest {
    private val progettoId = ProgettoId("progetto-1")
    private val registrazioni = RegistrazioneRepositoryFinta()
    private val catalogo = CatalogoRegistrazioni(registrazioni, IncontroRepositoryFinta(registrazioni))

    @Test
    fun `AC-97 id noto restituisce la RegistrazioneVista con tutti i campi della Registrazione`() {
        val id = RegistrazioneId("id-1")
        registrazioni.salva(unaRegistrazione(id, "Seduta del 12 marzo"))

        assertEquals(
            RegistrazioneVista(
                registrazioneId = id,
                progettoId = progettoId,
                incontroId = IncontroId("incontro-di-id-1"),
                titolo = "Seduta del 12 marzo",
                riferimentoAudio = RiferimentoAudio("audio/id-1.m4a"),
                dataRegistrazione = LocalDate.of(2026, 3, 12),
                oraDiInizio = null,
                durataMs = 3_600_000L,
            ),
            catalogo.registrazione(id),
        )
    }

    @Test
    fun `AC-97 id sconosciuto restituisce null`() {
        assertNull(catalogo.registrazione(RegistrazioneId("id-sconosciuto")))
    }

    @Test
    fun `AC-97 id sconosciuto tra registrazioni note restituisce null senza toccarle`() {
        val nota = RegistrazioneId("id-1")
        registrazioni.salva(unaRegistrazione(nota, "Seduta del 12 marzo"))

        assertNull(catalogo.registrazione(RegistrazioneId("id-sconosciuto")))
        assertEquals("Seduta del 12 marzo", catalogo.registrazione(nota)?.titolo)
    }

    @Test
    fun `AC-I203 parti restituisce esattamente le Registrazioni dell'Incontro, in nessun ordine garantito`() {
        val incontro = IncontroId("incontro-a")
        registrazioni.salva(unaRegistrazione(RegistrazioneId("id-1"), "Parte 1", incontro))
        registrazioni.salva(unaRegistrazione(RegistrazioneId("id-2"), "Parte 2", incontro))
        registrazioni.salva(unaRegistrazione(RegistrazioneId("id-3"), "Altro incontro"))

        assertEquals(setOf(RegistrazioneId("id-1"), RegistrazioneId("id-2")), catalogo.parti(incontro)?.toSet())
        assertEquals(2, catalogo.parti(incontro)?.size)
    }

    @Test
    fun `AC-I203 parti di un Incontro sconosciuto restituisce null`() {
        registrazioni.salva(unaRegistrazione(RegistrazioneId("id-1"), "Seduta"))
        assertNull(catalogo.parti(IncontroId("incontro-sconosciuto")))
    }

    @Test
    fun `AC-I203 parti dopo l'eliminazione dell'unica Parte restituisce null`() {
        val r = unaRegistrazione(RegistrazioneId("id-1"), "Seduta")
        registrazioni.salva(r)
        registrazioni.rimuovi(r.id)
        assertNull(catalogo.parti(r.incontroId))
    }

    @Test
    fun `AC-I203 parti non elenca mai una Registrazione di un altro Incontro`() {
        val a = unaRegistrazione(RegistrazioneId("id-1"), "A")
        val b = unaRegistrazione(RegistrazioneId("id-2"), "B")
        registrazioni.salva(a)
        registrazioni.salva(b)
        assertEquals(listOf(a.id), catalogo.parti(a.incontroId))
        assertEquals(listOf(b.id), catalogo.parti(b.incontroId))
        assertEquals(a.incontroId, catalogo.registrazione(a.id)?.incontroId)
    }

    @Test
    fun `INV-I2 incontro con 3 Parti in disordine le restituisce ordinate 1-3 e una modifica d'ora cambia i numeri`() {
        val inc = IncontroId("incontro-a")
        val tarda = unaRegistrazione(RegistrazioneId("c"), "Tarda", inc, ora = LocalTime.of(15, 0))
        val senzaOra = unaRegistrazione(RegistrazioneId("b"), "Senza ora", inc)
        val presto = unaRegistrazione(RegistrazioneId("a"), "Presto", inc, ora = LocalTime.of(9, 30))
        listOf(tarda, senzaOra, presto).forEach(registrazioni::salva)

        val vista = catalogo.incontro(inc)!!
        assertEquals(inc, vista.incontroId)
        assertEquals(progettoId, vista.progettoId)
        assertEquals(listOf("a" to 1, "c" to 2, "b" to 3), numeri(inc))
        assertEquals(LocalTime.of(9, 30), vista.parti[0].oraDiInizio)
        assertEquals(
            ParteVista(RegistrazioneId("b"), 3, "Senza ora", LocalDate.of(2026, 3, 12), null, 3_600_000L),
            vista.parti[2],
        )

        presto.modificaOraDiInizio(oraDi(LocalTime.of(16, 0)))
        registrazioni.salva(presto)
        assertEquals(listOf("c" to 1, "a" to 2, "b" to 3), numeri(inc))
    }

    @Test
    fun `AC-I37 incontro sconosciuto o cessato restituisce null`() {
        assertNull(catalogo.incontro(IncontroId("sconosciuto")))
        val r = unaRegistrazione(RegistrazioneId("id-1"), "Seduta")
        registrazioni.salva(r)
        registrazioni.rimuovi(r.id)
        assertNull(catalogo.incontro(r.incontroId))
    }

    @Test
    fun `AC-I37 registrazione porta oraDiInizio e parti e la proiezione di incontro`() {
        val inc = IncontroId("incontro-a")
        val con = unaRegistrazione(RegistrazioneId("id-1"), "A", inc, ora = LocalTime.of(10, 0, 5))
        registrazioni.salva(con)
        registrazioni.salva(unaRegistrazione(RegistrazioneId("id-2"), "B", inc))
        assertEquals(LocalTime.of(10, 0, 5), catalogo.registrazione(con.id)?.oraDiInizio)
        assertNull(catalogo.registrazione(RegistrazioneId("id-2"))?.oraDiInizio)
        assertEquals(catalogo.incontro(inc)!!.parti.map { it.registrazioneId }, catalogo.parti(inc))
    }

    private fun numeri(inc: IncontroId) =
        catalogo.incontro(inc)!!.parti.map { it.registrazioneId.valore to it.numero }

    private fun oraDi(t: LocalTime): OraDiInizio = (OraDiInizio.di(t) as Esito.Ok).valore

    private fun unaRegistrazione(
        id: RegistrazioneId,
        titolo: String,
        incontroId: IncontroId = IncontroId("incontro-di-${id.valore}"),
        ora: LocalTime? = null,
    ): Registrazione =
        Registrazione.aggiungi(
            id = id,
            progettoId = progettoId,
            incontroId = incontroId,
            titolo = titolo,
            riferimentoAudio = RiferimentoAudio("audio/${id.valore}.m4a"),
            durataMs = 3_600_000L,
            dataRegistrazione = LocalDate.of(2026, 3, 12),
            aggiuntaAlle = Instant.parse("2026-09-23T10:00:00Z"),
            oraDiInizio = ora?.let(::oraDi),
        ).aggregato
}
