plugins {
    java
    id("org.springframework.boot") version "4.0.3" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "com.pricealert"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    dependencies {
        "compileOnly"("org.projectlombok:lombok:1.18.38")
        "annotationProcessor"("org.projectlombok:lombok:1.18.38")
        "testCompileOnly"("org.projectlombok:lombok:1.18.38")
        "testAnnotationProcessor"("org.projectlombok:lombok:1.18.38")
        "testImplementation"(platform("org.testcontainers:testcontainers-bom:1.21.3"))
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("--enable-preview"))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        jvmArgs("--enable-preview")
    }

    tasks.withType<JavaExec> {
        jvmArgs("--enable-preview")
    }
}
