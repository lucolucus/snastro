package snastro.trascrizione.applicazione.politiche

import snastro.kernel.ElaborazioneId
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.kernel.unIncontroDi
import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.applicazione.porte.ElaborazioneRepositoryFinta
import snastro.trascrizione.applicazione.porte.LettoreRegistrazione
import snastro.trascrizione.applicazione.porte.LettoreRegistrazioneFinta
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepository
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepositoryFinta
import snastro.trascrizione.applicazione.porte.ogniRegistrazioneNota
import snastro.trascrizione.applicazione.porte.unaRegistrazioneVista
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

/** The Trascrizione half of INV-28 (ADR 0020 §2 step 4): veto while an Elaborazione is open, otherwise purge. */
class ApplicaEliminazioneRegistrazionePoliticaTest {
    private val elaborazioni = ElaborazioneRepositoryContata(ElaborazioneRepositoryFinta())
    private val trascritti = VociDellIncontroRepositoryContata(VociDellIncontroRepositoryFinta())
    private val politica = ApplicaEliminazioneRegistrazionePolitica(elaborazioni, trascritti, ogniRegistrazioneNota())

    @Test
    fun `AC-608 INV-28 con un Elaborazione in attesa o in corso anche accanto a una completata nulla e tolto`() {
        for (aperta in listOf(IN_ATTESA, IN_CORSO)) {
            for (conCompletata in listOf(false, true)) {
                val r = RegistrazioneId("reg-$aperta-$conCompletata")
                if (conCompletata) salva(COMPLETATA, "completata-$r", r, PRIMA)
                salva(aperta, "aperta-$r", r, DOPO)
                trascritti.salva(unaRadice(registrazioneId = r))

                val errore = politica.applica(r, unIncontroDi(r)).erroreAtteso<ElaborazioneGiaAperta>()

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

        politica.applica(R, unIncontroDi(R)).atteso()

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
        politica.applica(R, unIncontroDi(R)).atteso()

        assertEquals(0, elaborazioni.rimozioni + trascritti.rimozioni)
    }

    @Test
    fun `AC-611 i collaboratori sono due repository e il lettore delle Parti - nessun decodificatore`() {
        val collaboratori = ApplicaEliminazioneRegistrazionePolitica::class.java.constructors.single().parameterTypes

        assertEquals(
            listOf(
                ElaborazioneRepository::class.java,
                VociDellIncontroRepository::class.java,
                LettoreRegistrazione::class.java,
            ),
            collaboratori.toList(),
        )
    }

    @Test
    fun `INV-I4 eliminare una Parte che non e' l'ultima toglie il suo Trascritto e tiene la radice col contatore`() {
        val politica = ApplicaEliminazioneRegistrazionePolitica(elaborazioni, trascritti, dueParti)
        val radice = VociDellIncontro.crea(INCONTRO)
        radice.completaParte(R, listOf(unSegmentoIniziale(0, 0)), DURATA_TRASCRITTO_MS).atteso()
        radice.completaParte(ALTRA, listOf(unSegmentoIniziale(0, 0)), DURATA_TRASCRITTO_MS).atteso()
        trascritti.salva(radice)
        salva(COMPLETATA, "completata", R, PRIMA)

        politica.applica(R, INCONTRO).atteso()

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

        politica.applica(R, unIncontroDi(R)).atteso()

        assertNull(trascritti.trova(unIncontroDi(R)))
        assertEquals(1, trascritti.rimozioni)
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

        /** [R] then [ALTRA], the two Parti of [INCONTRO]. */
        val dueParti = LettoreRegistrazioneFinta(
            listOf(R, ALTRA).associateWith { unaRegistrazioneVista(it).copy(incontroId = INCONTRO) },
        )
        val PRIMA: Instant = Instant.parse("2026-09-25T09:00:00Z")
        val DOPO: Instant = Instant.parse("2026-09-25T10:00:00Z")
    }
}
