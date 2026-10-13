# WebView / 浏览器兼容性实测报告

- **测试日期**：2026-10-13（v2 修正版，17:32 复测）
- **测试方式**：自建特性检测页，从 PC 起 HTTP 服务，用设备自带浏览器访问
- **测试页**：[`../tools/webview-test.html`](../tools/webview-test.html)（v1）、
  [`../tools/webview-test-v2.html`](../tools/webview-test-v2.html)（v2，**修正版，结论以 v2 为准**）
- **原始截图**：`../assets/webview-cap1.png` ~ `cap3.png`（v1）、`webview-v2-1.png` ~ `v2-2.png`（v2）

---

## 一、结论（TL;DR）

```
WebView 版本 = Chrome 37.0.0.0    (2014 年 8 月)
UA = Mozilla/5.0 (Linux; Android 5.1; XXB Build/LRX21M) AppleWebKit/537.36
     (KHTML, like Gecko) Version/4.0 Chrome/37.0.0.0 Mobile Safari/537.36

特性检测结果（v2）：22 / 53 PASS   (通过率 42%)
```

**一句话**：**ES6 语法基本不可用（但 `const` 是支持的），现代 CSS 布局特性大半缺失。**
用 WebView 承载 H5 界面时，**必须按 2014 年的浏览器标准写代码**（ES5 + 传统 CSS）。

> ⚠️ **v1 的错误已修正**：v1 用 `eval('let a=1; const b=2;')` 判断，
> 因为 `let` 报错就把 `const` 也判成 FAIL。
> 实际上 **`const` 是支持的**（Chrome 从 v6 就支持），只有 `let` 不支持。
> v2 改用 `new Function()` 做**纯语法解析测试**，绕开了 eval 作用域的干扰。

---

## 二、完整检测结果（52 项）

### 语法支持（v2 修正版，纯解析测试）

| 特性 | 结果 | 备注 |
|---|---|---|
| **`const`** | ✅ **PASS** | ⚠️ **v1 判错了**。Chrome 从 v6 就支持 |
| `let` | ❌ FAIL | ES6 块级作用域 `let` 尚未支持 |
| 箭头函数 `=>` | ❌ FAIL | |
| 模板字符串 `` `${x}` `` | ❌ FAIL | |
| `class` 语法 | ❌ FAIL | |
| 默认参数 `function(x=1)` | ❌ FAIL | |
| 解构赋值 `var {a}=obj` | ❌ FAIL | |
| 展开运算符 `...` | ❌ FAIL | |
| `generator` (`function*`) | ❌ FAIL | |
| `async` / `await` | ❌ FAIL | |
| `for...of` | ❌ FAIL | |
| Object 方法简写 `{a(){}}` | ❌ FAIL | |

> ⚠️ **这是最致命的一条**。`let`、`class`、箭头函数、`async/await` 写出来的 JS，
> 在这台设备上会直接**语法报错、整段脚本不执行**（不是降级，是白屏）。
> **必须用 Babel / TypeScript 编译到 ES5，并引入 polyfill。**

### ❌ 内建对象与方法 —— 14 项缺失

| 特性 | 结果 | 备注 |
|---|---|---|
| `Promise` | ✅ PASS | |
| `Promise.all` | ✅ PASS | |
| `Promise.finally` | ❌ FAIL | |
| `Symbol` | ❌ FAIL | |
| `Map` / `Set` | ❌ FAIL | ⚠️ 常用，必须 polyfill |
| `Proxy` | ❌ FAIL | |
| `BigInt` | ❌ FAIL | |
| `WeakMap` | ✅ PASS | |
| `Reflect` | ❌ FAIL | |
| `Object.assign` | ❌ FAIL | ⚠️ 高频使用 |
| `Object.entries` | ❌ FAIL | |
| `Object.values` | ❌ FAIL | |
| `Array.from` | ❌ FAIL | ⚠️ 高频使用 |
| `Array.includes` | ❌ FAIL | ⚠️ 高频使用 |
| `Array.find` | ❌ FAIL | |
| `String.includes` | ❌ FAIL | ⚠️ 高频使用 |
| `String.padStart` | ❌ FAIL | |
| `Number.isInteger` | ✅ PASS | |

### ❌ Web API

| 特性 | 结果 | 备注 |
|---|---|---|
| `fetch()` | ❌ FAIL | ⚠️ **必须改用 `XMLHttpRequest` 或引入 polyfill** |
| `XMLHttpRequest` | ✅ PASS | |
| `WebSocket` | ✅ PASS | |
| `localStorage` | ✅ PASS | |
| `sessionStorage` | ✅ PASS | |
| `URLSearchParams` | ❌ FAIL | |
| `TextEncoder` | ❌ FAIL | |
| `Intl` | ✅ PASS | |
| `Intl.DateTimeFormat` | ✅ PASS | |
| `requestAnimationFrame` | ✅ PASS | |
| `IntersectionObserver` | ❌ FAIL | |
| `ResizeObserver` | ❌ FAIL | |
| `MutationObserver` | ✅ PASS | |
| `JSON` | ✅ PASS | |
| `atob` / `btoa` | ✅ PASS | |
| **`crypto.subtle`** | ✅ **PASS** | v2 新增。WebCrypto 可用（意外收获） |

### ✅ Canvas 全部可用

| 特性 | 结果 |
|---|---|
| canvas 2d | ✅ PASS |
| canvas **webgl** | ✅ PASS |

> 图形能力是这台设备的亮点，WebGL 可用。

### ❌ CSS

| 特性 | 结果 | 备注 |
|---|---|---|
| `CSS.supports()` | ✅ PASS | |
| flexbox | ✅ PASS | ⚠️ **布局请用 flex，不要用 Grid** |
| **CSS Grid** | ❌ FAIL | ⚠️ 完全不可用 |
| **CSS 变量 `var(--x)`** | ❌ FAIL | ⚠️ 完全不可用 |
| `calc()` | ✅ PASS | |
| `transform` | ✅ PASS | |
| `position: sticky` | ❌ FAIL | |

---

## 二·5、TLS 版本实测（v2 新增，此前只是推断）

早期文档写「TLS 1.3 需要 Android 10+，这台不支持」——那是**推断，没实测**。
现在做了对照实验：

| 服务端配置 | 设备表现 | 结论 |
|---|---|---|
| **仅 TLS 1.2**（对照组） | 弹「安全警告：该网站的安全证书有问题」（自签证书），**握手成功** | ✅ TLS 1.2 可用 |
| **仅 TLS 1.3**（实验组） | 「找不到网页」+「**无法建立安全连接**」 | ❌ **TLS 1.3 不可用** |

测试方法：在本机（`192.168.191.105`）用 Python 起两个 HTTPS 服务，分别锁死 TLS 版本，
用设备自带浏览器访问，对比结果。

截图：[`../assets/tls12-device.png`](../assets/tls12-device.png)、
[`../assets/tls13-device.png`](../assets/tls13-device.png)

**对服务端的要求**：**必须保留 TLS 1.2 支持**，只开 TLS 1.3 的设备连不上。

---

## 三、给 H5 开发的硬性要求

如果 Kiosk 界面用 WebView 承载：

1. **JS 必须编译到 ES5**（Babel / TypeScript `target: es5`）
2. **必须引入 polyfill**：`core-js`（覆盖 Map/Set/Promise.finally/Object.assign/Array.from/
   Array.includes/String.includes 等）
3. **网络请求用 `XMLHttpRequest`**，不要用 `fetch`（或引 `whatwg-fetch` polyfill）
4. **CSS 用 flexbox + 传统布局**，禁用 Grid、CSS 变量、sticky
5. **不要用 `IntersectionObserver`** 做懒加载（可用 `scroll` 事件替代）
6. 框架选择：**Vue 2 或更早 / React 15 以下**可考虑；
   现代框架（Vue 3 / React 18）**默认产物跑不起来**，必须配置低版本 target + polyfill

### 更省事的替代方案

> **考虑不用 WebView，直接用原生 View 写界面。**
> 这台设备的 WebView 太老（Chrome 37），而原生 View 的性能和可控性都更好，
> 也不会遇到上面这些兼容问题。

---

## 四、一个重要澄清

**主流站点是能打开的。** 实测自带浏览器成功加载 `https://www.baidu.com`
（HTTPS + 锁图标正常），页面渲染完整。

原因：百度这类站点做了**服务端渲染 + 旧浏览器降级适配**，只用了 ES5 语法。
所以"WebView 老"**不等于"上不了网"**，而是**你自己写的现代前端代码跑不起来**。

**TLS 也没问题**：设备支持 TLS 1.2，能正常访问 HTTPS 站点。
（TLS 1.3 需要 Android 10+，这台不支持。）

---

## 五、复现方法

```bash
# 1. 在 PC 上启动 HTTP 服务（PC 需与设备同网段）
cd tools && python -m http.server 8000

# 2. 用设备浏览器访问
adb shell "am start -a android.intent.action.VIEW -d 'http://<PC_IP>:8000/webview-test.html'"

# 3. 截图查看结果
adb shell screencap -p /sdcard/wv.png && adb pull /sdcard/wv.png
```

> 注意：设备与 PC 必须在同一局域网。实测 PC `192.168.191.105`、设备 `192.168.191.104`，
> 设备 ping PC 延迟 1.8–23 ms。
