package snastro.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import snastro.kernel.Esito
import snastro.kernel.GeneratoreId
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.ProgettoId

/**
 * Fake [SessioneProgetto] (RC-9): `crea` mints a Progetto keyed by its own `percorso`; `apri` reopens
 * one previously created under that same `percorso` — a round trip, no filesystem.
 */
class SessioneProgettoFinta(private val generatoreId: GeneratoreId = GeneratoreIdFinto()) : SessioneProgetto {
    private val _corrente = MutableStateFlow<ProgettoAperto?>(null)
    override val corrente: StateFlow<ProgettoAperto?> = _corrente.asStateFlow()
    private val progetti = mutableMapOf<String, ProgettoAperto>()

    override fun crea(cartellaGenitore: String, nome: String): Esito<ProgettoAperto> {
        if (nome.isBlank()) return Esito.Errore(ErroreSessione.NomeProgettoVuoto)
        val percorso = "$cartellaGenitore/$nome.snastro"
        if (progetti.containsKey(percorso)) return Esito.Errore(ErroreSessione.CartellaNonValida)
        val progetto = ProgettoAperto(ProgettoId(generatoreId.nuovo()), nome, percorso)
        progetti[percorso] = progetto
        _corrente.value = progetto
        return Esito.Ok(progetto)
    }

    override fun apri(percorso: String): Esito<ProgettoAperto> {
        val progetto = progetti[percorso] ?: return Esito.Errore(ErroreSessione.CartellaNonValida)
        _corrente.value = progetto
        return Esito.Ok(progetto)
    }

    override fun chiudi() {
        _corrente.value = null
    }
}
