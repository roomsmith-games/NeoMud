plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    application
}

application {
    mainClass.set("com.neomud.server.ApplicationKt")
}

tasks.named<JavaExec>("run") {
    workingDir = project.projectDir
    dependsOn("packageWorld")
    environment("NEOMUD_ADMINS", System.getenv("NEOMUD_ADMINS") ?: "bob")
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.forwarded.header)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logback.classic)
    implementation(libs.bcrypt)
    implementation(libs.nimbus.jose.jwt)
    implementation("io.sentry:sentry:8.13.2")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.named<Test>("test") {
    dependsOn("packageWorld")
    workingDir = project.projectDir
}

kotlin {
    jvmToolchain(21)
}

ktor {
    fatJar {
        archiveFileName.set("neomud-server.jar")
    }
}

// Bundle the default world into the shadow JAR as a classpath resource
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    dependsOn("packageWorld")
    from(layout.buildDirectory.dir("worlds")) {
        include("default-world.nmd")
        into("bundled")
    }
}

tasks.register<Exec>("validateWorld") {
    description = "Validates world data integrity (coordinates, exits, reciprocals)"
    group = "verification"
    commandLine("node", rootProject.file("scripts/validate-world.mjs").absolutePath)
}

tasks.register<Zip>("packageWorld") {
    dependsOn("validateWorld")
    description = "Builds the default world bundle (.nmd) from maker source"
    group = "build"

    archiveFileName.set("default-world.nmd")
    destinationDirectory.set(layout.buildDirectory.dir("worlds"))

    from(rootProject.file("maker/default_world_src")) {
        include("manifest.json")
        include("world/**")
        include("assets/**")
    }

    val makerDest = rootProject.file("maker/default_world.nmd")
    doLast {
        if (makerDest.parentFile.exists()) {
            archiveFile.get().asFile.copyTo(makerDest, overwrite = true)
            logger.lifecycle("Copied world bundle to maker/default_world.nmd")
        }
    }
}
