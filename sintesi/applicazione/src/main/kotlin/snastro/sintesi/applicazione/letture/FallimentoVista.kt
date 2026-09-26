package snastro.sintesi.applicazione.letture

import snastro.sintesi.dominio.MotivoFallimento

/** AC-S106: the last Riassunto request of the Registrazione that ended `fallito`. */
public data class FallimentoVista(val motivo: MotivoFallimento)
