plugins {
    kotlin("jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

val localIdeaPath = providers.environmentVariable("IDEA_HOME")

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    implementation("org.apache.pdfbox:pdfbox:3.0.4")
    implementation("org.apache.poi:poi-ooxml:5.4.1")
    intellijPlatform {
        if (localIdeaPath.isPresent) {
            local(localIdeaPath.get())
        } else {
            create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
        }
    }
}

kotlin { jvmToolchain(21) }

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        ideaVersion { sinceBuild = "251" }
    }
}

tasks {
    register<JavaExec>("coreCheck") {
        dependsOn(testClasses)
        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("com.interviewstudy.core.CoreBehaviorCheck")
    }
    wrapper { gradleVersion = "8.13" }
}
