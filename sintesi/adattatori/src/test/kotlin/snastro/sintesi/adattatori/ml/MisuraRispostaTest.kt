package snastro.sintesi.adattatori.ml

import org.junit.jupiter.api.Test
import snastro.sintesi.applicazione.porte.RichiestaRiassunto
import kotlin.test.assertEquals

class MisuraRispostaTest {
    private fun ingressoDi(parole: Int): String =
        (1..parole / 10).joinToString("\n") { "[s$it V2] uno due tre quattro cinque sei sette otto nove dieci" }

    private fun misura(parole: Int, tetto: Int) = MisuraRisposta.di(
        RichiestaRiassunto(ingresso = ingressoDi(parole), argomento = null, lunghezzaMassimaParole = tetto),
    )

    @Test
    fun `le etichette di riga non contano come parole`() {
        assertEquals(20, MisuraRisposta.paroleDelTrascritto(ingressoDi(20)))
    }

    @Test
    fun `una registrazione breve ha un riassunto proporzionato, non il tetto`() {
        // New Recording 4: ~2000 words -> 40% = 800 target (cap 2000), Sommario 320 -> 300, 800 / 100 = 8 items.
        assertEquals(
            MisuraRisposta(obiettivoParole = 800, paroleSommario = 300, massimoVoci = 8),
            misura(2_000, 2_000),
        )
    }

    @Test
    fun `una registrazione lunga arriva al tetto e le liste crescono fino a 40`() {
        // 30 000 words -> 12 000 proportional, capped at 10 000.
        assertEquals(
            MisuraRisposta(obiettivoParole = 10_000, paroleSommario = 4_000, massimoVoci = 40),
            misura(30_000, 10_000),
        )
        assertEquals(20, misura(30_000, 2_000).massimoVoci)
    }

    @Test
    fun `un trascritto minimo ha comunque un obiettivo minimo, mai sopra il tetto, e almeno 6 voci`() {
        assertEquals(MisuraRisposta(obiettivoParole = 250, paroleSommario = 100, massimoVoci = 6), misura(100, 2_000))
        assertEquals(300, misura(30_000, 300).obiettivoParole)
    }
}
