package com.ocr.pponnx.ocr;

import android.graphics.Bitmap;
import android.util.Log;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

public class RecPostProcessBak {

    public static class RecResult {
        public String text;
        public float score;
    }
    public static final int MODEL_HEIGHT = 64;
    public static RecPostProcessBak.RecResult runRec(OrtSession recSession, OrtEnvironment env,
                                                     Bitmap crop, List<String> keys) {
        RecPostProcessBak.RecResult result = new RecPostProcessBak.RecResult();
        if (recSession == null || crop == null || keys == null || keys.isEmpty()) return result;

        try {
            int cropH = Math.max(crop.getHeight(), 1);
            int cropW = Math.max(crop.getWidth(), 1);

            // 固定模型高度
            int inputH = MODEL_HEIGHT;
            float scale = (float) inputH / cropH;
            int inputW = (int) (cropW * scale + 0.5f);

            // 宽度取32的倍数，防止卷积/池化除法异常
            inputW = Math.max(32, ((inputW + 31) / 32) * 32);

            Bitmap resized = Bitmap.createScaledBitmap(crop, inputW, inputH, true);
            float[] inputData = bitmapToFloatTensor(resized);

            Log.d("runRec", "inputH: " + inputH + " cropW: " + cropW + " cropH: " + cropH + " inputW: " + inputW);

            long[] shape = new long[]{1, 3, inputH, inputW};
            OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), shape);

            Map<String, OnnxTensor> inputs = new HashMap<>();
            for (String name : recSession.getInputNames()) {
                inputs.put(name, inputTensor);
            }

            OrtSession.Result run = recSession.run(inputs);

            // 提取输出，CTC解码
            float[][] recOutput = OcrUtils.extractRecOutput(run);
            result = decode(recOutput, keys);

            run.close();
            inputTensor.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    private static float[] bitmapToFloatTensor(Bitmap bmp) {
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

                // 标准 NCHW 索引
                data[0 * w * h + y * w + x] = r / 255f;
                data[1 * w * h + y * w + x] = g / 255f;
                data[2 * w * h + y * w + x] = b / 255f;
            }
        }
        return data;
    }
    private static float[] convertBitmapToModelInput(Bitmap bitmap) {
        int h = bitmap.getHeight();
        int w = bitmap.getWidth();
        float[] data = new float[1 * 3 * h * w];

        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);

        int planeSize = h * w;
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                int pixel = pixels[i * w + j];

                // RGB归一化到[-1, 1]
                float r = ((pixel >> 16) & 0xFF) / 255.0f * 2.0f - 1.0f;
                float g = ((pixel >> 8) & 0xFF) / 255.0f * 2.0f - 1.0f;
                float b = (pixel & 0xFF) / 255.0f * 2.0f - 1.0f;

                int idx = i * w + j;
                data[idx] = r;                      // R通道
                data[planeSize + idx] = g;          // G通道
                data[2 * planeSize + idx] = b;      // B通道
            }
        }

        return data;
    }
    public static RecResult decode(float[][] preds, List<String> keys) {
        StringBuilder sb = new StringBuilder();
        float scoreSum = 0f;
        int count = 0;
        int lastIdx = -1;

        for (int t = 0; t < preds.length; t++) {
            float[] p = preds[t];

            int maxIdx = 0;
            float maxScore = p[0];
            for (int i = 1; i < p.length; i++) {
                if (p[i] > maxScore) {
                    maxScore = p[i];
                    maxIdx = i;
                }
            }

            // CTC blank = 0
            if (maxIdx > 0 && maxIdx != lastIdx) {
                int keyIdx = maxIdx - 1;
                if (keyIdx >= 0 && keyIdx < keys.size()) {
                    sb.append(keys.get(keyIdx));
                    scoreSum += maxScore;
                    count++;
                }
            }
            lastIdx = maxIdx;
        }

        RecResult r = new RecResult();
        r.text = sb.toString();
        r.score = count == 0 ? 0f : scoreSum / count;
        return r;
    }
}
