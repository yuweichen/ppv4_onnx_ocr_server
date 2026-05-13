# ppv4_onnx_ocr_server

基于 PaddleOCR PP-OCRv4 模型的 Android 端 OCR HTTP 服务，通过 ONNX Runtime 在本地运行推理，使用 NanoHTTPD 提供 HTTP API，支持返回文字坐标用于自动化点击。

## 接口信息

| 项目 | 说明 |
|------|------|
| 地址 | `http://127.0.0.1:8080/ocr` |
| 方法 | `POST` |
| Content-Type | `application/json` |

---

## 请求参数

```json
{
  "image": "base64字符串（必填）",
  "offset": {
    "x": 0,
    "y": 0
  }
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| image | string | 是 | Base64 编码的图片，不含 `data:image/png;base64,` 头部 |
| offset | object | 否 | 截图在屏幕中的偏移坐标 |
| offset.x | number | 否 | 截图左上角在屏幕中的 X 像素坐标，默认 0 |
| offset.y | number | 否 | 截图左上角在屏幕中的 Y 像素坐标，默认 0 |

> **关于 offset**：传入 offset 后返回的坐标为屏幕绝对坐标（可直接用于 click），不传则返回相对于截图的坐标。

---

## 响应格式

### 成功响应

```json
{
  "code": 200,
  "times": 150,
  "data": [
    {
      "text": "开始游戏",
      "score": 0.97,
      "centerX": 330,
      "centerY": 520,
      "box": [
        { "x": 280, "y": 498 },
        { "x": 380, "y": 498 },
        { "x": 380, "y": 542 },
        { "x": 280, "y": 542 }
      ]
    },
    {
      "text": "设置",
      "score": 0.95,
      "centerX": 450,
      "centerY": 620,
      "box": [
        { "x": 420, "y": 600 },
        { "x": 480, "y": 600 },
        { "x": 480, "y": 640 },
        { "x": 420, "y": 640 }
      ]
    }
  ]
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| code | number | 状态码，200 表示成功 |
| times | number | OCR 处理耗时（毫秒） |
| data | array | 识别结果列表 |
| data[].text | string | 识别出的文字内容 |
| data[].score | number | 置信度分数（0-1） |
| data[].centerX | number | 文字区域中心点 X 坐标 |
| data[].centerY | number | 文字区域中心点 Y 坐标 |
| data[].box | array | 文字区域 4 个角点，顺序：左上→右上→右下→左下 |
| data[].box[].x | number | 角点 X 坐标 |
| data[].box[].y | number | 角点 Y 坐标 |

> **坐标说明**：传入 `offset` 时为屏幕绝对坐标，未传时为相对截图的坐标。

### 错误响应

```json
{
  "code": 500,
  "message": "错误描述信息"
}
```

---

## 使用示例

### 自动精灵（JavaScript）

自动精灵截图返回的完整数据结构：

```json
{
  "left": 264,
  "top": 498,
  "right": 402,
  "bottom": 541,
  "left_dp": 176,
  "top_dp": 332,
  "right_dp": 268,
  "bottom_dp": 360,
  "left_100": 48.889,
  "top_100": 51.875,
  "right_100": 74.444,
  "bottom_100": 56.354,
  "area": "48.8% 51.9% 74.4% 56.4%"
}
```

| 字段 | 说明 |
|------|------|
| left / top / right / bottom | 截图区域在屏幕中的像素坐标 |
| left_dp 等 | 对应的 dp 值 |
| left_100 等 | 相对于屏幕尺寸的百分比（0-100） |
| area | 百分比格式的边界框，"左% 上% 右% 下%" |

x:left y:top 作为 offset

#### 示例：区域截图识别并返回坐标

```javascript
// 截图区域（以屏幕坐标）
const left = 264;
const top = 498;
const right = 402;
const bottom = 541;

// 截取区域图片并转 Base64
const screen = captureScreen();
const bitmap = images.clip(screen, left, top, right - left, bottom - top);
const base64 = images.toBase64(bitmap, "PNG", 100);

// 调用 OCR，传入 left/top 作为 offset，返回屏幕绝对坐标
const response = http({
    url: "http://127.0.0.1:8080/ocr",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
        image: base64,
        offset: {
            x: left,
            y: top
        }
    })
});

const result = JSON.parse(response.bodyString());
if (result.code === 200) {
    result.data.forEach(item => {
        log(JSON.stringify(item));
    });
}
```

### cURL 测试

```bash
# 不传 offset（返回相对坐标）
curl -X POST http://127.0.0.1:8080/ocr \
  -H "Content-Type: application/json" \
  -d '{"image": "iVBORw0KGgoAAAANSUhEUgAA..."}'

# 传入 offset（返回屏幕绝对坐标）
curl -X POST http://127.0.0.1:8080/ocr \
  -H "Content-Type: application/json" \
  -d '{"image": "iVBORw0KGgoAAAANSUhEUgAA...", "offset": {"x": 264, "y": 498}}'
```

---

## JS 工具库（Node.js 环境）

适用于在 Node.js 端配合自动化框架（Appium、ADB 等）使用。

```javascript
const { toAbsolute, clickByCenter, longPressByBox, findByText, sortByY } = require('./ocr-utils.js');
```

### toAbsolute(results, offset)

将 OCR 返回的相对坐标转换为屏幕绝对坐标。

```javascript
const { toAbsolute } = require('./ocr-utils.js');

// OCR 返回的相对坐标
const ocrResults = [
  {
    text: "开始游戏",
    score: 0.97,
    centerX: 66,
    centerY: 22,
    box: [{ x: 16, y: 10 }, { x: 116, y: 10 }, { x: 116, y: 34 }, { x: 16, y: 34 }]
  }
];

// 加上截图偏移
const absolute = toAbsolute(ocrResults, { x: 264, y: 498 });

console.log(absolute[0].centerX); // 330  ← 66 + 264
console.log(absolute[0].centerY); // 520  ← 22 + 498
```

### clickByCenter(center, method, options)

根据文字中心坐标执行点击。

```javascript
const { clickByCenter } = require('./ocr-utils.js');

// method='adb'（默认）
await clickByCenter({ x: 330, y: 520 }, 'adb');
// 等价于: adb shell input tap 330 520

// 指定设备序列号
await clickByCenter({ x: 330, y: 520 }, 'adb', { serial: 'emulator-5554' });

// method='appium'
await clickByCenter({ x: 330, y: 520 }, 'appium', { driver: webDriver });

// method='autojs'（在 AutoJS 环境中）
clickByCenter({ x: 330, y: 520 }, 'autojs');
```

### longPressByBox(box, ms, method, options)

在文字区域执行长按（取区域中心点）。

```javascript
const { longPressByBox } = require('./ocr-utils.js');

// 在文字区域长按 500ms
const box = [
  { x: 280, y: 498 }, { x: 380, y: 498 },
  { x: 380, y: 542 }, { x: 280, y: 542 }
];
await longPressByBox(box, 500, 'adb');
// 等价于: adb shell input swipe 330 520 330 520 500
```

### findByText(results, text, exact)

查找包含指定文字的 OCR 结果。

```javascript
const { findByText } = require('./ocr-utils.js');

// 模糊匹配（包含即返回）
const found = findByText(ocrResults, "开始");
// found = [{ text: "开始游戏", centerX: 330, ... }]

// 精确匹配
const exact = findByText(ocrResults, "开始游戏", true);
```

### sortByY(results)

按 Y 坐标从上到下排序（用于按行处理文字）。

```javascript
const { sortByY } = require('./ocr-utils.js');

const sorted = sortByY(ocrResults);
// 从屏幕上方到下方依次排列
```

### debugDrawBoxes(results, color, lineWidth)

生成盒子的绘图日志，用于调试可视化。

```javascript
const { debugDrawBoxes } = require('./ocr-utils.js');

const logs = debugDrawBoxes(ocrResults);
logs.forEach(log => console.log(log));
// 输出：
// #0 "开始游戏" 框: [280,498] -> [380,498] -> [380,542] -> [280,542]
```

---

## 使用限制

- 图像大小建议不超过 **1MB**
- Base64 编码需为 **标准格式**，不包含 `data:image/png;base64,` 头部
- 坐标精度受检测框膨胀（padding）影响，中心点通常偏差在 **5px 以内**
