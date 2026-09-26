package snastro.sintesi.applicazione.porte

import snastro.sintesi.applicazione.porte.StatoModelloLinguistico.DownloadFallito
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico.InDownload
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico.Installato
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico.NonInstallato

/**
 * AC-S19 (D1): [DisponibilitaModelloLinguisticoFinta] passes [DisponibilitaModelloLinguisticoContratto]
 * in the gate.
 */
class DisponibilitaModelloLinguisticoFintaTest : DisponibilitaModelloLinguisticoContratto() {
    override fun ambiente(): AmbienteDisponibilitaModello = AmbienteFinto()

    /**
     * Plays the supplier: a "disk" (the .part bytes, the installed marker) that survives [riavvia],
     * and a session that does not; the Finta is set from them, the way `:avvio` projects its holder.
     */
    private class AmbienteFinto : AmbienteDisponibilitaModello {
        private val totali = 6_600_000_000L
        private val hashCatalogo = "sha-catalogo"
        private var parziali = 0L
        private var marcatore: String? = null
        private var finta = DisponibilitaModelloLinguisticoFinta(daDisco())

        override val disponibilita: DisponibilitaModelloLinguistico get() = finta

        override fun avviaDownload() {
            finta.corrente = InDownload(parziali, totali)
        }

        override fun avanza() {
            parziali = minOf(totali, parziali + totali / 10)
            finta.corrente = InDownload(parziali, totali)
        }

        override fun completa() {
            parziali = 0
            marcatore = hashCatalogo
            finta.corrente = daDisco()
        }

        override fun fallisci(motivo: MotivoDownload) {
            finta.corrente = DownloadFallito(motivo)
        }

        override fun installaConMarcatoreDiverso() {
            marcatore = "sha-versione-precedente"
            finta.corrente = daDisco()
        }

        override fun riavvia(): DisponibilitaModelloLinguistico {
            finta = DisponibilitaModelloLinguisticoFinta(daDisco())
            return finta
        }

        private fun daDisco(): StatoModelloLinguistico =
            if (marcatore == hashCatalogo) Installato else NonInstallato(totali)
    }
}
