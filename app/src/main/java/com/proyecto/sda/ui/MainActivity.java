package com.proyecto.sda.ui;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.proyecto.sda.R;
import com.proyecto.sda.storage.StorageManager;

/**
 * Pantalla principal del SDA.
 * Muestra estado de calibración y navegación a módulos.
 */
public class MainActivity extends AppCompatActivity {

    private StorageManager storageManager;
    private TextView tvCalibStatus;
    private TextView tvBaseFreq;
    private Button btnCalibrate;
    private Button btnDiagnose;
    private Button btnClearCalib;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        storageManager = new StorageManager(this);

        tvCalibStatus = (TextView) findViewById(R.id.tv_calib_status);
        tvBaseFreq    = (TextView) findViewById(R.id.tv_base_freq);
        btnCalibrate  = (Button)   findViewById(R.id.btn_calibrate);
        btnDiagnose   = (Button)   findViewById(R.id.btn_diagnose);
        btnClearCalib = (Button)   findViewById(R.id.btn_clear_calib);

        btnCalibrate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, CalibrationActivity.class));
            }
        });

        btnDiagnose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!storageManager.hasCalibration()) {
                    tvCalibStatus.setText("⚠ Realice una calibración primero");
                    tvCalibStatus.setTextColor(0xFFFF6600);
                    return;
                }
                startActivity(new Intent(MainActivity.this, DiagnosticActivity.class));
            }
        });

        btnClearCalib.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                storageManager.clearCalibration();
                updateCalibrationUI();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCalibrationUI();
    }

    private void updateCalibrationUI() {
        if (storageManager.hasCalibration()) {
            float f0 = storageManager.getBaseFrequency();
            String date = storageManager.getCalibrationDate();
            tvCalibStatus.setText("✓ Calibrado");
            tvCalibStatus.setTextColor(0xFF2E7D32);
            tvBaseFreq.setText(String.format("f₀ = %.4f Hz\n%s", f0, date));
            btnDiagnose.setEnabled(true);
            btnClearCalib.setVisibility(View.VISIBLE);
        } else {
            tvCalibStatus.setText("Sin calibración");
            tvCalibStatus.setTextColor(0xFF757575);
            tvBaseFreq.setText("—");
            btnDiagnose.setEnabled(false);
            btnClearCalib.setVisibility(View.GONE);
        }
    }
}
