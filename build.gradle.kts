import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.10"
    kotlin("kapt") version "1.6.10"
    id("com.github.johnrengelman.shadow") version "7.1.2"

}

group = "io.teamif"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "velocity"
        url = uri("https://nexus.velocitypowered.com/repository/maven-public/")
    }
    maven("https://repo.velocitypowered.com/snapshots/")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.0.1")
    implementation(kotlin("reflect"))
    implementation("io.lettuce:lettuce-core:6.1.6.RELEASE")
    implementation("com.j256.two-factor-auth:two-factor-auth:1.3")
    kapt("com.velocitypowered:velocity-api:3.0.1")
}

tasks {
    withType<ShadowJar> {
        listOf("com.j256.twofactorauth", "io.lettuce.core", "kotlin").forEach { pattern ->
            relocate(pattern, "io.teamif.minecord.shaded.$pattern")
        }
    }
    build {
        finalizedBy(shadowJar)
    }
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "16"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "16"
}
