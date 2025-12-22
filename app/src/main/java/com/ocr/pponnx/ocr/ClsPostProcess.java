package com.ocr.pponnx.ocr;

public class ClsPostProcess {

    /** 文字方向 */
    public enum TextDirection {
        HORIZONTAL,     // 正常
        ROTATE_180      // 需要旋转 180°
    }

    /** 置信度阈值（可调） */
    private static final float CLS_THRESH = 0.9f;

    /**
     * 根据 cls 输出判断方向
     *
     * @param clsOutput shape = [1, 2]
     */
    public static TextDirection getDirection(float[][] clsOutput) {
        if (clsOutput == null || clsOutput.length == 0) {
            return TextDirection.HORIZONTAL;
        }

        float[] scores = clsOutput[0];
        if (scores.length < 2) {
            return TextDirection.HORIZONTAL;
        }

        int label = argMax(scores);
        float score = scores[label];

        // 低置信度 → 当作正常方向
        if (score < CLS_THRESH) {
            return TextDirection.HORIZONTAL;
        }

        // label == 1 表示需要旋转 180°
        return label == 1 ? TextDirection.ROTATE_180 : TextDirection.HORIZONTAL;
    }

    /** argmax */
    private static int argMax(float[] arr) {
        int idx = 0;
        float max = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > max) {
                max = arr[i];
                idx = i;
            }
        }
        return idx;
    }
}
