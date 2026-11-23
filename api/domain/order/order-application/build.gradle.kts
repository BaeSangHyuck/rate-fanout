plugins {
  kotlin("plugin.spring")
  kotlin("plugin.jpa")
}

dependencies {
  api(project(":domain:order:order-domain"))
  implementation(project(":libs:common"))
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.kafka:spring-kafka")
}

allOpen {
  annotation("jakarta.persistence.Entity")
  annotation("jakarta.persistence.MappedSuperclass")
}
