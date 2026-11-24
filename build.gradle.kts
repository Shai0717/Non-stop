import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.20"
}

group = "io.github.Shai0717"
version = "1.1.0"

base {
    archivesName.set("Non-stop")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.github.Shai0717:Kolor:1.0.0")
    compileOnly("io.papermc.paper:paper-api:26.3.build.254-stable")
}

kotlin {
    jvmToolchain(25)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
}

val modelTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
}

kotlin {
    sourceSets.named("modelTest") {
        kotlin.srcDir("src/modelTest/kotlin")
    }
}

tasks.register<JavaExec>("modelTest") {
    dependsOn(tasks.named("modelTestClasses"))
    classpath = modelTest.runtimeClasspath
    mainClass.set("io.github.Shai0717.stopreset.MovementTest")
}

tasks.check {
    dependsOn("modelTest")
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("paper-plugin.yml") {
        expand("version" to project.version)
    }
}
