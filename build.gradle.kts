plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = "com.testkit"
version = "1.1209"

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
    implementation(files("jar/testkit-trace/target/testkit-trace-1.0.jar"))
    implementation(files("jar/testkit-starter/target/testkit-starter-1.0.jar"))
    implementation(files("jar/testkit-agent/target/testkit-agent-1.0.jar"))
    implementation(files("jar/testkit-cli/target/testkit-cli-1.0.jar"))
    implementation("mysql:mysql-connector-java:8.0.33")
    implementation("com.github.jsqlparser:jsqlparser:4.6")
    implementation("dev.langchain4j:langchain4j-mcp:1.7.1-beta14")
    intellijPlatform{
        create("IC","2025.1")
//        "Git4Idea","com.intellij.spring.boot","com.intellij.database"
//        https://github.com/linyimin0812/spring-startup-analyzer/releases/download/v3.1.4/spring-startup-analyzer.tar.gz
        bundledPlugins(listOf("com.intellij.java","org.intellij.groovy","com.intellij.modules.json"))
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
intellijPlatform{
    pluginConfiguration{
        ideaVersion {
            sinceBuild = "231.*"
            untilBuild = "273.*"
        }
    }
}

tasks.register<Exec>("buildMavenDependencies") {
    description = "执行Maven命令构建依赖项"

    // 设置工作目录为jar目录
    workingDir("${rootDir}/jar")

    // 根据操作系统选择适当的命令
    if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
        commandLine("cmd", "/c", "mvn clean package")
    } else {
        commandLine("sh", "-c", "mvn clean package")
    }

    // 输出命令执行结果
    doLast {
        println("Maven构建完成")
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
        sinceBuild.set("231.*")
        untilBuild.set("273.*")
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

tasks.named("runIde") {
    dependsOn("buildMavenDependencies")
}

tasks.named("buildPlugin") {
    dependsOn("buildMavenDependencies")
}
