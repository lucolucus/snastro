package snastro.sintesi.applicazione.porte

/**
 * Settable in-memory [DisponibilitaModelloLinguistico] (passes [DisponibilitaModelloLinguisticoContratto]):
 * [stato] returns whatever [corrente] holds now, so a test moves the model through its states by
 * assigning it. Volatile: read from worker threads while a test thread sets it.
 */
public class DisponibilitaModelloLinguisticoFinta(stato: StatoModelloLinguistico) : DisponibilitaModelloLinguistico {
    @Volatile
    public var corrente: StatoModelloLinguistico = stato

    override fun stato(): StatoModelloLinguistico = corrente
}
