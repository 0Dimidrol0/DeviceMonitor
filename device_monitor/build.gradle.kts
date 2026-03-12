import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    signing
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("com.vanniktech.maven.publish") version "0.36.0"
}

group = "io.github.dimidrol"
version = System.getenv("GITHUB_REF_NAME")?.removePrefix("v") ?: "0.2.0"

android {
    namespace = "io.github.dimidrol"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

mavenPublishing {

    publishToMavenCentral()

    signAllPublications()

    coordinates(
        groupId = "io.github.0dimidrol0",
        artifactId = "DeviceMonitor",
        version = System.getenv("GITHUB_REF_NAME")?.removePrefix("v") ?: "0.2.0"
    )

    pom {
        name.set("Device Monitor")
        description.set("Lightweight Android device telemetry monitor")
        url.set("https://github.com/0Dimidrol0/DeviceMonitor")
        inceptionYear.set("2026")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("0Dimidrol0")
                name.set("Dimidrol")
                url.set("https://github.com/0Dimidrol0/")
            }
        }

        scm {
            url.set("https://github.com/0Dimidrol0/DeviceMonitor")
            connection.set("scm:git:git://github.com/0Dimidrol0/DeviceMonitor.git")
            developerConnection.set("scm:git:ssh://git@github.com:0Dimidrol0/DeviceMonitor.git")
        }
    }
}

signing {
    useGpgCmd()
}