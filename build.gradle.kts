@file:Suppress("VulnerableLibrariesLocal")

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.21"
    kotlin("kapt") version "1.8.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"

}

group = "xyz.irodev"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.1.1")
    implementation("io.lettuce:lettuce-core:6.2.0.RELEASE")
    kapt("com.velocitypowered:velocity-api:3.1.1")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
}

kotlin {
    jvmToolchain(11)
}

kapt {
    includeCompileClasspath = false
}

tasks {
    withType<ShadowJar> {
        isEnableRelocation = true
        relocationPrefix = "xyz.irodev.dislinkmc.shaded"
    }
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_11.toString()
        }
    }
    build {
        dependsOn(shadowJar)
    }
}