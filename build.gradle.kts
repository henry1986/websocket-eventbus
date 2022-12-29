import org.daiv.dependency.Versions

buildscript {
    repositories {
        maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
        mavenCentral()
    }
    dependencies {
        classpath("org.daiv.dependency:DependencyHandling:0.1.41")
    }
}

plugins {
    kotlin("multiplatform") version "1.6.10"
    kotlin("plugin.serialization") version "1.6.10"
    id("org.daiv.dependency.VersionsPlugin") version "0.1.4"
    id("signing")
    `maven-publish`
}

val versions = org.daiv.dependency.DefaultDependencyBuilder(Versions.current())

group = "org.daiv.websocket"
version = versions.setVersion { eventbus }

repositories {
    mavenCentral()
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
val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

signing {
    sign(publishing.publications)
}

publishing {
    publications.withType<MavenPublication> {
        artifact(javadocJar.get())
        pom {
            packaging = "jar"
            name.set("websocket-eventbus")
            description.set("a library for interprocess communication via websocket. Also useable via browser")
            url.set("https://github.com/henry1986/websocket-eventbus")
            licenses {
                license {
                    name.set("The Apache Software License, Version 2.0")
                    url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            issueManagement {
                system.set("Github")
                url.set("https://github.com/henry1986/websocket-eventbus/issues")
            }
            scm {
                connection.set("scm:git:https://github.com/henry1986/websocket-eventbus.git")
                developerConnection.set("scm:git:https://github.com/henry1986/websocket-eventbus.git")
                url.set("https://github.com/henry1986/kutil")
            }
            developers {
                developer {
                    id.set("henry86")
                    name.set("Martin Heinrich")
                    email.set("martin.heinrich.dresden@gmx.de")
                }
            }
        }
    }
    repositories {
        maven {
            name = "sonatypeRepository"
            val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            credentials(PasswordCredentials::class)
        }
    }
}

versionPlugin {
    versionPluginBuilder = Versions.versionPluginBuilder {
        versionMember = { eventbus }
        resetVersion = { copy(eventbus = it) }
        publishTaskName = "publish"
    }
    setDepending(tasks, "publish")
}

