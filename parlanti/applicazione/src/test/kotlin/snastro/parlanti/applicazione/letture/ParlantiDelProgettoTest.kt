package snastro.parlanti.applicazione.letture

import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.kernel.unIncontroDi
import snastro.kernel.unicaParteDi
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.porte.AttribuzioneRepositoryFinta
import snastro.parlanti.applicazione.porte.LettoreRegistrazione
import snastro.parlanti.applicazione.porte.LettoreRegistrazioneFinta
import snastro.parlanti.applicazione.porte.LettoreVoci
import snastro.parlanti.applicazione.porte.ParlanteRepositoryFinta
import snastro.parlanti.applicazione.porte.RegistrazioneVista
import snastro.parlanti.applicazione.porte.lettoreVociDiUnicheParti
import snastro.parlanti.applicazione.porte.ogniRegistrazioneNota
import snastro.parlanti.applicazione.porte.unaVoceVista
import snastro.parlanti.dominio.Attribuzione
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.TipoParlante
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [ParlantiDelProgetto] against the ports' fakes (D1): AC-175/AC-176 (S4 Galleria). */
class ParlantiDelProgettoTest {

    @Test
    fun `un Progetto senza Parlanti ha lista vuota`() {
        val ambiente = Ambiente()

        assertEquals(emptyList(), ambiente.api.parlanti(PROGETTO))
    }

    @Test
    fun `AC-175 la vista espone tutti i campi del Parlante, il conteggio degli Incontri e l'estratto`() {
        val unaRegistrazioneVista = unaRegistrazione(REG_1, LocalDate.of(2026, 3, 10))
        val ambiente = Ambiente(
            registrazioni = LettoreRegistrazioneFinta(mapOf(REG_1 to unaRegistrazioneVista)),
            lettoreVoci = unLettoreVoci(VOCE_A),
        )
        val marco = unParlante("p-1", "Marco", TipoParlante.RICORRENTE)
        marco.aggiungiImpronta(VOCE_A, unicaParteDi(VOCE_A), impronta(1f), "s1", "finto").atteso()
        ambiente.parlanti.salva(marco).atteso()
        ambiente.attribuzioni.salva(Attribuzione.conferma(VOCE_A, PROGETTO, marco.id).aggregato)

        val riga = ambiente.api.parlanti(PROGETTO).single()

        assertEquals(marco.id, riga.parlanteId)
        assertEquals("Marco", riga.nome)
        assertEquals(TipoParlanteVista.RICORRENTE, riga.tipoParlante)
        assertEquals(StatoParlanteVista.ATTIVO, riga.statoParlante)
        assertEquals(1, riga.numImpronte)
        assertEquals(1, riga.numIncontri)
        assertEquals(LocalDate.of(2026, 3, 10), riga.ultimaApparizione)
        assertEquals(ambiente.estrattoAudio.estratto(VOCE_A), riga.estratto)
    }

    @Test
    fun `AC-176 un eliminato ha numImpronte 0 ed estratto null ma conserva numIncontri`() {
        val ambiente = Ambiente(
            registrazioni = LettoreRegistrazioneFinta(
                mapOf(
                    REG_1 to unaRegistrazione(REG_1, LocalDate.of(2026, 1, 5)),
                    REG_2 to unaRegistrazione(REG_2, LocalDate.of(2026, 2, 20)),
                ),
            ),
            lettoreVoci = unLettoreVoci(VOCE_A),
        )
        val marco = unParlante("p-1", "Marco", TipoParlante.RICORRENTE)
        marco.aggiungiImpronta(VOCE_A, unicaParteDi(VOCE_A), impronta(1f), "s1", "finto").atteso()
        ambiente.parlanti.salva(marco).atteso()
        ambiente.attribuzioni.salva(Attribuzione.conferma(VOCE_A, PROGETTO, marco.id).aggregato)
        ambiente.attribuzioni.salva(Attribuzione.conferma(VOCE_B_REG_2, PROGETTO, marco.id).aggregato)

        // simula EliminaParlante: purga le impronte e marca ELIMINATO, le Attribuzioni restano (ADR 0009)
        marco.elimina().atteso()
        ambiente.parlanti.salva(marco).atteso()

        val riga = ambiente.api.parlanti(PROGETTO).single()

        assertEquals(StatoParlanteVista.ELIMINATO, riga.statoParlante)
        assertEquals(0, riga.numImpronte)
        assertNull(riga.estratto)
        assertEquals(2, riga.numIncontri, "le Attribuzioni passate sopravvivono all'Eliminazione")
    }

    @Test
    fun `AC-I47 numIncontri conta gli Incontri distinti, non le Attribuzioni`() {
        val ambiente = Ambiente(lettoreVoci = unLettoreVoci(VOCE_A))
        val marco = unParlante("p-1", "Marco", TipoParlante.RICORRENTE)
        ambiente.parlanti.salva(marco).atteso()
        // due Voci dello STESSO Incontro attribuite allo stesso Parlante (INV-22): conta una volta.
        ambiente.attribuzioni.salva(Attribuzione.conferma(VOCE_A, PROGETTO, marco.id).aggregato)
        ambiente.attribuzioni.salva(
            Attribuzione.conferma(VoceRef(unIncontroDi(REG_1), VoceId(2)), PROGETTO, marco.id).aggregato,
        )

        val riga = ambiente.api.parlanti(PROGETTO).single()

        assertEquals(1, riga.numIncontri)
    }

    @Test
    fun `AC-I47 numIncontri conta una volta un Incontro di due parti e separatamente un altro Incontro`() {
        val incontro = IncontroId("incontro-2-parti")
        val ambiente = Ambiente(
            registrazioni = LettoreRegistrazioneFinta(
                mapOf(
                    REG_1 to unaRegistrazione(REG_1, LocalDate.of(2026, 1, 5)).copy(incontroId = incontro),
                    REG_2 to unaRegistrazione(REG_2, LocalDate.of(2026, 1, 5)).copy(incontroId = incontro),
                    REG_3 to unaRegistrazione(REG_3, LocalDate.of(2026, 2, 1)),
                ),
            ),
        )
        val marco = unParlante("p-1", "Marco", TipoParlante.RICORRENTE)
        ambiente.parlanti.salva(marco).atteso()
        // Voce 1 (parti 1 e 2) e Voce 4 (parte 2) dell'Incontro di due parti, Voce 1 di un altro Incontro.
        listOf(VoceRef(incontro, VoceId(1)), VoceRef(incontro, VoceId(4)), VoceRef(unIncontroDi(REG_3), VoceId(1)))
            .forEach { ambiente.attribuzioni.salva(Attribuzione.conferma(it, PROGETTO, marco.id).aggregato) }

        val riga = ambiente.api.parlanti(PROGETTO).single()

        assertEquals(2, riga.numIncontri)
        assertEquals(LocalDate.of(2026, 2, 1), riga.ultimaApparizione)
    }

    @Test
    fun `ultimaApparizione e la data piu recente tra le Registrazioni, null senza Attribuzioni`() {
        val senzaAttribuzioni = Ambiente()
        val marcoSolo = unParlante("p-1", "Marco", TipoParlante.RICORRENTE)
        senzaAttribuzioni.parlanti.salva(marcoSolo).atteso()
        assertNull(senzaAttribuzioni.api.parlanti(PROGETTO).single().ultimaApparizione)

        val ambiente = Ambiente(
            registrazioni = LettoreRegistrazioneFinta(
                mapOf(
                    REG_1 to unaRegistrazione(REG_1, LocalDate.of(2026, 1, 5)),
                    REG_2 to unaRegistrazione(REG_2, LocalDate.of(2026, 6, 30)),
                ),
            ),
        )
        val marco = unParlante("p-2", "Marco", TipoParlante.RICORRENTE)
        ambiente.parlanti.salva(marco).atteso()
        ambiente.attribuzioni.salva(Attribuzione.conferma(VOCE_A, PROGETTO, marco.id).aggregato)
        ambiente.attribuzioni.salva(Attribuzione.conferma(VOCE_B_REG_2, PROGETTO, marco.id).aggregato)

        val riga = ambiente.api.parlanti(PROGETTO).single()

        assertEquals(LocalDate.of(2026, 6, 30), riga.ultimaApparizione, "la piu recente, non la prima trovata")
    }

    @Test
    fun `senza impronte l estratto e null`() {
        val ambiente = Ambiente()
        val marco = unParlante("p-1", "Marco", TipoParlante.RICORRENTE)
        ambiente.parlanti.salva(marco).atteso()

        val riga = ambiente.api.parlanti(PROGETTO).single()

        assertEquals(0, riga.numImpronte)
        assertNull(riga.estratto)
    }

    private class Ambiente(
        val parlanti: ParlanteRepositoryFinta = ParlanteRepositoryFinta(),
        val attribuzioni: AttribuzioneRepositoryFinta = AttribuzioneRepositoryFinta(),
        registrazioni: LettoreRegistrazione = ogniRegistrazioneNota(),
        lettoreVoci: LettoreVoci = lettoreVociDiUnicheParti(),
    ) {
        val estrattoAudio: EstrattoAudio = EstrattoAudio(lettoreVoci, ogniRegistrazioneNota())
        val api: ParlantiDelProgetto = ParlantiDelProgetto(parlanti, attribuzioni, registrazioni, estrattoAudio)
    }

    private fun impronta(seme: Float): Impronta = Impronta(FloatArray(8) { i -> seme + i })

    private fun unLettoreVoci(vararg voci: VoceRef): LettoreVoci {
        val perRegistrazione = voci.groupBy { unicaParteDi(it) }
            .mapValues { (_, v) -> v.map { unaVoceVista(it, listOf(IntervalloMs(0, 2_000))) } }
        return lettoreVociDiUnicheParti(perRegistrazione)
    }

    private fun unaRegistrazione(id: RegistrazioneId, dataRegistrazione: LocalDate): RegistrazioneVista =
        RegistrazioneVista(
            registrazioneId = id,
            incontroId = unIncontroDi(id),
            progettoId = PROGETTO,
            titolo = "Seduta",
            riferimentoAudio = RiferimentoAudio("audio/${id.valore}.m4a"),
            dataRegistrazione = dataRegistrazione,
            durataMs = 3_600_000L,
        )

    private fun unParlante(id: String, nome: String, tipo: TipoParlante): Parlante =
        Parlante.crea(ParlanteId(id), PROGETTO, Nome.di(nome).atteso(), tipo).aggregato

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val REG_1 = RegistrazioneId("registrazione-1")
        val REG_2 = RegistrazioneId("registrazione-2")
        val REG_3 = RegistrazioneId("registrazione-3")
        val VOCE_A = VoceRef(unIncontroDi(REG_1), VoceId(1))
        val VOCE_B_REG_2 = VoceRef(unIncontroDi(REG_2), VoceId(1))
    }
}
