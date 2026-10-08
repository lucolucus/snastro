package snastro.avvio.progetto

import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** AC-238: a project-level lock, distinct from the per-user registry's own file lock (F4). */
internal class LockCartella(val canale: FileChannel, val lock: FileLock)

/** `null` = the lock is already held (by another process, or another instance in this same JVM). */
internal fun acquisisciLock(cartella: Path): LockCartella? {
    val canale = FileChannel.open(cartella.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    val lock = try {
        canale.tryLock()
    } catch (ignored: OverlappingFileLockException) {
        null
    }
    if (lock == null) {
        canale.close()
        return null
    }
    return LockCartella(canale, lock)
}

internal fun rilasciaLock(lockCartella: LockCartella) {
    try {
        lockCartella.lock.release()
    } finally {
        lockCartella.canale.close()
    }
}
