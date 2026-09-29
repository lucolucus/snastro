package snastro.avvio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import snastro.ui.AggiornamentiVista
import snastro.ui.Cambiamento

/** ADR 0030 §1: the ONE merge of the modules' [AggiornamentiVista] into the open project's. */
internal fun unisci(fonti: List<AggiornamentiVista>): AggiornamentiVista = object : AggiornamentiVista {
    override val cambiamenti: Flow<Cambiamento> = fonti.map { it.cambiamenti }.merge()
}
