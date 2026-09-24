package snastro.ui.modelli

/** AC-231: one catalogue entry's licence/attribution, read from `:modelli`'s catalogue (ADR 0008). */
data class LicenzaVista(val nome: String, val ruolo: String, val licenza: String, val attribuzione: String)
