plugins {
    java
    id("org.springframework.boot") version "3.5.16"
}

group = "ru.litper"
version = "1.0.0"

repositories {
    mavenCentral()
}

val springBootVersion = "3.5.16"

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    modularity.inferModulePath.set(false)
}

springBoot {
    mainClass = "ru.litper.server.ServerApp"
}

tasks.test {
    useJUnitPlatform()
}

/**
 * Сервер без GUI для работы с отдельным клиентом (лаб. 6).
 * Запуск: gradlew.bat serverRun
 */
tasks.register<JavaExec>("serverRun") {
    group = "application"
    description = "Запуск Spring Boot-сервера без GUI"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "ru.litper.server.ServerApp"
}

/**
 * Сравнение собственного Trie против TreeMap из java.util.
 * Запуск: gradlew.bat benchmark
 */
tasks.register<JavaExec>("benchmark") {
    group = "verification"
    description = "Сравнение собственного префиксного дерева с аналогами из java.util"
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
    description = "Демонстрация гонки потоков на синхронизации в рамках Trie"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "ru.litper.structure.RaceDemo"
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
}

/**
 * Генератор тестового CSV для демонстрации приложения.
 * Запуск: gradlew.bat sampleCsv [количество] [сид]
 */
tasks.register<JavaExec>("sampleCsv") {
    group = "verification"
    description = "Создаёт data/contacts-large.csv для проверки загрузки в структуру"
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
    description = "Оценка файлов и времени сохранения/загрузки JSON и бинарного формата"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "ru.litper.persistence.serialize.SerializationBench"
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
    (findProperty("count") as String?)?.let { args = listOf(it) }
}
