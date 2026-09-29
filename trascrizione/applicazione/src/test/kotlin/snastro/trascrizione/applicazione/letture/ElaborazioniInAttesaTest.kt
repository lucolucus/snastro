package snastro.trascrizione.applicazione.letture

import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.applicazione.porte.ElaborazioneRepositoryFinta

/** D1 (dev-architecture-app.md#porta-contratto, A41): [ElaborazioniInAttesaContratto] over the Finta. */
class ElaborazioniInAttesaTest : ElaborazioniInAttesaContratto() {
    override fun repository(): ElaborazioneRepository = ElaborazioneRepositoryFinta()
}
