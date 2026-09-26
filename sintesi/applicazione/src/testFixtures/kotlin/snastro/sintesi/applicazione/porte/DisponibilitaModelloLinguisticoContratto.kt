package snastro.sintesi.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico.DownloadFallito
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico.InDownload
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico.Installato
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico.NonInstallato
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Consumer-driven contract of Sintesi's [DisponibilitaModelloLinguistico] (boundary
 * `disponibilita-modello`): one subclass per implementation — [DisponibilitaModelloLinguisticoFinta]
 * (D1) and the `:avvio` implementation over its fake provisioning (D2). Each test takes
 * [AmbienteDisponibilitaModello.disponibilita] once, up front, and keeps reading through it: an
 * implementation serving a stale snapshot fails.
 */
public abstract class DisponibilitaModelloLinguisticoContratto {
    /** A fresh supplier: nothing on disk, no download. */
    protected abstract fun ambiente(): AmbienteDisponibilitaModello

    @Test
    public fun `AC-S17 un fornitore nuovo legge NonInstallato con la dimensione da scaricare`() {
        val stato = assertIs<NonInstallato>(ambiente().disponibilita.stato())
        assertTrue(stato.dimensioneByte > 0, "dimensioneByte = ${stato.dimensioneByte}")
    }

    @Test
    public fun `AC-S17 un download avviato legge InDownload con scaricati entro i totali e mai in calo`() {
        val a = ambiente()
        val d = a.disponibilita
        a.avviaDownload()
        var precedente = inDownloadValido(d.stato())
        repeat(3) {
            a.avanza()
            val ora = inDownloadValido(d.stato())
            assertTrue(ora.scaricatiByte >= precedente.scaricatiByte, "scaricati in calo: $precedente -> $ora")
            precedente = ora
        }
    }

    @Test
    public fun `AC-S17 un download riuscito legge Installato`() {
        val a = ambiente()
        val d = a.disponibilita
        a.avviaDownload()
        a.avanza()
        a.completa()
        assertEquals(Installato, d.stato())
    }

    @Test
    public fun `AC-S17 un download fallito legge DownloadFallito con il motivo mappato`() {
        for (motivo in MotivoDownload.entries) {
            val a = ambiente()
            val d = a.disponibilita
            a.avviaDownload()
            a.avanza()
            a.fallisci(motivo)
            assertEquals(DownloadFallito(motivo), d.stato(), "motivo $motivo")
        }
    }

    @Test
    public fun `AC-S18 un marcatore diverso dall'hash del catalogo non legge Installato`() {
        val a = ambiente()
        val d = a.disponibilita
        a.installaConMarcatoreDiverso()
        assertIs<NonInstallato>(d.stato())
        assertIs<NonInstallato>(a.riavvia().stato())
    }

    @Test
    public fun `AC-S18 dopo un riavvio un download parziale legge NonInstallato`() {
        val a = ambiente()
        a.avviaDownload()
        a.avanza()
        inDownloadValido(a.disponibilita.stato())

        val stato = assertIs<NonInstallato>(a.riavvia().stato())
        assertTrue(stato.dimensioneByte > 0, "dimensioneByte = ${stato.dimensioneByte}")
    }

    @Test
    public fun `AC-S18 dopo un riavvio un modello installato legge ancora Installato`() {
        val a = ambiente()
        a.avviaDownload()
        a.completa()
        assertEquals(Installato, a.riavvia().stato())
    }

    private fun inDownloadValido(stato: StatoModelloLinguistico): InDownload {
        val s = assertIs<InDownload>(stato)
        assertTrue(s.scaricatiByte in 0..s.totaliByte && s.totaliByte > 0, "InDownload fuori intervallo: $s")
        return s
    }
}
