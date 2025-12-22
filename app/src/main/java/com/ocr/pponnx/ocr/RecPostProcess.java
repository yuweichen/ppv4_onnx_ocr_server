package com.ocr.pponnx.ocr;

import android.graphics.Bitmap;
import android.util.Log;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

public class RecPostProcess {

    public static final int MODEL_HEIGHT = 64;

    public static OcrResult runRec(OrtSession recSession, OrtEnvironment env,
                                   Bitmap crop, List<String> keys) {

        OcrResult result = new OcrResult();
        result.text = "";
        result.score = 0f;

        if (recSession == null || crop == null || keys == null || keys.isEmpty()) return result;

        try {
            int inputH = MODEL_HEIGHT; // 固定高度
            int cropW = crop.getWidth();
            int cropH = Math.max(crop.getHeight(), 1);

            int inputW = Math.max(32, Math.min(320, cropW * inputH / cropH));

            Bitmap resized = Bitmap.createScaledBitmap(crop, inputW, inputH, true);

            Log.d("runRec", "inputH: " + inputH + " cropW: " + cropW + " cropH: " + cropH + " inputW: " + inputW);

            float[] inputData = bitmapToFloatTensor(resized);
            long[] shape = new long[]{1, 3, inputH, inputW};
            OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), shape);

            Map<String, OnnxTensor> inputs = new HashMap<>();
            for (String name : recSession.getInputNames()) {
                inputs.put(name, inputTensor);
            }

            OrtSession.Result run = recSession.run(inputs);

            float[][] recOutput = extractRecOutput(run); // [seqLen, numChars]

            result = decodeCTC(recOutput, keys);

            inputTensor.close();
            run.close();

        } catch (OrtException e) {
            e.printStackTrace();
        }

        return result;
    }

    // 转 Bitmap -> float[NCHW]
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

                int idx = y * w + x;
                data[idx] = r / 255f;
                data[w * h + idx] = g / 255f;
                data[2 * w * h + idx] = b / 255f;
            }
        }

        return data;
    }

    // 提取 Rec 输出为 [seqLen, numChars]
    public static float[][] extractRecOutput(OrtSession.Result run) throws OrtException {
        if (run == null || run.size() == 0) return new float[1][1];

        OnnxTensor output = (OnnxTensor) run.get(0);
        Object value = output.getValue();

        // 常见 Rec 输出是 [1, C, T] 或 [1, T, C]，这里假设 [1, T, C]
        if (value instanceof float[][][]) {
            float[][][] arr = (float[][][]) value;
            int seqLen = arr[0].length;
            int numChars = arr[0][0].length;
            float[][] out = new float[seqLen][numChars];
            for (int t = 0; t < seqLen; t++) {
                System.arraycopy(arr[0][t], 0, out[t], 0, numChars);
            }
            return out;
        }

        // fallback
        return new float[1][1];
    }

    // CTC 解码
    private static OcrResult decodeCTC(float[][] preds, List<String> keys) {
        OcrResult r = new OcrResult();
        StringBuilder sb = new StringBuilder();
        float scoreSum = 0f;
        int count = 0;

        int blankIdx = 0; // CTC blank
        int lastIdx = -1;

        for (int t = 0; t < preds.length; t++) {
            float[] timestep = preds[t];
            int maxIdx = 0;
            float maxScore = timestep[0];
            for (int i = 1; i < timestep.length; i++) {
                if (timestep[i] > maxScore) {
                    maxScore = timestep[i];
                    maxIdx = i;
                }
            }

            if (maxIdx != blankIdx && maxIdx != lastIdx && maxIdx - 1 < keys.size()) {
                sb.append(keys.get(maxIdx - 1));
                scoreSum += maxScore;
                count++;
            }

            lastIdx = maxIdx;
        }

        r.text = sb.toString();
        r.score = count == 0 ? 0f : scoreSum / count;
        return r;
    }
}
