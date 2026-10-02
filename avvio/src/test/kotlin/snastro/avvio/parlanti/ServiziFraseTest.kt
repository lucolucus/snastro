package snastro.avvio.parlanti

import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.trascrizione.applicazione.comandi.ConfermaSegmento
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmento
import snastro.ui.registrazione.FraseRef
import snastro.ui.registrazione.ObiettivoNome
import snastro.ui.registrazione.PassiNominaFrase
import kotlin.test.Test
import kotlin.test.assertEquals

/** AC-I89 (INV-I7 hand-off): 'Dai un nome a questa frase' states the Incontro its commands read the Voci from. */
class ServiziFraseTest {
    private val parte = RegistrazioneId("parte-2")
    private val incontro = IncontroId("incontro-1")
    private val frase = FraseRef(parte, SegmentoId(3))
    private val conferme = mutableListOf<ConfermaSegmento>()
    private val riassegnazioni = mutableListOf<RiassegnaSegmento>()
    private val servizi = ServiziFrase(
        confermaSegmento = {
            conferme += it
            Esito.Ok(Unit)
        },
        riassegnaSegmento = {
            riassegnazioni += it
            Esito.Ok(VoceId(7))
        },
        confermaAttribuzione = { Esito.Ok(Unit) },
        incontroDi = { if (it == parte) incontro else null },
    )

    @Test
    fun `AC-I89 la conferma della frase porta l Incontro delle Voci`() {
        servizi.esegui(frase, PassiNominaFrase.SoloConferma)

        assertEquals(listOf(incontro), conferme.map { it.incontroDelleVoci })
    }

    @Test
    fun `AC-I89 sposta e nuova Voce portano l Incontro delle Voci`() {
        servizi.esegui(frase, PassiNominaFrase.Sposta(VoceId(2)))
        servizi.esegui(frase, PassiNominaFrase.NuovaVoce(ObiettivoNome.Nuovo("Anna", ricorrente = false)))

        assertEquals(listOf(incontro, incontro), riassegnazioni.map { it.incontroDelleVoci })
        assertEquals(listOf(VoceId(2), null), riassegnazioni.map { it.destinazione })
    }

    @Test
    fun `AC-I89 attribuire una Voce conferma poi la frase con l Incontro delle Voci`() {
        servizi.esegui(frase, PassiNominaFrase.AttribuisciVoce(VoceId(2), ObiettivoNome.Nuovo("Anna", false)))

        assertEquals(listOf(incontro), conferme.map { it.incontroDelleVoci })
    }
}
