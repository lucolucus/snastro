package snastro.parlanti.dominio

import snastro.kernel.IncontroId
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import kotlin.test.Test
import kotlin.test.assertEquals

/** [INV-I8] prints keyed per (VoceRef, Parte), [INV-I8b] purge by Parte, [INV-21] re-keying on unire (ADR 0035 §6). */
class ParlanteImprontePerParteTest {
    private fun unParlante(): Parlante =
        Parlante.crea(
            ParlanteId("id-1"),
            ProgettoId("id-p"),
            Nome.di("Marco").atteso(),
            TipoParlante.RICORRENTE,
        ).aggregato

    private fun unaVoce(n: Int): VoceRef = VoceRef(INCONTRO, VoceId(n))

    private fun unaImpronta(valore: Float): Impronta = Impronta(floatArrayOf(valore))

    private fun Parlante.aggiungi(voce: VoceRef, parte: RegistrazioneId, valore: Float, sorgente: String = SORGENTE) =
        aggiungiImpronta(voce, parte, unaImpronta(valore), sorgente, MODELLO).atteso()

    private fun stampa(voce: VoceRef, parte: RegistrazioneId, valore: Float, sorgente: String = SORGENTE) =
        ImprontaVocale(voce, unaImpronta(valore), sorgente, MODELLO, parte)

    @Test
    fun `INV-I8 aggiungiImpronta due volte sulla stessa Parte sostituisce, su un altra Parte aggiunge`() {
        val p = unParlante()

        p.aggiungi(unaVoce(1), PARTE_A, 1f)
        p.aggiungi(unaVoce(1), PARTE_A, 2f, sorgente = "0-2000")
        p.aggiungi(unaVoce(1), PARTE_B, 3f)

        assertEquals(
            listOf(stampa(unaVoce(1), PARTE_A, 2f, sorgente = "0-2000"), stampa(unaVoce(1), PARTE_B, 3f)),
            p.impronte,
        )
        assertEquals(listOf(PARTE_A, PARTE_B), p.impronte.map { it.parte }, "ogni impronta riporta la sua Parte")
    }

    @Test
    fun `INV-I8 un Parlante eliminato rifiuta aggiungiImpronta`() {
        val p = unParlante()
        p.elimina().atteso()

        p.aggiungiImpronta(unaVoce(1), PARTE_A, unaImpronta(1f), SORGENTE, MODELLO)
            .erroreAtteso<ErroreParlanti.ParlanteEliminatoNonModificabile>()

        assertEquals(emptyList(), p.impronte)
    }

    @Test
    fun `INV-I8b rimuoviImpronteDellaParte toglie ogni impronta di quella Parte e lascia intatte le altre`() {
        val p = unParlante()
        p.aggiungi(unaVoce(1), PARTE_A, 1f)
        p.aggiungi(unaVoce(2), PARTE_A, 2f)
        p.aggiungi(unaVoce(1), PARTE_B, 3f)

        p.rimuoviImpronteDellaParte(PARTE_A)

        assertEquals(listOf(stampa(unaVoce(1), PARTE_B, 3f)), p.impronte)
    }

    @Test
    fun `INV-I8 rimuoviImpronta di una Voce in una Parte lascia la stessa Voce nelle altre Parti`() {
        val p = unParlante()
        p.aggiungi(unaVoce(1), PARTE_A, 1f)
        p.aggiungi(unaVoce(1), PARTE_B, 2f)

        p.rimuoviImpronta(unaVoce(1), PARTE_A)
        p.rimuoviImpronta(unaVoce(7), PARTE_A)

        assertEquals(listOf(stampa(unaVoce(1), PARTE_B, 2f)), p.impronte)
    }

    @Test
    fun `INV-21 riassegnaImpronte tiene l impronta di A e ri-chiava su A quelle di B dove A non ne ha`() {
        val p = unParlante()
        val a = unaVoce(1)
        val b = unaVoce(2)
        p.aggiungi(a, PARTE_A, 1f)
        p.aggiungi(b, PARTE_A, 2f)
        p.aggiungi(b, PARTE_B, 3f, sorgente = "4000-9000")
        p.aggiungi(unaVoce(5), PARTE_B, 5f)

        p.riassegnaImpronte(da = b, a = a)

        assertEquals(
            setOf(
                stampa(a, PARTE_A, 1f),
                stampa(a, PARTE_B, 3f, sorgente = "4000-9000"),
                stampa(unaVoce(5), PARTE_B, 5f),
            ),
            p.impronte.toSet(),
        )
        assertEquals(3, p.impronte.size, "l impronta di B nella Parte 1 e scartata")
    }

    @Test
    fun `INV-21 riassegnaImpronte in eredita sposta ogni impronta di B su A con la sua Parte`() {
        val p = unParlante()
        val a = unaVoce(1)
        val b = unaVoce(2)
        p.aggiungi(b, PARTE_A, 2f)
        p.aggiungi(b, PARTE_B, 3f)

        p.riassegnaImpronte(da = b, a = a)

        assertEquals(listOf(stampa(a, PARTE_A, 2f), stampa(a, PARTE_B, 3f)), p.impronte)
    }

    private companion object {
        val INCONTRO = IncontroId("id-i")
        val PARTE_A = RegistrazioneId("id-r1")
        val PARTE_B = RegistrazioneId("id-r2")
        const val SORGENTE = "0-1000"
        const val MODELLO = "finto"
    }
}
