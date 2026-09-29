package snastro.avvio.modelli

import snastro.kernel.Esito
import snastro.modelli.CatalogoModelli
import snastro.modelli.ErroreModelli
import snastro.modelli.FormatoVoce
import snastro.modelli.VoceCatalogo
import snastro.ui.modelli.ServizioModelli
import snastro.ui.modelli.ServizioModelliFacoltativoContratto

/**
 * D2 (block `modello-facoltativo-avvio`): [ServizioModelliProvisioning] passes the
 * `tec-modelli-ui-facoltativo` contract over a small in-memory stand-in for `ProvisioningModelli`'s
 * by-id `installata`/`scarica` (never the real, network-bound class — a fresh catalogue + a set of
 * pre-installed ids is enough to reproduce every [con] shape the contract needs).
 */
class ServizioModelliProvisioningFacoltativoTest : ServizioModelliFacoltativoContratto() {
    override fun con(dimensioniByte: Map<String, Long>, installati: Set<String>): ServizioModelli {
        val catalogo = CatalogoModelli(dimensioniByte.map { (id, dimensione) -> voce(id, dimensione) })
        val installatiCorrenti = installati.toMutableSet()
        fun scarica(id: String, progresso: (Long, Long) -> Unit): Esito<Unit> {
            val dimensione = catalogo.voci.find { it.id == id }?.dimensioneByte
            return when {
                id in installatiCorrenti -> Esito.Ok(Unit)
                dimensione == null -> Esito.Errore(ErroreModelli.DownloadFallito("id sconosciuto: '$id'"))
                else -> {
                    progresso(dimensione, dimensione)
                    installatiCorrenti += id
                    Esito.Ok(Unit)
                }
            }
        }
        return ServizioModelliProvisioning(
            catalogo,
            { true },
            { emptyList() },
            { Esito.Ok(Unit) },
            { id -> id in installatiCorrenti },
            ::scarica,
        )
    }

    private fun voce(id: String, dimensione: Long) = VoceCatalogo(
        id = id,
        ruolo = "llm",
        url = "https://example.invalid/$id.gguf",
        sha256 = "0".repeat(64),
        dimensioneByte = dimensione,
        formato = FormatoVoce.FILE,
        licenza = "Apache-2.0",
        attribuzione = "Autori di prova",
        obbligatoria = false,
    )
}
