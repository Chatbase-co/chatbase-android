plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.maven.publish)
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates("com.chatbase", "chatbase-sdk", version.toString())

    pom {
        name.set("Chatbase SDK")
        description.set("Official Chatbase SDK for JVM and Android")
        url.set("https://github.com/Chatbase-co/chatbase-android")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("chatbase-co")
                name.set("Chatbase")
                url.set("https://github.com/Chatbase-co")
            }
        }

        scm {
            url.set("https://github.com/Chatbase-co/chatbase-android")
            connection.set("scm:git:git://github.com/Chatbase-co/chatbase-android.git")
            developerConnection.set("scm:git:ssh://git@github.com/Chatbase-co/chatbase-android.git")
        }
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.register<Test>("e2eTest") {
    description = "Runs E2E tests against the real Chatbase API"
    group = "verification"
    useJUnit()
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter { includeTestsMatching("com.chatbase.sdk.e2e.*") }
    environment("CHATBASE_API_KEY", System.getenv("CHATBASE_API_KEY") ?: "")
    environment("CHATBASE_AGENT_ID", System.getenv("CHATBASE_AGENT_ID") ?: "")
}

tasks.test {
    filter {
        excludeTestsMatching("com.chatbase.sdk.e2e.*")
        isFailOnNoMatchingTests = false
    }
}
