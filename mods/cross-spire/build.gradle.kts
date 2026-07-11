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

val desktopJar = files("${System.getProperty("user.home")}/steamapps/common/SlayTheSpire/desktop-1.0.jar")
val stsModsDir = file("${System.getProperty("user.home")}/SlayTheAmethystModded/app/src/main/assets/components/mods")

dependencies {
    compileOnly(desktopJar)
    compileOnly(files("$stsModsDir/BaseMod.jar"))
    compileOnly(files("$stsModsDir/ModTheSpire.jar"))
    compileOnly(files("$stsModsDir/StSLib.jar"))
    implementation("org.java-websocket:Java-WebSocket:1.5.3")
}

tasks.jar {
    archiveFileName = "CrossSpire.jar"
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
