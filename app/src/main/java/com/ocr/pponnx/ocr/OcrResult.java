package com.ocr.pponnx.ocr;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class OcrResult {
    public String text;
    public float score;
    /** 文字区域中心点 X 坐标 */
    public float centerX;
    /** 文字区域中心点 Y 坐标 */
    public float centerY;
    /**
     * 文字区域 4 个角点坐标
     * 顺序：左上、右上、右下、左下
     */
    public Point[] box;

    public static class Point {
        public float x;
        public float y;

        public Point() {}

        public Point(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("text", text);
        o.put("score", score);
        o.put("centerX", centerX);
        o.put("centerY", centerY);
        JSONArray boxArr = new JSONArray();
        for (Point p : box) {
            JSONObject pt = new JSONObject();
            pt.put("x", p.x);
            pt.put("y", p.y);
            boxArr.put(pt);
        }
        o.put("box", boxArr);
        return o;
    }

    @Override
    public String toString() {
        return "OcrResult{text='" + text + '\'' +
                ", score=" + score +
                ", centerX=" + centerX +
                ", centerY=" + centerY +
                '}';
    }
}
