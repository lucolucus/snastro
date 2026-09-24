---
id: "documento"
type: "read-model"
context: "documento"
side: "app"
wave: 4
release: "R1"
module: ":documento:applicazione"
consumes:
  - "kernel-pl"
  - "trascritto-per-documento"
  - "nomi-per-documento"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0010"
  - "0012"
view_shape:
  markdown: "String"
  nomeFile: "String"
view_sources:
  markdown: "← trascritto-per-documento (titolo, dataRegistrazione, segmenti) + nomi-per-documento (nomi)"
  nomeFile: "← dataRegistrazione + titolo (trascritto-per-documento / RegistrazioneVista)"
invariants:
  - "INV-23 Documento = deterministic function of (Trascritto, Attribuzioni, Nomi): unchanged inputs → byte-identical output; the .md is never read back"
  - "INV-24 every Voce renders as the Nome of its attributed Parlante (also eliminato); without Attribuzione as 'Voce n'; Segmenti in time order across Voci, text verbatim"
---
# documento — Proiezione Documento (markdown puro)

## What to do
Pure projection: '# <titolo>' line, a blank line, 'Registrata il dd/MM/yyyy', a blank line, then one line per Segmento '**Nome** (mm:ss): testo' (mm = total minutes, not wrapped at 60), each line followed by a blank line so Markdown keeps them separate; consecutive Segmenti of the same Voce stay separate lines. nomeFile(data, titolo) = '<AAAA-MM-DD> ' + pulisci(titolo) + '.md' — pure, sanitized (invalid chars → '_', trimmed, capped so the name and its '.tmp' fit 255 bytes, reserved names suffixed '_', empty → 'registrazione'); unique because titolo is unique per Progetto.

Note: AMENDED 2026-09-23 (user decision, open-questions/documento.md answered and cleared): the Documento file name is unique per Progetto because titolo is unique per Progetto (servizi-registrazione AC-322..324 assigns ' (2)', ' (3)'… at import) — nomeFile itself adds no tie-breaker (an ordinal computed here would break INV-23). nomeFile SANITIZES the titolo with pulisci(t) — the AC-263 rule applied to a Documento titolo: NFC-normalize; every character invalid on Windows/macOS/Linux (< > : " / \ | ? * and the control characters U+0000–U+001F, U+007F) → '_'; leading/trailing spaces and dots removed; truncated to 237 UTF-8 bytes on a code-point boundary (never splitting a surrogate pair) and trailing spaces/dots removed again; a Windows reserved name (CON, PRN, AUX, NUL, COM1–COM9, LPT1–LPT9, case-insensitive) gets a trailing '_' (kept for identity with AC-263 although the date prefix already neutralizes it); empty result → 'registrazione'. 237 = 255 − 11 ('AAAA-MM-DD ') − 3 ('.md') − 4 ('.tmp' of the atomic write), in UTF-8 bytes, which also bounds NTFS's 255 UTF-16 units. The same rule text is pinned in tec-scrittore-documento keys.nomeFile and applied by servizi-registrazione to compute the uniqueness key — keep the two table tests' shared rows identical. LOW carry-over (code-review): format dates with explicit patterns, independent of the default Locale (Locale.ROOT). FOLLOW-UP: branch block/documento (8e7167f, parked) must be reworked to AC-320/321 and the reversed-input INV-24 tie-break test.

### Invariants owned here (one test each, name starts with the tag)
- INV-23 Documento = deterministic function of (Trascritto, Attribuzioni, Nomi): unchanged inputs → byte-identical output; the .md is never read back
- INV-24 every Voce renders as the Nome of its attributed Parlante (also eliminato); without Attribuzione as 'Voce n'; Segmenti in time order across Voci, text verbatim

### view_shape (field ← source)
- `markdown`: String ← ← trascritto-per-documento (titolo, dataRegistrazione, segmenti) + nomi-per-documento (nomi)
- `nomeFile`: String ← ← dataRegistrazione + titolo (trascritto-per-documento / RegistrazioneVista)

## Tasks
- INV-23 stessi input → markdown byte-identico
- INV-24 Voce non attribuita → 'Voce n'; Parlante eliminato → il suo Nome
- INV-24 Segmenti in ordine di inizio tra le Voci (a parità di inizioMs, per segmentoId); testo IT/EN verbatim — il test fornisce i Segmenti a pari inizioMs in ordine di segmentoId INVERSO nell'input (un ordinamento stabile che ignorasse il tie-break deve far fallire il test)
- AC-103 Il markdown inizia con '# <titolo>' e l'intestazione 'Registrata il dd/MM/yyyy', poi una riga '**Nome** (mm:ss): testo' per ogni Segmento, e due Segmenti consecutivi della stessa Voce restano righe separate
- AC-104 nomeFile(2026-09-12, 'Riunione') = '2026-09-12 Riunione.md'
- AC-320 nomeFile(data, titolo) = '<AAAA-MM-DD> ' + pulisci(titolo) + '.md' (funzione pura, test a tabella con data 2026-09-12): 'Riunione 3/10: budget?' → '2026-09-12 Riunione 3_10_ budget_.md'; '  Nota finale.. ' → '2026-09-12 Nota finale.md'; 'a<b>c|d*e"f' → '2026-09-12 a_b_c_d_e_f.md'; un carattere di controllo U+0007 → '_'; 'con' → '2026-09-12 con_.md'; '...' e '' → '2026-09-12 registrazione.md'; 'e' + U+0301 (NFD) → '2026-09-12 é.md' (NFC); un titolo già pulito resta invariato (idempotenza: nomeFile(d, pulisci(t)) = nomeFile(d, t))
- AC-321 Limite di lunghezza: per un titolo di 300 'è' (2 byte UTF-8) e per uno di 100 emoji (4 byte, coppie surrogate) nomeFile è lungo al più 251 byte UTF-8 (così nomeFile + '.tmp' ≤ 255), non spezza mai un code point né una coppia surrogata, e non termina con spazio o punto prima di '.md'

## Dependencies
- **kernel-pl** (consumed/implemented) — owner `kernel`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ProgettoId`: @JvmInline value class(valore: String) — UUID
    - `RegistrazioneId`: @JvmInline value class(valore: String) — UUID
    - `ElaborazioneId`: @JvmInline value class(valore: String) — UUID
    - `ParlanteId`: @JvmInline value class(valore: String) — UUID
    - `VoceId`: @JvmInline value class(numero: Int) — equals the n of 'Voce n'
    - `SegmentoId`: @JvmInline value class(numero: Int)
    - `VoceRef`: data class(registrazioneId: RegistrazioneId, voceId: VoceId)
    - `IntervalloMs`: data class(inizioMs: Long, fineMs: Long) — require 0 <= inizioMs < fineMs; ordered numerically
    - `RiferimentoAudio`: @JvmInline value class(percorsoRelativo: String)
    - `CampioniAudio`: class(campioni: FloatArray) — 16 kHz mono float, explicit equals/hashCode (CR-5)
    - `EstrattoRef`: data class(registrazioneId: RegistrazioneId, intervalli: List<IntervalloMs>) — non-empty, ordered by inizioMs, total duration <= 10 000 ms, played as a sequence
    - `Esito`: sealed interface Esito<out T> { Ok<T>(valore: T); Errore(errore: ErroreDominio) } + poi / mappa / seErrore
    - `ErroreDominio`: interface (NOT sealed — Kotlin forbids cross-module sealed subtypes; NOT Throwable); each context declares its own sealed hierarchy Errore<Contesto> : ErroreDominio in file Errori<Contesto>.kt (ADR 0003 amended)
    - `EventoDominio`: marker interface for domain events (returned by aggregate methods)
    - `EventoPubblicato`: marker interface for published events (Published Language, <ctx>:applicazione.eventi)
    - `Creato`: data class Creato<A, E>(aggregato: A, evento: E)
    - `GeneratoreId`: interface { fun nuovo(): String } — UUID v4; testFixtures GeneratoreIdFinto: 'id-1', 'id-2', …
    - `UnitaDiLavoro`: interface { fun <T> inTransazione(blocco: () -> Esito<T>): Esito<T> } — rolls back when the block returns Errore or throws
    - `DispatcherEventi`: interface { fun pubblica(evento: EventoPubblicato) } + registration of AbbonatoSincrono / AbbonatoDopoCommit
    - `RicostituzioneDaPersistenza`: @RequiresOptIn(level = ERROR) annotation class — only ..adattatori.persistenza.. opts in (CR-15)
  - keys (minting rules):
    - `ProgettoId`: minted by crea-progetto via kernel GeneratoreId (UUID v4 string) — immutable; stored in progetto.db so it survives moving/copying the project folder
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
    - `VoceId`: minted by the trascritto aggregate from its persisted counter prossimaVoce — at creation 1..n in order of FIRST APPEARANCE (smallest turn inizioMs, tie: diarizer voceIndice); DividiVoce / riassegna-to-new take prossimaVoce++; never reused, never renumbered, == the n of the label 'Voce n'; stable for the life of one Trascritto GENERATION: a Ritrascrivi replacement (ADR 0018) is a fresh Trascritto.crea numbered from 1 again, and every VoceRef-keyed Parlanti row of the old generation is purged in the same transaction (TrascrittoSostituito)
    - `SegmentoId`: minted by the trascritto aggregate at creation only, 1..m in order (inizioMs, then voceId); no Segmento is ever created afterwards (INV-8) — stable for the Trascritto generation's life (ADR 0018: a replacement renumbers from 1)
    - `VoceRef`: composite (registrazioneId, voceId), typed kernel VO because >=2 contexts use it — correlation key of Attribuzione, ImprontaVocale and the Documento name map; stable as its parts
    - `ParlanteId`: minted by conferma-attribuzione (new Nome) and salta-voce via GeneratoreId (UUID v4) — stable across rinomina, promozione and eliminazione (tombstone keeps it); disappears only via INV-25 (occasionale left without Attribuzioni)
    - `RiferimentoAudio`: minted by audio-progetto (ArchivioAudio.copia): 'audio/<registrazioneId>.<source extension lowercased>', relative to the project folder — immutable
- **trascritto-per-documento** (consumed/implemented) — owner `porta-lettore-trascritto`, supplier `api-trascritto + catalogo-registrazioni`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `LettoreTrascritto`: interface { fun trascritto(id: RegistrazioneId): TrascrittoTesto?; fun registrazioniConTrascritto(): List<RegistrazioneId> }
    - `TrascrittoTesto`: data class(registrazioneId: RegistrazioneId, titolo: String, dataRegistrazione: LocalDate, segmenti: List<SegmentoVista>)
    - `SegmentoVista`: data class(segmentoId: SegmentoId, voceId: VoceId, intervallo: IntervalloMs, testo: String) — list ordered by inizioMs then segmentoId ACROSS Voci; testo verbatim
  - keys (minting rules):
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
    - `VoceId`: minted by the trascritto aggregate from its persisted counter prossimaVoce — at creation 1..n in order of FIRST APPEARANCE (smallest turn inizioMs, tie: diarizer voceIndice); DividiVoce / riassegna-to-new take prossimaVoce++; never reused, never renumbered, == the n of the label 'Voce n'; stable for the life of one Trascritto GENERATION: a Ritrascrivi replacement (ADR 0018) is a fresh Trascritto.crea numbered from 1 again, and every VoceRef-keyed Parlanti row of the old generation is purged in the same transaction (TrascrittoSostituito)
    - `SegmentoId`: minted by the trascritto aggregate at creation only, 1..m in order (inizioMs, then voceId); no Segmento is ever created afterwards (INV-8) — stable for the Trascritto generation's life (ADR 0018: a replacement renumbers from 1)
- **nomi-per-documento** (consumed/implemented) — owner `porta-lettore-nomi`, supplier `nomi-delle-voci`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `LettoreNomi`: interface { fun nomi(id: RegistrazioneId): Map<VoceRef, String>; fun registrazioniCon(p: ParlanteId): List<RegistrazioneId> } — attributed Voci only; an eliminato Parlante still resolves to its Nome (INV-24)
  - keys (minting rules):
    - `VoceRef`: composite (registrazioneId, voceId), typed kernel VO because >=2 contexts use it — correlation key of Attribuzione, ImprontaVocale and the Documento name map; stable as its parts
    - `ParlanteId`: minted by conferma-attribuzione (new Nome) and salta-voce via GeneratoreId (UUID v4) — stable across rinomina, promozione and eliminazione (tombstone keeps it); disappears only via INV-25 (occasionale left without Attribuzioni)

Sources: ADRs 0002, 0003, 0010, 0012 (.mismagent/decisions/); features/trascrizione-con-parlanti/tactical-model.md § Documento (INV-23, INV-24) + R24 format, ADR 0010; format details USER-CONFIRMED 2026-09-23: date line 'Registrata il dd/MM/yyyy', a blank line between Segmento lines, mm = total minutes not wrapping at 60.
