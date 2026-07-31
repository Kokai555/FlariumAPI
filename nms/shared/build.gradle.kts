plugins {
    id("java-library")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("1.21.4-R0.1-SNAPSHOT")
    implementation(project(":nms:api"))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks.named("reobfJar") {
    enabled = false
}
