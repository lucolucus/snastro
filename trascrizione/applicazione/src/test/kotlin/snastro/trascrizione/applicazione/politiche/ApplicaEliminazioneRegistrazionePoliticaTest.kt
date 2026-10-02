package snastro.trascrizione.applicazione.politiche

import snastro.kernel.DispatcherEventi
import snastro.kernel.ElaborazioneId
import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.kernel.unIncontroDi
import snastro.trascrizione.applicazione.eventi.TrascrittoEliminato
import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.applicazione.porte.ElaborazioneRepositoryFinta
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepository
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepositoryFinta
import snastro.trascrizione.dominio.DURATA_TRASCRITTO_MS
import snastro.trascrizione.dominio.ErroreTrascrizione.ElaborazioneGiaAperta
import snastro.trascrizione.dominio.StatoElaborazione
import snastro.trascrizione.dominio.StatoElaborazione.COMPLETATA
import snastro.trascrizione.dominio.StatoElaborazione.FALLITA
import snastro.trascrizione.dominio.StatoElaborazione.IN_ATTESA
import snastro.trascrizione.dominio.StatoElaborazione.IN_CORSO
import snastro.trascrizione.dominio.VociDellIncontro
import snastro.trascrizione.dominio.unSegmentoIniziale
import snastro.trascrizione.dominio.unaElaborazione
import snastro.trascrizione.dominio.unaRadice
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Trascrizione half of INV-28 (ADR 0020 §2 step 4): veto while an Elaborazione is open, otherwise purge. */
class ApplicaEliminazioneRegistrazionePoliticaTest {
    private val elaborazioni = ElaborazioneRepositoryContata(ElaborazioneRepositoryFinta())
    private val trascritti = VociDellIncontroRepositoryContata(VociDellIncontroRepositoryFinta())
    private val pubblicati = mutableListOf<EventoPubblicato>()
    private val politica = politicaCon(pubblicati)

    @Test
    fun `AC-608 INV-28 con un Elaborazione in attesa o in corso anche accanto a una completata nulla e tolto`() {
        for (aperta in listOf(IN_ATTESA, IN_CORSO)) {
            for (conCompletata in listOf(false, true)) {
                val r = RegistrazioneId("reg-$aperta-$conCompletata")
                if (conCompletata) salva(COMPLETATA, "completata-$r", r, PRIMA)
                salva(aperta, "aperta-$r", r, DOPO)
                trascritti.salva(unaRadice(registrazioneId = r))

                val esito = politica.applica(r, unIncontroDi(r), incontroCessato = true)
                val errore = esito.erroreAtteso<ElaborazioneGiaAperta>()

                assertEquals(ElaborazioneGiaAperta(r), errore)
                assertEquals(if (conCompletata) 2 else 1, elaborazioni.diRegistrazione(r).size)
                assertNotNull(trascritti.trascritto(r))
            }
        }
        assertEquals(0, elaborazioni.rimozioni + trascritti.rimozioni, "rimuovi / rimuoviDiRegistrazione mai chiamati")
    }

    @Test
    fun `AC-609 INV-28 con completata fallita e un Trascritto toglie tutto di r e niente di un altra Registrazione`() {
        salva(FALLITA, "fallita", R, PRIMA)
        salva(COMPLETATA, "completata", R, DOPO)
        trascritti.salva(unaRadice(registrazioneId = R))
        salva(COMPLETATA, "altra-completata", ALTRA, PRIMA)
        salva(IN_ATTESA, "altra-in-attesa", ALTRA, DOPO)
        trascritti.salva(unaRadice(registrazioneId = ALTRA))

        politica.applica(R, unIncontroDi(R), incontroCessato = true).atteso()

        assertEquals(emptyList(), elaborazioni.diRegistrazione(R))
        assertNull(trascritti.trascritto(R))
        assertEquals(
            setOf("altra-completata", "altra-in-attesa"),
            elaborazioni.diRegistrazione(ALTRA).map { it.id.valore }.toSet(),
        )
        assertNotNull(trascritti.trascritto(ALTRA))
    }

    @Test
    fun `AC-610 senza Elaborazione e senza Trascritto e Ok e nessuna chiamata rimuove qualcosa`() {
        politica.applica(R, unIncontroDi(R), incontroCessato = true).atteso()

        assertEquals(0, elaborazioni.rimozioni + trascritti.rimozioni)
    }

    @Test
    fun `AC-611 i collaboratori sono due repository e il dispatcher - nessun lettore, nessun decodificatore`() {
        val collaboratori = ApplicaEliminazioneRegistrazionePolitica::class.java.constructors.single().parameterTypes

        assertEquals(
            listOf(
                ElaborazioneRepository::class.java,
                VociDellIncontroRepository::class.java,
                DispatcherEventi::class.java,
            ),
            collaboratori.toList(),
        )
    }

    @Test
    fun `INV-I6 una Parte non ultima toglie le Voci solo sue, tiene le condivise e pubblica TrascrittoEliminato`() {
        val radice = VociDellIncontro.crea(INCONTRO)
        radice.completaParte(R, dueVoci, DURATA_TRASCRITTO_MS).atteso() // Voce 1, Voce 2
        radice.completaParte(ALTRA, dueVoci, DURATA_TRASCRITTO_MS).atteso() // Voce 3, Voce 4
        radice.unisci(VoceId(2), VoceId(3)).atteso() // Voce 2 speaks in both Parti, Voce 4 only in ALTRA
        trascritti.salva(radice)

        politica.applica(ALTRA, INCONTRO, incontroCessato = false).atteso()

        val rimasta = assertNotNull(trascritti.trova(INCONTRO))
        assertNull(rimasta.trascritto(ALTRA))
        assertEquals(listOf(VoceId(1), VoceId(2)), rimasta.voci)
        assertEquals<List<EventoPubblicato>>(listOf(TrascrittoEliminato(ALTRA, INCONTRO, setOf(VoceId(4)))), pubblicati)
    }

    @Test
    fun `INV-I6 una Elaborazione aperta di un altra Parte non pone il veto`() {
        val radice = VociDellIncontro.crea(INCONTRO)
        radice.completaParte(R, listOf(unSegmentoIniziale(0, 0)), DURATA_TRASCRITTO_MS).atteso()
        trascritti.salva(radice)
        salva(IN_ATTESA, "altra-aperta", ALTRA, DOPO)

        politica.applica(R, INCONTRO, incontroCessato = false).atteso()

        assertEquals(1, elaborazioni.diRegistrazione(ALTRA).size)
        assertEquals(1, pubblicati.size)
    }

    @Test
    fun `INV-28 una Parte senza Trascritto non pubblica TrascrittoEliminato`() {
        val radice = VociDellIncontro.crea(INCONTRO)
        radice.completaParte(ALTRA, listOf(unSegmentoIniziale(0, 0)), DURATA_TRASCRITTO_MS).atteso()
        trascritti.salva(radice)
        salva(FALLITA, "fallita", R, PRIMA)

        politica.applica(R, INCONTRO, incontroCessato = false).atteso()

        assertTrue(pubblicati.isEmpty())
        assertEquals(emptyList(), elaborazioni.diRegistrazione(R))
        assertNotNull(trascritti.trascritto(ALTRA))
    }

    @Test
    fun `INV-28 il veto non pubblica nulla`() {
        trascritti.salva(unaRadice(registrazioneId = R))
        salva(IN_ATTESA, "aperta", R, DOPO)

        politica.applica(R, unIncontroDi(R), incontroCessato = true).erroreAtteso<ElaborazioneGiaAperta>()

        assertTrue(pubblicati.isEmpty())
    }

    @Test
    fun `INV-I4 eliminare una Parte che non e' l'ultima toglie il suo Trascritto e tiene la radice col contatore`() {
        val politica = politicaCon(pubblicati)
        val radice = VociDellIncontro.crea(INCONTRO)
        radice.completaParte(R, listOf(unSegmentoIniziale(0, 0)), DURATA_TRASCRITTO_MS).atteso()
        radice.completaParte(ALTRA, listOf(unSegmentoIniziale(0, 0)), DURATA_TRASCRITTO_MS).atteso()
        trascritti.salva(radice)
        salva(COMPLETATA, "completata", R, PRIMA)

        politica.applica(R, INCONTRO, incontroCessato = false).atteso()

        val rimasta = assertNotNull(trascritti.trova(INCONTRO))
        assertNull(rimasta.trascritto(R))
        assertNotNull(rimasta.trascritto(ALTRA))
        assertEquals(3, rimasta.prossimaVoce)
        assertEquals(0, trascritti.rimozioni, "the root is removed only when the Incontro ceases")
    }

    @Test
    fun `INV-I4 eliminare l'ultima Parte toglie la radice perche' l'Incontro cessa, anche senza un suo Trascritto`() {
        val radice = VociDellIncontro.crea(unIncontroDi(R))
        radice.completaParte(R, listOf(unSegmentoIniziale(0, 0)), DURATA_TRASCRITTO_MS).atteso()
        radice.rimuoviParte(R).atteso() // the counter outlives its last Trascritto
        trascritti.salva(radice)

        politica.applica(R, unIncontroDi(R), incontroCessato = true).atteso()

        assertNull(trascritti.trova(unIncontroDi(R)))
        assertEquals(1, trascritti.rimozioni)
        assertTrue(pubblicati.isEmpty(), "no Trascritto of the Parte: nothing to announce")
    }

    @Test
    fun `INV-I6 l'ultima Parte con un Trascritto toglie la radice e pubblica TrascrittoEliminato`() {
        trascritti.salva(unaRadice(registrazioneId = R, voci = 2))

        politica.applica(R, unIncontroDi(R), incontroCessato = true).atteso()

        assertNull(trascritti.trova(unIncontroDi(R)))
        val attesi = setOf(VoceId(1), VoceId(2))
        assertEquals<List<EventoPubblicato>>(listOf(TrascrittoEliminato(R, unIncontroDi(R), attesi)), pubblicati)
    }

    @Test
    fun `INV-I4 togliere l unica Parte di un Incontro che non cessa tiene la radice col contatore`() {
        val radice = VociDellIncontro.crea(INCONTRO)
        radice.completaParte(R, dueVoci, DURATA_TRASCRITTO_MS).atteso() // Voce 1, Voce 2
        trascritti.salva(radice)

        politica.applica(R, INCONTRO, incontroCessato = false).atteso()

        val rimasta = assertNotNull(trascritti.trova(INCONTRO), "the root outlives its last Trascritto (INV-I4)")
        assertTrue(rimasta.trascritti.isEmpty())
        assertEquals(3, rimasta.prossimaVoce)
        assertEquals(0, trascritti.rimozioni)
        val attesi = setOf(VoceId(1), VoceId(2))
        assertEquals<List<EventoPubblicato>>(listOf(TrascrittoEliminato(R, INCONTRO, attesi)), pubblicati)
    }

    @Test
    fun `INV-I6 la radice e scritta e le Elaborazioni tolte prima che TrascrittoEliminato sia pubblicato`() {
        val radice = VociDellIncontro.crea(INCONTRO)
        radice.completaParte(R, dueVoci, DURATA_TRASCRITTO_MS).atteso()
        radice.completaParte(ALTRA, dueVoci, DURATA_TRASCRITTO_MS).atteso()
        trascritti.salva(radice)
        salva(COMPLETATA, "completata", R, PRIMA)
        val visto = mutableListOf<Pair<Boolean?, Int>>()
        val politica = ApplicaEliminazioneRegistrazionePolitica(
            elaborazioni,
            trascritti,
            object : DispatcherEventi {
                override fun pubblica(evento: EventoPubblicato) {
                    visto += trascritti.trova(INCONTRO)?.haParte(R) to elaborazioni.diRegistrazione(R).size
                }
            },
        )

        politica.applica(R, INCONTRO, incontroCessato = false).atteso()

        assertEquals(listOf<Pair<Boolean?, Int>>(false to 0), visto)
    }

    @Test
    fun `INV-I6 con l ultima Parte la radice e gia tolta quando TrascrittoEliminato e pubblicato`() {
        trascritti.salva(unaRadice(registrazioneId = R, voci = 2))
        val visto = mutableListOf<Boolean>()
        val politica = ApplicaEliminazioneRegistrazionePolitica(
            elaborazioni,
            trascritti,
            object : DispatcherEventi {
                override fun pubblica(evento: EventoPubblicato) {
                    visto += trascritti.trova(unIncontroDi(R)) == null
                }
            },
        )

        politica.applica(R, unIncontroDi(R), incontroCessato = true).atteso()

        assertEquals(listOf(true), visto)
    }

    private fun politicaCon(dove: MutableList<EventoPubblicato>) =
        ApplicaEliminazioneRegistrazionePolitica(elaborazioni, trascritti, registra(dove))

    private fun registra(dove: MutableList<EventoPubblicato>) = object : DispatcherEventi {
        override fun pubblica(evento: EventoPubblicato) {
            dove += evento
        }
    }

    private fun salva(stato: StatoElaborazione, id: String, r: RegistrazioneId, creataAlle: Instant) {
        elaborazioni.salva(unaElaborazione(stato, ElaborazioneId(id), r, creataAlle)).atteso()
    }

    private class ElaborazioneRepositoryContata(private val delegata: ElaborazioneRepository) :
        ElaborazioneRepository by delegata {
        var rimozioni = 0

        override fun rimuoviDiRegistrazione(id: RegistrazioneId) {
            rimozioni++
            delegata.rimuoviDiRegistrazione(id)
        }
    }

    private class VociDellIncontroRepositoryContata(private val delegata: VociDellIncontroRepository) :
        VociDellIncontroRepository by delegata {
        var rimozioni = 0

        override fun rimuovi(id: IncontroId) {
            rimozioni++
            delegata.rimuovi(id)
        }
    }

    private companion object {
        val R = RegistrazioneId("reg-1")
        val ALTRA = RegistrazioneId("reg-2")
        val INCONTRO = IncontroId("incontro-a-b")

        val dueVoci = listOf(unSegmentoIniziale(0, 0), unSegmentoIniziale(1, 1_000))
        val PRIMA: Instant = Instant.parse("2026-09-25T09:00:00Z")
        val DOPO: Instant = Instant.parse("2026-09-25T10:00:00Z")
    }
}
