plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.fling"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    // FastJSON 依赖
    implementation("com.alibaba:fastjson:1.2.83")
    implementation("org.mybatis:mybatis:3.5.3")
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation(files("libs/spring-fling_side_server-0.0.1.jar"))
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set("2023.2.6")
    type.set("IU") // Target IDE Platform

    plugins.set(listOf("com.intellij.java","Git4Idea","com.intellij.spring.boot","com.intellij.database"))
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("232")
        untilBuild.set("242.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
