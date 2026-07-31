plugins {
    id("java-library")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("26.2.build.82-beta")
    implementation(project(":nms:api"))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(26)
}

// The 26.x dev bundle's metadata demands a JVM 25+ consumer, so claim it for resolution.
// The compiled classes are still emitted as Java 21 bytecode below: JVM 25+ runs them fine,
// and older servers' plugin remappers (ASM) can still read every class in the final jar.
listOf("compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath").forEach { configurationName ->
    configurations.named(configurationName) {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

// 26.2 moved the EntityType display constants to net.minecraft.world.entity.EntityTypes,
// so this module ships its own full DisplayAdapterImpl instead of the shared source.
tasks {
    named("reobfJar") {
        enabled = false
    }
}
