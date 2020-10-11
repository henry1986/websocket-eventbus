//val coroutines_version = "1.3.9"
//val kutil_version = "0.3.0"
//val ktor_version = "1.4.0"
//
//val serialization_version = "1.0.0-RC"

plugins {
    kotlin("multiplatform") version "1.4.10"
    kotlin("plugin.serialization") version "1.4.10"
    id("org.daiv.dependency") version ("0.0.6")
    id("com.jfrog.artifactory") version "4.17.2"
    `maven-publish`
}

group = "org.daiv.websocket"
version = "0.5.2"

repositories {
    mavenCentral()
    maven("https://dl.bintray.com/kotlin/kotlinx")
    maven("https://daiv.org/artifactory/gradle-dev-local")
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
    }
    js {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                    webpackConfig.cssSupport.enabled = true
                }
            }
        }
    }
//    val hostOs = System.getProperty("os.name")
//    val isMingwX64 = hostOs.startsWith("Windows")
//    val nativeTarget = when {
//        hostOs == "Mac OS X" -> macosX64("native")
//        hostOs == "Linux" -> linuxX64("native")
//        isMingwX64 -> mingwX64("native")
//        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
//    }
    val versions = org.daiv.dependency.Versions.versions1_4_0

    sourceSets {
        val commonMain by getting {
            versions.deps(this){
                kutil()
                coroutines()
                serialization()
            }
//            dependencies {
//                implementation("org.daiv.util:kutil:$kutil_version")
//                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines_version")
//                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$serialization_version")
//            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            versions.deps(this){
                ktor("websockets")
                gson()
            }
//            dependencies {
////                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$serialization_version")
////                api("org.daiv.util:kutil-jvm:$kutil_version")
//                implementation("io.ktor:ktor-websockets:$ktor_version")
//                implementation("com.google.code.gson:gson:2.8.5")
//
//            }
        }
        val jvmTest by getting {
            versions.deps(this){
                mockk()
            }
            dependencies {
                implementation(kotlin("test-junit"))
//                api("io.mockk:mockk:1.9.2")
            }
        }
        val jsMain by getting
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
//        val nativeMain by getting
//        val nativeTest by getting
    }
}

artifactory {
    setContextUrl("${project.findProperty("daiv_contextUrl")}")
    publish(delegateClosureOf<org.jfrog.gradle.plugin.artifactory.dsl.PublisherConfig> {
        repository(delegateClosureOf<groovy.lang.GroovyObject> {
            setProperty("repoKey", "gradle-dev-local")
            setProperty("username", project.findProperty("daiv_user"))
            setProperty("password", project.findProperty("daiv_password"))
            setProperty("maven", true)
        })
        defaults(delegateClosureOf<groovy.lang.GroovyObject> {
            invokeMethod("publications", arrayOf("jvm", "js", "kotlinMultiplatform", "metadata", "linuxX64"))
            setProperty("publishPom", true)
            setProperty("publishArtifacts", true)
        })
    })
}
