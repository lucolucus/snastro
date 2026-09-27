// ADR 0028 §1: the domain-free technical library. Only kotlinx-coroutines-core, no snastro project
// dependency (not even :kernel), not a test-fixtures producer: its public API is pinned by CR-18c.
plugins {
    id("snastro.kotlin-jvm")
    id("snastro.explicit-api")
}

dependencies {
    // `api`: the public API exposes CoroutineScope, Job, CoroutineDispatcher, CoroutineExceptionHandler.
    api(libs.kotlinx.coroutines.core)
}
