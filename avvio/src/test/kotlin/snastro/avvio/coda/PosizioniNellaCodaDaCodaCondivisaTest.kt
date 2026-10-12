package snastro.avvio.coda

import snastro.supporto.test.conScopeDiProva
import snastro.ui.coda.PosizioniNellaCoda
import snastro.ui.coda.PosizioniNellaCodaContratto
import snastro.ui.coda.ScenarioCoda
import snastro.ui.coda.TipoInCoda
import java.time.Instant

/**
 * D2 of `posizioni-nella-coda` (ADR 0023 §4, boundary `coda-condivisa`): [PosizioniNellaCodaContratto]
 * against [CodaCondivisa]'s OWN [CodaCondivisa.istantanea] — the shared queue IS the port's real
 * implementation (no separate adapter class). [con] seeds [ScenarioCoda] through fake [FonteCoda]s, in
 * LIST ORDER with STRICTLY INCREASING instants (carry-over 3: the SQL Elaborazione source truncates to
 * milliseconds while the fake keeps sub-ms `creataAlle` — the equal-millisecond tie is exercised in
 * [CodaCondivisaTest] instead, over primitive ids only). The worker is stopped right after construction:
 * only [PosizioniNellaCoda.istantanea] is exercised here, never a tick.
 */
class PosizioniNellaCodaDaCodaCondivisaTest : PosizioniNellaCodaContratto() {
    override fun con(scenario: ScenarioCoda): PosizioniNellaCoda {
        // carry-over 3: `snastro.ui.coda.unaElaborazione` (ScenarioCoda's fixture) vs
        // `snastro.trascrizione.dominio.unaElaborazione` — this file imports neither by name, only the
        // scenario's own data (`ElementoScenario`), so there is no ambiguity to guard against here.
        val elementi = scenario.inAttesa.mapIndexed { indice, elemento ->
            elemento.tipo to ElementoInCoda(
                id = "id-$indice",
                oggettoId = elemento.registrazioneId.valore,
                istante = ISTANTE_BASE.plusMillis(indice.toLong()), // list order, strictly increasing
            )
        }
        val fonti = TipoInCoda.entries.map { tipo ->
            val propri = elementi.filter { (t, _) -> t == tipo }.map { it.second }
            FonteCoda(
                tipo = tipo.aTipoElementoCoda(),
                teste = { esclusi -> propri.firstOrNull { it.id !in esclusi } },
                prossima = { _, _ -> RisultatoTentativo.Nessuno }, // mai richiesto: solo istantanea() e' esercitata
                ultimaTentata = { null },
                recupera = {},
                trattenuta = { false },
            )
        }
        // nessun tick necessario per istantanea(): lo scope cancellato ferma subito il worker dedicato
        val coda = conScopeDiProva { scope -> codaAvviata(scope = scope, fonti = fonti) }
        coda.fermaEAttendi(1_000)
        return coda
    }

    private fun TipoInCoda.aTipoElementoCoda(): TipoElementoCoda = when (this) {
        TipoInCoda.ELABORAZIONE -> TipoElementoCoda.ELABORAZIONE
        TipoInCoda.RIASSUNTO -> TipoElementoCoda.RIASSUNTO
    }

    private companion object {
        val ISTANTE_BASE: Instant = Instant.parse("2026-09-23T10:00:00Z")
    }
}
