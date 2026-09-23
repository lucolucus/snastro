plugins {
    id("snastro.kotlin-jvm")
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("SnastroDatabase") {
            packageName.set("snastro.persistenza")
            // ADR 0006: forward-only, build-verified migrations against the committed snapshots
            // in src/main/sqldelight/databases/<n>.db (verifySqlDelightMigration, part of `check`).
            verifyMigrations.set(true)
            schemaOutputDirectory.set(project.file("src/main/sqldelight/databases"))
        }
    }
}

dependencies {
    implementation(project(":kernel"))
    implementation(libs.sqldelight.driver)
    implementation(libs.sqlite.jdbc)

    testFixturesImplementation(libs.sqldelight.driver)
    testFixturesImplementation(libs.sqlite.jdbc)

    testImplementation(testFixtures(project(":kernel")))
}
