package com.ocr.pponnx.ocr;

import android.graphics.PointF;

import com.ocr.pponnx.ocr.det.GeometryUtils;
import com.ocr.pponnx.ocr.det.RotatedBox;

import java.util.ArrayList;
import java.util.List;

public class DetPostProcess {

    /**
     * 两趟连通域标记 + union-find，替代逐像素 flood fill
     * 速度提升 5-10 倍
     */
    public static List<PointF[]> run(float[][][] detOutput) {
        int h = detOutput.length;
        int w = detOutput[0].length;
        float threshold = OcrConfig.Det.BOX_THRESH;
        float paddingMin = OcrConfig.Det.PADDING_MIN;
        float paddingMax = OcrConfig.Det.PADDING_MAX;
        float paddingRatio = OcrConfig.Det.PADDING_RATIO;

        // 估算最大连通域数量（最多每4个像素一个连通域）
        int maxLabels = (h * w) / 4 + 1;
        int[] parent = new int[maxLabels];
        int[] rankArr = new int[maxLabels];
        int[][] labels = new int[h][w];

        int labelCount = 0;

        // ========== 第一趟：标记 & 合并 ==========
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (detOutput[y][x][0] <= threshold) continue;

                // 只看 4 邻域（上下左右）
                int north = (y > 0) ? labels[y - 1][x] : 0;
                int west = (x > 0) ? labels[y][x - 1] : 0;

                if (north == 0 && west == 0) {
                    // 新建一个连通域
                    labels[y][x] = ++labelCount;
                    parent[labelCount] = labelCount;
                    rankArr[labelCount] = 0;
                } else if (north == 0) {
                    labels[y][x] = west;
                } else if (west == 0) {
                    labels[y][x] = north;
                } else {
                    // 两个邻域都属于不同连通域，合并
                    labels[y][x] = Math.min(north, west);
                    union(parent, rankArr, north, west);
                }
            }
        }

        if (labelCount == 0) return new ArrayList<>();

        // ========== 第二趟：路径压缩，展平标签 ==========
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (labels[y][x] == 0) continue;
                labels[y][x] = find(parent, labels[y][x]);
            }
        }

        // ========== 统计每个连通域的点数 ==========
        int[] counts = new int[labelCount + 1];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int root = labels[y][x];
                if (root > 0) counts[root]++;
            }
        }

        // ========== 预分配坐标数组 ==========
        float[][] xs = new float[labelCount + 1][];
        float[][] ys = new float[labelCount + 1][];
        int[] idx = new int[labelCount + 1];

        int minPoints = 3;
        for (int i = 1; i <= labelCount; i++) {
            if (counts[i] >= minPoints) {
                xs[i] = new float[counts[i]];
                ys[i] = new float[counts[i]];
            }
        }

        // ========== 第三趟：填入坐标 ==========
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int root = labels[y][x];
                if (root == 0 || xs[root] == null) continue;
                int i = idx[root]++;
                xs[root][i] = x;
                ys[root][i] = y;
            }
        }

        // ========== 计算最小外接矩形 ==========
        List<PointF[]> polygons = new ArrayList<>();
        float dynamicPadding, longSide;

        for (int i = 1; i <= labelCount; i++) {
            if (xs[i] == null) continue;

            RotatedBox rotatedBox = GeometryUtils.minAreaRect(xs[i], ys[i], counts[i]);
            longSide = Math.max(rotatedBox.width, rotatedBox.height);
            dynamicPadding = Math.max(paddingMin, Math.min(longSide * paddingRatio, paddingMax));
            rotatedBox.expand(dynamicPadding);

            PointF[] poly = rotatedBox.toPolygon();
            polygons.add(poly);
        }

        return polygons;
    }

    // ========== Union-Find with path compression & union by rank ==========

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]]; // 路径压缩
            x = parent[x];
        }
        return x;
    }

    private static void union(int[] parent, int[] rankArr, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra == rb) return;

        if (rankArr[ra] < rankArr[rb]) {
            parent[ra] = rb;
        } else if (rankArr[ra] > rankArr[rb]) {
            parent[rb] = ra;
        } else {
            parent[rb] = ra;
            rankArr[ra]++;
        }
    }
}
