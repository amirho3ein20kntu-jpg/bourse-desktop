plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
}
dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
  testImplementation(kotlin("test-junit"))
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
  testImplementation("org.json:json:20240303")
}
