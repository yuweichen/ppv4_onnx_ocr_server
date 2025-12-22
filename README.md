
# ppv4_onnx_ocr_server
* onnxruntime
* pp-ocrv4模型
* NanoHTTPD


## 接口基本信息

* **接口地址**：`http://127.0.0.1:8080/ocr`
* **请求方法**：`POST`
* **请求头**：

  * `Content-Type: application/json`

---

## 请求信息

### 请求体格式（JSON）

```json
{
  "image": "string"
}
```

### 参数说明

| 参数名   | 类型     | 必填 | 描述                                                 | 示例                            |
| ----- | ------ | -- | -------------------------------------------------- | ----------------------------- |
| image | string | 是  | Base64 编码的图像数据，不包含头部信息（如 `data:image/png;base64,`） | `iVBORw0KGgoAAAANSUhEUgAA...` |

---

## 响应信息

### 成功响应

* **HTTP 状态码**：`200 OK`
* **响应头**：

  * `Content-Type: application/json`

#### 响应体格式（JSON）

```json
{
  "code": 200,
  "data": [
    {
      "text": "string",
      "score": 0.95
    }
  ]
}
```

#### 响应字段说明

| 字段名        | 类型     | 描述              |
| ---------- | ------ | --------------- |
| code       | number | 业务状态码，200 表示成功  |
| data       | array  | OCR 识别结果列表      |
| data.text  | string | 识别出的文本内容        |
| data.score | number | 识别结果的置信度分数（0-1） |

---

### 错误响应

* **HTTP 状态码**：`500 Internal Server Error`
* **响应头**：

  * `Content-Type: application/json`

#### 响应体格式（JSON）

```json
{
  "code": 500,
  "message": "错误描述信息"
}
```

#### 响应字段说明

| 字段名     | 类型     | 描述                  |
| ------- | ------ | ------------------- |
| code    | number | 业务错误码，500 表示服务器内部错误 |
| message | string | 错误信息描述              |

---

## 使用限制

* 图像大小建议不超过 **1MB**
* Base64 编码需为**标准格式**，且**不包含换行符**
