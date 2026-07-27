plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.10"
    id("org.jetbrains.intellij.platform") version "2.3.0"
}

group = "com.sum.grok"
version = "0.1.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.2.5")
        bundledPlugin("org.jetbrains.plugins.terminal")
        // Required for :signPlugin (Marketplace)
        zipSigner()
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    instrumentCode = false
    pluginConfiguration {
        id = "com.sum.grok.jetbrains"
        name = "Grok Build"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "242"
            untilBuild = "261.*"
        }
        vendor {
            name = "lineryforjs"
            email = "lineryforjs@gmail.com"
            url = "https://github.com/nightsumx/grok-jetbrains"
        }
    }

    // Marketplace signing — load from env or ./signing/
    signing {
        val chainFile = layout.projectDirectory.file("signing/chain.crt")
        val keyFile = layout.projectDirectory.file("signing/private.pem")
        certificateChain.set(
            providers.environmentVariable("CERTIFICATE_CHAIN")
                .orElse(providers.fileContents(chainFile).asText),
        )
        privateKey.set(
            providers.environmentVariable("PRIVATE_KEY")
                .orElse(providers.fileContents(keyFile).asText),
        )
        password.set(
            providers.environmentVariable("PRIVATE_KEY_PASSWORD").orElse(""),
        )
    }

    publishing {
        token.set(
            providers.environmentVariable("PUBLISH_TOKEN")
                .orElse(providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN")),
        )
        // channels = listOf("default") // or "eap"
    }
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
    wrapper {
        gradleVersion = "8.12.1"
    }
    // Faster local builds — searchable options optional for publish
    named("buildSearchableOptions") {
        enabled = providers.gradleProperty("withSearchableOptions")
            .map { it == "true" }
            .orElse(false)
            .get()
    }
}
