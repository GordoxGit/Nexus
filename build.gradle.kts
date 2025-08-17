plugins {
    id("java")
    // Mise à jour de la version du plugin shadowJar pour la compatibilité avec Gradle 9.0
    id("com.github.johnrengelman.shadow") version "8.1.7"
}

group = "fr.gordox"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    // Spigot API for Minecraft 1.21
    compileOnly("org.spigotmc:spigot-api:1.21-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    shadowJar {
        archiveBaseName.set(rootProject.name)
        archiveClassifier.set("") 
        
        // On conserve ces règles qui sont de bonnes pratiques
        mergeServiceFiles()
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
    build {
        dependsOn(shadowJar)
    }
}
