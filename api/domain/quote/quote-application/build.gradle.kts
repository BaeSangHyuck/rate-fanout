plugins {
  kotlin("plugin.spring")
}

dependencies {
  api(project(":domain:quote:quote-domain"))
  implementation("org.springframework:spring-context")
  implementation("org.springframework:spring-webflux")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
}
