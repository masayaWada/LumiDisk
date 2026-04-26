plugins {
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.example.diskanalyzer"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // 永続化はキャッシュ用 JSON のみで運用しており SQLite は未使用のため
    // sqlite-jdbc は依存から外した (将来必要になったら再導入)。
    implementation("ch.qos.logback:logback-classic:1.5.6")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml")
}

application {
    mainClass.set("com.example.diskanalyzer.MainApp")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.test {
    useJUnitPlatform()
}

// installDist で集めた jar 群を入力に jpackage で OS ネイティブインストーラを生成する。
// jpackage はクロスコンパイル不可: macOS で dmg、Windows で exe をそれぞれ実行する必要がある。
// 非モジュラ JavaFX のため main-class には Launcher (Application を継承しない) を指定する。
tasks.register<Exec>("jpackage") {
    group = "distribution"
    description = "OS ネイティブインストーラを生成 (macOS=dmg / Windows=exe / Linux=deb)"
    dependsOn("installDist")

    val installDir = layout.buildDirectory.dir("install/${project.name}").get().asFile
    val outputDir = layout.buildDirectory.dir("jpackage").get().asFile
    val os = org.gradle.internal.os.OperatingSystem.current()
    val mainJar = "${project.name}-${project.version}.jar"

    val installerType = when {
        os.isWindows -> "exe"
        os.isMacOsX -> "dmg"
        os.isLinux -> "deb"
        else -> throw GradleException("jpackage はこの OS をサポートしていません: ${os.name}")
    }

    val osSpecificArgs = buildList {
        when {
            os.isWindows -> addAll(
                listOf(
                    "--win-dir-chooser",
                    "--win-menu",
                    "--win-shortcut",
                    "--win-per-user-install"
                )
            )
            os.isMacOsX -> addAll(
                listOf(
                    "--mac-package-name", "LumiDisk"
                )
            )
        }
    }

    doFirst {
        outputDir.mkdirs()
        outputDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    val toolchain = javaToolchains.launcherFor(java.toolchain).get()
    val jpackageBinary = toolchain.metadata.installationPath.file("bin/jpackage").asFile

    commandLine = listOf(
        jpackageBinary.absolutePath,
        "--type", installerType,
        "--name", "LumiDisk",
        "--app-version", project.version.toString(),
        "--vendor", "LumiDisk",
        "--input", installDir.resolve("lib").absolutePath,
        "--main-jar", mainJar,
        "--main-class", "com.example.diskanalyzer.Launcher",
        "--dest", outputDir.absolutePath
    ) + osSpecificArgs
}
