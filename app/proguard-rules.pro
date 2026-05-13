# ================================================================
# ppv4_onnx_ocr_server ProGuard 规则
# ================================================================

# -------------------- Android 基本 --------------------

-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod
-keepattributes Exceptions
-keepattributes Annotation

# -------------------- ONNX Runtime --------------------

# ONNX Runtime 必须保留的类和方法
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# -------------------- NanoHTTPD --------------------

-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# -------------------- org.json --------------------

# JSONObject/JSONArray 的字段通过字符串访问，不能混淆
-keep public class org.json.** { *; }
-keepclassmembers class org.json.** { *; }

# -------------------- AndroidX --------------------

-keep class androidx.** { *; }
-dontwarn androidx.**
-keep class * extends androidx.** { *; }

# -------------------- App 自身 --------------------

# OnnxOcrEngine 被 HttpOcrServer 通过反射/类名调用
-keep class com.ocr.pponnx.ocr.OnnxOcrEngine { *; }
-keep class com.ocr.pponnx.ocr.OcrResult { *; }
-keep class com.ocr.pponnx.ocr.OcrConfig { *; }
-keep class com.ocr.pponnx.ocr.DetPostProcess { *; }
-keep class com.ocr.pponnx.ocr.RecPostProcess { *; }
-keep class com.ocr.pponnx.ocr.ClsPostProcess { *; }
-keep class com.ocr.pponnx.ocr.OcrUtils { *; }
-keep class com.ocr.pponnx.ocr.det.RotatedBox { *; }
-keep class com.ocr.pponnx.ocr.det.GeometryUtils { *; }

# 枚举保留
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# -------------------- tess-two（未使用但保留以防编译报错） --------------------

-keep class com.googlecode.tesseract.** { *; }
-keep class com.googlecode.leptonica.** { *; }
-dontwarn com.googlecode.**
