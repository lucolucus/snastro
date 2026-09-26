package io.github.lucolucus.llamajni

/** A compute device the backend can run a model on. */
public data class BackendDevice(
    public val name: String,
    public val kind: DeviceKind,
    public val freeMemoryBytes: Long,
    public val totalMemoryBytes: Long,
)
