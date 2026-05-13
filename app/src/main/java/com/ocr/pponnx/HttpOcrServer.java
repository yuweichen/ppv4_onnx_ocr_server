package com.ocr.pponnx;

import android.content.Context;
import android.util.Log;

import com.ocr.pponnx.ocr.OcrResult;
import com.ocr.pponnx.ocr.OnnxOcrEngine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.onnxruntime.OrtException;
import fi.iki.elonen.NanoHTTPD;

public class HttpOcrServer extends NanoHTTPD {

    private final OnnxOcrEngine ocr;

    public HttpOcrServer(int port, Context ctx) throws OrtException {
        super("127.0.0.1", port);
        ocr = new OnnxOcrEngine(ctx);
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (Method.POST.equals(session.getMethod()) && "/ocr".equals(session.getUri())) {
            try {
                Map<String, String> body = new HashMap<>();
                session.parseBody(body);
                String json = body.get("postData");
                JSONObject obj = new JSONObject(json);
                String base64 = obj.getString("image");

                // 解析 offset（可选）
                float offsetX = 0f, offsetY = 0f;
                if (obj.has("offset")) {
                    JSONObject offset = obj.getJSONObject("offset");
                    offsetX = (float) offset.optDouble("x", 0);
                    offsetY = (float) offset.optDouble("y", 0);
                }

                long l = System.currentTimeMillis();
                List<OcrResult> results = ocr.runBase64(base64, offsetX, offsetY);
                long times = System.currentTimeMillis() - l;
                Log.d("HttpOcrServer", "times: " + times + "ms results=" + results);

                JSONArray arr = new JSONArray();
                for (OcrResult r : results) {
                    arr.put(r.toJson());
                }
                JSONObject jo = new JSONObject();
                jo.put("code", 200);
                jo.put("data", arr);
                jo.put("times", times);
                return newFixedLengthResponse(jo.toString());
            } catch (Exception e) {
                Log.e("HttpOcrServer", "serve: ", e);
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR,
                        "application/json",
                        "{\"message\":\"" + e.getMessage() + "\",\"code\":500}");
            }
        }
        return newFixedLengthResponse("OCR Service Running");
    }
}
