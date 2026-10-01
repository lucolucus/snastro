# Rework 1 — incontro (2026-10-02)

Not a review finding: an integration blocker the worker reported, answered by the user (D-0035).

- FAIL (gate after integration): `adr-0033-ordine-solo-nel-dominio.sh` (from: incontro) fails on the project tree at
  `progetto/applicazione/src/main/kotlin/snastro/progetto/applicazione/letture/RegistrazioniDelProgetto.kt:24`
  (`.thenByDescending { it.aggiuntaAlle }`), the existing S2 list order. Once `incontro` is integrated the check is mandatory.
- User decision: move that S2 list ordering into a pure function in `:progetto:dominio` (same order as today:
  date desc, then aggiuntaAlle desc, then the existing tie-breaks), with a table test; RegistrazioniDelProgetto calls it.
  No visible change. Keep the check unchanged (strict). Then the check must PASS on the tree.
