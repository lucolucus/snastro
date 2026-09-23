package snastro.ui.testi

import snastro.trascrizione.applicazione.porte.FaseElaborazione
import snastro.ui.formattaDurata

/** S2 · Registrazioni screen labels, Italian (dev-architecture `#presenter`: UI strings live in `snastro.ui.testi`). */
const val MESSAGGIO_REGISTRAZIONI_VUOTO: String = "Nessuna registrazione. Trascina qui un file audio"
const val ETICHETTA_IMPORTA_FILE: String = "Importa file audio…"
const val ETICHETTA_TRASCRIVI: String = "Trascrivi"
const val ETICHETTA_RIPROVA: String = "Riprova"
const val ETICHETTA_COMPLETATA: String = "Completata"

/** AC-203: the phase label of an `in_corso` Elaborazione, e.g. "In corso · separazione voci · 3:12". */
fun etichetta(fase: FaseElaborazione): String = when (fase) {
    FaseElaborazione.DECODIFICA -> "decodifica"
    FaseElaborazione.DIARIZZAZIONE -> "separazione voci"
    FaseElaborazione.TRASCRIZIONE -> "trascrizione"
    FaseElaborazione.ALLINEAMENTO -> "allineamento"
}

/** AC-203: "In coda (n)". */
fun etichettaInAttesa(posizione: Int): String = "In coda ($posizione)"

/** AC-203: "In corso · <fase> · mm:ss" — the elapsed time reuses [formattaDurata] (no hour cap). */
fun etichettaInCorso(faseEtichetta: String, trascorsoMs: Long): String =
    "In corso · $faseEtichetta · ${formattaDurata(trascorsoMs)}"
