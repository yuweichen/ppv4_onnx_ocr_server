package com.ocr.pponnx.ocr;

import android.graphics.PointF;
import android.util.Log;

import com.ocr.pponnx.ocr.det.GeometryUtils;
import com.ocr.pponnx.ocr.det.RotatedBox;

import java.util.ArrayList;
import java.util.List;

public class DetPostProcess {
    private static float detThreshold = 0.2f; // 阈值
    private static int padding = 20;           // 膨胀边界

    /**
     * 输出旋转 polygon
     */
    public static List<PointF[]> run(float[][][] detOutput) {
        int h = detOutput.length;
        int w = detOutput[0].length;
        boolean[][] visited = new boolean[h][w];
        List<PointF[]> polygons = new ArrayList<>();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!visited[y][x] && detOutput[y][x][0] > detThreshold) {
                    // flood fill 找到连通区域
                    List<PointF> regionPoints = floodFillPoints(detOutput, visited, x, y, h, w);
                    if (regionPoints.size() < 3) continue; // 太小忽略

                    // 调用你的 minAreaRect 得到旋转矩形
                    RotatedBox rotatedBox = minAreaRect(regionPoints);
                    float longSide = Math.max(rotatedBox.width, rotatedBox.height);
                    float padding = Math.max(5, Math.min(longSide * 0.1f, 30));

                    Log.i("DetPostProcess", "动态 padding = " + padding);
                    // 可选：padding
                    rotatedBox.expand(padding);

                    // 转为 polygon
                    PointF[] poly = rotatedBox.toPolygon();
                    polygons.add(poly);
                }
            }
        }
        return polygons;
    }

    /**
     * 8 邻域 flood fill，返回该连通区域所有点
     */
    private static List<PointF> floodFillPoints(float[][][] detOutput, boolean[][] visited, int startX, int startY, int H, int W) {
        List<PointF> points = new ArrayList<>();
        List<int[]> stack = new ArrayList<>();
        stack.add(new int[]{startX, startY});
        visited[startY][startX] = true;

        int[] dx = {-1, -1, -1, 0, 0, 1, 1, 1};
        int[] dy = {-1, 0, 1, -1, 1, -1, 0, 1};

        while (!stack.isEmpty()) {
            int[] p = stack.remove(stack.size() - 1);
            int x = p[0], y = p[1];
            points.add(new PointF(x, y));

            for (int i = 0; i < 8; i++) {
                int nx = x + dx[i];
                int ny = y + dy[i];
                if (nx >= 0 && nx < W && ny >= 0 && ny < H
                        && !visited[ny][nx]
                        && detOutput[ny][nx][0] > detThreshold) {
                    visited[ny][nx] = true;
                    stack.add(new int[]{nx, ny});
                }
            }
        }
        return points;
    }

    /**
     * 占位，调用你的 minAreaRect
     * 你自己的方法已经存在
     */
    private static RotatedBox minAreaRect(List<PointF> hull) {
        // 这里会调用你已有的方法
        return GeometryUtils.minAreaRect(hull);
    }
}
