import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.eclipse.jgit.api.Git
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    kotlin("jvm") version "1.9.10"
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
        classpath("org.eclipse.jgit:org.eclipse.jgit:6.7.0.202309050840-r")
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val exposedVersion: String by project
dependencies {
    @Suppress("VulnerableLibrariesLocal", "RedundantSuppression")
    compileOnly("com.velocitypowered:velocity-api:3.1.1")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.20.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.2.0")
    implementation("net.dv8tion:JDA:5.0.0-beta.15") {
        exclude(module = "opus-java")
    }
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
}

kotlin {
    jvmToolchain(11)
}

tasks {
    withType<ShadowJar> {
        dependencies {
            exclude(dependency("org.slf4j:slf4j-api"))
        }
        minimize {
            exclude(dependency("org.jetbrains.exposed:.*:$exposedVersion"))
            exclude(dependency("org.mariadb.jdbc:mariadb-java-client:3.2.0"))
        }
    }
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_11.toString()
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