package snastro.avvio.r3

import org.junit.jupiter.api.AfterEach
import snastro.avvio.r1.ServizioModelliProvisioning
import snastro.kernel.Esito
import snastro.modelli.CatalogoModelli
import snastro.modelli.ErroreModelli
import snastro.modelli.FormatoVoce
import snastro.modelli.VoceCatalogo
import snastro.sintesi.applicazione.porte.AmbienteDisponibilitaModello
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguistico
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguisticoContratto
import snastro.sintesi.applicazione.porte.MotivoDownload
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico
import snastro.supporto.test.attendiFinche
import snastro.ui.modelli.ErroreServizioModelli
import snastro.ui.modelli.StatoModelloFacoltativo
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

private const val ID_PROVA = "llm-prova"
private const val DIMENSIONE_PROVA = 6_000L
private const val TIMEOUT_MS = 2_000L

/**
 * D2 (AC-S72, ADR 0025 §4: "a contract test runs in `:avvio` — fake provisioning"):
 * [DisponibilitaModelloLinguisticoAvvio] over a controllable [ProvisioningFacoltativaControllabile]
 * standing in for `ProvisioningModelli`'s network-bound `scarica(id, …)` — driven through
 * [ServizioModelliProvisioning.scaricaFacoltativo], the SAME real `:avvio` port `:ui` uses, never a
 * shortcut that pokes the shared holder directly (lessons-by-block-type: seed/advance through the
 * supplier's own commands).
 */
class DisponibilitaModelloLinguisticoAvvioTest : DisponibilitaModelloLinguisticoContratto() {
    // Some contract tests leave a download mid-flight on purpose (never wired to a completion step of
    // their own) — tracked here so `pulisci` can unblock every background thread this test class ever
    // started, never a daemon thread parked on the fake's queue for the rest of the gate's JVM.
    private val ambientiCreati = mutableListOf<AmbienteReale>()

    override fun ambiente(): AmbienteDisponibilitaModello = AmbienteReale().also { ambientiCreati += it }

    @AfterEach
    fun pulisci() {
        ambientiCreati.forEach { it.terminaSeInCorso() }
        ambientiCreati.clear()
    }

    @Test
    fun `AC-S73 statoFacoltativi e DisponibilitaModelloLinguistico leggono lo stesso InDownload`() {
        val fake = ProvisioningFacoltativaControllabile(ID_PROVA, DIMENSIONE_PROVA)
        val servizio = costruisciServizio(fake)
        val disponibilita = DisponibilitaModelloLinguisticoAvvio(
            ID_PROVA,
            DIMENSIONE_PROVA,
            fake::installata,
            servizio.statoFacoltativi,
        )

        // AC-S73: "scaricaFacoltativo(id) runs off the caller thread" — a real background dispatcher would.
        val thread = Thread { servizio.scaricaFacoltativo(ID_PROVA) }.apply {
            isDaemon = true
            start()
        }
        try {
            fake.attendiPartenza()

            val daServizio = servizio.statoFacoltativi.value[ID_PROVA] as StatoModelloFacoltativo.InDownload
            val daDisponibilita = disponibilita.stato() as StatoModelloLinguistico.InDownload
            assertEquals(daServizio.scaricatiByte, daDisponibilita.scaricatiByte)
            assertEquals(daServizio.totaliByte, daDisponibilita.totaliByte)
        } finally {
            fake.completa()
            thread.join(TIMEOUT_MS)
        }
    }

    @Test
    fun `AC-S74 ogni ErroreServizioModelli mappa nel MotivoDownload della sua riga`() {
        val tabella = listOf(
            ErroreServizioModelli.ReteAssente to MotivoDownload.ConnessioneInterrotta,
            ErroreServizioModelli.DownloadFallito("HTTP 500") to MotivoDownload.ConnessioneInterrotta,
            ErroreServizioModelli.HashNonValido(ID_PROVA) to MotivoDownload.FileNonIntegro,
            ErroreServizioModelli.ArchivioNonValido(ID_PROVA) to MotivoDownload.FileNonIntegro,
            ErroreServizioModelli.SpazioInsufficiente(DIMENSIONE_PROVA) to MotivoDownload.SpazioInsufficiente,
            ErroreServizioModelli.ScritturaFallita("disco pieno") to MotivoDownload.ScritturaFallita,
        )

        tabella.forEach { (errore, motivo) ->
            assertEquals(motivo, mappaMotivoDownload(errore), "mappatura di $errore")
        }
    }

    /**
     * Drives [ServizioModelliProvisioning.scaricaFacoltativo] through the port's own commands:
     * [avviaDownload] starts it on a background thread, [avanza] unblocks the next progress tick and
     * waits until the shared holder observably carries it (never a fixed sleep), [completa]/[fallisci]
     * unblock the fake's terminal outcome and join the thread. [installaConMarcatoreDiverso] is a
     * no-op: at this port a mismatched marker and "nothing on disk" are the SAME
     * [StatoModelloLinguistico.NonInstallato] (the marker's own hash check is `:modelli`'s own tested
     * behaviour, exercised in `modelli-provisioning-facoltativo`, not this port's).
     */
    private class AmbienteReale : AmbienteDisponibilitaModello {
        private var fake = ProvisioningFacoltativaControllabile(ID_PROVA, DIMENSIONE_PROVA)
        private var servizio = costruisciServizio(fake)
        private var thread: Thread? = null

        override val disponibilita: DisponibilitaModelloLinguistico
            get() = DisponibilitaModelloLinguisticoAvvio(
                ID_PROVA,
                DIMENSIONE_PROVA,
                fake::installata,
                servizio.statoFacoltativi,
            )

        override fun avviaDownload() {
            thread = Thread { servizio.scaricaFacoltativo(ID_PROVA) }.apply {
                isDaemon = true
                start()
            }
            fake.attendiPartenza()
        }

        override fun avanza() {
            val atteso = fake.avanza()
            attendiFinche(messaggio = "InDownload($atteso) osservato") {
                (servizio.statoFacoltativi.value[ID_PROVA] as? StatoModelloFacoltativo.InDownload)?.scaricatiByte ==
                    atteso
            }
        }

        override fun completa() {
            fake.completa()
            thread?.join(TIMEOUT_MS)
        }

        override fun fallisci(motivo: MotivoDownload) {
            fake.fallisci(erroreModelliPer(motivo))
            thread?.join(TIMEOUT_MS)
        }

        // A wrong marker and no marker at all are the SAME NonInstallato at this port (see class KDoc).
        override fun installaConMarcatoreDiverso() = Unit

        override fun riavvia(): DisponibilitaModelloLinguistico {
            val installataPrimaDelRiavvio = fake.installata(ID_PROVA)
            terminaSeInCorso() // AC-S18: "the session state is lost" — this Ambiente's own pending download too.
            fake = ProvisioningFacoltativaControllabile(ID_PROVA, DIMENSIONE_PROVA, installataPrimaDelRiavvio)
            servizio = costruisciServizio(fake)
            thread = null
            return disponibilita
        }

        /** Test-only cleanup: unblocks a fake still mid-download when its owning test never itself ended it. */
        fun terminaSeInCorso() {
            fake.fallisci(ErroreModelli.DownloadFallito("interrotto per pulizia del test"))
            thread?.join(TIMEOUT_MS)
        }

        private fun erroreModelliPer(motivo: MotivoDownload): ErroreModelli = when (motivo) {
            MotivoDownload.ConnessioneInterrotta -> ErroreModelli.ReteAssente
            MotivoDownload.FileNonIntegro -> ErroreModelli.HashNonValido(ID_PROVA)
            MotivoDownload.SpazioInsufficiente -> ErroreModelli.SpazioInsufficiente(DIMENSIONE_PROVA)
            MotivoDownload.ScritturaFallita -> ErroreModelli.ScritturaFallita("disco pieno")
        }
    }
}

private fun costruisciServizio(fake: ProvisioningFacoltativaControllabile) = ServizioModelliProvisioning(
    CatalogoModelli(listOf(voceFacoltativaDiProva())),
    { true },
    { emptyList() },
    { Esito.Ok(Unit) },
    fake::installata,
    fake::scarica,
)

private fun voceFacoltativaDiProva() = VoceCatalogo(
    id = ID_PROVA,
    ruolo = "llm",
    url = "https://example.invalid/$ID_PROVA.gguf",
    sha256 = "0".repeat(64),
    dimensioneByte = DIMENSIONE_PROVA,
    formato = FormatoVoce.FILE,
    licenza = "Apache-2.0",
    attribuzione = "Autori di prova",
    obbligatoria = false,
)

/**
 * A controllable stand-in for `ProvisioningModelli`'s by-id `scarica` (ADR 0025 §4's "fake
 * provisioning"): [scarica] blocks on a queue of steps a test feeds through [avanza]/[completa]/
 * [fallisci], reporting one `InDownload` tick per step — same shape as the real class, no network.
 */
private class ProvisioningFacoltativaControllabile(
    private val id: String,
    private val dimensione: Long,
    installataIniziale: Boolean = false,
) {
    private sealed interface Comando
    private data class Avanza(val scaricatiByte: Long) : Comando
    private data object Completa : Comando
    private data class Fallisci(val errore: ErroreModelli) : Comando

    private val comandi = LinkedBlockingQueue<Comando>()
    private val partenza = LinkedBlockingQueue<Unit>()

    @Volatile
    private var installato = installataIniziale

    @Volatile
    private var scaricatiCorrente = 0L

    fun installata(idRichiesto: String): Boolean = installato && idRichiesto == id

    fun scarica(idRichiesto: String, progresso: (Long, Long) -> Unit): Esito<Unit> {
        check(idRichiesto == id) { "id inatteso: '$idRichiesto'" }
        if (installato) return Esito.Ok(Unit)
        progresso(0, dimensione)
        partenza.put(Unit)
        var esito: Esito<Unit>? = null
        while (esito == null) {
            when (val comando = comandi.take()) {
                is Avanza -> progresso(comando.scaricatiByte, dimensione)
                Completa -> {
                    installato = true
                    esito = Esito.Ok(Unit)
                }
                is Fallisci -> esito = Esito.Errore(comando.errore)
            }
        }
        return esito
    }

    fun attendiPartenza() {
        check(partenza.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS) != null) { "scarica non partito in tempo" }
    }

    /** Advances by a tenth of the total (never past it) and returns the new `scaricatiByte`. */
    fun avanza(): Long {
        scaricatiCorrente = minOf(dimensione, scaricatiCorrente + dimensione / 10 + 1)
        comandi.put(Avanza(scaricatiCorrente))
        return scaricatiCorrente
    }

    fun completa() = comandi.put(Completa)

    fun fallisci(errore: ErroreModelli) = comandi.put(Fallisci(errore))
}
