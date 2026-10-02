# Open question — porte-sintesi-incontro (parked 2026-10-02 after 2 rework cycles)

Reviews: verifier PASS + code-review APPROVE at b2279043 (rework 2). The candidate gate (merge onto integration/incontro d403b9f5, which now
contains adattatori-progetto-incontro) is RED:
`sintesi/adattatori/src/test/kotlin/snastro/sintesi/adattatori/porte/LettoreNomiDaParlantiTest.kt:170-172` — AggiungiRegistrazioneServizio
gained an `incontri: IncontroRepository` parameter (adattatori-progetto-incontro); this test builds it positionally without it.
All three reworks of this block were merge-forward compile breaks caused by sibling port blocks integrating first, not defects of the block.
Question: allow a third, merge-forward-only cycle (and change the policy so merge-forward compile fixes do not count toward the rework cap)?
