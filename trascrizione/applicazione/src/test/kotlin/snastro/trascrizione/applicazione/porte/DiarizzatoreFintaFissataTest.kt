package snastro.trascrizione.applicazione.porte

import snastro.kernel.IntervalloMs

/** The scripted Finta (overlapping turns, one past the duration) passes the same contract. */
class DiarizzatoreFintaFissataTest : DiarizzatoreContratto() {
    override fun diarizzatore(): Diarizzatore =
        DiarizzatoreFinta(
            listOf(
                Turno(IntervalloMs(0, 2_000), voceIndice = 0),
                Turno(IntervalloMs(1_500, 3_000), voceIndice = 1),
                Turno(IntervalloMs(2_500, 9_000), voceIndice = 2),
            ),
        )
}
