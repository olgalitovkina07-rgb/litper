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
    implementation("org.openjfx:javafx-base:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-controls:$javafxVersion:$javafxPlatform")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    modularity.inferModulePath.set(true)
}

application {
    mainClass = "ru.litper.Main"
    mainModule = "ru.litper"
    // Отключаем предупреждение о нативном доступе из модуля JavaFX.
    applicationDefaultJvmArgs = listOf("--enable-native-access=javafx.graphics")
}

tasks.test {
    useJUnitPlatform()
}

/**
 * Замеры для отчёта: собственное Trie против TreeMap из java.util.
 * Запуск: gradlew.bat benchmark
 */
tasks.register<JavaExec>("benchmark") {
    group = "verification"
    description = "Сравнение собственного префиксного дерева с аналогом из java.util"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "ru.litper.structure.TrieBenchmark"
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
}

/**
 * Демонстрация гонки потоков для отчёта.
 * Запуск: gradlew.bat raceDemo
 */
tasks.register<JavaExec>("raceDemo") {
    group = "verification"
    description = "Демонстрация гонки потоков без синхронизации и защиты в Trie"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "ru.litper.structure.RaceDemo"
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
}