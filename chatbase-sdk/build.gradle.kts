plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.maven.publish)
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

android {
    namespace = "com.chatbase.sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "SDK_VERSION", "\"$version\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
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
        description.set("Official Chatbase SDK for Android")
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

// E2E tests are run via: ./gradlew :chatbase-sdk:testDebugUnitTest --tests "com.chatbase.sdk.e2e.*"
// Unit tests exclude e2e by default
tasks.withType<Test> {
    filter {
        excludeTestsMatching("com.chatbase.sdk.e2e.*")
        isFailOnNoMatchingTests = false
    }
}
