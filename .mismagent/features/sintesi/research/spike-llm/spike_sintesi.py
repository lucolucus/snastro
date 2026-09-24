"""Spike Sintesi: riassunto di un Documento con un LLM locale (Ollama), citazioni validate.

Uso: python3 spike_sintesi.py <modello> <documento.md> <out.json> [contesto]
"""
import json, re, sys, time, urllib.request

MODELLO, DOC, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
CONTESTO = sys.argv[4] if len(sys.argv) > 4 else ""

RIGA = re.compile(r"^\*\*(?P<nome>[^*]+)\*\* \((?P<t>[\d:]+)\): (?P<testo>.*)$")
segmenti = []
for riga in open(DOC, encoding="utf-8"):
    m = RIGA.match(riga.strip())
    if m:
        segmenti.append({"id": f"s{len(segmenti) + 1}", "parlante": m["nome"], "t": m["t"], "testo": m["testo"]})
per_id = {s["id"]: s for s in segmenti}
parlanti = sorted({s["parlante"] for s in segmenti})
trascritto = "\n".join(f"[{s['id']} {s['parlante']} {s['t']}] {s['testo']}" for s in segmenti)

CIT = {"type": "array", "items": {"type": "string"}}
SCHEMA = {
    "type": "object",
    "properties": {
        "riassunto": {"type": "string"},
        "punti_chiave": {"type": "array", "items": {"type": "object", "properties": {
            "testo": {"type": "string"}, "parlante": {"type": "string"}, "fonti": CIT},
            "required": ["testo", "parlante", "fonti"]}},
        "decisioni": {"type": "array", "items": {"type": "object", "properties": {
            "testo": {"type": "string"}, "fonti": CIT}, "required": ["testo", "fonti"]}},
        "azioni": {"type": "array", "items": {"type": "object", "properties": {
            "testo": {"type": "string"}, "responsabile": {"type": "string"}, "fonti": CIT},
            "required": ["testo", "responsabile", "fonti"]}},
    },
    "required": ["riassunto", "punti_chiave", "decisioni", "azioni"],
}

SISTEMA = f"""Sei un assistente che riassume trascrizioni di riunioni di lavoro, in italiano.
La trascrizione è automatica: contiene errori di riconoscimento, frasi spezzate e parti in inglese.
Ogni riga ha la forma [id parlante minuto] testo. I parlanti sono: {", ".join(parlanti)}.
Regole:
- Scrivi tutto in italiano.
- Ignora chiacchiere, battute e argomenti che non c'entrano con lo scopo della riunione.
- Ogni punto chiave, decisione e azione deve citare in "fonti" gli id (es. "s12") delle righe da cui deriva. Usa SOLO id presenti nella trascrizione.
- "parlante" e "responsabile" devono essere uno dei parlanti elencati, oppure "nessuno" se non è chiaro.
- Una decisione è qualcosa su cui il gruppo ha concordato; un'azione è un compito che qualcuno deve fare. Se non ce ne sono, lascia la lista vuota: non inventare.
- Riassunto: al massimo 5 frasi."""
if CONTESTO:
    SISTEMA += f"\nContesto della riunione (fornito dall'utente): {CONTESTO}"

corpo = {
    "model": MODELLO, "stream": False, "think": False, "format": SCHEMA,
    "options": {"num_ctx": 32768, "temperature": 0.2},
    "messages": [{"role": "system", "content": SISTEMA}, {"role": "user", "content": trascritto}],
}
t0 = time.time()
req = urllib.request.Request("http://localhost:11434/api/chat", data=json.dumps(corpo).encode(),
                             headers={"Content-Type": "application/json"})
risposta = json.load(urllib.request.urlopen(req, timeout=3600))
parete = time.time() - t0
sintesi = json.loads(risposta["message"]["content"])

# Validazione citazioni: id esistente; per i punti chiave, il parlante coincide con almeno una fonte.
problemi = []
def controlla(sezione, voce, campo_parlante=None):
    fonti = voce.get("fonti", [])
    if not fonti:
        problemi.append((sezione, voce["testo"][:60], "nessuna fonte"))
    inesistenti = [f for f in fonti if f not in per_id]
    if inesistenti:
        problemi.append((sezione, voce["testo"][:60], f"id inesistenti {inesistenti}"))
    if campo_parlante and voce.get(campo_parlante) not in parlanti + ["nessuno"]:
        problemi.append((sezione, voce["testo"][:60], f"parlante sconosciuto {voce.get(campo_parlante)}"))
    if campo_parlante == "parlante":
        valide = [per_id[f]["parlante"] for f in fonti if f in per_id]
        if valide and voce["parlante"] not in valide:
            problemi.append((sezione, voce["testo"][:60], f"parlante {voce['parlante']} non in fonti {valide}"))
    return len(fonti)

n = 0
for v in sintesi["punti_chiave"]: n += controlla("punto", v, "parlante")
for v in sintesi["decisioni"]: n += controlla("decisione", v)
for v in sintesi["azioni"]: n += controlla("azione", v, "responsabile")

misure = {
    "modello": MODELLO, "documento": DOC, "contesto": CONTESTO, "segmenti": len(segmenti),
    "token_prompt": risposta.get("prompt_eval_count"), "token_output": risposta.get("eval_count"),
    "s_lettura": round(risposta.get("prompt_eval_duration", 0) / 1e9, 1),
    "s_scrittura": round(risposta.get("eval_duration", 0) / 1e9, 1),
    "s_totale_parete": round(parete, 1), "citazioni": n, "problemi": problemi,
}
json.dump({"misure": misure, "sintesi": sintesi}, open(OUT, "w"), ensure_ascii=False, indent=2)
print(json.dumps(misure, ensure_ascii=False, indent=2))
