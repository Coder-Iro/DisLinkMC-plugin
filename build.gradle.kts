import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
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
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.0.1")
    implementation("redis.clients:jedis:4.0.0")
    implementation("com.j256.two-factor-auth:two-factor-auth:1.3")
    annotationProcessor("com.velocitypowered:velocity-api:3.0.1")
}

tasks {
    withType<ShadowJar> {
        listOf("com.j256.twofactorauth", "redis.clients.jedis").forEach { pattern ->
            relocate(pattern, "io.teamif.minecord.shaded.$pattern")
        }
    }
    build {
        finalizedBy(shadowJar)
    }
}
