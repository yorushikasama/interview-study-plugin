# Interview Study

IntelliJ IDEA 2025.1.2 插件：根据岗位或 PDF、DOCX、Markdown、TXT 资料通过 OpenAI 兼容接口生成自动混合的选择题和问答题，并在 IDE 内练习、批改、追问和复习。

## 文档

- [完整使用说明](docs/使用说明.md)

## 安装包

```text
dist\interview-study-plugin-0.3.1.zip
```

在 IDEA 中进入 `Settings | Plugins | Install Plugin from Disk`，选择该 ZIP 并重启。

## 构建

```powershell
$env:IDEA_HOME='D:\IntelliJ IDEA 2025.1.2'
$env:JAVA_HOME="$env:IDEA_HOME\jbr"
.\gradlew.bat buildPlugin --offline
```

API 配置入口：打开右侧 `Interview Study` Tool Window，进入 `设置` 页签。API Key 存储在 JetBrains Password Safe。点击 `获取模型` 可从 OpenAI 兼容接口读取模型列表并回填模型名。
## 练习和复习

- `题库练习` 支持关键词、题型、标签、来源、今日复习、收藏、错题筛选。
- 每题显示复习状态和下次复习时间，答错自动回退，答对按间隔推进。
- 支持收藏、错题标记、JSON 导入导出、AI 批改回答和 AI 追问。

## 大资料处理

- 不超过 18,000 字：单次 AI 调用直接生成。
- 超过 18,000 字：按段落自动分块，每块最多 12,000 字；先逐块提炼知识卡，再汇总生成题目。
- 最多处理 20 个分块（约 24 万字）。开始前会显示预计 AI 调用次数并要求确认。

