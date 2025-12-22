package com.ocr.pponnx.ocr;

import android.util.Log;

/**
 * OCR配置参数类 - 完整版
 */
public class OcrConfig {

    private static final String TAG = "OcrConfig";

    // ========== 检测模型参数 ==========
    public static class Det {
        public static int MAX_SIDE_LEN = 960;

        /**
         * 分数阈值
         */
        public static float BOX_SCORE_THRESH = 0.5f;
        /**
         * 二值化阈值
         * <pre>
         *     太高（0.3~0.5） 只保留文字核心像素 → 检测框可能偏小，漏掉文字边缘
         *     太低（0.1~0.2） 边缘噪点可能也被当作文字 → 框可能偏大，误检增加
         * </pre>
         */
        public static float BOX_THRESH = 0.3f;
        public static boolean DO_ANGLE = false;//角度分类 true 开启cls false 关闭cls
        public static int PADDING = 10;

        public static void logConfig() {
            Log.i(TAG, "检测配置: maxSide=" + MAX_SIDE_LEN +
                    ", boxThresh=" + BOX_THRESH +
                    ", scoreThresh=" + BOX_SCORE_THRESH +
                    ", padding=" + PADDING +
                    ", doAngle=" + DO_ANGLE);
        }
    }

    // ========== 识别模型参数 ==========
    public static class Rec {
        public static final int INPUT_HEIGHT = 32;
        public static int MAX_WIDTH = 320;
        public static int MAX_HEIGHT = 48;
        public static float THRESH = 0.5f;
        public static int MIN_WIDTH = 16;
        public static int PAD_MULTIPLE = 8;
        public static boolean USE_CTC = true;
        public static boolean REMOVE_DUPLICATE = true;
        public static float REC_SCORE_THRESHOLD = 0.5f;//分数过滤标准

        public static void logConfig() {
            Log.i(TAG, "识别配置: size=" + MAX_WIDTH + "x" + MAX_HEIGHT +
                    ", thresh=" + THRESH +
                    ", useCTC=" + USE_CTC);
        }
    }

    // ========== 分类模型参数 ==========
    public static class Cls {
        public static int IMG_SIZE = 48;
        public static float THRESH = 0.9f;
        public static float ROTATE_THRESH = 0.5f;

        public static void logConfig() {
            Log.i(TAG, "分类配置: size=" + IMG_SIZE + "x" + IMG_SIZE +
                    ", thresh=" + THRESH);
        }
    }

    // ========== 后处理参数 ==========
    public static class Post {
        public static int MIN_BOX_SIZE = 2;
        public static int MAX_BOX_SIZE = 500;
        public static int EXPAND_PIXELS = 2;
        public static float MERGE_IOU_THRESH = 0.3f;
        public static int SAME_LINE_THRESH = 20;
        public static int MIN_PIXELS_IN_BOX = 10;
        public static float MIN_AVG_SCORE = 0.1f;

        public static void logConfig() {
            Log.i(TAG, "后处理配置: minBox=" + MIN_BOX_SIZE +
                    ", maxBox=" + MAX_BOX_SIZE +
                    ", mergeIoU=" + MERGE_IOU_THRESH);
        }
    }

    // ========== 预处理参数 ==========
    public static class Preprocess {
        public static float MEAN_R = 0.5f;
        public static float MEAN_G = 0.5f;
        public static float MEAN_B = 0.5f;
        public static float STD_R = 0.5f;
        public static float STD_G = 0.5f;
        public static float STD_B = 0.5f;
        public static int RESIZE_MULTIPLE = 32;
        public static int DET_MAX_RESIZE = 1216;
        public static boolean KEEP_ASPECT_RATIO = true;

        public static void logConfig() {
            Log.i(TAG, "预处理配置: mean=[" + MEAN_R + "," + MEAN_G + "," + MEAN_B + "]" +
                    ", std=[" + STD_R + "," + STD_G + "," + STD_B + "]");
        }
    }

    // ========== 性能参数 ==========
    public static class Performance {
        public static boolean ENABLE_LOG = true;
        public static boolean ENABLE_TIMING = true;
        public static int MAX_CONCURRENT = 1;
        public static boolean RECYCLE_BITMAPS = true;

        public static void logConfig() {
            Log.i(TAG, "性能配置: enableLog=" + ENABLE_LOG +
                    ", enableTiming=" + ENABLE_TIMING);
        }
    }

    // ========== 输出参数 ==========
    public static class Output {
        public static boolean INCLUDE_SCORE = true;
        public static boolean INCLUDE_POSITION = true;
        public static boolean INCLUDE_ANGLE = false;
        public static String LANGUAGE = "ch";
        public static int MAX_TEXT_LENGTH = 100;

        public static void logConfig() {
            Log.i(TAG, "输出配置: language=" + LANGUAGE +
                    ", includeScore=" + INCLUDE_SCORE);
        }
    }

    /**
     * 输出所有配置
     */
    public static void logAllConfig() {
        Log.i(TAG, "=== OCR配置参数 ===");
        Det.logConfig();
        Rec.logConfig();
        Cls.logConfig();
        Post.logConfig();
        Preprocess.logConfig();
        Performance.logConfig();
        Output.logConfig();
        Log.i(TAG, "==================");
    }
}