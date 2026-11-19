package com.lememo.aicommit.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe
import java.net.URI

/**
 * API Key 存系统钥匙串（macOS Keychain），配置文件里不落明文。
 * 按 endpoint 的 host 区分，切换服务商不会互相覆盖。
 */
object ApiKeyStore {

    private const val SERVICE = "com.lememo.aicommit"

    fun hostOf(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        return runCatching { URI.create(trimmed).host }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: trimmed
    }

    fun get(baseUrl: String): String? =
        PasswordSafe.instance.getPassword(attributes(hostOf(baseUrl)))

    fun set(baseUrl: String, key: String?) {
        val attributes = attributes(hostOf(baseUrl))
        val passwordSafe = PasswordSafe.instance
        if (key.isNullOrBlank()) {
            passwordSafe.setPassword(attributes, null)
        } else {
            passwordSafe.setPassword(attributes, key)
        }
    }

    private fun attributes(host: String) = CredentialAttributes("$SERVICE:$host")
}
