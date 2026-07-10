plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("com.gradleup.shadow") version "9.4.3"
    id("maven-publish") // Ezt a sort kell hozzáadni
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven(url = "https://repo.extendedclip.com/releases/")
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.12.2")

    compileOnly("com.sk89q.worldedit:worldedit-core:7.3.0-SNAPSHOT")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.0-SNAPSHOT")

    implementation("com.zaxxer:HikariCP:6.0.0")
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    implementation("com.mysql:mysql-connector-j:9.0.0")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.4")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

group = "com.flarium"
version = "1.0"

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "com.flarium"
            artifactId = "flarium-api"
            version = "1.0"
        }
    }
}

tasks {
    runServer {
        minecraftVersion("1.21.8")
        jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        relocate("com.zaxxer.hikari", "com.flarium.libs.hikari")
        relocate("org.sqlite", "com.flarium.libs.sqlite")
        relocate("com.mysql", "com.flarium.libs.mysql")

        relocate("com.github.benmanes.caffeine", "com.flarium.libs.caffeine")

        archiveClassifier.set("")
    }

    build {
        dependsOn(shadowJar)
    }
}