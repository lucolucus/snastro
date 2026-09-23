package snastro.persistenza

/**
 * A project's database file was written by a newer version of the app than this one (ADR 0006):
 * the file is left untouched, nothing is created or migrated.
 */
public class SchemaProgettoPiuRecenteException(public val versioneTrovata: Long, public val versioneSupportata: Long) :
    RuntimeException(
        "Il file di progetto e stato salvato da una versione piu recente dell'app " +
            "(schema $versioneTrovata, questa versione supporta fino a $versioneSupportata): " +
            "aggiorna l'app prima di aprire questo progetto.",
    )
