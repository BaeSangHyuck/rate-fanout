plugins {
  kotlin("plugin.spring")
  kotlin("plugin.jpa")
}

dependencies {
  api(project(":domain:coupon:coupon-domain"))
  implementation(project(":libs:common"))
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.redisson:redisson:4.8.0")
}

allOpen {
  annotation("jakarta.persistence.Entity")
  annotation("jakarta.persistence.MappedSuperclass")
}
