plugins {
    id("java-library")
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
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.12.2")

    compileOnly("com.sk89q.worldedit:worldedit-core:7.3.0-SNAPSHOT")
    compileOnly("org.xerial:sqlite-jdbc:3.47.1.0")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.0-SNAPSHOT")

    implementation("com.zaxxer:HikariCP:6.0.0")
    implementation("com.mysql:mysql-connector-j:9.0.0")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.4")

    implementation(project(":nms:api"))
    implementation(project(":nms:shared"))
    implementation(project(":nms:v1_21_4"))
    implementation(project(":nms:v1_21_5"))
    implementation(project(":nms:v1_21_6"))
    implementation(project(":nms:v1_21_7"))
    implementation(project(":nms:v1_21_8"))
    implementation(project(":nms:v1_21_9"))
    implementation(project(":nms:v1_21_10"))
    implementation(project(":nms:v1_21_11"))
    implementation(project(":nms:v26_1_1"))
    implementation(project(":nms:v26_1_2"))
    implementation(project(":nms:v26_2"))
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
    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        relocate("com.zaxxer.hikari", "com.flarium.libs.hikari")
        relocate("com.mysql", "com.flarium.libs.mysql")

        relocate("com.github.benmanes.caffeine", "com.flarium.libs.caffeine")

        archiveClassifier.set("")
    }

    build {
        dependsOn(shadowJar)
    }
}
