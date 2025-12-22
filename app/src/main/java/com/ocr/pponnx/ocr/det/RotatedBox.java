package com.ocr.pponnx.ocr.det;

import android.graphics.PointF;

public class RotatedBox {
    public PointF center;
    public float width;
    public float height;
    public float angle; // 弧度
    private PointF[] polygon;

    public RotatedBox(PointF center, float width, float height, float angle) {
        this.center = center;
        this.width = width;
        this.height = height;
        this.angle = angle;
        computePolygon();
    }

    private void computePolygon() {
        polygon = new PointF[4];
        float cosA = (float) Math.cos(angle);
        float sinA = (float) Math.sin(angle);

        float hw = width / 2;
        float hh = height / 2;

        // 左上
        polygon[0] = new PointF(center.x - hw * cosA + hh * sinA, center.y - hw * sinA - hh * cosA);
        // 右上
        polygon[1] = new PointF(center.x + hw * cosA + hh * sinA, center.y + hw * sinA - hh * cosA);
        // 右下
        polygon[2] = new PointF(center.x + hw * cosA - hh * sinA, center.y + hw * sinA + hh * cosA);
        // 左下
        polygon[3] = new PointF(center.x - hw * cosA - hh * sinA, center.y - hw * sinA + hh * cosA);
    }

    public PointF[] toPolygon() {
        return polygon;
    }

    public void expand(float pad) {
        width += pad * 2;
        height += pad * 2;
        computePolygon();
    }

}
