import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.google.protobuf.gradle.id

plugins {
    kotlin("jvm") version "1.9.0"
    id("com.google.protobuf") version "0.9.4"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

group = "com.fvlaenix"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.grpc:grpc-kotlin-stub:1.4.0")
    implementation("com.google.protobuf:protobuf-java:3.16.3")
    implementation("com.google.protobuf:protobuf-kotlin:3.24.4")
    implementation("com.google.cloud:google-cloud-vision:3.26.0")
    runtimeOnly("io.grpc:grpc-netty-shaded:1.59.0")
    implementation("io.grpc:grpc-protobuf:1.59.0")
    implementation("io.grpc:grpc-stub:1.59.0")
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")
    implementation("com.fasterxml.jackson.core:jackson-core:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")

    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.slf4j:slf4j-simple:2.0.9")
    
    protobuf(files("discord-bots-rpc/image.proto","discord-bots-rpc/is-alive.proto", "discord-bots-rpc/ocr-request.proto"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

task<JavaExec>("runServer") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("RunServerKt")
}

fun createJarTaskByJavaExec(name: String) = tasks.create<ShadowJar>("${name}Jar") {
    mergeServiceFiles()
    group = "shadow"
    description = "Run server $name"
    
    from(sourceSets.main.get().output)
    from(project.configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
    configurations = listOf(project.configurations.runtimeClasspath.get())
    
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("${name}.jar")
    manifest {
        attributes["Main-Class"] = (tasks.findByName(name) as JavaExec).mainClass.get()
    }
}.apply task@ { tasks.named("jar") { dependsOn(this@task) } }

createJarTaskByJavaExec("runServer")

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.24.4"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.59.0"
        }
        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.0:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("grpc")
                id("grpckt")
            }
            it.builtins {
                create("kotlin")
            }
        }
    }
}
