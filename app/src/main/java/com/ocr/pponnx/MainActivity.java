package com.ocr.pponnx;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        findViewById(R.id.btnStart).setOnClickListener(v -> {
            Intent i = new Intent(this, OcrForegroundService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(i);
            }
        });


        findViewById(R.id.btnStop).setOnClickListener(v -> {
            Intent i = new Intent(this, OcrForegroundService.class);
            stopService(i);
        });
    }
}