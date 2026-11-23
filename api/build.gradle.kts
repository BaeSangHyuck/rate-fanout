import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
  kotlin("jvm") version "2.4.21" apply false
  kotlin("plugin.spring") version "2.4.21" apply false
  kotlin("plugin.jpa") version "2.4.21" apply false
  id("org.springframework.boot") version "4.2.0" apply false
}

subprojects {
  // domain, domain:quote 같은 묶음 폴더는 건너뛴다
  if (childProjects.isNotEmpty()) return@subprojects

  apply(plugin = "java-library")
  apply(plugin = "org.jetbrains.kotlin.jvm")

  group = "dev.sanghyuck"
  version = "0.0.1-SNAPSHOT"

  repositories {
    mavenCentral()
  }

  extensions.configure<JavaPluginExtension> {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
  }

  extensions.configure<KotlinJvmProjectExtension> {
    compilerOptions {
      freeCompilerArgs.addAll("-Xjsr305=strict", "-Xemit-jvm-type-annotations")
      javaParameters = true // @Query 파라미터를 이름으로 바인딩
    }
  }

  dependencies {
    // 버전은 스프링 부트 BOM 하나로 맞춘다
    "implementation"(platform(SpringBootPlugin.BOM_COORDINATES))
    "testImplementation"("org.jetbrains.kotlin:kotlin-test-junit5")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
  }

  tasks.withType<Test> {
    useJUnitPlatform()
  }
}
