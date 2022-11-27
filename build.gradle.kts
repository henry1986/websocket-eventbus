import org.daiv.dependency.Versions

buildscript {
    repositories {
        maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
        maven("https://artifactory.daiv.org/artifactory/gradle-dev-local")
    }
    dependencies {
        classpath("org.daiv.dependency:DependencyHandling:0.2.37")
    }
}

plugins {
    kotlin("multiplatform") version "1.6.10"
    kotlin("plugin.serialization") version "1.6.10"
    id("com.jfrog.artifactory") version "4.17.2"
    id("org.daiv.dependency.VersionsPlugin") version "0.1.3"
    `maven-publish`
}

val versions = org.daiv.dependency.DefaultDependencyBuilder(Versions.current())

group = "org.daiv.websocket"
version = versions.setVersion { eventbus }

repositories {
    mavenCentral()
    maven("https://artifactory.daiv.org/artifactory/gradle-dev-local")
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") }
}

kotlin.sourceSets.all {
    languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
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

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(versions.kutil())
                implementation(versions.coroutines())
                implementation(versions.coroutines_lib())
                implementation(versions.serialization())
                implementation(versions.serialization_json())
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")

            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(versions.ktor("websockets"))
                implementation(versions.gson())
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(versions.ktor("server-netty"))
                implementation(versions.ktor("client-cio"))
                implementation(versions.ktor("server-websockets"))
                implementation(versions.mockk())
                implementation(kotlin("test-junit"))
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

versionPlugin {
    versionPluginBuilder = Versions.versionPluginBuilder {
        versionMember = { eventbus }
        resetVersion = { copy(eventbus = it) }
    }
    setDepending(tasks)
}

