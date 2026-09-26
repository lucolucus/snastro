---
id: "servizio-modelli-facoltativo"
type: "ui"
context: "ui"
side: "app"
wave: 1
release: "R3"
module: ":ui (snastro.ui.modelli, shell sidebar, MessaggiErrore)"
consumes: []
reuses:
  - "trascrizione-con-parlanti/tec-modelli-ui"
  - "trascrizione-con-parlanti/tec-shell-ui"
related_adrs:
  - "0008"
  - "0025"
tests_nl_status: "draft"
consumes_rm: []
triggers:
  - "ServizioModelli.scaricaFacoltativo(id)"
---
# servizio-modelli-facoltativo — Rework ServizioModelli (:ui): download facoltativo, stato del modello di linguaggio in barra laterale

## What to do
Rework of the tec-modelli-ui boundary owned by schermata-modelli (merged): ServizioModelli gains scaricaFacoltativo(id) and statoFacoltativi (a separate flow, so StatoModelli and S5 onboarding keep counting required models only); ErroreServizioModelli gains SpazioInsufficiente(richiestiByte) with its text; the sidebar foot shows the optional LLM model's download progress. ServizioModelliFinta updated.

Note: REWORK of trascrizione-con-parlanti's schermata-modelli / tec-modelli-ui (merged). R19-6 DECIDED 2026-09-25 (user): separate statoFacoltativi StateFlow; ADR 0025 amended (Amendment 2026-09-25 (a)).

## Tasks
- AC-S32 The existing S5 / shell presenter tests stay green unmodified; StatoModelli.Mancanti(numero, totaleByte) never counts an optional entry
- AC-S33 Sidebar foot: while statoFacoltativi[llm] is InDownload(2 100 000 000, 6 600 000 000) the line reads 'Modello di linguaggio: 2,1 di 6,6 GB' (decimal GB, one decimal, comma); no line for NonInstallato or Installato; the required-models line is unchanged
- AC-S34 MessaggiErrore maps ErroreServizioModelli.SpazioInsufficiente(6 600 000 000) to 'Non c'è abbastanza spazio sul disco (servono 6,6 GB).'
- AC-S35 STATES: sidebar with and without the download line render at the minimum window width and 1280×800, light and dark, no clipped text

## Dependencies
- **tec-modelli-ui-facoltativo** (OWNED here; owner servizio-modelli-facoltativo; projection in-process; contract_test `consumer-driven`)
  - `ServizioModelli`: + fun scaricaFacoltativo(id: String); + val statoFacoltativi: StateFlow<Map<String, StatoModelloFacoltativo>> (StatoModelli unchanged: required entries only)
  - `StatoModelloFacoltativo`: sealed { NonInstallato(dimensioneByte: Long); InDownload(scaricatiByte: Long, totaliByte: Long); Errore(errore: ErroreServizioModelli); Installato }
  - `ErroreServizioModelli`: + SpazioInsufficiente(richiestiByte: Long)
  - key `id`: see tec-modelli-facoltativo
- **tec-modelli-ui** (REUSED — boundary `tec-modelli-ui` of features/trascrizione-con-parlanti/building-blocks.yaml, owner `schermata-modelli` already integrated on main; not redeclared in this manifest; projection in-process; contract_test `consumer-driven`)
  - `(unchanged)`: ServizioModelli / StatoModelli / ErroreServizioModelli / LicenzaVista as pinned in the sibling manifest; extended by tec-modelli-ui-facoltativo
- **tec-shell-ui** (REUSED — boundary `tec-shell-ui` of features/trascrizione-con-parlanti/building-blocks.yaml, owner `ui-fondamenta` already integrated on main; not redeclared in this manifest; projection in-process; contract_test `consumer-driven`)
  - `(unchanged)`: AggiornamentiVista.cambiamenti: Flow<Cambiamento>; Cambiamento(registrazioneId: RegistrazioneId?) — as pinned in the sibling manifest

Sources: ADR 0025 §4, ux-proposal § Sidebar / S5 Modelli; related_adrs 0008, 0010, 0025; tactical-model: features/sintesi/tactical-model.md
