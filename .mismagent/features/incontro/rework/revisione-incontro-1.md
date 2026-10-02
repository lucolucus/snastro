# Rework 1 — revisione-incontro (2026-10-02)

FAIL (verifier, ac-coverage): Task 2 second half — "a SegmentoRef of a Registrazione outside the Incontro → Errore(SegmentoNonTrovato)" has
no test and cannot happen as built (commands keyed by registrazioneId; a foreign Registrazione gives VoceNonTrovata or TrascrittoNonTrovato).
Composer decision: fix (a), keep the AC. When `incontroDelleVoci` is stated and differs from the root's incontroId (the Incontro of
`c.registrazioneId`), a command that names a Segmento (RiassegnaSegmento, RiassegnaSegmenti, ConfermaSegmento, DividiVoce) returns
`SegmentoNonTrovato(SegmentoRef(c.registrazioneId, <segmentoId>))`, nothing changed, no event; add the command-level test (AC tag of Task 2).

FAIL (verifier, guard bug): `vociDiQuestoIncontro` (VoceDelLIncontro.kt:20) returns null ("no objection") when every given Voce is null, so
`RiassegnaSegmento(..., destinazione = null, incontroDelleVoci = <other>)` passes. A STATED mismatch must always be refused, before the root
is touched; add the test. Also: RiassegnazioneNonAmmessa still carries a bare SegmentoId — align to SegmentoRef only if trivial.
