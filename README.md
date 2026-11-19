# AI Commit Message

IntelliJ IDEA 插件：读取提交面板中**勾选的变更**，调用大模型生成 Git 提交信息，一键写入提交框。

## 环境要求

- IntelliJ IDEA 2026.2（`since-build = 262`）
- Git 可执行文件在 `PATH` 中
- 任意 OpenAI 兼容的模型服务：DeepSeek、硅基流动、通义千问、智谱 GLM、Kimi、OpenAI，或本地 Ollama

## 安装

```bash
./gradlew buildPlugin
```

产物在 `build/distributions/ai-commit-message-0.1.0.zip`。

`Settings → Plugins → ⚙ → Install Plugin from Disk…` 选择该 zip，重启 IDE。

## 配置

`Settings → Tools → AI Commit Message`：

| 项 | 说明 |
|---|---|
| 服务商 | 选择预设会自动填好 Base URL 与模型，仍可手改 |
| Base URL | 不带 `/chat/completions` 也可以，插件会自行拼接 |
| API Key | 存入系统钥匙串，不写进 XML 配置 |
| 输出语言 | 中文 / English |
| 提交信息格式 | Conventional Commits / 纯文本标题 |
| 写入方式 | 覆盖输入框 / 仅当输入框为空时写入 |
| 流式输出 | 默认开启：边生成边写入输入框；关闭则等整段返回后一次写入 |
| 测试连接 | 用当前表单值发一次极短请求，验证地址、Key、模型 |

本地模型示例：Base URL 填 `http://localhost:11434/v1`，模型填 `qwen2.5-coder:7b`，Key 留空。

推理模型（如硅基流动的 `deepseek-ai/DeepSeek-V3.2`）会先输出思考过程，`max_tokens` 建议给到
2048 以上，否则预算会被思考吃光、正文为空。「测试连接」只验证地址、鉴权与模型是否存在，
不会因为正文为空而判定失败。

## 使用

1. 打开提交面板，勾选要提交的文件。
2. 点提交信息框下方工具条里的 ✨ 按钮（在「历史」图标右侧）。
3. 生成中按钮变为「停止生成」，再次点击即取消；流式模式下**已输出的部分会保留**，可 `⌘Z` 撤销。
4. 生成完成后结果直接写入输入框，`⌘Z` 可撤销。

也可以 `Settings → Keymap` 给 `AI Commit Message` 动作绑一个快捷键。

## 隐私

- 只有**被勾选文件**的 diff 文本、文件清单、分支名，以及（可关闭的）最近 30 条提交标题会发送到你配置的 endpoint。
- 不发送整仓内容，不发送 `.env` 等被 Git 忽略的文件。
- 使用本地 Ollama 时数据不出本机。
- 插件不执行任何写 Git 的操作，只读取 diff。

## 开发

```bash
./gradlew compileKotlin   # 编译
./gradlew test            # 纯逻辑单测
./gradlew buildPlugin     # 打包 zip
./gradlew runIde          # 起沙箱 IDE 调试（宿主为 gradle.properties 中的 ideaLocalPath）
```

注意：IntelliJ Platform Gradle Plugin 2.20.1 要求 **Gradle 9.0+**，且平台 262 的类使用
**Kotlin 2.4 元数据**，因此编译器版本不能低于 2.4，工程里固定为 2.4.21。

### 在 PyCharm 里调试

宿主 IDE 由 `ideaLocalPath` 决定，默认 `/Applications/IntelliJ IDEA.app`。想用 PyCharm 调试，
在项目根目录建一个 `local.properties`（已被 `.gitignore` 忽略，不会随仓库公开）：

```properties
ideaLocalPath=/Applications/PyCharm.app
```

也可以通过 `-PideaLocalPath=...` 传入。两端平台版本都是 262，VCS 相关 jar 一致，换宿主不影响编译。

**直接运行**：用 PyCharm 打开本工程，等 Gradle 同步完成 → Gradle 工具窗 →
`ai-commit-message → Tasks → intellij platform → runIde`，会拉起一个装好插件的 PyCharm 沙箱。

**断点调试**：

1. 终端执行 `./gradlew runIde --debug-jvm`，JVM 在 5005 端口等调试器，沙箱窗口暂时不出现。
2. PyCharm → `Run → Edit Configurations… → + → Remote JVM Debug`，Host `localhost`，Port `5005`，点 Debug。
3. 附加成功后沙箱继续启动，插件代码里的断点即可命中；改完代码重新执行第 1 步。

工程结构：

```
src/main/kotlin/com/lememo/aicommit/
├── action/     按钮与生成任务的生命周期
├── collect/    勾选变更采集、git diff、过滤与预算裁剪
├── generate/   编排与输出清洗
├── llm/        OpenAI 兼容客户端与错误映射
├── notify/     气泡通知
├── prompt/     prompt 模板与组装
├── settings/   持久化配置、钥匙串、服务商预设
└── ui/         设置页
```

关键挂载点：动作组 `Vcs.MessageActionGroup`；变更来源 `VcsDataKeys.COMMIT_WORKFLOW_UI.getIncludedChanges()`；写回 `VcsDataKeys.COMMIT_MESSAGE_DOCUMENT`。

## 已知限制

- 仅支持 Git 仓库。
- 推理模型（如 `deepseek-ai/DeepSeek-V3.2`）会先思考再输出，首个字可能要等数秒（实测约 8.7s）；
  追求首字速度可以换成同平台的轻量/非推理模型。
- 参考最近提交的措辞时，多仓库项目只取第一个仓库的历史。
- 目标平台为 2026.2，`CommitWorkflowUi` 等接口属于平台 vcs 实现包，升级 IDE 后需要重新验证。
