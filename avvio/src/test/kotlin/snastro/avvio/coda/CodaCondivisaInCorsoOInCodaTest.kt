package snastro.avvio.coda

import kotlinx.coroutines.cancel
import snastro.supporto.test.conScopeDiProva
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val ELEMENTO = ElementoInCoda("id-1", "reg-1", Instant.EPOCH)

/** R0 (EX-5): [CodaCondivisa.inCorsoOInCoda] — something running, or waiting, in any of its sources. */
class CodaCondivisaInCorsoOInCodaTest {
    private fun fonte(
        tutti: () -> List<ElementoInCoda>,
        teste: (Set<String>) -> ElementoInCoda? = { null },
        prossima: (Set<String>, Instant?) -> RisultatoTentativo = { _, _ -> RisultatoTentativo.Nessuno },
    ) = FonteCoda(
        tipo = TipoElementoCoda.ELABORAZIONE,
        teste = teste,
        prossima = prossima,
        ultimaTentata = { null },
        recupera = {},
        trattenuta = { false },
        tutti = tutti,
    )

    @Test
    fun `una coda senza niente in attesa e niente in corso e libera`() {
        assertFalse(CodaCondivisa(listOf(fonte(tutti = { emptyList() }))).inCorsoOInCoda())
    }

    @Test
    fun `un elemento in attesa in una qualunque fonte la occupa`() {
        val coda = CodaCondivisa(listOf(fonte(tutti = { emptyList() }), fonte(tutti = { listOf(ELEMENTO) })))

        assertTrue(coda.inCorsoOInCoda())
    }

    @Test
    fun `un elemento in corso la occupa anche quando non e piu in attesa`() = conScopeDiProva { scope ->
        val iniziato = CountDownLatch(1)
        val fine = CountDownLatch(1)
        val coda = codaAvviata(
            scope,
            listOf(
                fonte(
                    tutti = { emptyList() }, // reclamato: non piu' in_attesa
                    teste = { esclusi -> ELEMENTO.takeIf { iniziato.count > 0 && ELEMENTO.id !in esclusi } },
                    prossima = { _, _ ->
                        iniziato.countDown()
                        fine.await()
                        RisultatoTentativo.Avviata(ELEMENTO.id)
                    },
                ),
            ),
        )
        try {
            assertTrue(iniziato.await(10, TimeUnit.SECONDS), "l'elemento avrebbe dovuto partire")

            assertTrue(coda.inCorsoOInCoda())
        } finally {
            fine.countDown()
            scope.cancel()
            coda.fermaEAttendi()
        }
    }
}
