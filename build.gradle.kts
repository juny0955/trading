import org.jooq.meta.jaxb.Logging
import org.jooq.meta.jaxb.Property
import org.jooq.meta.jaxb.ForcedType

plugins {
    java
    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jooq.jooq-codegen-gradle") version "3.20.8"
}

group = "dev.junyoung"
version = "0.0.1-SNAPSHOT"
description = "trading"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

val jooqGeneratedDir = layout.buildDirectory.dir("generated-src/jooq/main")

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-h2console")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    runtimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    jooqCodegen("com.h2database:h2")
    jooqCodegen("org.jooq:jooq-meta-extensions")
}

jooq {
    configuration {
        logging = Logging.WARN

        generator {
            database {
                name = "org.jooq.meta.extensions.ddl.DDLDatabase"
                inputSchema = "PUBLIC"

                properties.addAll(
                    listOf(
                        Property().withKey("scripts").withValue("src/main/resources/db/schema.sql"),
                        Property().withKey("sort").withValue("semantic"),
                        Property().withKey("unqualifiedSchema").withValue("none"),
                        Property().withKey("defaultNameCase").withValue("lower")
                    )
                )

                forcedTypes.add(
                    ForcedType()
                        .withName("INSTANT")
                        .withIncludeTypes("TIMESTAMP\\s+WITH\\s+TIME\\s+ZONE")
                )
            }

            target {
                packageName = "dev.junyoung.trading.jooq"
                directory = jooqGeneratedDir.get().asFile.absolutePath
            }
        }
    }
}

sourceSets {
    main {
        java.srcDir(jooqGeneratedDir)
    }
}


tasks.named("compileJava") {
    dependsOn("jooqCodegen")
}


tasks.withType<Test> {
    useJUnitPlatform()
}
