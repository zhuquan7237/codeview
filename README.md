# CodeView · 代码预览

把 AI 输出的代码粘进来，直接看渲染效果。

新建任意后缀的文件 → 粘贴代码 → 一键预览。专为「AI 只给代码，不给你文件」这个场景做的。

## 功能

- **新建文件 + 自定义后缀**：内置 html / svg / xml / json / css / js / md / kt / py / txt 快捷后缀，也可以手输任意后缀（vue、tsx、svelte…）。
- **导入已有文件**：从系统文件选择器一次导入多个文件（复制进 App 自己的目录，**不申请存储权限**）。单文件上限 4 MB，二进制文件会被拒绝。
- **分享到 CodeView**：在别的 App（比如 AI 对话）里点「分享 → CodeView」，文本和文件都能接住，自动建文件并直接打开预览。
- **粘贴即用（AI 场景）**：
  - AI 的回答通常带 ``` 围栏和一堆说明文字：粘贴时**自动只取代码块**（多个代码块取最长的那个；回答被截断、围栏没闭合也能救回半截代码）。
  - 后缀按围栏语言或内容自动判定，粘进 `.txt` 的 SVG 也会**按 SVG 渲染**。
  - 首页「剪贴板」按钮：一下就从剪贴板建文件并打开。
- **实时渲染预览**：
  - `.svg` / 内嵌 SVG 的 `.xml`：真正渲染成图形（WebView 里测量过几何尺寸，不是白屏）。
  - `.html`：按移动端视口渲染，自动补 `viewport`。
  - `.xml` / `.json`：自动格式化后再看结构（JSON 解析失败会提示「内容可能被截断」）。
  - 其它代码：语法高亮 + 行号。
  - 渲染结果是**实测**的：内容加载了但渲染区域是空的（标签残缺 / 被截断）会直接提示，页面里的 JS 报错也会显示出来，不用对着白屏猜。
  - 内容类型和扩展名不符时（比如 `.txt` 里其实是 SVG），顶部会提示「内容像 SVG · 已按 SVG 预览」，并提供「另存为 .svg」。
- **编辑体验**：语法高亮输入（只重绘改动的那几行）、撤销/重做、自动保存（没有保存按钮）、字号可调、长文件滚动流畅（见下）、编辑/预览切换不丢滚动位置。
- **文件管理**：搜索、重命名、复制副本、分享代码、删除。
- **网络**：只有 `INTERNET` 一个权限，用于预览依赖 CDN 的 HTML（Tailwind / ECharts / 字体）。没有账号、没有统计、没有任何后台上传。

## 性能

代码视图由平台 `TextView` / `EditText` 渲染，而不是 Compose 文本：Compose 会把整篇文档当成一个 paragraph，每帧重绘全部行；`android.text.Layout` 只画裁剪区里的行。

同一段滑动操作，`dumpsys gfxinfo` 实测（debug 版 + 模拟器，p50 为单帧耗时）：

| 场景 | 改前 p50 | 改后 p50 | 改前卡顿率 | 改后卡顿率 |
| --- | --- | --- | --- | --- |
| 编辑 3000 行 / 139 KB 的 `.kt` | 133 ms（≈7 fps） | **18 ms（≈55 fps）** | 91% | 47% |
| 预览 1002 行 `.json`（带高亮） | 18 ms | 18 ms | 36% | 32% |
| 预览 12604 行 / 199 KB `.json` | 29 ms | **17 ms** | 37% | — |

## 体积

release 版开启 R8 + 资源压缩，签名用 debug key（方便本地直接覆盖安装）。
不做任何多平台打包，依赖只有 Compose + 系统 WebView，渲染引擎用系统自带的，不占包体。约 1.1 MB。

## 兼容性

- minSdk 26（Android 8.0）
- targetSdk 35
- 渲染依赖系统 WebView（任何正常 Android 设备都有）

## 构建

```bash
export JAVA_HOME=<jdk17>
export ANDROID_HOME=<android-sdk>
./gradlew.bat :app:assembleRelease    # 产出 app/build/outputs/apk/release/app-release.apk
./gradlew.bat :app:testDebugUnitTest  # 单元测试
./gradlew.bat :app:connectedAndroidTest  # 真机/模拟器上的 WebView 渲染验证
```

## 测试

- 单元测试（51 个）覆盖：文件名校验/后缀合成、XML/JSON 格式化器、语法扫描器（token 不重叠、越界、未闭合）、撤销栈、文件仓库（含路径穿越防护、导入的体积/二进制/重名规则）、预览包装页规则、**AI 回答提取**（围栏、多代码块、未闭合围栏、内容嗅探、后缀推断）。
- 仪器测试覆盖：在真实 Android WebView 里测量 SVG 几何尺寸、注入 viewport 的 HTML 渲染、>700KB 的大文件走 `file://` 路径、剪贴板读写。
- 手工验证过：分享到 CodeView → 自动建文件并打开预览；文件选择器导入；`.txt` 里的 SVG 按 SVG 渲染 + 「另存为」；空渲染提示；长文件滚动帧率对比。

## 结构

```
app/src/main/java/com/zhuquan/codeview/
├── MainActivity.kt        入口 + ACTION_SEND（分享进来）
├── core/                  纯逻辑：文件类型、语法扫描、格式化、预览页包装、AI 代码提取、撤销栈
├── data/                  FileRepo：单目录文件存储；DocumentImport：从系统选择器导入；ShareInbox：分享中转
└── ui/                    Compose 界面 + 平台文本视图（Code.kt）、列表页、编辑页、预览页、新建面板
```
