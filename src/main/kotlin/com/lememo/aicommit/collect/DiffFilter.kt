package com.lememo.aicommit.collect

/**
 * 决定哪些文件不需要把 diff 送进模型：二进制、锁文件、构建产物、IDE 元数据。
 * 这些文件仍会出现在「变更概览」的文件清单里，只是不展开内容。
 */
class DiffFilter(private val ignoreIdeaFiles: Boolean = true) {

    fun isIncluded(relativePath: String): Boolean = exclusionReason(relativePath) == null

    fun exclusionReason(relativePath: String): String? {
        val path = relativePath.replace('\\', '/')
        val name = path.substringAfterLast('/').lowercase()
        val ext = name.substringAfterLast('.', "")

        if (ext.isNotEmpty() && ext in BINARY_EXTENSIONS) return "二进制文件"
        if (name in LOCK_FILES) return "锁文件"
        if (GENERATED_SUFFIXES.any { name.endsWith(it) }) return "生成物"

        val segments = path.lowercase().split('/')
        if (segments.size > 1 && segments.dropLast(1).any { it in GENERATED_DIRS }) return "生成物"

        if (ignoreIdeaFiles && (path.startsWith(".idea/") || name.endsWith(".iml"))) return "IDE 元数据"

        return null
    }

    companion object {
        private val BINARY_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "gif", "bmp", "ico", "webp", "svgz", "tif", "tiff", "psd",
            "ttf", "otf", "woff", "woff2", "eot",
            "mp3", "wav", "flac", "ogg", "m4a", "aac",
            "mp4", "mov", "avi", "mkv", "webm", "flv",
            "zip", "gz", "tgz", "bz2", "xz", "7z", "rar", "jar", "war", "ear",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "class", "so", "dylib", "dll", "exe", "bin", "o", "a", "lib", "wasm",
            "db", "sqlite", "sqlite3", "p12", "jks", "keystore",
        )

        private val LOCK_FILES = setOf(
            "package-lock.json", "pnpm-lock.yaml", "yarn.lock", "npm-shrinkwrap.json",
            "poetry.lock", "pipfile.lock", "cargo.lock", "go.sum", "gemfile.lock",
            "composer.lock", "mix.lock", "podfile.lock", "pubspec.lock", "flake.lock",
            "packages.lock.json", "gradle.lockfile",
        )

        private val GENERATED_DIRS = setOf(
            "build", "buildservice", "dist", "out", "target", "node_modules", "vendor",
            ".gradle", ".next", ".nuxt", "__pycache__", "coverage", ".venv", "venv",
            "deriveddata", ".dart_tool", "pods",
        )

        private val GENERATED_SUFFIXES = listOf(
            ".min.js", ".min.css", ".map", ".pb.go", "_pb2.py", ".generated.kt",
            ".generated.java", ".designer.cs", ".g.dart", ".freezed.dart",
        )
    }
}
