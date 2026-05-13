package com.ocr.pponnx.ocr;

import android.util.Log;

/**
 * OCR 全局配置参数类
 *
 * 所有参数均为静态字段，直接通过类名引用（如 OcrConfig.Det.BOX_THRESH）。
 * 调整参数后需重新构建并启动服务。
 */
public class OcrConfig {

    private static final String TAG = "OcrConfig";

    // ================================================================
    // 检测（Detection）模型参数
    // ================================================================
    public static class Det {
        /**
         * 二值化阈值
         *
         * 检测模型输出的像素值大于此阈值则认定为文字区域。
         * - 值过高（0.3~0.5）：仅保留文字核心像素，检测框偏小，漏掉文字边缘
         * - 值过低（0.1~0.15）：边缘噪点也被当作文字，检测框偏大，误检增加
         * - 默认 0.2f，经过实际测试验证
         */
        public static float BOX_THRESH = 0.2f;

        /**
         * 角度分类开关
         *
         * true：加载并使用 cls 模型修正倒立文字方向（180°旋转）
         * false：跳过 cls 模型，节省推理时间（文字方向影响不大时建议关闭）
         */
        public static boolean DO_ANGLE = false;

        /**
         * 检测框动态膨胀最小值（像素）
         *
         * 在最小外接矩形基础上向外扩展的最小像素数。
         * 防止裁剪时切到文字边缘。
         * 与 PADDING_RATIO 共同决定膨胀量，取两者较大值。
         */
        public static int PADDING_MIN = 5;

        /**
         * 检测框动态膨胀最大值（像素）
         *
         * 膨胀量的上限，防止过度膨胀导致包含过多背景。
         * 与 PADDING_RATIO 共同决定膨胀量，取较小值生效。
         */
        public static int PADDING_MAX = 30;

        /**
         * 检测框动态膨胀比例（相对于长边）
         *
         * 膨胀量 = 长边 × PADDING_RATIO，
         * 结果与 PADDING_MIN/PADDING_MAX 取夹紧值。
         * 例如长边为 200px，比例为 0.1f，则膨胀 20px。
         */
        public static float PADDING_RATIO = 0.1f;
    }

    // ================================================================
    // 识别（Recognition）模型参数
    // ================================================================
    public static class Rec {
        /**
         * 识别模型输入高度（像素）
         *
         * PP-OCRv4 rec 模型固定输入高度为 64px。
         * 宽度按原始宽高比自适应，范围由 MIN_INPUT_WIDTH 和 MAX_INPUT_WIDTH 夹紧。
         */
        public static int MODEL_HEIGHT = 64;

        /**
         * 识别模型输入最小宽度（像素）
         *
         * 裁剪后的文字区域宽度小于此值时，按此值处理，防止过窄导致识别失败。
         */
        public static int MIN_INPUT_WIDTH = 32;

        /**
         * 识别模型输入最大宽度（像素）
         *
         * 裁剪后的文字区域宽度大于此值时，按此值处理，防止过宽导致显存/内存溢出。
         */
        public static int MAX_INPUT_WIDTH = 320;

        /**
         * 识别结果置信度过滤阈值
         *
         * 单个字符识别置信度低于此值的结果将被过滤丢弃。
         * 值越低越宽松，0.5f 为经验默认值。
         */
        public static float REC_SCORE_THRESHOLD = 0.5f;
    }

    // ================================================================
    // 方向分类（Classification）模型参数
    // ================================================================
    public static class Cls {
        /**
         * 方向分类输入图像高度（像素）
         *
         * PaddleOCR cls 模型的固定输入高度。
         */
        public static int IMG_HEIGHT = 48;

        /**
         * 方向分类输入图像宽度（像素）
         *
         * PaddleOCR cls 模型的固定输入宽度。
         */
        public static int IMG_WIDTH = 192;

        /**
         * 方向分类置信度阈值
         *
         * 当分类置信度 ≥ 此值时才对文字进行 180° 旋转修正。
         * 值越高越保守，低置信度时不旋转，避免误修正。
         */
        public static float THRESH = 0.9f;
    }

    // ================================================================
    // 预处理参数
    // ================================================================
    public static class Preprocess {
        /**
         * 图像 resize 时的对齐倍数
         *
         * 检测模型要求输入宽高为 32 的倍数，
         * 原始图像会先 resize 到最近的 32 倍数尺寸。
         */
        public static int RESIZE_MULTIPLE = 32;
    }

    // ================================================================
    // 性能与运行时参数
    // ================================================================
    public static class Performance {
        /**
         * ONNX Runtime 最大并发线程数
         *
         * 控制 ONNX session 的线程池大小。
         * - 设为 1：串行执行，稳定省电
         * - 设为 >1：可提升多图并发场景吞吐量，但增加 CPU 占用
         * 注意：ONNX Runtime session 本身非线程安全，多线程需自行加锁
         */
        public static int MAX_CONCURRENT = 1;
    }

    // ================================================================
    // 服务参数
    // ================================================================
    public static class Server {
        /**
         * HTTP 服务监听端口
         *
         * OCR HTTP 服务绑定到此端口。
         * 仅监听 127.0.0.1（本地回环），不对外网暴露。
         */
        public static int PORT = 8080;
    }

    // ================================================================
    // 日志配置
    // ================================================================
    public static class Logging {
        /**
         * 是否输出详细日志
         */
        public static boolean ENABLE = true;
    }

    /**
     * 打印当前所有配置（用于调试）
     */
    public static void logAllConfig() {
        Log.i(TAG, "=== OCR 配置参数 ===");
        Log.i(TAG, "[Det]    boxThresh=" + Det.BOX_THRESH
                + ", doAngle=" + Det.DO_ANGLE
                + ", paddingMin=" + Det.PADDING_MIN
                + ", paddingMax=" + Det.PADDING_MAX
                + ", paddingRatio=" + Det.PADDING_RATIO);
        Log.i(TAG, "[Rec]    modelHeight=" + Rec.MODEL_HEIGHT
                + ", minWidth=" + Rec.MIN_INPUT_WIDTH
                + ", maxWidth=" + Rec.MAX_INPUT_WIDTH
                + ", scoreThresh=" + Rec.REC_SCORE_THRESHOLD);
        Log.i(TAG, "[Cls]    imgSize=" + Cls.IMG_HEIGHT + "x" + Cls.IMG_WIDTH
                + ", thresh=" + Cls.THRESH);
        Log.i(TAG, "[Pre]    resizeMultiple=" + Preprocess.RESIZE_MULTIPLE);
        Log.i(TAG, "[Perf]   maxConcurrent=" + Performance.MAX_CONCURRENT);
        Log.i(TAG, "[Server] port=" + Server.PORT);
        Log.i(TAG, "=====================");
    }
}
