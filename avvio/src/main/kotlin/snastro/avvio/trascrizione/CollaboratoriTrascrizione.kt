package snastro.avvio.trascrizione

import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.trascrizione.applicazione.comandi.AnnullaElaborazione
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.comandi.ConfermaSegmento
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmenti
import snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista
import snastro.trascrizione.applicazione.letture.TrascrittoView

/**
 * Trascrizione's typed collaborators of ONE open project ([ModuloTrascrizione]), as plain functions (CR-1: `:ui`
 * binds function types): S2's sources ([statiElaborazione], [avviaElaborazione], [annullaElaborazione], AC-355/AC-478),
 * S3's [trascritto], the Revisione commands, and the two commands Parlanti's glue composes (ADR 0019 §4.1/§5).
 */
@Suppress("LongParameterList") // one parameter per collaborator of the Trascrizione screens and glue
internal class CollaboratoriTrascrizione(
    val statiElaborazione: (List<RegistrazioneId>) -> List<StatoRegistrazioneVista>,
    val avviaElaborazione: (AvviaElaborazione) -> Esito<Unit>,
    val annullaElaborazione: (AnnullaElaborazione) -> Esito<Unit>,
    val trascritto: (RegistrazioneId) -> TrascrittoView?,
    val revisione: ComandiRevisione,
    val confermaSegmento: (ConfermaSegmento) -> Esito<Unit>,
    val riassegnaSegmenti: (RiassegnaSegmenti) -> Esito<Unit>,
)
