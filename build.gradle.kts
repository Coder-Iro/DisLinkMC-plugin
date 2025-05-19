import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.eclipse.jgit.api.Git
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    kotlin("jvm") version "2.1.20"
    id("com.gradleup.shadow") version "9.0.0-beta12"
}

group = "xyz.irodev"
version = LocalDateTime.ofEpochSecond(
    Git.open(projectDir).log().setMaxCount(1).call().iterator().next().commitTime.toLong(),
    0,
    ZoneOffset.of("+9")
).format(DateTimeFormatter.ofPattern("yyyy.MM.dd-HHmm"))


buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.eclipse.jgit:org.eclipse.jgit:7.2.0.202503040940-r")
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    @Suppress("VulnerableLibrariesLocal", "RedundantSuppression")
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.24.3")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.3")
    implementation("net.dv8tion:JDA:5.5.1") {
        exclude(module = "opus-java")
    }
    implementation("org.jetbrains.exposed:exposed-core:1.0.0-beta-1")
    implementation("org.jetbrains.exposed:exposed-dao:1.0.0-beta-1")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.0.0-beta-1")
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
