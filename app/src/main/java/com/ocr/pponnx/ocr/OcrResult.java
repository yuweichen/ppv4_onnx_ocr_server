package com.ocr.pponnx.ocr;

import org.json.JSONException;
import org.json.JSONObject;

public class OcrResult {
    public String text;
    public int x, y, width, height;
    public float score;


    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("text", text);
        o.put("x", x);
        o.put("y", y);
        o.put("width", width);
        o.put("height", height);
        o.put("score", score);
        return o;
    }
    @Override
    public String toString() {
        return "OcrResult{" +
                "text='" + text + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", width=" + width +
                ", height=" + height +
                ", score=" + score +
                '}';
    }
}