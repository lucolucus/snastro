package snastro.ui.testi

import snastro.kernel.ElaborazioneId
import snastro.kernel.ErroreDiProva
import snastro.kernel.ErroreDominio
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.parlanti.dominio.ErroreParlanti
import snastro.progetto.applicazione.porte.ErroreApplicazioneProgetto
import snastro.progetto.dominio.ErroreProgetto
import snastro.trascrizione.dominio.ErroreTrascrizione
import snastro.trascrizione.dominio.StatoElaborazione
import snastro.ui.ErroreSessione
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * AC-180: every member of every context error hierarchy `:ui` can see has a plain-Italian message,
 * with no `else` branch anywhere (RC-4) — a new member breaks `messaggioPer`'s compilation.
 * [verificaCopertura] additionally reflects on the sealed interface's real JVM `PermittedSubclasses`
 * (this toolchain, JDK 21, compiles Kotlin `sealed` to a genuine JVM sealed type) and checks its count
 * against the instances mapped below, so a member added here with a `messaggioPer` branch but no test
 * instance — or a branch that compiles but returns a blank string — goes red too, without relying on
 * anyone remembering to keep a hand-written list in sync.
 */
class MessaggiErroreTest {
    private fun <E : Any> verificaCopertura(gerarchia: Class<E>, istanze: List<E>, messaggioPer: (E) -> String) {
        val permesse = gerarchia.permittedSubclasses
            ?: error("${gerarchia.name} non è un'interfaccia sealed JVM (nessun PermittedSubclasses)")
        assertEquals(
            permesse.size,
            istanze.size,
            "attesa un'istanza per ognuno dei ${permesse.size} membri di ${gerarchia.simpleName}, " +
                "trovate ${istanze.size} — un membro è stato aggiunto o rimosso senza aggiornare questo test",
        )
        istanze.forEach { errore -> assertTrue(messaggioPer(errore).isNotBlank(), "messaggio vuoto per $errore") }
    }

    @Test
    fun `AC-180 ErroreSessione`() {
        verificaCopertura(
            ErroreSessione::class.java,
            listOf(
                ErroreSessione.NomeProgettoVuoto,
                ErroreSessione.CartellaNonValida,
                ErroreSessione.ProgettoGiaAperto,
                ErroreSessione.DatabasePiuRecente,
            ),
        ) { messaggioPer(it) }
    }

    @Test
    fun `AC-180 ErroreApplicazioneProgetto`() {
        verificaCopertura(
            ErroreApplicazioneProgetto::class.java,
            listOf(
                ErroreApplicazioneProgetto.AudioNonLeggibile("x"),
                ErroreApplicazioneProgetto.FormatoNonSupportato("x"),
                ErroreApplicazioneProgetto.CopiaFallita("x"),
            ),
        ) { messaggioPer(it) }
    }

    @Test
    fun `AC-180 ErroreProgetto`() {
        verificaCopertura(
            ErroreProgetto::class.java,
            listOf(
                ErroreProgetto.NomeProgettoVuoto,
                ErroreProgetto.ProgettoGiaPresente,
                ErroreProgetto.RegistrazioneNonTrovata(RegistrazioneId("id-1")),
            ),
        ) { messaggioPer(it) }
    }

    @Test
    fun `AC-180 ErroreTrascrizione`() {
        verificaCopertura(
            ErroreTrascrizione::class.java,
            listOf(
                ErroreTrascrizione.TransizioneNonAmmessa(
                    ElaborazioneId("id-1"),
                    StatoElaborazione.IN_ATTESA,
                    StatoElaborazione.IN_CORSO,
                ),
                ErroreTrascrizione.ElaborazioneGiaAperta(RegistrazioneId("id-1")),
                ErroreTrascrizione.ElaborazioneGiaCompletata(RegistrazioneId("id-1")),
                ErroreTrascrizione.RegistrazioneNonTrovata(RegistrazioneId("id-1")),
                ErroreTrascrizione.TrascrittoNonTrovato(RegistrazioneId("id-1")),
                ErroreTrascrizione.NessunParlatoRilevato,
                ErroreTrascrizione.SegmentoOltreLaDurata(IntervalloMs(0, 1000), 500),
                ErroreTrascrizione.VoceNonTrovata(VoceId(1)),
                ErroreTrascrizione.SegmentoNonTrovato(SegmentoId(1)),
                ErroreTrascrizione.UnioneNonAmmessa(VoceId(1), VoceId(2)),
                ErroreTrascrizione.DivisioneNonAmmessa(VoceId(1), setOf(SegmentoId(1))),
                ErroreTrascrizione.RiassegnazioneNonAmmessa(SegmentoId(1), null),
            ),
        ) { messaggioPer(it) }
    }

    @Test
    fun `AC-180 ErroreParlanti`() {
        verificaCopertura(
            ErroreParlanti::class.java,
            listOf(
                ErroreParlanti.ParlanteNonTrovato(ParlanteId("id-1")),
                ErroreParlanti.TrascrittoNonTrovato(RegistrazioneId("id-1")),
                ErroreParlanti.VoceNonTrovata(VoceRef(RegistrazioneId("id-1"), VoceId(1))),
                ErroreParlanti.ParlanteEliminatoNonModificabile(ParlanteId("id-1")),
                ErroreParlanti.PromozioneNonAmmessa(ParlanteId("id-1")),
                ErroreParlanti.NomeGiaInUso("Marco"),
                ErroreParlanti.NomeVuoto,
                ErroreParlanti.VoceGiaAttribuita(VoceRef(RegistrazioneId("id-1"), VoceId(1))),
                ErroreParlanti.VoceCambiata(VoceRef(RegistrazioneId("id-1"), VoceId(1))),
            ),
        ) { messaggioPer(it) }
    }

    @Test
    fun `il punto di ingresso instrada ogni gerarchia raggiungibile`() {
        val esempi: List<ErroreDominio> = listOf(
            ErroreSessione.CartellaNonValida,
            ErroreApplicazioneProgetto.AudioNonLeggibile("x"),
            ErroreProgetto.ProgettoGiaPresente,
            ErroreTrascrizione.NessunParlatoRilevato,
            ErroreParlanti.NomeVuoto,
        )
        esempi.forEach { errore -> assertTrue(messaggioPer(errore).isNotBlank()) }
    }

    @Test
    fun `il punto di ingresso rifiuta un ErroreDominio non mappato`() {
        // ErroreDiProva (kernel testFixtures, CR-8 shape) stands in for "a hierarchy not yet wired
        // into the entry-point dispatcher" — RC-4: its only `else` is a programmer error, never a
        // generic user message.
        assertFailsWith<IllegalStateException> { messaggioPer(ErroreDiProva.Fallito("x")) }
    }
}
