import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.google.protobuf.gradle.id

plugins {
  kotlin("jvm") version "1.9.0"
  id("com.google.protobuf") version "0.9.4"
  id("com.github.johnrengelman.shadow") version "8.1.1"
  `java-library`
  `maven-publish`
  id("org.jetbrains.dokka") version "1.9.20"
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

  protobuf(
    files(
      "discord-bots-rpc/image.proto",
      "discord-bots-rpc/is-alive.proto",
      "discord-bots-rpc/ocr-request.proto"
    )
  )

  testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
  testImplementation("io.mockk:mockk:1.13.12")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
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
}.apply task@{ tasks.named("jar") { dependsOn(this@task) } }

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

fun Project.resolveVersion(): String {
  val cli = findProperty("releaseVersion") as String?
  if (!cli.isNullOrBlank()) return cli

  val tagPattern = Regex("^v\\d+\\.\\d+\\.\\d+$")
  val envTag = sequenceOf(
    System.getenv("GIT_TAG"),
    System.getenv("GITHUB_REF_NAME")
  ).firstOrNull { it != null && tagPattern.matches(it) }

  if (envTag != null) return envTag.removePrefix("v")

  return "0.0.0-SNAPSHOT"
}

val resolvedVersion = project.resolveVersion()

allprojects {
  group = "com.github.fvlaenix"
  version = resolvedVersion
}

java {
  withSourcesJar()
}

val dokkaJavadoc = tasks.named("dokkaJavadoc")
val dokkaJavadocJar = tasks.register<Jar>("dokkaJavadocJar") {
  dependsOn(dokkaJavadoc)
  archiveClassifier.set("javadoc")
  from(dokkaJavadoc.map { it.outputs.files })
}

publishing {
  repositories {
    maven {
      name = "nexus"
      url = uri("https://maven.fvlaenix.com/repository/maven-releases/")
      credentials {
        username = System.getenv("NEXUS_USERNAME")
        password = System.getenv("NEXUS_PASSWORD")
      }
    }
  }

  publications {
    create<MavenPublication>("maven") {
      from(components["java"])
      artifact(dokkaJavadocJar.get())
      artifactId = "ocr-image-to-text"
    }
  }
}
