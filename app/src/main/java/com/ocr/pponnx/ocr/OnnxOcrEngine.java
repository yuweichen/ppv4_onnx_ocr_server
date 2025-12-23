package com.ocr.pponnx.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
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
            // 输出配置信息
            OcrConfig.logAllConfig();

            Log.i(TAG, "初始化ONNX OCR引擎...");

            // 初始化环境
            env = OrtEnvironment.getEnvironment();

            // 根据性能配置设置会话选项
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            if (OcrConfig.Performance.MAX_CONCURRENT > 1) {
                options.setInterOpNumThreads(OcrConfig.Performance.MAX_CONCURRENT);
                options.setIntraOpNumThreads(OcrConfig.Performance.MAX_CONCURRENT);
            }

            // 加载模型
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

            // 加载字符集
            Log.i(TAG, "加载字符集...");
            keys = loadKeys(ctx);

            Log.i(TAG, "字符集大小: " + keys.size());
            Log.i(TAG, "ONNX OCR引擎初始化完成");

        } catch (Exception e) {
            Log.e(TAG, "初始化失败", e);
            throw new RuntimeException("OCR引擎初始化失败", e);
        }
    }

    public List<OcrResult> runBase64(String base64) throws Exception {
        // 1. 解码 base64
        byte[] imgBytes = Base64.decode(base64, Base64.DEFAULT);
        Bitmap originalBitmap = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.length);
        if (originalBitmap == null) throw new Exception("Failed to decode base64");

        int w = originalBitmap.getWidth();
        int h = originalBitmap.getHeight();
        int newW = ((w + 31) / 32) * 32;
        int newH = ((h + 31) / 32) * 32;

        Bitmap resizedBitmap = originalBitmap;
        if (newW != w || newH != h) {
            resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, newW, newH, true);
        }

        // 2. 转 float tensor
        float[] inputData = OcrUtils.bitmapToFloatTensor(resizedBitmap);
        long[] shape = new long[]{1, 3, newH, newW}; // NCHW
        OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), shape);

        // 3. 执行 det 模型
        Map<String, OnnxTensor> inputs = Collections.singletonMap(detSession.getInputNames().iterator().next(), inputTensor);
        OrtSession.Result run = detSession.run(inputs);
        float[][][][] output4D = (float[][][][]) run.get(0).getValue(); // 正确类型
        int H = output4D[0][0].length;
        int W = output4D[0][0][0].length;

        // 4. 获取输出，假设 det 输出为 float[][][]
        float[][][] detOutput = new float[H][W][1];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                detOutput[y][x][0] = output4D[0][0][y][x];
            }
        }


        // 5. 后处理得到 polygon
        List<PointF[]> boxes = DetPostProcess.run(detOutput);

        // 6. 映射回原图
//        float scaleX = (float) w / newW;
//        float scaleY = (float) h / newH;
//        Bitmap bitmapWithBoxes = OcrUtils.drawBoxesOnImage(originalBitmap, boxes, scaleX, scaleY);

        List<OcrResult> results = new ArrayList<>();
        for (PointF[] poly : boxes) {
            //todo 计算坐标返回 deepseek

            // 5a. 可选裁剪 RotatedBox
            RotatedBox box = new RotatedBox(
                    OcrUtils.getBoxCenter(poly),
                    OcrUtils.getBoxWidth(poly),
                    OcrUtils.getBoxHeight(poly),
                    OcrUtils.getBoxAngle(poly)
            );
            Bitmap crop = OcrUtils.cropRotatedBox(resizedBitmap, box);

            if (OcrConfig.Det.DO_ANGLE) {
                // 5b. 执行 Cls（可选）
                // 2. resize 到 cls 模型输入尺寸
                // Paddle 官方 cls 输入是：48 x 192（HxW）
                int clsH = 48;
                int clsW = 192;
                Bitmap resizedCls = Bitmap.createScaledBitmap(
                        crop,
                        clsW,
                        clsH,
                        true
                );
                // 3. Bitmap → float[]（NCHW，RGB，归一化到 [0,1]）
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

                // 4. 创建 OnnxTensor（注意是 4 维）
                long[] clsShape = new long[]{1, 3, clsH, clsW};
                OnnxTensor clsTensor = OnnxTensor.createTensor(
                        env,
                        FloatBuffer.wrap(clsInputData),
                        clsShape
                );

                // 5. 执行 cls
                String clsInputName = clsSession.getInputNames().iterator().next();
                OrtSession.Result clsRun = clsSession.run(
                        Collections.singletonMap(clsInputName, clsTensor)
                );

                // 6. 取输出（shape = [1, 2]）
                float[][] clsOutput = (float[][]) clsRun.get(0).getValue();

                // 7. 后处理：判断方向
                ClsPostProcess.TextDirection dir =
                        ClsPostProcess.getDirection(clsOutput);

                // 8. 如果是 180°，旋转 crop
                if (dir == ClsPostProcess.TextDirection.ROTATE_180) {
                    crop = rotateBitmap(crop, ClsPostProcess.TextDirection.ROTATE_180);
                }

                clsTensor.close();
                clsRun.close();
            }
            //rec
            OcrResult ocrResult = RecPostProcess.runRec(recSession, env, crop, keys);

            // 过滤 score
            if (ocrResult.score < OcrConfig.Rec.REC_SCORE_THRESHOLD) {
                continue;
            }
            results.add(ocrResult);
        }
        Log.d(TAG, "runBase64: " + results);
        return results;
    }


    // --------------------------------------
    // Bitmap 旋转
    // --------------------------------------
    private Bitmap rotateBitmap(Bitmap bmp, ClsPostProcess.TextDirection dir) {
        Matrix matrix = new Matrix();
        if (dir == ClsPostProcess.TextDirection.ROTATE_180) {
            matrix.postRotate(180);
        } else if (dir == ClsPostProcess.TextDirection.HORIZONTAL) {
            matrix.postRotate(0);
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
    }


    /**
     * 加载模型文件
     */
    private byte[] load(Context ctx, String name) throws IOException {
        InputStream is = ctx.getAssets().open(name);
        byte[] buf = new byte[is.available()];
        is.read(buf);
        is.close();
        return buf;
    }

    /**
     * 加载字符集
     */
    private List<String> loadKeys(Context ctx) throws IOException {
        List<String> list = new ArrayList<>();
        BufferedReader br = new BufferedReader(new InputStreamReader(ctx.getAssets().open("ppocr_keys_v1.txt")));
        String line;
        while ((line = br.readLine()) != null) {
            list.add(line.trim());
        }
        br.close();
        return list;
    }

    /**
     * 释放资源
     */
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