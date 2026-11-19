package com.lememo.aicommit.collect

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiffFilterTest {

    private val filter = DiffFilter(ignoreIdeaFiles = true)

    @Test
    fun `keeps普通源文件`() {
        assertTrue(filter.isIncluded("src/main/kotlin/com/demo/Foo.kt"))
        assertTrue(filter.isIncluded("README.md"))
        assertTrue(filter.isIncluded("build.gradle.kts"))
    }

    @Test
    fun `drops二进制与图片`() {
        assertFalse(filter.isIncluded("assets/logo.png"))
        assertFalse(filter.isIncluded("docs/manual.pdf"))
        assertEquals("二进制文件", filter.exclusionReason("assets/logo.png"))
    }

    @Test
    fun `drops锁文件`() {
        assertFalse(filter.isIncluded("package-lock.json"))
        assertFalse(filter.isIncluded("gradle.lockfile"))
        assertEquals("锁文件", filter.exclusionReason("pnpm-lock.yaml"))
    }

    @Test
    fun `drops生成物`() {
        assertFalse(filter.isIncluded("dist/bundle.js"))
        assertFalse(filter.isIncluded("app/build/outputs/apk/app.apk"))
        assertFalse(filter.isIncluded("src/gen/Schema.generated.kt"))
        assertFalse(filter.isIncluded("public/app.min.js"))
    }

    @Test
    fun `dropsIDE元数据`() {
        assertFalse(filter.isIncluded(".idea/workspace.xml"))
        assertFalse(filter.isIncluded("demo.iml"))
        assertEquals("IDE 元数据", filter.exclusionReason(".idea/misc.xml"))
    }

    @Test
    fun `IDE元数据可保留`() {
        val keepAll = DiffFilter(ignoreIdeaFiles = false)
        assertTrue(keepAll.isIncluded(".idea/workspace.xml"))
        assertNull(keepAll.exclusionReason(".idea/workspace.xml"))
        assertNotNull(filter.exclusionReason(".idea/workspace.xml"))
    }

    @Test
    fun `路径分隔符归一化`() {
        assertFalse(filter.isIncluded(".idea\\workspace.xml"))
    }
}
