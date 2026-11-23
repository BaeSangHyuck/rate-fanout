dependencies {
  api("org.slf4j:slf4j-api")
  api("org.jetbrains.kotlinx:kotlinx-coroutines-core")

  // AssignedIdEntity용. JPA를 쓰는 모듈만 런타임에 들고 온다
  compileOnly("jakarta.persistence:jakarta.persistence-api")
  compileOnly("org.springframework.data:spring-data-commons")
}
