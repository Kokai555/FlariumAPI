plugins {
    id("java-library")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("1.21.5-R0.1-SNAPSHOT")
    implementation(project(":nms:api"))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

// Compile the shared implementation source against THIS version's dev bundle as a
// compatibility proof; the shared classes themselves ship only in :nms:shared's jar.
sourceSets.main {
    java.srcDir("../shared/src/main/java")
}

tasks {
    named<Jar>("jar") {
        exclude("com/flarium/api/nms/shared/**")
    }
    named("reobfJar") {
        enabled = false
    }
}
