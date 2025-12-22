package com.ocr.pponnx.ocr;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Paint;

import com.ocr.pponnx.ocr.det.RotatedBox;

import java.nio.FloatBuffer;
import java.util.List;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtSession;

public class OcrUtils {

    /**
     * 将旋转矩形裁剪为 Bitmap
     */
    public static Bitmap cropRotatedBox(Bitmap src, RotatedBox box) {
        PointF[] poly = box.toPolygon();
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = 0, maxY = 0;
        for (PointF p : poly) {
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
        }

        int width = (int) (maxX - minX);
        int height = (int) (maxY - minY);
        Bitmap cropped = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(cropped);
        Matrix matrix = new Matrix();
        matrix.postTranslate(-minX, -minY);
        canvas.drawBitmap(src, matrix, null);
        return cropped;
    }

    /**
     * 根据 Cls 结果旋转 Bitmap
     */
    public static Bitmap rotateBitmap(Bitmap bmp, ClsPostProcess.TextDirection dir) {
        Matrix matrix = new Matrix();
        if (dir == ClsPostProcess.TextDirection.ROTATE_180) {
            matrix.postRotate(180);
        } else if (dir == ClsPostProcess.TextDirection.HORIZONTAL) {
            matrix.postRotate(0);
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
    }

    /**
     * 将二维 float 数组展平为一维
     */
    public static float[] flatten(float[][] array) {
        int h = array.length;
        int w = array[0].length;
        float[] flat = new float[h * w];
        for (int i = 0; i < h; i++) {
            System.arraycopy(array[i], 0, flat, i * w, w);
        }
        return flat;
    }

    /**
     * 预处理 Bitmap → float[][] (Cls/Rec)
     * 这里可以实现 resize + normalize
     */
    public static float[][] preprocess(Bitmap bmp, int targetW, int targetH) {
        Bitmap resized = Bitmap.createScaledBitmap(bmp, targetW, targetH, true);
        float[][] data = new float[targetH][targetW];
        int[] pixels = new int[targetW * targetH];
        resized.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH);
        for (int y = 0; y < targetH; y++) {
            for (int x = 0; x < targetW; x++) {
                int c = pixels[y * targetW + x];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                // 简单归一化到 [0,1]
                data[y][x] = (r + g + b) / 3f / 255f;
            }
        }
        return data;
    }

    /**
     * 从 Rec 模型输出解码为文本
     * 需要结合字符集 keys
     */
    public static String decodeRecOutput(float[][] recOutput, java.util.List<String> keys) {
        StringBuilder sb = new StringBuilder();
        for (int t = 0; t < recOutput.length; t++) {
            float maxVal = -Float.MAX_VALUE;
            int maxIdx = 0;
            for (int i = 0; i < recOutput[t].length; i++) {
                if (recOutput[t][i] > maxVal) {
                    maxVal = recOutput[t][i];
                    maxIdx = i;
                }
            }
            if (maxIdx < keys.size()) sb.append(keys.get(maxIdx));
        }
        return sb.toString();
    }

    /**
     * 计算 RotatedBox 的中心点
     */
    public static PointF getBoxCenter(PointF[] poly) {
        float cx = 0, cy = 0;
        for (PointF p : poly) {
            cx += p.x;
            cy += p.y;
        }
        return new PointF(cx / poly.length, cy / poly.length);
    }

    /**
     * RotatedBox 宽度（approx）
     */
    public static float getBoxWidth(PointF[] poly) {
        float minX = Float.MAX_VALUE, maxX = 0;
        for (PointF p : poly) {
            minX = Math.min(minX, p.x);
            maxX = Math.max(maxX, p.x);
        }
        return maxX - minX;
    }

    /**
     * RotatedBox 高度（approx）
     */
    public static float getBoxHeight(PointF[] poly) {
        float minY = Float.MAX_VALUE, maxY = 0;
        for (PointF p : poly) {
            minY = Math.min(minY, p.y);
            maxY = Math.max(maxY, p.y);
        }
        return maxY - minY;
    }

    /**
     * RotatedBox 角度占位（如果需要可以改为真正旋转角度）
     */
    public static float getBoxAngle(PointF[] poly) {
        return 0f; // 简化
    }

    /**
     * 标准 PaddleOCR 风格裁剪
     * polygon → 拉正 → 水平文字图
     */
    public static Bitmap cropPolygonToBitmap(Bitmap src, PointF[] poly) {
        if (poly == null || poly.length != 4) return null;

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = 0, maxY = 0;

        for (PointF p : poly) {
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
        }

        int x = Math.max((int) minX, 0);
        int y = Math.max((int) minY, 0);
        int w = Math.min((int) (maxX - minX), src.getWidth() - x);
        int h = Math.min((int) (maxY - minY), src.getHeight() - y);

        if (w <= 0 || h <= 0) return null;

        return Bitmap.createBitmap(src, x, y, w, h);
    }

    //    public static Bitmap cropPolygonToBitmap(Bitmap src, PointF[] poly) {
//        if (poly == null || poly.length != 4) return null;
//
//        // 1. 计算目标宽高
//        float widthTop = distance(poly[0], poly[1]);
//        float widthBottom = distance(poly[3], poly[2]);
//        float heightLeft = distance(poly[0], poly[3]);
//        float heightRight = distance(poly[1], poly[2]);
//
//        int dstWidth = Math.round(Math.max(widthTop, widthBottom));
//        int dstHeight = Math.round(Math.max(heightLeft, heightRight));
//
//        if (dstWidth <= 0 || dstHeight <= 0) return null;
//
//        // 2. 目标矩形
//        float[] dst = new float[]{
//                0, 0,
//                dstWidth, 0,
//                dstWidth, dstHeight,
//                0, dstHeight
//        };
//
//        // 3. 源 polygon
//        float[] srcPts = new float[]{
//                poly[0].x, poly[0].y,
//                poly[1].x, poly[1].y,
//                poly[2].x, poly[2].y,
//                poly[3].x, poly[3].y
//        };
//
//        // 4. 透视变换
//        Matrix matrix = new Matrix();
//        boolean ok = matrix.setPolyToPoly(srcPts, 0, dst, 0, 4);
//        if (!ok) return null;
//
//        Bitmap dstBitmap = Bitmap.createBitmap(dstWidth, dstHeight, Bitmap.Config.ARGB_8888);
//        Canvas canvas = new Canvas(dstBitmap);
//        canvas.drawBitmap(src, matrix, new Paint(Paint.FILTER_BITMAP_FLAG));
//
//        // 5. 若是竖排文字，旋转 90°
//        if (dstHeight > dstWidth * 1.2f) {
//            Matrix rotate = new Matrix();
//            rotate.postRotate(90);
//            dstBitmap = Bitmap.createBitmap(
//                    dstBitmap,
//                    0, 0,
//                    dstBitmap.getWidth(),
//                    dstBitmap.getHeight(),
//                    rotate,
//                    true
//            );
//        }
//
//        return dstBitmap;
//    }
    public static float[] bitmapToFloatTensor(Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        float[] data = new float[3 * w * h];
        int[] pixels = new int[w * h];
        bmp.getPixels(pixels, 0, w, 0, 0, w, h);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = pixels[y * w + x];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                data[y * w + x] = b / 255f;
                data[w * h + y * w + x] = g / 255f;
                data[2 * w * h + y * w + x] = r / 255f;
            }
        }
        return data;
    }

    public static Bitmap drawBoxesOnImage(Bitmap original, List<PointF[]> boxes, float scaleX, float scaleY) {
        if (original == null || boxes == null || boxes.isEmpty()) return original;

        Bitmap resultBitmap = original.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(resultBitmap);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1);

        for (PointF[] box : boxes) {
            for (int i = 0; i < box.length; i++) {
                PointF p1 = box[i];
                PointF p2 = box[(i + 1) % box.length];
                canvas.drawLine(p1.x * scaleX, p1.y * scaleY, p2.x * scaleX, p2.y * scaleY, paint);
            }
        }
        return resultBitmap;
    }

    private static float distance(PointF a, PointF b) {
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // --------------------------------------
    // 预处理示例
    // --------------------------------------
    public static float[][][] preprocessDet(Bitmap bmp) {
        // TODO: resize 到 32 倍数、归一化到 [0,1] 或 [-1,1] 等
        return new float[1][bmp.getHeight()][bmp.getWidth()];
    }

    public static float[][] preprocessCls(Bitmap crop) {
        // TODO: resize 到 Cls 模型输入大小 + 归一化
        return new float[1][crop.getHeight() * crop.getWidth()];
    }

    public static float[][] preprocessRec(Bitmap crop) {
        // TODO: resize 到 Rec 模型输入 + 归一化
        return new float[1][crop.getHeight() * crop.getWidth()];
    }

    // --------------------------------------
    // OrtSession.Result 提取输出示例
    // --------------------------------------
    public static float[][][] extractDetOutput(OrtSession.Result run) {
        // TODO: 从 OrtSession.Result 提取 float[][][] det 输出
        return new float[1][1][1];
    }

    public static float[][] extractClsOutput(OrtSession.Result run) {
        // TODO: 从 OrtSession.Result 提取 float[][] cls 输出
        return new float[1][1];
    }

    /**
     * 从 OrtSession.Result 提取 Rec 模型输出
     * 假设输出 shape 为 [1, C, T] 或 [1, T, C]（常见 OCR 模型）
     *
     * @param run OrtSession.Result
     * @return float[seqLen][numChars]
     */
    public static float[][] extractRecOutput(OrtSession.Result run) {
        try {
            // 取第一个输出
            OnnxTensor outputTensor = (OnnxTensor) run.get(0);
            Object value = outputTensor.getValue();

            // 常见输出：float[1][C][T] 或 float[1][T][C]
            if (value instanceof float[][][]) {
                float[][][] arr3d = (float[][][]) value;

                int dim0 = arr3d.length;         // batch size, 通常为1
                int dim1 = arr3d[0].length;      // 通道或时间
                int dim2 = arr3d[0][0].length;   // 时间或通道

                // 判断是 [1, C, T] 还是 [1, T, C]，我们统一返回 [T, C]
                if (dim1 < dim2) {
                    // [1, C, T] -> 转置为 [T, C]
                    int T = dim2;
                    int C = dim1;
                    float[][] out = new float[T][C];
                    for (int c = 0; c < C; c++) {
                        for (int t = 0; t < T; t++) {
                            out[t][c] = arr3d[0][c][t];
                        }
                    }
                    return out;
                } else {
                    // [1, T, C] -> 去掉 batch
                    int T = dim1;
                    int C = dim2;
                    float[][] out = new float[T][C];
                    for (int t = 0; t < T; t++) {
                        System.arraycopy(arr3d[0][t], 0, out[t], 0, C);
                    }
                    return out;
                }
            } else {
                // 输出类型不匹配，返回空结果
                return new float[1][1];
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new float[1][1];
        }
    }
}
