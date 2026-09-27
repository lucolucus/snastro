# Tactical model — consolidamento

**No domain model change.** This feature is a technical refactoring of side `app`:

- no bounded context is added or changed (`context-map.md` untouched);
- no aggregate, invariant, domain event or command is added, removed or changed — hence no `INV-n` in the manifest;
- the ubiquitous language is unchanged; the new technical names (`LetturaCoerente`, `RitentaConBackoff`, `Segnalazione`,
  `PorteProgetto`, `Modulo<Contesto>`, `CollaboratoriProgetto`, `Campanello`, `ArrestoProgetto`) are infrastructure, and
  CR-18b forbids ubiquitous-language tokens inside `:supporto`.

The design lives in the ADRs:
- [ADR 0028](../../decisions/0028-librerie-tecniche-supporto.md) — `:supporto` / `:supporto-test`;
- [ADR 0029](../../decisions/0029-lettura-coerente-deferred.md) — kernel port `LetturaCoerente`, snapshot rule;
- [ADR 0030](../../decisions/0030-composizione-unica-per-contesto.md) — single composition, one module per context.

Downstream consumer: `building-blocks.yaml` (blocks derive from the ADRs' block lists, not from aggregates).
