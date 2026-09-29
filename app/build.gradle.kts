import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
  kotlin("plugin.compose")
  id("org.jetbrains.compose")
}

dependencies {
  implementation(project(":core"))
  implementation(compose.desktop.currentOs)
  implementation(compose.material3)
  implementation(compose.materialIconsExtended)
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
  implementation("org.xerial:sqlite-jdbc:3.46.1.3")
  implementation("org.json:json:20240303")
  implementation("net.sf.kxml:kxml2:2.3.0")
  testImplementation(kotlin("test-junit"))
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

compose.desktop {
  application {
    mainClass = "com.example.MainKt"
    nativeDistributions {
      targetFormats(TargetFormat.Msi, TargetFormat.Exe)
      packageName = "PcmrRebalance"
      packageVersion = "1.0.0"
    }
  }
}

// جایگزین BuildConfig اندروید: کلید FundBase از .env (اولویت) یا .env.example خوانده می‌شود.
val generateBuildConfig by tasks.registering {
  val envFiles = listOf(rootProject.file(".env"), rootProject.file(".env.example"))
  val outDir = layout.buildDirectory.dir("generated/buildconfig")
  inputs.files(envFiles.filter { it.exists() })
  outputs.dir(outDir)
  doLast {
    fun read(key: String): String = envFiles.filter { it.exists() }.firstNotNullOfOrNull { f ->
      f.readLines().map { it.trim() }.firstOrNull { it.startsWith("$key=") }?.substringAfter("=")?.trim()
    } ?: ""
    val file = outDir.get().file("com/example/BuildConfig.kt").asFile
    file.parentFile.mkdirs()
    file.writeText(
      "package com.example\n\nobject BuildConfig {\n" +
        "    const val FUNDBASE_API_KEY: String = \"${read("FUNDBASE_API_KEY")}\"\n" +
        "    const val VERSION_NAME: String = \"${project.version}\"\n}\n"
    )
  }
}
kotlin.sourceSets.main { kotlin.srcDir(generateBuildConfig) }
