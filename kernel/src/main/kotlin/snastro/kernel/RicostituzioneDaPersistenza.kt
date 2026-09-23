package snastro.kernel

/**
 * Gates an aggregate's `ricostituisci` (rebuild from persisted state, no re-validation): only
 * `..adattatori.persistenza..` may opt in (CR-15).
 */
@RequiresOptIn(
    message = "Solo gli adattatori di persistenza ricostituiscono un aggregato (CR-15).",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
public annotation class RicostituzioneDaPersistenza
