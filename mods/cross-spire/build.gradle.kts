plugins {
    java
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenCentral()
}

fun requiredJar(propertyName: String): File {
    val path = providers.gradleProperty(propertyName).orNull
        ?: throw GradleException("Missing -P$propertyName=/absolute/path/to/file.jar")
    val jar = file(path)
    if (!jar.isFile) {
        throw GradleException("-P$propertyName does not point to a file: ${jar.absolutePath}")
    }
    return jar
}

val stsJar = requiredJar("stsJar")
val baseModJar = requiredJar("baseModJar")
val modTheSpireJar = requiredJar("modTheSpireJar")

dependencies {
    compileOnly(files(stsJar, baseModJar, modTheSpireJar))
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("junit:junit:4.13.2")
}

tasks.jar {
    archiveFileName = "CrossSpire.jar"
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.test {
    testLogging {
        events("passed", "failed", "skipped")
    }
}
