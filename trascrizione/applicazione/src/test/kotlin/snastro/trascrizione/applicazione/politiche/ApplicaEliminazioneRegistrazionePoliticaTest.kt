package snastro.trascrizione.applicazione.politiche

import snastro.kernel.ElaborazioneId
import snastro.kernel.RegistrazioneId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.applicazione.porte.ElaborazioneRepositoryFinta
import snastro.trascrizione.applicazione.porte.TrascrittoRepository
import snastro.trascrizione.applicazione.porte.TrascrittoRepositoryFinta
import snastro.trascrizione.dominio.ErroreTrascrizione.ElaborazioneGiaAperta
import snastro.trascrizione.dominio.StatoElaborazione
import snastro.trascrizione.dominio.StatoElaborazione.COMPLETATA
import snastro.trascrizione.dominio.StatoElaborazione.FALLITA
import snastro.trascrizione.dominio.StatoElaborazione.IN_ATTESA
import snastro.trascrizione.dominio.StatoElaborazione.IN_CORSO
import snastro.trascrizione.dominio.unTrascritto
import snastro.trascrizione.dominio.unaElaborazione
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The Trascrizione half of INV-28 (ADR 0020 §2 step 4): veto while an Elaborazione is open, otherwise purge. */
class ApplicaEliminazioneRegistrazionePoliticaTest {
    private val elaborazioni = ElaborazioneRepositoryContata(ElaborazioneRepositoryFinta())
    private val trascritti = TrascrittoRepositoryContata(TrascrittoRepositoryFinta())
    private val politica = ApplicaEliminazioneRegistrazionePolitica(elaborazioni, trascritti)

    @Test
    fun `AC-608 INV-28 con un Elaborazione in attesa o in corso anche accanto a una completata nulla e tolto`() {
        for (aperta in listOf(IN_ATTESA, IN_CORSO)) {
            for (conCompletata in listOf(false, true)) {
                val r = RegistrazioneId("reg-$aperta-$conCompletata")
                if (conCompletata) salva(COMPLETATA, "completata-$r", r, PRIMA)
                salva(aperta, "aperta-$r", r, DOPO)
                trascritti.salva(unTrascritto(registrazioneId = r))

                val errore = politica.applica(r).erroreAtteso<ElaborazioneGiaAperta>()

                assertEquals(ElaborazioneGiaAperta(r), errore)
                assertEquals(if (conCompletata) 2 else 1, elaborazioni.diRegistrazione(r).size)
                assertNotNull(trascritti.trova(r))
            }
        }
        assertEquals(0, elaborazioni.rimozioni + trascritti.rimozioni, "rimuovi / rimuoviDiRegistrazione mai chiamati")
    }

    @Test
    fun `AC-609 INV-28 con completata fallita e un Trascritto toglie tutto di r e niente di un altra Registrazione`() {
        salva(FALLITA, "fallita", R, PRIMA)
        salva(COMPLETATA, "completata", R, DOPO)
        trascritti.salva(unTrascritto(registrazioneId = R))
        salva(COMPLETATA, "altra-completata", ALTRA, PRIMA)
        salva(IN_ATTESA, "altra-in-attesa", ALTRA, DOPO)
        trascritti.salva(unTrascritto(registrazioneId = ALTRA))

        politica.applica(R).atteso()

        assertEquals(emptyList(), elaborazioni.diRegistrazione(R))
        assertNull(trascritti.trova(R))
        assertEquals(
            setOf("altra-completata", "altra-in-attesa"),
            elaborazioni.diRegistrazione(ALTRA).map { it.id.valore }.toSet(),
        )
        assertNotNull(trascritti.trova(ALTRA))
    }

    @Test
    fun `AC-610 senza Elaborazione e senza Trascritto e Ok e nessuna chiamata rimuove qualcosa`() {
        politica.applica(R).atteso()

        assertEquals(0, elaborazioni.rimozioni + trascritti.rimozioni)
    }

    @Test
    fun `AC-611 i collaboratori sono solo i due repository - nessun decodificatore ne estrattore`() {
        val collaboratori = ApplicaEliminazioneRegistrazionePolitica::class.java.constructors.single().parameterTypes

        assertEquals(
            listOf(ElaborazioneRepository::class.java, TrascrittoRepository::class.java),
            collaboratori.toList(),
        )
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

    private class TrascrittoRepositoryContata(private val delegata: TrascrittoRepository) :
        TrascrittoRepository by delegata {
        var rimozioni = 0

        override fun rimuovi(id: RegistrazioneId) {
            rimozioni++
            delegata.rimuovi(id)
        }
    }

    private companion object {
        val R = RegistrazioneId("reg-1")
        val ALTRA = RegistrazioneId("reg-2")
        val PRIMA: Instant = Instant.parse("2026-09-25T09:00:00Z")
        val DOPO: Instant = Instant.parse("2026-09-25T10:00:00Z")
    }
}
