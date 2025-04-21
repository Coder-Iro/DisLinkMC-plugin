rootProject.name = "dislinkmc"

plugins {
    id("com.gradle.develocity") version ("4.0")
}

develocity {
    buildScan {
        termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
        termsOfUseAgree.set("yes")
        publishing.onlyIf { !System.getenv("CI").isNullOrEmpty() }
    }
}