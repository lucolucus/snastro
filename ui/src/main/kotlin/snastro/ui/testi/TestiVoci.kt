package snastro.ui.testi

/** S3 · Voci panel + Revisione toolbar labels (R2), Italian (dev-architecture `#presenter`). */
const val TITOLO_PANNELLO_VOCI: String = "Voci"

// AC-558/AC-559/AC-589b: `Icona.Listen` renders at the call site (`SchermataPannelloVoci`'s
// `IntestazioneCarta`) — these constants carry no glyph.
const val ETICHETTA_ESTRATTO: String = "Estratto"

/** AC-585: "È <Nome>" — label change of 'Conferma', same command ([AzioniRegistrazione.conferma]).
 * [ETICHETTA_CONFERMA] is the fallback while the Proposta has no known Candidato yet (`Caricamento`/
 * `InAttesa`/`Errore`) — the button still renders (disabled) so an in-flight state is never a hole. */
fun testoConferma(nomeCandidato: String): String = "È $nomeCandidato"
const val ETICHETTA_CONFERMA: String = "Conferma"

// AC-589b: no '▾' glyph baked into any of these — the menu affordance is `Icona.ChevronDown` at the
// render call site.
const val ETICHETTA_ALTRI: String = "Altri"

/** AC-585: label of 'nuovo…' — also the AC-586 menu item's own text. */
const val ETICHETTA_NUOVA_PERSONA: String = "Nuova persona"

/** AC-585: the first-recording (empty Galleria) Primario — same command as [ETICHETTA_NUOVA_PERSONA]. */
const val ETICHETTA_DAI_UN_NOME: String = "Dai un nome"
const val ETICHETTA_SALTA: String = "Salta"

/** AC-585: a named card's compact 'More' menu (cambia + unisci con…) — no standalone dropdown text. */
const val ETICHETTA_CAMBIA: String = "Cambia"
const val ETICHETTA_UNISCI_CON: String = "Unisci con…"
const val ETICHETTA_UNISCI: String = "Unisci"
const val ETICHETTA_NOME: String = "Nome"
const val ETICHETTA_OCCASIONALE: String = "occasionale"
const val ETICHETTA_RICORRENTE: String = "ricorrente"
const val ETICHETTA_RIASSEGNA_A: String = "Riassegna a"
const val ETICHETTA_NUOVA_VOCE: String = "nuova voce"
const val ETICHETTA_DIVIDI_VOCE: String = "Dividi voce"
const val ETICHETTA_DESELEZIONA: String = "Deseleziona"

/** AC-212: empty Galleria (first recording of a project). */
const val SUGGERIMENTO_PRIMA_REGISTRAZIONE: String = "Prima registrazione: dai un nome alle voci"

/** AC-412: a card command past SOGLIA_ATTESA_VISIBILE_MS — no percentage, no countdown. */
const val MESSAGGIO_COMANDO_IN_ATTESA: String = "In attesa dell'elaborazione…"

/** AC-416: a Proposta past SOGLIA_ATTESA_VISIBILE_MS, in place of the Candidati (no 'Annulla'). */
const val MESSAGGIO_PROPOSTA_IN_ATTESA: String = "Proposta in attesa dell'elaborazione…"

/** AC-405: a Parlanti source could not be read — shown in the card, the transcript stays usable. */
const val MESSAGGIO_ERRORE_VOCI: String = "Non è stato possibile leggere i parlanti di questa voce."

/** AC-405: this Voce's Proposta could not be read. */
const val MESSAGGIO_ERRORE_PROPOSTA: String = "Non è stato possibile calcolare la proposta."

/** AC-403: the audio source is missing — every '▶' of the panel is disabled. */
const val MESSAGGIO_ESTRATTI_NON_DISPONIBILI: String = "Audio non disponibile: gli estratti non si possono ascoltare."

/** AC-210: 'Dividi voce' on the whole Voce (INV-10). */
const val SPIEGAZIONE_DIVIDI_INTERA_VOCE: String =
    "Hai selezionato tutta la voce: per dividerla lasciane fuori almeno un segmento."

/** ADR 0018 Amendment (b) §2 (AC-454): the panel's own third line of the read-only banner — appended
 * to the base presenter's two-line [snastro.ui.testi.MESSAGGIO_RITRASCRIZIONE_IN_CORSO] (AC-452). */
const val MESSAGGIO_RITRASCRIZIONE_PERSA: String = "Le correzioni e i nomi assegnati andranno persi."

/** Tooltip-free label of a Fascia bar (AC-214: a bar, never a number). */
const val DESCRIZIONE_FASCIA_FORTE: String = "somiglianza forte"
const val DESCRIZIONE_FASCIA_DEBOLE: String = "somiglianza debole"
const val DESCRIZIONE_FASCIA_NESSUNA: String = "nessuna somiglianza"

/** AC-216: the merge banner, e.g. "Voce 1 e Voce 3 sono entrambe Marco". */
fun testoUnione(voceA: Int, voceB: Int, nome: String): String = "Voce $voceA e Voce $voceB sono entrambe $nome"

/** AC-209: the toolbar's summary of the selection. */
fun testoSelezione(numero: Int, etichetta: String): String =
    if (numero == 1) "1 segmento di $etichetta" else "$numero segmenti di $etichetta"
