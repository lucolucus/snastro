plugins {
    id("snastro.kotlin-jvm")
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("SnastroDatabase") {
            packageName.set("snastro.persistenza")
            // ADR 0006 Amendment (a) / CR-13: migrations ARE the schema — every CREATE TABLE/INDEX
            // lives in numbered migrations/<n>.sqm (1.sqm = the full current schema; version 1 has
            // never shipped); `.sq` files hold queries only, compiled against the schema derived
            // from the migrations. No `verifyMigrations` snapshot diff: its object-diff blew up
            // exponentially when the snapshot drifted from the schema and verified nothing useful
            // pre-release. The replacement gate is the fast `:persistenza:test` migration test.
            deriveSchemaFromMigrations.set(true)
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
