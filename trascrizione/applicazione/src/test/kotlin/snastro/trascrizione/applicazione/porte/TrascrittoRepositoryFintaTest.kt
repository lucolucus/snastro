package snastro.trascrizione.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.trascrizione.dominio.DURATA_TRASCRITTO_MS
import snastro.trascrizione.dominio.SegmentoIniziale
import snastro.trascrizione.dominio.Trascritto
import snastro.trascrizione.dominio.unSegmentoIniziale
import snastro.trascrizione.dominio.unTrascritto
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class TrascrittoRepositoryFintaTest : TrascrittoRepositoryContratto() {
    override fun repository(): TrascrittoRepository = TrascrittoRepositoryFinta()

    @Test
    fun `AC-30 un rollback di UnitaDiLavoroFinta annulla anche le modifiche fatte sul Trascritto trovato`() {
        val repo = TrascrittoRepositoryFinta()
        val uow = UnitaDiLavoroFinta(repo)
        repo.salva(unTrascritto(voci = 2, segmentiPerVoce = 2, registrazioneId = ID))
        val prima = firma(assertNotNull(repo.trova(ID)))

        uow.inTransazione<Unit> {
            val t = assertNotNull(repo.trova(ID))
            t.unisci(VoceId(1), VoceId(2)).atteso()
            repo.salva(t)
            Esito.Errore(ErroreDiProva.Fallito("politica violata"))
        }.erroreAtteso<ErroreDiProva.Fallito>()

        assertEquals(prima, firma(assertNotNull(repo.trova(ID))))
    }

    @Test
    fun `AC-30 la Finta restituisce una copia uguale e indipendente per ogni stato raggiungibile`() {
        val repo = TrascrittoRepositoryFinta()
        var stati = 0

        INIZI.forEach { iniziali ->
            statiRaggiungibili(iniziali).forEach { t ->
                repo.salva(t)
                val copia = assertNotNull(repo.trova(ID))
                assertNotSame(t, copia)
                assertEquals(firma(t), firma(copia))
                stati++
            }
        }

        assertTrue(stati > 1_000, "stati esplorati: $stati")
    }

    /** Every distinct state reachable from `crea(iniziali)` in at most [PROFONDITA] valid Revisione steps. */
    private fun statiRaggiungibili(iniziali: List<SegmentoIniziale>): List<Trascritto> {
        val visti = mutableMapOf<Any, List<Passo>>()
        var frontiera = listOf(emptyList<Passo>())
        repeat(PROFONDITA + 1) {
            frontiera = frontiera.flatMap { cammino ->
                val t = ripeti(iniziali, cammino)
                if (visti.putIfAbsent(firma(t), cammino) != null) {
                    emptyList()
                } else {
                    passi(t).map { cammino + it }.filter { valido(iniziali, it) }
                }
            }
        }
        return visti.values.map { ripeti(iniziali, it) }
    }

    private fun valido(iniziali: List<SegmentoIniziale>, cammino: List<Passo>): Boolean {
        val t = ripeti(iniziali, cammino.dropLast(1))
        return cammino.last()(t) is Esito.Ok
    }

    private fun ripeti(iniziali: List<SegmentoIniziale>, cammino: List<Passo>): Trascritto {
        val t = Trascritto.crea(ID, DURATA_TRASCRITTO_MS, iniziali).atteso().aggregato
        cammino.forEach { assertTrue(it(t) is Esito.Ok) }
        return t
    }

    /** Every Revisione step on [t]'s current Voci and Segmenti (valid or not). */
    private fun passi(t: Trascritto): List<Passo> {
        val voci = t.voci.map { it.id }
        val unioni = voci.flatMap { a ->
            voci.filter { it != a }.map { b -> Passo { x: Trascritto -> x.unisci(a, b) } }
        }
        val divisioni = t.voci.flatMap { v ->
            val ids = v.segmenti.map { it.id }
            sottoinsiemiPropri(ids).map { s -> Passo { x: Trascritto -> x.dividi(v.id, s) } }
        }
        val riassegnazioni = t.segmenti.flatMap { s ->
            (voci + null).map { dest -> Passo { x: Trascritto -> x.riassegna(s.id, dest) } }
        }
        return unioni + divisioni + riassegnazioni
    }

    private fun sottoinsiemiPropri(ids: List<SegmentoId>): List<Set<SegmentoId>> =
        (1 until (1 shl ids.size) - 1).map { maschera ->
            ids.filterIndexed { i, _ -> maschera shr i and 1 == 1 }.toSet()
        }

    private fun firma(t: Trascritto): Any =
        listOf(t.registrazioneId, t.segmenti, t.voci, t.prossimaVoce, t.prossimoSegmento)

    private fun interface Passo {
        operator fun invoke(t: Trascritto): Esito<*>
    }

    private companion object {
        val ID = RegistrazioneId("registrazione-1")
        const val PROFONDITA = 5

        val INIZI: List<List<SegmentoIniziale>> = listOf(
            // one Segmento: no Revisione is possible
            listOf(unSegmentoIniziale(0, 0)),
            // two voices taking turns
            listOf(unSegmentoIniziale(0, 0), unSegmentoIniziale(1, 1_000), unSegmentoIniziale(0, 2_000)),
            // one voice, four Segmenti: every Voce id is minted by Revisione
            (0 until 4).map { unSegmentoIniziale(0, it * 1_000L) },
            // ties on inizio whose fine decreases (creation needs several Voci to keep the ids) + later turns
            listOf(
                unSegmentoIniziale(0, 0, fineMs = 3_000),
                unSegmentoIniziale(1, 0, fineMs = 1_000),
                unSegmentoIniziale(2, 0, fineMs = 2_000),
                unSegmentoIniziale(1, 5_000),
            ),
            // ties on inizio within ONE voice and overlapping turns of another
            listOf(
                unSegmentoIniziale(0, 0, fineMs = 2_000),
                unSegmentoIniziale(0, 0, fineMs = 1_000),
                unSegmentoIniziale(1, 500, fineMs = 2_500),
                unSegmentoIniziale(1, 3_000),
            ),
            // five Segmenti, three voices, two tie groups with decreasing fine
            listOf(
                unSegmentoIniziale(2, 0, fineMs = 4_000),
                unSegmentoIniziale(0, 0, fineMs = 2_000),
                unSegmentoIniziale(1, 1_000),
                unSegmentoIniziale(0, 3_000, fineMs = 5_000),
                unSegmentoIniziale(1, 3_000, fineMs = 3_500),
            ),
        )
    }
}
