package com.ocr.pponnx.ocr.det;

import android.graphics.PointF;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GeometryUtils {

    /**
     * 接收原始数组的最小外接矩形（避免创建 PointF 对象）
     */
    public static RotatedBox minAreaRect(float[] xs, float[] ys, int count) {
        if (count < 3) {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;
            for (int i = 0; i < count; i++) {
                minX = Math.min(minX, xs[i]);
                minY = Math.min(minY, ys[i]);
                maxX = Math.max(maxX, xs[i]);
                maxY = Math.max(maxY, ys[i]);
            }
            PointF center = new PointF((minX + maxX) / 2, (minY + maxY) / 2);
            return new RotatedBox(center, maxX - minX, maxY - minY, 0);
        }

        // ---------- 凸包（使用原始数组） ----------
        int[] stack = new int[count];
        int top = 0;

        // 找最左下的点
        int base = 0;
        for (int i = 1; i < count; i++) {
            if (ys[i] < ys[base] || (ys[i] == ys[base] && xs[i] < xs[base])) {
                base = i;
            }
        }

        // 极角排序（叉积判断）
        final int fb = base;
        Integer[] order = new Integer[count];
        for (int i = 0; i < count; i++) order[i] = i;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            java.util.Arrays.sort(order, (a, b) -> {
                if (a == fb) return -1;
                if (b == fb) return 1;
                float cross = cross(xs[fb], ys[fb], xs[a], ys[a], xs[b], ys[b]);
                if (cross > 0) return -1;
                if (cross < 0) return 1;
                return Float.compare(
                        (xs[a] - xs[fb]) * (xs[a] - xs[fb]) + (ys[a] - ys[fb]) * (ys[a] - ys[fb]),
                        (xs[b] - xs[fb]) * (xs[b] - xs[fb]) + (ys[b] - ys[fb]) * (ys[b] - ys[fb])
                );
            });
        }

        stack[top++] = fb;
        for (int k = 0; k < count; k++) {
            int i = order[k];
            if (i == fb) continue;
            while (top >= 2) {
                int j = stack[top - 1];
                int k2 = stack[top - 2];
                if (cross(xs[k2], ys[k2], xs[j], ys[j], xs[i], ys[i]) <= 0) {
                    top--;
                } else {
                    break;
                }
            }
            stack[top++] = i;
        }

        int hullSize = top;
        float[] hx = new float[hullSize];
        float[] hy = new float[hullSize];
        for (int i = 0; i < hullSize; i++) {
            int idx = stack[i];
            hx[i] = xs[idx];
            hy[i] = ys[idx];
        }

        // ---------- 求最小面积旋转矩形 ----------
        float minArea = Float.MAX_VALUE;
        RotatedBox bestBox = null;

        for (int i = 0; i < hullSize; i++) {
            float x1 = hx[i], y1 = hy[i];
            float x2 = hx[(i + 1) % hullSize], y2 = hy[(i + 1) % hullSize];

            float dx = x2 - x1;
            float dy = y2 - y1;
            float angle = (float) Math.atan2(dy, dx);
            float cosA = (float) Math.cos(-angle);
            float sinA = (float) Math.sin(-angle);

            float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
            float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;

            for (int j = 0; j < hullSize; j++) {
                float rx = hx[j] * cosA - hy[j] * sinA;
                float ry = hx[j] * sinA + hy[j] * cosA;
                if (rx < minX) minX = rx;
                if (rx > maxX) maxX = rx;
                if (ry < minY) minY = ry;
                if (ry > maxY) maxY = ry;
            }

            float area = (maxX - minX) * (maxY - minY);
            if (area < minArea) {
                minArea = area;
                float cx = (minX + maxX) / 2;
                float cy = (minY + maxY) / 2;
                PointF center = new PointF(
                        cx * cosA + cy * sinA,
                        -cx * sinA + cy * cosA
                );
                bestBox = new RotatedBox(center, maxX - minX, maxY - minY, angle);
            }
        }

        return bestBox != null ? bestBox : new RotatedBox(new PointF(0, 0), 0, 0, 0);
    }

    /**
     * 保留原有接口，内部委托到原始数组版本
     */
    public static RotatedBox minAreaRect(List<PointF> points) {
        int count = points.size();
        float[] xs = new float[count];
        float[] ys = new float[count];
        for (int i = 0; i < count; i++) {
            xs[i] = points.get(i).x;
            ys[i] = points.get(i).y;
        }
        return minAreaRect(xs, ys, count);
    }

    /**
     * 叉积 (x1,y1)->(x2,y2) 与 (x1,y1)->(x3,y3)
     */
    private static float cross(float x1, float y1, float x2, float y2, float x3, float y3) {
        return (x2 - x1) * (y3 - y1) - (y2 - y1) * (x3 - x1);
    }
}
