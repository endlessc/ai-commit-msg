package com.lememo.aicommit.settings

/**
 * 常见 OpenAI 兼容服务商的默认地址与模型。用户可随时改成自定义 endpoint。
 */
enum class ProviderPreset(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val model: String,
) {
    DEEPSEEK("deepseek", "DeepSeek", "https://api.deepseek.com", "deepseek-chat"),
    SILICONFLOW(
        "siliconflow",
        "硅基流动",
        "https://api.siliconflow.cn/v1",
        "deepseek-ai/DeepSeek-V3.2",
    ),
    QWEN("qwen", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
    ZHIPU("zhipu", "智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-4-plus"),
    KIMI("kimi", "Kimi", "https://api.moonshot.cn/v1", "moonshot-v1-8k"),
    OPENAI("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    OLLAMA("ollama", "Ollama（本地）", "http://localhost:11434/v1", "qwen2.5-coder:7b"),
    CUSTOM("custom", "自定义", "", ""),
    ;

    companion object {
        fun byId(id: String): ProviderPreset = entries.firstOrNull { it.id == id } ?: CUSTOM
    }
}
