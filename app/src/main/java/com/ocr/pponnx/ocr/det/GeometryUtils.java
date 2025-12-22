package com.ocr.pponnx.ocr.det;

import android.graphics.PointF;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GeometryUtils {

    /**
     * 最小外接矩形（旋转矩形）算法
     * @param points 输入点集
     * @return RotatedBox
     */
    public static RotatedBox minAreaRect(List<PointF> points) {
        if (points.size() < 3) {
            // 少于3点就用 axis-aligned
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;
            for (PointF p : points) {
                minX = Math.min(minX, p.x);
                minY = Math.min(minY, p.y);
                maxX = Math.max(maxX, p.x);
                maxY = Math.max(maxY, p.y);
            }
            PointF center = new PointF((minX + maxX) / 2, (minY + maxY) / 2);
            return new RotatedBox(center, maxX - minX, maxY - minY, 0);
        }

        // 简化实现：用凸包的边计算最小面积矩形
        List<PointF> hull = convexHull(points);
        float minArea = Float.MAX_VALUE;
        RotatedBox bestBox = null;

        int n = hull.size();
        for (int i = 0; i < n; i++) {
            PointF p1 = hull.get(i);
            PointF p2 = hull.get((i + 1) % n);
            float angle = (float) Math.atan2(p2.y - p1.y, p2.x - p1.x);

            float cosA = (float) Math.cos(-angle);
            float sinA = (float) Math.sin(-angle);

            float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
            float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;

            for (PointF p : hull) {
                float x = p.x * cosA - p.y * sinA;
                float y = p.x * sinA + p.y * cosA;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }

            float area = (maxX - minX) * (maxY - minY);
            if (area < minArea) {
                minArea = area;
                // 旋转矩形中心
                float cx = (minX + maxX) / 2;
                float cy = (minY + maxY) / 2;
                // 转回原坐标
                PointF center = new PointF(
                        cx * cosA + cy * -sinA,
                        cx * sinA + cy * cosA
                );
                bestBox = new RotatedBox(center, maxX - minX, maxY - minY, angle);
            }
        }
        return bestBox;
    }

    /**
     * Graham 扫描凸包
     */
    private static List<PointF> convexHull(List<PointF> points) {
        if (points.size() <= 3) return new ArrayList<>(points);

        List<PointF> pts = new ArrayList<>(points);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Collections.sort(pts, Comparator.comparingDouble(p -> p.x * 1e6 + p.y)); // 简单排序
        }

        List<PointF> lower = new ArrayList<>();
        for (PointF p : pts) {
            while (lower.size() >= 2 && cross(lower.get(lower.size() - 2), lower.get(lower.size() - 1), p) <= 0)
                lower.remove(lower.size() - 1);
            lower.add(p);
        }

        List<PointF> upper = new ArrayList<>();
        for (int i = pts.size() - 1; i >= 0; i--) {
            PointF p = pts.get(i);
            while (upper.size() >= 2 && cross(upper.get(upper.size() - 2), upper.get(upper.size() - 1), p) <= 0)
                upper.remove(upper.size() - 1);
            upper.add(p);
        }

        lower.remove(lower.size() - 1);
        upper.remove(upper.size() - 1);
        lower.addAll(upper);
        return lower;
    }

    private static float cross(PointF O, PointF A, PointF B) {
        return (A.x - O.x) * (B.y - O.y) - (A.y - O.y) * (B.x - O.x);
    }
}
