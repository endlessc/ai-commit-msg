package com.lememo.aicommit.collect

import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * 通过向上查找 `.git`（目录或文件，后者对应 worktree/submodule）定位仓库根。
 * 结果按起始目录缓存，避免每个文件都走一遍文件系统。
 */
object GitRepoResolver {

    private val cache = ConcurrentHashMap<Path, Optional<Path>>()

    fun rootFor(file: Path): Path? {
        val absolute = file.toAbsolutePath().normalize()
        val start = if (Files.isDirectory(absolute)) absolute else absolute.parent ?: return null

        cache[start]?.let { return it.orElse(null) }

        var current: Path? = start
        var found: Path? = null
        while (current != null) {
            if (Files.exists(current.resolve(".git"))) {
                found = current
                break
            }
            current = current.parent
        }
        cache[start] = Optional.ofNullable(found)
        return found
    }

    fun relativePath(repoRoot: Path, file: Path): String =
        repoRoot.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/')
}
