# CodeView · 代码预览

把 AI 输出的代码粘进来，直接看渲染效果。

新建任意后缀的文件 → 粘贴代码 → 一键预览。专为「AI 只给代码，不给你文件」这个场景做的。

## 功能

- **新建文件 + 自定义后缀**：内置 html / svg / xml / json / css / js / md / kt / py / txt 快捷后缀，也可以手输任意后缀（vue、tsx、svelte…）。
- **代码输入与粘贴**：新建时可直接取用剪贴板内容，编辑器里也有「粘贴」按钮，长文一次性贴进去不卡。
- **实时渲染预览**：
  - `.svg` / 内嵌 SVG 的 `.xml`：真正渲染成图形（WebView 里测量过几何尺寸，不是白屏）。
  - `.html`：按移动端视口渲染，自动补 `viewport`。
  - `.xml`：自动格式化缩进后再看结构。
  - `.json`：自动格式化。
  - 其它代码：语法高亮 + 行号显示。
- **编辑体验**：语法高亮输入、撤销/重做、自动保存（没有保存按钮）、字号可调、行数/字符数状态栏。
- **文件管理**：搜索、重命名、复制副本、分享代码、删除。

## 体积

release 版开启 R8 + 资源压缩，签名用 debug key（方便本地直接覆盖安装）。
不做任何多平台打包，依赖只有 Compose + 系统 WebView，渲染引擎用系统自带的，不占包体。

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

- 单元测试覆盖：文件名校验/后缀合成、XML/JSON 格式化器、语法扫描器（token 不重叠、越界、未闭合）、撤销栈、文件仓库（含路径穿越防护）、预览包装页规则。
- 仪器测试覆盖：在真实 Android WebView 里测量 SVG 几何尺寸、注入 viewport 的 HTML 渲染、>700KB 的大文件走 `file://` 路径、以及「列表 → 打开 → 预览 → 编辑」全流程。

## 结构

```
app/src/main/java/com/zhuquan/codeview/
├── MainActivity.kt
├── core/          纯逻辑：文件类型、语法扫描、格式化、预览页包装、撤销栈
├── data/          FileRepo：单目录文件存储
└── ui/            Compose 界面：主题、列表页、编辑页、预览页、新建面板
```
