package snastro.sintesi.adattatori.persistenza

import snastro.kernel.RegistrazioneId
import snastro.kernel.RicostituzioneDaPersistenza
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
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
import migrations.Riassunto as RiassuntoRiga

/** The database is trusted, nothing re-validated (CR-15): [Argomento]/[LunghezzaMassimaParole] were already
 * validated by the only writers (their smart constructors) before [RiassuntoRepositorySql] ever stored them. */
@OptIn(RicostituzioneDaPersistenza::class)
internal fun inDominio(db: SnastroDatabase, riga: RiassuntoRiga): Riassunto {
    val figli = leggiFigli(db, RiassuntoId(riga.id))
    return Riassunto.ricostituisci(
        id = RiassuntoId(riga.id),
        registrazioneId = RegistrazioneId(riga.registrazione_id),
        argomento = riga.argomento?.let { Argomento.di(it).dalDatabase("argomento invalido nel database: $it") },
        lunghezzaMassima = LunghezzaMassimaParole.di(riga.lunghezza_massima_parole.toInt())
            .dalDatabase("lunghezza_massima_parole fuori intervallo: ${riga.lunghezza_massima_parole}"),
        richiestoAlle = Instant.ofEpochMilli(riga.richiesto_alle),
        stato = StatoRiassunto.valueOf(riga.stato.uppercase()),
        avviatoAlle = riga.avviato_alle?.let(Instant::ofEpochMilli),
        motivoFallimento = riga.motivo_fallimento?.let(::motivoFallimentoDiCodice),
        sommario = riga.sommario?.let { Sommario(testoDiStorage(it)) },
        decisioni = figli.decisioni,
        questioniAperte = figli.questioniAperte,
        azioni = figli.azioni,
        puntiChiave = figli.puntiChiave,
        omessi = riga.omessi?.toInt(),
        struttura = riga.struttura,
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
