import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.eclipse.jgit.api.Git
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    kotlin("jvm") version "2.0.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "xyz.irodev"
version = LocalDateTime.ofEpochSecond(
    Git.open(projectDir).log().setMaxCount(1).call().iterator().next().commitTime.toLong(),
    0,
    ZoneOffset.UTC
).format(DateTimeFormatter.ofPattern("yyyy.MM.dd-HHmm"))


buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.eclipse.jgit:org.eclipse.jgit:7.0.0.202409031743-r")
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    @Suppress("VulnerableLibrariesLocal", "RedundantSuppression")
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.24.2")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.1")
    implementation("net.dv8tion:JDA:5.2.1") {
        exclude(module = "opus-java")
    }
    implementation("org.jetbrains.exposed:exposed-core:0.56.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.59.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.56.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}


tasks {
    withType<ShadowJar> {
        dependencies {
            exclude(dependency("org.slf4j:slf4j-api"))
        }
    }

    withType<ProcessResources> {
        val props = "version" to version
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("velocity-plugin.json") {
            expand(props)
        }
    }

    build {
        dependsOn(shadowJar)
    }
}
