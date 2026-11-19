pluginManagement {
    repositories {
        // 国内直连更快，失败自动回落到官方源
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "ai-commit-message"
