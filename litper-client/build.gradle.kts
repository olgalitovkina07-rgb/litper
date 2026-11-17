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
val jacksonVersion = "2.21.4"

val osName = System.getProperty("os.name").lowercase()
val javafxPlatform = when {
    osName.startsWith("windows") -> "win"
    osName.startsWith("linux") -> "linux"
    osName.startsWith("mac") -> if ("aarch64" in System.getProperty("os.arch").lowercase()) "mac-aarch64" else "mac"
    else -> throw GradleException("Неподдерживаемая ОС: $osName")
}

dependencies {
    implementation("org.openjfx:javafx-base:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-controls:$javafxVersion:$javafxPlatform")

    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    modularity.inferModulePath.set(false)
}

application {
    mainClass = "ru.litper.client.ClientMain"
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("clientRun") {
    group = "application"
    description = "Запуск REST-клиента (лаб. 6). Требует запущенный сервер"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "ru.litper.client.ClientMain"
    jvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}
