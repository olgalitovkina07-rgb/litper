// Общего кода здесь нет: сборки живут в litper-server и litper-client.
// Файл нужен, чтобы IntelliJ IDEA мог синхронизировать корневую папку.
tasks.register("buildAll") {
    group = "build"
    description = "Собирает оба проекта"
    dependsOn(gradle.includedBuild("litper-server").task(":build"))
    dependsOn(gradle.includedBuild("litper-client").task(":build"))
}
