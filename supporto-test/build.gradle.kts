// ADR 0028 §1/§3: shared test helpers, never shipped. Reached only from test source sets
// (testImplementation / testRuntimeOnly, verificaDipendenzeModuli test-only rule); no snastro dependency.
plugins {
    id("snastro.kotlin-jvm")
    id("snastro.explicit-api")
}

dependencies {
    api(libs.kotlin.test)
    api(libs.junit.jupiter)
    api(libs.kotlinx.coroutines.test)
}
