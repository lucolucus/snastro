---
id: riassunto-incontro
type: aggregate
context: sintesi
side: app
wave: 3
release: I1
high_value: true
model_hint: deep
module: ":sintesi:dominio"
consumes:
  - kernel-incontro
reuses:
  - sintesi/agg-riassunto
  - trascrizione-con-parlanti/kernel-pl
related_adrs:
  - "0021"
  - "0022"
  - "0032"
  - "0037"
invariants:
  - "INV-I9 Riassumi on an Incontro is accepted only if the model is installed, every Parte is TRASCRITTA, no Riassunto is open, the whole input fits LimiteIngresso and the Argomento is within its bound; the first blocking Parte in INV-I2 order is reported"
  - "INV-I10 Verifica delle fonti per Parte: a Fonte must map to a Parte of the Incontro as read and a segmentoId of that Parte's Trascritto as read; bound speakers are Voci of the Incontro; a PuntoChiave speaker is among the Voci of its valid Fonti, possibly in different Parti"
  - "INV-I11 superato is derived: a pronto Riassunto is superato iff StrutturaIncontro.chiave now differs from the recorded one; names and Attribuzioni never change it; restoring the exact structure clears it"
  - "INV-I19 the input is one pass: Parte after Parte in INV-I2 order, Segmenti in time order within each Parte, each line '[s<k> V<n>] <testo>' with k the 1-based position in the whole input, legend 'V<n> = Voce n'; the label table maps k back to exactly one SegmentoRef"
invariant_fields:
  - incontroId
  - "struttura (StrutturaIncontro.chiave)"
  - "elementi.fonti (SegmentoRef)"
identity: "RiassuntoId (unchanged); keyed by IncontroId"
tables:
  - riassunto
  - riassunto_elemento
  - riassunto_fonte
tests_nl_status: confirmed
---
# riassunto-incontro

## What to do
Amend the Riassunto root to the Incontro: Fonti are SegmentoRef, the recorded structure is StrutturaIncontro (ordered Parti, each with its StrutturaTrascritto), Verifica delle fonti per Parte; amend the pure guards Riassumibilita (every Parte TRASCRITTA, first blocking Parte reported) and IngressoRiassunto (one pass, sequential labels s1..sN mapped back through a label table).

## Invariants
- INV-I9 Riassumi on an Incontro is accepted only if the model is installed, every Parte is TRASCRITTA, no Riassunto is open, the whole input fits LimiteIngresso and the Argomento is within its bound; the first blocking Parte in INV-I2 order is reported
- INV-I10 Verifica delle fonti per Parte: a Fonte must map to a Parte of the Incontro as read and a segmentoId of that Parte's Trascritto as read; bound speakers are Voci of the Incontro; a PuntoChiave speaker is among the Voci of its valid Fonti, possibly in different Parti
- INV-I11 superato is derived: a pronto Riassunto is superato iff StrutturaIncontro.chiave now differs from the recorded one; names and Attribuzioni never change it; restoring the exact structure clears it
- INV-I19 the input is one pass: Parte after Parte in INV-I2 order, Segmenti in time order within each Parte, each line '[s<k> V<n>] <testo>' with k the 1-based position in the whole input, legend 'V<n> = Voce n'; the label table maps k back to exactly one SegmentoRef

## Tasks
- INV-I19 IngressoRiassunto.costruisci on Parti [A (Segmenti 5, 7), B (Segmenti 2)] gives lines '[s1 V1] …', '[s2 V3] …', '[s3 V2] …' in that order, no Parte separator, legend 'V<n> = Voce n' only; etichette = [(A,5), (A,7), (B,2)]
- INV-I10 an answer citing fonti [3, 9] keeps the Fonte (B,2) and drops label 9 (outside 1..N, counted by the existing rules); a PuntoChiave with Fonti in A and B whose speaker is the Voce of the B Fonte stays bound
- INV-I10 a Responsabile V9 that is not a Voce of the Incontro as read is unbound (element kept); a text token {V9} drops the element and counts it
- INV-I11 StrutturaIncontro.chiave of [A: {1→1, 2→2}, B: {1→3}] is 'A=1:1,2:2;B=1:3'; superato false for the same structure; true after a Revisione across Parti, a re-transcription of B (new ids), a reorder (B before A), the removal of B, the import of C (current 'C=' appended); false again when the exact structure is restored; renaming a Parlante never changes it
- INV-I9 Riassumibilita table test: Parti [TRASCRITTA, DA_TRASCRIVERE] → PartiNonTrascritte(parte 2); [IN_TRASCRIZIONE, TRASCRITTA] → ElaborazioneAperta(parte 1); [NON_RIUSCITA] → PartiFallite(parte 1); several blocking → the first in Parte order; model not installed first; all ok → Ok
- AC-I17 a 1-part Incontro: the struttura chiave is '<registrazioneId>=' + the old StrutturaTrascritto encoding (the 7.sqm re-encoding), so an unchanged Trascritto compares equal (INV-I3)

## Dependencies
- `agg-riassunto-incontro` (owns it) — consumers: `porte-sintesi-incontro`, `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `riassunto-vista-incontro`, `adattatori-sintesi-incontro` · contract_test: invariant-test
  - pinned `Riassunto (amended)`: richiedi(id, incontroId: IncontroId, argomento, lunghezzaMassima, richiestoAlle); completa(bozza, struttura: StrutturaIncontro, etichette: List<SegmentoRef>); superato(corrente: StrutturaIncontro): Boolean; Fonti as Set<SegmentoRef>
  - pinned `StrutturaIncontro`: (parti: List<Pair<RegistrazioneId, StrutturaTrascritto?>>) in Parte order; chiave = '<registrazioneId>=<StrutturaTrascritto.chiave or empty>' joined by ';'
  - pinned `Riassumibilita`: valuta(modelloInstallato: Boolean, stati: List<Pair<Int /* numero */, StatoParte>>, riassuntoAperto: Boolean, stimaToken: Int?): Esito<Unit> — errors ModelloNonInstallato, PartiNonTrascritte(parte), ElaborazioneGiaAperta(parte), PartiFallite(parte), RiassuntoGiaAperto, IngressoTroppoLungo; first blocking Parte in order
  - pinned `IngressoRiassunto`: costruisci(parti: List<List<SegmentoIngresso>> /* Parte order */): IngressoEtichettato(testo: String, etichette: List<SegmentoRef>) — '[s<k> V<n>] <testo>', legend 'V<n> = Voce n'
  - key `k`: minted by IngressoRiassunto per run — the 1-based position in the input, lives only in memory for the run
  - key `struttura`: minted by StrutturaIncontro.chiave — exact, collision-free (registrazioneId is a UUID text with no '=' or ';')
- `kernel-incontro` (consumes it; owner `incontro-chiavi`) — consumers: `incontro`, `voci-dell-incontro`, `parlante-impronte-per-parte`, `riassunto-incontro`, `porte-progetto-incontro`, `porte-trascrizione-incontro`, `porte-parlanti-incontro`, `porte-sbobinatura-incontro`, `porte-sintesi-incontro`, `aggiungi-registrazione-incontro`, `modifica-ora-di-inizio`, `elimina-parte`, `avvia-elaborazioni-incontro`, `esegui-elaborazione-incontro`, `revisione-incontro`, `eliminazione-parte-trascrizione`, `politiche-parlanti-incontro`, `attribuzione-incontro`, `rigenerazione-sbobinatura-incontro`, `riassumi-incontro`, `esegui-riassunto-incontro`, `eliminazione-parte-sintesi`, `catalogo-incontro`, `incontri-del-progetto`, `voci-del-trascritto-incontro`, `viste-parte-incontro`, `nomi-delle-voci-incontro`, `letture-parlanti-incontro`, `proposta-tra-parti`, `riassunto-vista-incontro`, `adattatori-progetto-incontro`, `adattatori-trascrizione-incontro`, `adattatori-parlanti-incontro`, `adattatori-sbobinatura-incontro`, `adattatori-sintesi-incontro`, `schermata-incontri`, `dialogo-importa-parti`, `dialogo-elimina-parte`, `schermata-parte`, `pannello-voci-incontro`, `scheda-riassunto-incontro`, `schermata-parlanti-incontri`, `banner-proposta-tra-parti`, `avvio-incontro`, `avvio-incontro-parti`, `avvio-proposta-tra-parti` · contract_test: invariant-test
  - pinned `IncontroId`: @JvmInline value class IncontroId(val valore: String) in :kernel — non-blank
  - pinned `SegmentoRef`: data class SegmentoRef(registrazioneId: RegistrazioneId, segmentoId: SegmentoId) in :kernel — a Segmento across contexts
  - pinned `VoceRef`: data class VoceRef(incontroId: IncontroId, voceId: VoceId) in :kernel — replaces VoceRef(registrazioneId, voceId); voceId is the 'Voce n' number of the Incontro
  - pinned `EstrattoRef`: unchanged (registrazioneId, inizioMs, fineMs) — always ONE Parte
  - key `incontroId`: minted by aggiungi-registrazione-incontro via GeneratoreId (UUID v4) for every new Incontro; by 7.sqm for migrated ones (equal to their Registrazione's id, a migration fact no code relies on) — immutable, never reused
  - key `voceId`: minted by voci-dell-incontro from the Incontro counter (prossimaVoce) — unique in the Incontro, never reused (INV-I4)
  - key `segmentoId`: minted by voci-dell-incontro from the Parte's prossimoSegmento — unique in its Registrazione across generations (INV-I16)

Sources: ADR 0037 §2-§5 · tactical-model.md § Sintesi · decisions.md D-0010, D-0020
