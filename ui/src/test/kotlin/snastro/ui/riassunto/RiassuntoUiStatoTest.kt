package snastro.ui.riassunto

import kotlin.test.Test
import kotlin.test.assertEquals

private fun unArgomento(valore: String = "") = ArgomentoUiStato(valore, "0/200", null)

@Suppress("LongParameterList") // one parameter per RiassuntoUiStato.Dati field this table varies
private fun unDati(
    modello: ModelloUi = ModelloUi.Installato,
    richiesta: RichiestaUi? = null,
    fallimentoTesto: String? = null,
    nonDisponibileTesto: String? = null,
    contenuto: ContenutoUi? = null,
) = RiassuntoUiStato.Dati(
    modello = modello,
    richiesta = richiesta,
    fallimentoTesto = fallimentoTesto,
    nonDisponibileTesto = nonDisponibileTesto,
    contenuto = contenuto,
    argomento = unArgomento(),
    lunghezzaMassima = LunghezzaMassimaUiStato.Testo(2_000),
)

private fun unContenuto(superato: Boolean = false) = ContenutoUi(
    superato = superato,
    sommario = "Un sommario.",
    decisioni = emptyList(),
    azioni = emptyList(),
    questioniAperte = emptyList(),
    puntiChiave = emptyList(),
    omessiTesto = null,
    metadatiTesto = "Lunghezza massima: 2000 parole",
)

/**
 * [RiassuntoUiStato.Dati.areaAzione] as a pure, table-driven predicate (dev-architecture #test — the
 * same pattern as `RegistrazioneUiStato.Dati.bannerSchermata` — no presenter, no Compose): one
 * assertion per row of the ux-proposal's own precedence (model line first, then queued/running, then
 * failed, then not-available, then the actionable form).
 */
class RiassuntoUiStatoTest {
    @Test
    fun `AC-S128 modello installato nessuna richiesta e nessun contenuto e Azionabile non nuovo`() {
        assertEquals(AreaAzione.Azionabile(nuovo = false), unDati().areaAzione)
    }

    @Test
    fun `AC-S132 modello installato con un Riassunto mostrato e Azionabile nuovo`() {
        assertEquals(AreaAzione.Azionabile(nuovo = true), unDati(contenuto = unContenuto()).areaAzione)
    }

    @Test
    fun `AC-S125 modello non installato vince su tutto il resto`() {
        val modello = ModelloUi.NonInstallato("messaggio", "bottone")
        val dati = unDati(
            modello = modello,
            richiesta = RichiestaUi.InAttesa("In coda"),
            fallimentoTesto = "fallito",
            nonDisponibileTesto = "non disponibile",
            contenuto = unContenuto(),
        )
        assertEquals(AreaAzione.ScaricaModello("messaggio", "bottone"), dati.areaAzione)
    }

    @Test
    fun `AC-S126 modello in download vince su richiesta fallimento e non disponibile`() {
        val modello = ModelloUi.InDownload("testo", 0.5f)
        val dati = unDati(modello = modello, richiesta = RichiestaUi.InAttesa("In coda"), fallimentoTesto = "fallito")
        assertEquals(AreaAzione.Scaricando("testo", 0.5f), dati.areaAzione)
    }

    @Test
    fun `AC-S127 download fallito vince su richiesta fallimento e non disponibile`() {
        val modello = ModelloUi.DownloadFallito("messaggio")
        val dati = unDati(modello = modello, richiesta = RichiestaUi.InAttesa("In coda"), fallimentoTesto = "fallito")
        assertEquals(AreaAzione.DownloadFallito("messaggio"), dati.areaAzione)
    }

    @Test
    fun `AC-S130 in_attesa vince su fallimento e non disponibile`() {
        val dati = unDati(
            richiesta = RichiestaUi.InAttesa("In coda · 2"),
            fallimentoTesto = "fallito",
            nonDisponibileTesto = "non disponibile",
        )
        assertEquals(AreaAzione.InCoda("In coda · 2"), dati.areaAzione)
    }

    @Test
    fun `AC-S131 in_corso vince su fallimento e non disponibile`() {
        val dati = unDati(
            richiesta = RichiestaUi.InCorso("Sto riassumendo… 1:12"),
            fallimentoTesto = "fallito",
            nonDisponibileTesto = "non disponibile",
        )
        assertEquals(AreaAzione.InCorso("Sto riassumendo… 1:12"), dati.areaAzione)
    }

    @Test
    fun `AC-S134 fallito vince su non disponibile`() {
        val dati = unDati(
            fallimentoTesto = "Il riassunto non è riuscito: interrotto.",
            nonDisponibileTesto = "non disponibile",
        )
        assertEquals(AreaAzione.Fallito("Il riassunto non è riuscito: interrotto."), dati.areaAzione)
    }

    @Test
    fun `AC-S129 non disponibile quando nient altro si applica`() {
        val dati = unDati(
            nonDisponibileTesto = "La registrazione è troppo lunga per il riassunto (oltre 1 h 10 circa).",
        )
        assertEquals(
            AreaAzione.NonDisponibile("La registrazione è troppo lunga per il riassunto (oltre 1 h 10 circa)."),
            dati.areaAzione,
        )
    }

    @Test
    fun `AC-S133 superato non cambia areaAzione (Azionabile nuovo), il contenuto porta il proprio avviso`() {
        val dati = unDati(contenuto = unContenuto(superato = true))
        assertEquals(AreaAzione.Azionabile(nuovo = true), dati.areaAzione)
    }
}
