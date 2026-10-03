package snastro.sintesi.applicazione.porte

import snastro.kernel.Esito
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.sintesi.dominio.ErroreSintesi
import snastro.sintesi.dominio.LimiteIngresso
import snastro.sintesi.dominio.StatoParte
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The two-pass guard shared by `RiassumiServizio` and `RiassuntoVisteLettura` (L216): the estimate is lazy. */
class RiassumibilitaInDuePassiTest {
    private val trascritta = listOf(1 to StatoParte.TRASCRITTA, 2 to StatoParte.TRASCRITTA)

    @Test
    fun `la stima non e calcolata quando un altra guardia ha gia deciso`() {
        val casi = listOf(
            "modello non installato" to Triple(false, trascritta, false),
            "Parte 2 da trascrivere" to Triple(true, trascritta.take(1) + (2 to StatoParte.DA_TRASCRIVERE), false),
            "Riassunto aperto" to Triple(true, trascritta, true),
        )
        casi.forEach { (nome, c) ->
            var stime = 0
            val esito = riassumibilitaInDuePassi(c.first, c.second, c.third) {
                stime++
                Esito.Ok(0)
            }

            assertTrue(esito is Esito.Errore, nome)
            assertEquals(0, stime, nome)
        }
    }

    @Test
    fun `con le altre guardie superate la stima decide una volta sola`() {
        var stime = 0
        val troppo = riassumibilitaInDuePassi(true, trascritta, false) {
            stime++
            Esito.Ok(LimiteIngresso.LIMITE_TOKEN + 1)
        }

        assertEquals(1, stime)
        val limite = LimiteIngresso.LIMITE_TOKEN
        assertEquals(Esito.Errore(ErroreSintesi.IngressoTroppoLungo(limite + 1, limite)), troppo)
        assertEquals(Esito.Ok(Unit), riassumibilitaInDuePassi(true, trascritta, false) { Esito.Ok(1) })
    }

    @Test
    fun `L229 una Parte TRASCRITTA senza Segmenti letti blocca come da trascrivere, mai saltando la stima`() {
        val esito = riassumibilitaInDuePassi(true, trascritta, false) {
            Esito.Errore(ErroreSintesi.PartiNonTrascritte(2))
        }

        assertEquals(Esito.Errore(ErroreSintesi.PartiNonTrascritte(2)), esito)
    }

    @Test
    fun `stimaTokenDi si ferma alla prima Parte senza Trascritto e la nomina`() {
        val r1 = RegistrazioneId("r1")
        val r2 = RegistrazioneId("r2")
        val r3 = RegistrazioneId("r3")
        val segmento = SegmentoSintesi(SegmentoId(1), VoceId(1), IntervalloMs(0, 1_000), "Testo di prova.")
        val lette = mutableListOf<RegistrazioneId>()
        val parti = listOf(ParteSintesi(r1, 1), ParteSintesi(r2, 2), ParteSintesi(r3, 3))

        val stima = stimaTokenDi(parti) { r ->
            lette += r
            if (r == r2) null else listOf(segmento)
        }

        assertEquals(Esito.Errore(ErroreSintesi.PartiNonTrascritte(2)), stima)
        assertEquals(listOf(r1, r2), lette)
        val tutte = stimaTokenDi(parti) { listOf(segmento) }
        assertTrue(tutte is Esito.Ok && tutte.valore > 0)
    }
}
