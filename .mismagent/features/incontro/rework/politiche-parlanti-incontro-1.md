# Rework 1 — politiche-parlanti-incontro (2026-10-02)

FAIL (verifier, suspected regression): `ApplicaRevisionePolitica.kt:124-126` reads `voci.voci(incontroId)` BEFORE checking that the Voce has
an Attribuzione/print. The real LettoreVoci fails closed on >1 Parte (D-0032), so on a multi-Parte Incontro every DividiVoce /
SegmentoRiassegnato leaving the source alive would throw, even for an unattributed Voce. Move the read after the guard (no Attribuzione and
no print → nothing to do, no read).
FAIL (verifier, ac-coverage Task 3): the SegmentoRiassegnato(daRimossa = false) → rimuoviImprontePerParteSvuotate branch has no test with a
populated reader (all tests use LettoreVociFinta() → null, so deleting the branch passes). Add a fake-based test that discriminates.
Also (code-review MED 1, cheap): treat a Parte whose interval list is empty as having NO slice (`filterValues { it.isNotEmpty() }`), as
RiallineaImpronteServizio does.
Composer decision (D-0043): the real-SQLite multi-Parte proof of Task 3 is deferred to the I2 release check (the real LettoreVoci is
one-Parte until voci-del-trascritto-incontro, D-0032/D-0037; the code-review proved the FK behaviour on a SQLite copy). Task 4
(RiallineaImpronte(incontroId)) was realized and tested by attribuzione-incontro; not this block's code.
