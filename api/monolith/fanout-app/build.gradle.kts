plugins {
  kotlin("plugin.spring")
  id("org.springframework.boot")
}

dependencies {
  implementation(project(":domain:quote:quote-application"))
  implementation(project(":domain:order:order-application"))
  implementation(project(":domain:coupon:coupon-application"))

  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  implementation("org.springframework.boot:spring-boot-starter-kafka")
  implementation("org.redisson:redisson:4.8.0")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("tools.jackson.module:jackson-module-kotlin")
  runtimeOnly("com.h2database:h2")
  runtimeOnly("org.postgresql:postgresql")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("io.micrometer:micrometer-registry-prometheus")

  testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
  testImplementation("org.springframework.boot:spring-boot-starter-micrometer-metrics-test")
  testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
  testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testImplementation("org.testcontainers:testcontainers-postgresql")
  testImplementation("org.testcontainers:testcontainers-kafka")
}

tasks.jar { enabled = false } // bootJar 하나만 만든다
