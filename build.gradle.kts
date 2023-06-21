import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.text.SimpleDateFormat
import java.util.*

plugins {
    kotlin("jvm") version "1.8.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "xyz.irodev"
SimpleDateFormat("yyyy.MM.dd-HHmm").let {
    it.timeZone = TimeZone.getTimeZone("UTC")
    version = it.format(Calendar.getInstance().time)
}


repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val exposedVersion: String by project
dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.1.1")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.6")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.1.4")
    implementation("net.dv8tion:JDA:5.0.0-beta.11") {
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
            exclude(dependency("org.mariadb.jdbc:mariadb-java-client:3.1.4"))
        }
    }
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_11.toString()
        }
    }

    @Suppress("UnstableApiUsage")
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