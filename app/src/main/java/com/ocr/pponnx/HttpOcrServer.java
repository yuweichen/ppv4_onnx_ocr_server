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
                long l = System.currentTimeMillis();
                List<OcrResult> results = ocr.runBase64(base64);
                long times = System.currentTimeMillis() - l;
                Log.d("HttpOcrServer", "times: " + times + "ms results=" + results);
                JSONArray arr = new JSONArray();
                for (OcrResult r : results) {
                    arr.put(r.toJson());
                }
                JSONObject jo = new JSONObject();
                jo.put("code", Response.Status.OK);
                jo.put("data", arr);
                jo.put("times", times);
                return newFixedLengthResponse(jo.toString());
            } catch (Exception e) {
                Log.e("error", "serve: ", e);
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR,
                        "application/json",
                        "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
        return newFixedLengthResponse("OCR Service Running");
    }
}