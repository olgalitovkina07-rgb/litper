plugins {
    java
    application
}

group = "ru.litper"
version = "1.0.0"

repositories {
    mavenCentral()
}

val javafxVersion = "25.0.2"

val osName = System.getProperty("os.name").lowercase()
val javafxPlatform = when {
    osName.startsWith("windows") -> "win"
    osName.startsWith("linux") -> "linux"
    osName.startsWith("mac") -> if ("aarch64" in System.getProperty("os.arch").lowercase()) "mac-aarch64" else "mac"
    else -> throw GradleException("Неизвестная ОС: $osName")
}

dependencies {
    implementation("org.openjfx:javafx-controls:$javafxVersion")
    implementation("org.openjfx:javafx-base:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-controls:$javafxVersion:$javafxPlatform")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

application {
    mainClass = "ru.litper.Main"
}

tasks.test {
    useJUnitPlatform()
}