// Корень — агрегатор для IDE. Клиент и сервер остаются независимыми
// Gradle-проектами: у каждого свой settings.gradle.kts, build.gradle.kts и Wrapper.
rootProject.name = "litper"

includeBuild("litper-server")
includeBuild("litper-client")
