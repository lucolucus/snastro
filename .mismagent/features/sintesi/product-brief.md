# Product brief — sintesi

## Problema
Dopo una registrazione l'utente ha il trascritto con i parlanti (R1/R2): un'ora di audio fa
diecimila parole di parlato spezzato, con chiacchiere e parti in inglese. Per sapere **cosa si è deciso
e chi deve fare cosa** deve rileggere tutto. Il valore del trascritto resta sepolto.

## Utente
Lo stesso di trascrizione-con-parlanti: una persona su macOS (M3 Pro, 36 GB), riunioni di lavoro
con 2–4 persone, italiano e inglese insieme. Il caso reale di riferimento sono le sessioni di design di un gioco
(Via Roquel, New Recording 4).

## Valore atteso
- Un pulsante **"Riassumi"** sulla registrazione produce un **Riassunto** nella scheda Riassunto:
  un **Sommario** breve, le **Decisioni**, le **Azioni** con il Responsabile, le **QuestioniAperte** e i
  **PuntiChiave**. Ogni elemento cita le sue **Fonti** (parlante e minuto).
- Un **Argomento** facoltativo ("di cosa si parla") lascia fuori ciò che è **fuori tema**.
- I nomi seguono sempre quelli attuali, come nel Documento: una rinomina non invalida il Riassunto.
- Tutto **in locale**: LLM open-source incluso nell'app, nessun testo lascia la macchina.

## Scope (primo rilascio)
- Riassumi manuale nella scheda Riassunto, anche con Argomento facoltativo, in coda FIFO con le Elaborazioni.
- Modello **Qwen3.5 9B q4_K_M** (Apache 2.0, 6,6 GB), scaricato al primo "Riassumi"; l'onboarding non cambia.
- Runtime LLM **incluso nell'app**, senza Ollama installato dall'utente.
- Verifica delle fonti: ciò che non ha Fonti valide viene scartato e contato, mai mostrato come fatto.
- Riassunto salvato nel **DB SQLite del progetto**: backup, niente file sparsi, e la ricerca futura è possibile.
- Revisione dei parlanti → Riassunto **superato** (avviso + "Riassumi di nuovo"). Ritrascrivi →
  il Riassunto è cancellato e rifatto in automatico dopo la nuova Elaborazione.
- Limite: registrazioni fino a circa 1 h 15 (circa 28k token); oltre, "Riassumi" non è disponibile.

## Fuori scope (primo rilascio)
- Capitoli, riassunto di progetto ("Da fare" di progetto), click sulla Fonte che porta all'audio.
- Riassunto dentro il Documento .md: il Documento non dipende mai dall'LLM.
- Storico dei Riassunti sostituiti, annullamento in coda, modifica del Riassunto a mano.
- Ricerca e indicizzazione full-text su Riassunti e Trascritti: feature successiva, lo schema non la deve precludere.
- Registrazioni oltre circa 1 h 15: spike contesto-lungo.
- Qualsiasi opzione cloud.

## Esito misurabile
- Un'ora di registrazione riassunta in **≤ ~5 min** sul M3 Pro. Misura dello spike: 175 s su 25k token.
- Sulle registrazioni reali l'utente riconosce le Decisioni e le Azioni come vere. Nessuna Fonte
  mostrata punta a un segmento inesistente o al parlante sbagliato.

## Rischi / spike
1. **runtime-llm-in-app**: llama.cpp via JNI o sidecar `llama-server`. Serve Metal, output con
   schema e cancellazione; l'ADR 0008 va aggiornato, perché prevedeva Ollama in loopback.
2. **contesto-lungo**: spezzare e ricomporre oltre circa 1 h 15.
3. **filtro-fuori-tema**: l'Argomento toglie il fuori tema senza perdere Decisioni?
4. **qualita-riassunto**: Decisione e QuestioneAperta tenute distinte, Fonti non duplicate,
   Sommario sui brevi, costo dei riferimenti-voce nel testo.

Ricerca: [research/scelta-modello-llm.md](research/scelta-modello-llm.md) (Qwen3.5 9B vs Gemma 4 12B).

## Verdetto challenger
RESHAPE. Rimodellato così:
- stato superato e riferimenti-voce;
- Fonti come id validati;
- Decisione distinta da QuestioneAperta;
- coda condivisa;
- branch rifondato su master dopo il merge di R1/R2.

L'utente ha tenuto il Sommario in prosa e ha scelto il runtime incluso nell'app invece di Ollama. Il test del modello
fatto a costo zero (spike) ha confermato la fattibilità: 0 citazioni invalide, entro il budget.
