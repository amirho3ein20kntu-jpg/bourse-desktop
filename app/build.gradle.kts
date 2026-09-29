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
