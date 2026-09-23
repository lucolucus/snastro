package snastro.trascrizione.adattatori.ml

import snastro.trascrizione.applicazione.porte.Allineatore
import snastro.trascrizione.applicazione.porte.AllineatoreContratto
import snastro.trascrizione.applicazione.porte.RiconoscitoreParlatoFinta
import snastro.trascrizione.applicazione.porte.VadFinta

/** AC-246: [AllineatoreContratto] passa contro [AllineatorePerTurno] costruito con le Finte del
 * riconoscitore e del vad — gira nel gate, nessun modello reale. */
class AllineatorePerTurnoContrattoTest : AllineatoreContratto() {
    override fun allineatore(): Allineatore = AllineatorePerTurno(RiconoscitoreParlatoFinta(), VadFinta())
}
