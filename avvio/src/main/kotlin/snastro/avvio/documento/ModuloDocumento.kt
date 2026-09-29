package snastro.avvio.documento

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import snastro.avvio.Abbonamento
import snastro.avvio.ModuloComposizione
import snastro.avvio.abbonamenti
import snastro.avvio.gestoreErrori
import snastro.avvio.progetto.AperturaProgetto
import snastro.avvio.progetto.ComponentiApp
import snastro.avvio.progetto.PorteProgetto
import snastro.avvio.segnalazioneApp
import snastro.documento.adattatori.eventi.AbbonatoDocumentoEventi
import snastro.documento.adattatori.porte.ScrittoreDocumentoFile
import snastro.documento.applicazione.letture.Documento
import snastro.documento.applicazione.politiche.RigenerazioneDocumentoPolitica
import snastro.kernel.AbbonatoDopoCommit
import snastro.kernel.RegistrazioneId
import snastro.parlanti.applicazione.eventi.AttribuzioneConfermata
import snastro.parlanti.applicazione.eventi.ParlantePromosso
import snastro.parlanti.applicazione.eventi.ParlanteRinominato
import snastro.progetto.applicazione.eventi.DataRegistrazioneModificata
import snastro.progetto.applicazione.eventi.RegistrazioneEliminata
import snastro.progetto.applicazione.eventi.RegistrazioneRinominata
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni
import snastro.supporto.figlioDi
import snastro.trascrizione.applicazione.eventi.ElaborazioneCompletata
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.eventi.VoceDivisa
import snastro.trascrizione.applicazione.eventi.VociUnite
import java.nio.file.Files
import java.nio.file.Path

/**
 * Documento's part of the single composition (ADR 0030 §1), built from [PorteProgetto] only: the ONE
 * [RigenerazioneDocumentoPolitica] of the project (AC-C61: Progetto's `PuliziaDerivatiFile` reuses it), reading the
 * Nomi from Parlanti (AC-356/AC-359: a Voce with no named Parlante renders as 'Voce n'), and its after-commit
 * subscriber `AbbonatoDocumentoEventi` — whose worker (startup sweep included, AC-185) starts only at [avvia], on its
 * own child of the project scope, on the app's io dispatcher (never the UI thread).
 */
internal class ModuloDocumento(
    porte: PorteProgetto,
    apertura: AperturaProgetto,
    private val app: ComponentiApp,
) : ModuloComposizione {
    val politica = RigenerazioneDocumentoPolitica(
        porte.trascrittoPerDocumento,
        porte.nomiPerDocumento,
        ScrittoreDocumentoFile(apertura.cartella.resolve(CARTELLA_DOCUMENTI)),
    )

    // AC-C47: the startup sweep lists ids itself (never RigenerazioneDocumentoPolitica's all-or-nothing fold);
    // AC-C54: the ONE JUL-backed Segnalazione of `:avvio`.
    private val abbonato = AbbonatoDocumentoEventi(
        politica,
        porte.trascrittoPerDocumento::registrazioniConTrascritto,
        segnalazioneApp,
    )

    @Volatile private var lavoro: Job? = null

    val collaboratori = CollaboratoriDocumento(
        percorsoDocumento = { id -> percorsoDocumento(apertura.cartella, porte.catalogo, id) },
    )

    override fun abbonatiDopoCommit(): List<Abbonamento<AbbonatoDopoCommit>> = abbonamenti(
        abbonato,
        ElaborazioneCompletata::class,
        VociUnite::class,
        VoceDivisa::class,
        SegmentoRiassegnato::class,
        AttribuzioneConfermata::class,
        DataRegistrazioneModificata::class,
        RegistrazioneRinominata::class,
        RegistrazioneEliminata::class,
        ParlanteRinominato::class,
        ParlantePromosso::class,
    )

    override fun avvia(scope: CoroutineScope) {
        val figlio = figlioDi(scope, app.io, gestoreErrori) // AC-C56
        abbonato.avvia(figlio)
        lavoro = figlio.coroutineContext.job
    }

    override fun ferma() {
        lavoro?.let { runBlocking { it.join() } }
    }

    private companion object {
        const val CARTELLA_DOCUMENTI = "documenti"

        /**
         * S3's "Apri documento" target (AC-218): `documenti/<Documento.nomeFile>` of [id] — the same pure name the
         * regeneration writes (ADR 0010) — or `null` while it has not been written yet.
         */
        fun percorsoDocumento(cartella: Path, catalogo: CatalogoRegistrazioni, id: RegistrazioneId): String? =
            catalogo.registrazione(id)
                ?.let { r -> Documento.nomeFile(r.dataRegistrazione, r.titolo) }
                ?.let { nome -> cartella.resolve(CARTELLA_DOCUMENTI).resolve(nome) }
                ?.takeIf(Files::isRegularFile)
                ?.toAbsolutePath()
                ?.toString()
    }
}

/** Documento's typed collaborator of ONE open project: S3's "Apri documento" path (AC-218). */
internal class CollaboratoriDocumento(val percorsoDocumento: (RegistrazioneId) -> String?)
