plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.2.0"
}

group = "com.fling"
version = "1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // FastJSON 依赖
    implementation("com.alibaba:fastjson:1.2.83")
    implementation("org.mybatis:mybatis:3.5.6")
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")
    implementation(files("libs/spring-fling_link-0.0.1.jar"))
    implementation(files("libs/spring-fling_side_server-0.0.1.jar"))
    implementation(files("libs/spring-fling_agent-0.0.1.jar"))
    implementation("mysql:mysql-connector-java:8.0.33")
    intellijPlatform{
        create("IU","2023.2")
//        "Git4Idea"
        bundledPlugins(listOf("com.intellij.java","com.intellij.spring.boot","com.intellij.database","org.intellij.groovy"))
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
intellijPlatform{
    pluginConfiguration{
        ideaVersion {
            sinceBuild = "231"
            untilBuild = "273.*"
        }
    }
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
        untilBuild.set("253.*")
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
