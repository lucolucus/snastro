# Rework 1 — porte-sintesi (reviewed head a8c5ba8, against the spec after D-0003)

## FAIL / HIGH 1 (verifier + code-review) — AC-S66 not discriminating
- `sintesi/applicazione/src/testFixtures/kotlin/snastro/sintesi/applicazione/porte/RiassuntoRepositoryContratto.kt:136`:
  the second-pronto case asserts only `assertIs<Esito.Errore>`. D-0003/AC-S66 require `Errore(RiassuntoGiaAperto(registrazioneId))`.
  **Fix:** as the non-pronto case at :120-121 — `erroreAtteso<RiassuntoGiaAperto>()` + `assertEquals(RiassuntoGiaAperto(REGISTRAZIONE), errore)`, plus nothing written.
- Port KDoc `sintesi/applicazione/src/main/kotlin/snastro/sintesi/applicazione/porte/RiassuntoRepository.kt:23-27`: name
  `RiassuntoGiaAperto(registrazioneId)` for BOTH unique indexes and state "any other constraint failure → infra fault (ADR 0003)";
  spell out concludi per D-0003 (CAS first; only for a pronto, remove the previous pronto after the check, same call).

## HIGH 2 (code-review; verifier MED) — D-0003 ordering of concludi untested
- `RiassuntoRepositoryContratto.kt:186-213` (AC-S68 absent / not-in_corso cases): no previous pronto of the same Registrazione is
  present, so a D2 doing `DELETE previous pronto` BEFORE the `UPDATE … WHERE stato='in_corso'` passes the whole contract and loses
  the shown pronto when the CAS loses.
  **Fix:** in the absent case and the in_attesa / fallito cases, seed a stored pronto P of the SAME Registrazione, call concludi
  with a pronto → `Ok(false)` and P survives unchanged (and nothing else written). Prove it: a temporarily broken Finta that removes
  the previous pronto before the check must make these cases RED; restore.
Nothing else.
