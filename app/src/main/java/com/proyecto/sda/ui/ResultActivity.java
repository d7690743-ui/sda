package com.proyecto.sda.ui;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.proyecto.sda.R;
import com.proyecto.sda.storage.StorageManager;

/**
 * Muestra el resultado del diagnóstico con semáforo visual
 * y opciones de exportación CSV/TXT.
 */
public class ResultActivity extends AppCompatActivity {

    public static final String EXTRA_BASE_FREQ   = "base_freq";
    public static final String EXTRA_NEW_FREQ    = "new_freq";
    public static final String EXTRA_DEVIATION   = "deviation";
    public static final String EXTRA_CLASS       = "classification";
    public static final String EXTRA_SAMPLE_RATE = "sample_rate";
    public static final String EXTRA_FREQUENCIES = "frequencies";
    public static final String EXTRA_AMPLITUDES  = "amplitudes";

    // Colores semáforo
    private static final int COLOR_GREEN  = 0xFF2E7D32;
    private static final int COLOR_YELLOW = 0xFFF57F17;
    private static final int COLOR_RED    = 0xFFB71C1C;

    private StorageManager storageManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        storageManager = new StorageManager(this);

        // Recuperar datos del intent
        final float baseFreq   = getIntent().getFloatExtra(EXTRA_BASE_FREQ, 0f);
        final float newFreq    = getIntent().getFloatExtra(EXTRA_NEW_FREQ, 0f);
        final float deviation  = getIntent().getFloatExtra(EXTRA_DEVIATION, 0f);
        final int   cls        = getIntent().getIntExtra(EXTRA_CLASS, 0);
        final float[] freqs    = getIntent().getFloatArrayExtra(EXTRA_FREQUENCIES);
        final float[] amps     = getIntent().getFloatArrayExtra(EXTRA_AMPLITUDES);

        // Views
        LinearLayout layoutSemaphore = (LinearLayout) findViewById(R.id.layout_semaphore);
        TextView tvTitle             = (TextView)     findViewById(R.id.tv_result_title);
        TextView tvDescription       = (TextView)     findViewById(R.id.tv_result_desc);
        TextView tvBaseFreq          = (TextView)     findViewById(R.id.tv_result_base_freq);
        TextView tvNewFreq           = (TextView)     findViewById(R.id.tv_result_new_freq);
        TextView tvDeviation         = (TextView)     findViewById(R.id.tv_result_deviation);
        Button   btnExportSpectrum   = (Button)       findViewById(R.id.btn_export_spectrum);
        Button   btnExportReport     = (Button)       findViewById(R.id.btn_export_report);
        Button   btnClose            = (Button)       findViewById(R.id.btn_close);

        // Datos frecuencias
        tvBaseFreq.setText(String.format("f₀ (Base):   %.4f Hz", baseFreq));
        tvNewFreq.setText( String.format("f₁ (Actual): %.4f Hz", newFreq));
        tvDeviation.setText(String.format("Desviación:  %.2f%%", deviation));

        // Semáforo
        String[] titles = {
            "✓ ESTRUCTURA ESTABLE",
            "⚠ ALERTA: Posible pérdida de rigidez",
            "✗ PELIGRO: Daño estructural severo"
        };
        String[] descs = {
            "La firma espectral no muestra variaciones significativas respecto a la línea base. " +
            "El elemento estructural presenta su rigidez original dentro de un margen del 3%.",
            "Se detectó una reducción de frecuencia natural entre 3% y 10%. " +
            "Puede indicar microfisuras, pérdida de uniones o acumulación de fatiga. " +
            "Se recomienda inspección visual y monitoreo continuo.",
            "La frecuencia natural ha disminuido más del 10% respecto a la calibración. " +
            "Esto indica una pérdida severa de rigidez estructural. " +
            "SUSPENDA el uso del elemento y realice inspección técnica inmediata."
        };
        int[] colors = {COLOR_GREEN, COLOR_YELLOW, COLOR_RED};

        int safeIdx = (cls >= 0 && cls <= 2) ? cls : 0;
        layoutSemaphore.setBackgroundColor(colors[safeIdx]);
        tvTitle.setText(titles[safeIdx]);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvDescription.setText(descs[safeIdx]);

        // Exportar espectro CSV
        btnExportSpectrum.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (freqs == null || amps == null) {
                    Toast.makeText(ResultActivity.this, "Sin datos de espectro", Toast.LENGTH_SHORT).show();
                    return;
                }
                String path = storageManager.exportSpectrumCSV(freqs, amps, "diagnostico");
                if (path != null) {
                    Toast.makeText(ResultActivity.this, "CSV guardado:\n" + path, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(ResultActivity.this, "Error al exportar", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Exportar reporte TXT
        btnExportReport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String path = storageManager.exportDiagnosticReport(
                        baseFreq, newFreq, deviation, cls,
                        storageManager.getCalibrationDate());
                if (path != null) {
                    Toast.makeText(ResultActivity.this, "Reporte guardado:\n" + path, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(ResultActivity.this, "Error al exportar", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
}
