package com.ocr.pponnx.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.util.Base64;
import android.util.Log;

import com.ocr.pponnx.ocr.det.RotatedBox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

public class OnnxOcrEngine {

    private static final String TAG = "OnnxOcrEngine";
    private OrtEnvironment env;
    private OrtSession detSession, recSession, clsSession;
    private List<String> keys;

    public OnnxOcrEngine(Context ctx) {
        try {
            OcrConfig.logAllConfig();
            Log.i(TAG, "初始化ONNX OCR引擎...");

            env = OrtEnvironment.getEnvironment();

            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            if (OcrConfig.Performance.MAX_CONCURRENT > 1) {
                options.setInterOpNumThreads(OcrConfig.Performance.MAX_CONCURRENT);
                options.setIntraOpNumThreads(OcrConfig.Performance.MAX_CONCURRENT);
            }

            Log.i(TAG, "加载检测模型...");
            detSession = env.createSession(load(ctx, "ch_PP-OCRv4_det_infer.onnx"), options);

            Log.i(TAG, "加载识别模型...");
            recSession = env.createSession(load(ctx, "ch_PP-OCRv4_rec_infer.onnx"), options);

            if (OcrConfig.Det.DO_ANGLE) {
                Log.i(TAG, "加载分类模型...");
                clsSession = env.createSession(load(ctx, "ch_ppocr_mobile_v2.0_cls_infer.onnx"), options);
            } else {
                Log.i(TAG, "跳过分类模型（配置禁用）");
                clsSession = null;
            }

            Log.i(TAG, "加载字符集...");
            keys = loadKeys(ctx);
            Log.i(TAG, "字符集大小: " + keys.size());
            Log.i(TAG, "ONNX OCR引擎初始化完成");

        } catch (Exception e) {
            Log.e(TAG, "初始化失败", e);
            throw new RuntimeException("OCR引擎初始化失败", e);
        }
    }

    /**
     * 执行 OCR 识别
     *
     * @param base64  Base64 编码的图片数据
     * @param offsetX 截图在屏幕中的左上角 X 坐标（未传入 offset 时填 0）
     * @param offsetY 截图在屏幕中的左上角 Y 坐标（未传入 offset 时填 0）
     * @return 识别结果列表，坐标为屏幕绝对坐标
     */
    public List<OcrResult> runBase64(String base64, float offsetX, float offsetY) throws Exception {
        byte[] imgBytes = Base64.decode(base64, Base64.DEFAULT);
        Bitmap originalBitmap = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.length);
        if (originalBitmap == null) throw new Exception("Failed to decode base64");

        int resizeMul = OcrConfig.Preprocess.RESIZE_MULTIPLE;
        int origW = originalBitmap.getWidth();
        int origH = originalBitmap.getHeight();
        int newW = ((origW + resizeMul - 1) / resizeMul) * resizeMul;
        int newH = ((origH + resizeMul - 1) / resizeMul) * resizeMul;

        Bitmap resizedBitmap = originalBitmap;
        if (newW != origW || newH != origH) {
            resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, newW, newH, true);
        }

        // 转 float tensor（NCHW）
        float[] inputData = OcrUtils.bitmapToFloatTensor(resizedBitmap);
        long[] shape = new long[]{1, 3, newH, newW};
        OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), shape);

        // 执行检测模型
        Map<String, OnnxTensor> inputs = Collections.singletonMap(
                detSession.getInputNames().iterator().next(), inputTensor);
        OrtSession.Result detRun = detSession.run(inputs);
        float[][][][] output4D = (float[][][][]) detRun.get(0).getValue();
        int H = output4D[0][0].length;
        int W = output4D[0][0][0].length;

        float[][][] detOutput = new float[H][W][1];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                detOutput[y][x][0] = output4D[0][0][y][x];
            }
        }

        // 后处理得到 polygon
        List<PointF[]> boxes = DetPostProcess.run(detOutput);

        // 缩放比例：检测框坐标基于 resize 后尺寸，需映射回原图
        float scaleX = (float) origW / newW;
        float scaleY = (float) origH / newH;

        List<OcrResult> results = new ArrayList<>();
        for (PointF[] poly : boxes) {
            // 计算裁剪区域
            RotatedBox box = new RotatedBox(
                    OcrUtils.getBoxCenter(poly),
                    OcrUtils.getBoxWidth(poly),
                    OcrUtils.getBoxHeight(poly),
                    OcrUtils.getBoxAngle(poly)
            );
            Bitmap crop = OcrUtils.cropRotatedBox(resizedBitmap, box);

            if (OcrConfig.Det.DO_ANGLE) {
                int clsH = OcrConfig.Cls.IMG_HEIGHT;
                int clsW = OcrConfig.Cls.IMG_WIDTH;
                Bitmap resizedCls = Bitmap.createScaledBitmap(crop, clsW, clsH, true);

                float[] clsInputData = new float[3 * clsH * clsW];
                int[] pixels = new int[clsH * clsW];
                resizedCls.getPixels(pixels, 0, clsW, 0, 0, clsW, clsH);

                for (int y = 0; y < clsH; y++) {
                    for (int x = 0; x < clsW; x++) {
                        int c = pixels[y * clsW + x];
                        float r = ((c >> 16) & 0xFF) / 255f;
                        float g = ((c >> 8) & 0xFF) / 255f;
                        float b = (c & 0xFF) / 255f;

                        int idx = y * clsW + x;
                        clsInputData[idx] = r;
                        clsInputData[clsH * clsW + idx] = g;
                        clsInputData[2 * clsH * clsW + idx] = b;
                    }
                }

                long[] clsShape = new long[]{1, 3, clsH, clsW};
                OnnxTensor clsTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(clsInputData), clsShape);

                String clsInputName = clsSession.getInputNames().iterator().next();
                OrtSession.Result clsRun = clsSession.run(
                        Collections.singletonMap(clsInputName, clsTensor));

                float[][] clsOutput = (float[][]) clsRun.get(0).getValue();
                ClsPostProcess.TextDirection dir = ClsPostProcess.getDirection(clsOutput);

                if (dir == ClsPostProcess.TextDirection.ROTATE_180) {
                    crop = rotateBitmap(crop, ClsPostProcess.TextDirection.ROTATE_180);
                }

                clsTensor.close();
                clsRun.close();
            }

            // 识别
            OcrResult ocrResult = RecPostProcess.runRec(recSession, env, crop, keys);
            if (ocrResult.score < OcrConfig.Rec.REC_SCORE_THRESHOLD) {
                continue;
            }

            // 填充坐标（屏幕绝对坐标）
            fillCoordinates(ocrResult, poly, scaleX, scaleY, offsetX, offsetY);

            results.add(ocrResult);
        }

        inputTensor.close();
        detRun.close();
        Log.d(TAG, "runBase64: " + results);
        return results;
    }

    /**
     * 填充检测框的坐标信息
     *
     * @param result   OcrResult（由 RecPostProcess 填充了 text 和 score）
     * @param poly     检测框 4 个角点（基于 resize 后尺寸）
     * @param scaleX   X 方向缩放比例
     * @param scaleY   Y 方向缩放比例
     * @param offsetX  截图在屏幕中的 X 偏移
     * @param offsetY  截图在屏幕中的 Y 偏移
     */
    private void fillCoordinates(OcrResult result, PointF[] poly,
                                  float scaleX, float scaleY,
                                  float offsetX, float offsetY) {
        // 中心点
        float centerX = 0, centerY = 0;
        for (PointF p : poly) {
            centerX += p.x;
            centerY += p.y;
        }
        result.centerX = centerX / 4 * scaleX + offsetX;
        result.centerY = centerY / 4 * scaleY + offsetY;

        // 4 个角点（顺时针：左上、右上、右下、左下）
        result.box = new OcrResult.Point[4];
        for (int i = 0; i < 4; i++) {
            result.box[i] = new OcrResult.Point(
                    poly[i].x * scaleX + offsetX,
                    poly[i].y * scaleY + offsetY
            );
        }
    }

    private Bitmap rotateBitmap(Bitmap bmp, ClsPostProcess.TextDirection dir) {
        Matrix matrix = new Matrix();
        if (dir == ClsPostProcess.TextDirection.ROTATE_180) {
            matrix.postRotate(180);
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
    }

    private byte[] load(Context ctx, String name) throws IOException {
        InputStream is = ctx.getAssets().open(name);
        byte[] buf = new byte[is.available()];
        is.read(buf);
        is.close();
        return buf;
    }

    private List<String> loadKeys(Context ctx) throws IOException {
        List<String> list = new ArrayList<>();
        BufferedReader br = new BufferedReader(
                new InputStreamReader(ctx.getAssets().open("ppocr_keys_v1.txt")));
        String line;
        while ((line = br.readLine()) != null) {
            list.add(line.trim());
        }
        br.close();
        return list;
    }

    public void release() {
        try {
            if (detSession != null) {
                detSession.close();
                detSession = null;
            }
            if (recSession != null) {
                recSession.close();
                recSession = null;
            }
            if (clsSession != null) {
                clsSession.close();
                clsSession = null;
            }
            if (env != null) {
                env.close();
                env = null;
            }
            Log.i(TAG, "OCR引擎资源已释放");
        } catch (Exception e) {
            Log.e(TAG, "释放资源失败", e);
        }
    }
}
