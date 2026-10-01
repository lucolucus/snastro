package snastro.sintesi.adattatori.persistenza

import snastro.kernel.RegistrazioneId
import snastro.kernel.RicostituzioneDaPersistenza
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.valoreOppureErrore
import snastro.persistenza.SnastroDatabase
import snastro.sintesi.dominio.Argomento
import snastro.sintesi.dominio.Azione
import snastro.sintesi.dominio.Decisione
import snastro.sintesi.dominio.LunghezzaMassimaParole
import snastro.sintesi.dominio.MotivoFallimento
import snastro.sintesi.dominio.PuntoChiave
import snastro.sintesi.dominio.QuestioneAperta
import snastro.sintesi.dominio.Riassunto
import snastro.sintesi.dominio.RiassuntoId
import snastro.sintesi.dominio.Sommario
import snastro.sintesi.dominio.StatoRiassunto
import snastro.sintesi.dominio.TestoConVoci
import java.time.Instant

/** The database is trusted, nothing re-validated (CR-15): [Argomento]/[LunghezzaMassimaParole] were already
 * validated by the only writers (their smart constructors) before [RiassuntoRepositorySql] ever stored them. */
@OptIn(RicostituzioneDaPersistenza::class)
internal fun inDominio(db: SnastroDatabase, riga: RigaRiassunto): Riassunto {
    val figli = leggiFigli(db, RiassuntoId(riga.id))
    return Riassunto.ricostituisci(
        id = RiassuntoId(riga.id),
        registrazioneId = RegistrazioneId(riga.registrazioneId),
        argomento = riga.argomento?.let { a ->
            Argomento.di(a).valoreOppureErrore { "argomento invalido nel database: $a" }
        },
        lunghezzaMassima = LunghezzaMassimaParole.di(riga.lunghezzaMassimaParole.toInt())
            .valoreOppureErrore { "lunghezza_massima_parole fuori intervallo: ${riga.lunghezzaMassimaParole}" },
        richiestoAlle = Instant.ofEpochMilli(riga.richiestoAlle),
        stato = StatoRiassunto.valueOf(riga.stato.uppercase()),
        avviatoAlle = riga.avviatoAlle?.let(Instant::ofEpochMilli),
        motivoFallimento = riga.motivoFallimento?.let(::motivoFallimentoDiCodice),
        sommario = riga.sommario?.let { Sommario(testoDiStorage(it)) },
        decisioni = figli.decisioni,
        questioniAperte = figli.questioniAperte,
        azioni = figli.azioni,
        puntiChiave = figli.puntiChiave,
        omessi = riga.omessi?.toInt(),
        // TRANSITION (ADR 0033 §6): the stored key is '<registrazioneId>=<old encoding>' (ADR 0034 §1); the
        // current domain compares the old encoding, so the prefix is stripped here (incontro-chiavi removes this).
        struttura = riga.struttura?.substringAfter('='),
    )
}

private fun leggiFigli(db: SnastroDatabase, id: RiassuntoId): Figli {
    val elementi = db.riassuntoElementoQueries.trovaDiRiassunto(id.valore).executeAsList()
    val fontiPerElemento = db.riassuntoFonteQueries.trovaDiRiassunto(id.valore).executeAsList()
        .groupBy { it.tipo to it.posizione }
        .mapValues { (_, righe) -> righe.map { SegmentoId(it.segmento_id.toInt()) }.toSet() }
    fun fontiDi(tipo: String, posizione: Long) = fontiPerElemento[tipo to posizione].orEmpty()
    return Figli(
        decisioni = elementi.filter { it.tipo == TIPO_DECISIONE }
            .map { Decisione(testoDiStorage(it.testo), fontiDi(it.tipo, it.posizione)) },
        questioniAperte = elementi.filter { it.tipo == TIPO_QUESTIONE_APERTA }
            .map { QuestioneAperta(testoDiStorage(it.testo), fontiDi(it.tipo, it.posizione)) },
        azioni = elementi.filter { it.tipo == TIPO_AZIONE }
            .map { e ->
                val voce = e.voce_id?.let { v -> VoceId(v.toInt()) }
                Azione(testoDiStorage(e.testo), fontiDi(e.tipo, e.posizione), voce)
            },
        puntiChiave = elementi.filter { it.tipo == TIPO_PUNTO_CHIAVE }
            .map { e ->
                val voce = e.voce_id?.let { v -> VoceId(v.toInt()) }
                PuntoChiave(testoDiStorage(e.testo), fontiDi(e.tipo, e.posizione), voce)
            },
    )
}

/** D-0002: a STORED text is written only by [TestoConVoci.codifica]; a malformed token here is a data fault. */
private fun testoDiStorage(raw: String): TestoConVoci =
    checkNotNull(TestoConVoci.decodifica(raw)) { "testo malformato nel database: $raw" }

private fun motivoFallimentoDiCodice(codice: String): MotivoFallimento =
    checkNotNull(MotivoFallimento.entries.firstOrNull { it.codice == codice }) {
        "motivo_fallimento sconosciuto nel database: $codice"
    }

/** The persisted elements (+ Fonti) of a `pronto` Riassunto, grouped by `tipo` ([TIPO_DECISIONE] etc.). */
private data class Figli(
    val decisioni: List<Decisione>,
    val questioniAperte: List<QuestioneAperta>,
    val azioni: List<Azione>,
    val puntiChiave: List<PuntoChiave>,
)
