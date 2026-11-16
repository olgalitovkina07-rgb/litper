plugins {
    java
    application
    id("org.springframework.boot") version "3.5.16"
}

group = "ru.litper"
version = "1.0.0"

repositories {
    mavenCentral()
}

val javafxVersion = "25.0.2"
val springBootVersion = "3.5.16"

val osName = System.getProperty("os.name").lowercase()
val javafxPlatform = when {
    osName.startsWith("windows") -> "win"
    osName.startsWith("linux") -> "linux"
    osName.startsWith("mac") -> if ("aarch64" in System.getProperty("os.arch").lowercase()) "mac-aarch64" else "mac"
    else -> throw GradleException("Неизвестная ОС: $osName")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))

    implementation("org.openjfx:javafx-base:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-controls:$javafxVersion:$javafxPlatform")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    modularity.inferModulePath.set(false)
}

application {
    mainClass = "ru.litper.Main"
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

springBoot {
    mainClass = "ru.litper.Main"
}

tasks.test {
    useJUnitPlatform()
}

/**
 * Только REST-сервер, без JavaFX — удобно проверять API через curl.
 * Запуск: gradlew.bat serverRun
 */
tasks.register<JavaExec>("serverRun") {
    group = "application"
    description = "Запускает Spring Boot-сервер без JavaFX"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "ru.litper.server.ServerApp"
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

/**
 * Большой тестовый CSV для ручной проверки приложения.
 * Запуск: gradlew.bat sampleCsv [количество] [путь]
 */
tasks.register<JavaExec>("sampleCsv") {
    group = "verification"
    description = "Создаёт data/contacts-large.csv для проверки загрузки и визуализации"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "ru.litper.persistence.SampleDataWriter"
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
}

/**
 * Сравнение форматов сериализации для отчёта (лаб. 3): JSON против бинарного.
 * Запуск: gradlew.bat serializationBench [-Pcount=50000]
 */
tasks.register<JavaExec>("serializationBench") {
    group = "verification"
    description = "Размер файлов и время сохранения/загрузки JSON и бинарного формата"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "ru.litper.persistence.serialize.SerializationBench"
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
    (findProperty("count") as String?)?.let { args = listOf(it) }
}
