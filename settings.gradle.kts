rootProject.name = "dislinkmc"

plugins {
    id("com.gradle.enterprise") version ("3.13.4")
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}