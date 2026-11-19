import java.util.Properties

plugins {
    // 平台 262 的类使用 Kotlin 2.4 元数据，编译器必须 >= 2.4
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.20.1"
}

group = "com.lememo"
version = "0.1.0"

// 本地调试宿主：优先 -PideaLocalPath，其次 local.properties（不入库），最后用默认安装路径
val localIdePath: String = providers.gradleProperty("ideaLocalPath").orNull
    ?: layout.projectDirectory.file("local.properties").asFile
        .takeIf { it.exists() }
        ?.let { propsFile ->
            Properties().apply { propsFile.inputStream().use { load(it) } }
                .getProperty("ideaLocalPath")
        }
    ?: "/Applications/IntelliJ IDEA.app"

repositories {
    maven("https://maven.aliyun.com/repository/public")
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        local(localIdePath)
    }

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

intellijPlatform {
    pluginConfiguration {
        name = "AI Commit Message"
        ideaVersion {
            sinceBuild = "262"
        }
    }
}

