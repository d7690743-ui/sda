package com.proyecto.sda.ui;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import com.proyecto.sda.R;
import com.proyecto.sda.math.FFTProcessor;
import com.proyecto.sda.sensors.SensorService;
import com.proyecto.sda.storage.StorageManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Módulo de diagnóstico: captura nuevo estado y compara con calibración.
 * Pasa resultados a ResultActivity.
 */
public class DiagnosticActivity extends AppCompatActivity implements SensorService.SensorDataListener {

    private static final int CAPTURE_DURATION_MS = 5000;
    private static final int MAX_CHART_POINTS    = 200;

    private SensorService sensorService;
    private StorageManager storageManager;
    private Handler handler;

    private Button btnStart;
    private TextView tvStatus;
    private TextView tvSamples;
    private TextView tvBaseInfo;
    private ProgressBar progressBar;
    private LineChart chartWaveform;
    private LineChart chartSpectrum;

    private FFTProcessor.FFTResult diagResult;
    private List<float[]> capturedSamples;

    private List<Entry> waveformEntries;
    private int waveformIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture);

        sensorService   = new SensorService(this);
        storageManager  = new StorageManager(this);
        handler         = new Handler();
        waveformEntries = new ArrayList<Entry>();
        capturedSamples = new ArrayList<float[]>();

        btnStart     = (Button)      findViewById(R.id.btn_start);
        btnSave      = (Button)      findViewById(R.id.btn_save);
        tvStatus     = (TextView)    findViewById(R.id.tv_status);
        tvSamples    = (TextView)    findViewById(R.id.tv_samples);
        tvFreqResult = (TextView)    findViewById(R.id.tv_freq_result);
        progressBar  = (ProgressBar) findViewById(R.id.progress_bar);
        chartWaveform = (LineChart)  findViewById(R.id.chart_waveform);
        chartSpectrum = (LineChart)  findViewById(R.id.chart_spectrum);

        // Reutilizamos btn_save como "Analizar"
        btnSave.setText("Analizar y comparar");
        btnSave.setEnabled(false);
        btnSave.setVisibility(View.GONE);

        float f0 = storageManager.getBaseFrequency();
        tvStatus.setText(String.format("Referencia: f₀ = %.4f Hz (%s)",
                f0, storageManager.getCalibrationDate()));

        configureChart(chartWaveform);
        configureChart(chartSpectrum);

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { startCapture(); }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { goToResult(); }
        });
    }

    // Campos reutilizados del layout activity_capture
    private Button btnSave;
    private TextView tvFreqResult;

    private void configureChart(LineChart chart) {
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getAxisRight().setEnabled(false);
        chart.setNoDataText("Sin datos");
    }

    private void startCapture() {
        waveformEntries.clear();
        waveformIndex = 0;
        capturedSamples.clear();
        diagResult = null;

        btnStart.setEnabled(false);
        btnSave.setEnabled(false);
        btnSave.setVisibility(View.GONE);
        progressBar.setProgress(0);
        tvFreqResult.setText("—");

        float f0 = storageManager.getBaseFrequency();
        tvStatus.setText(String.format("Capturando... (f₀ ref = %.4f Hz)", f0));

        boolean ok = sensorService.startCapture(this);
        if (!ok) {
            tvStatus.setText("Error: sensor no disponible");
            btnStart.setEnabled(true);
            return;
        }

        handler.postDelayed(new Runnable() {
            @Override
            public void run() { stopAndProcess(); }
        }, CAPTURE_DURATION_MS);

        updateProgress(0);
    }

    private void updateProgress(final int elapsed) {
        if (elapsed >= CAPTURE_DURATION_MS) return;
        progressBar.setProgress((elapsed * 100) / CAPTURE_DURATION_MS);
        tvSamples.setText("Muestras: " + sensorService.getSampleCount());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() { updateProgress(elapsed + 250); }
        }, 250);
    }

    private void stopAndProcess() {
        sensorService.stopCapture();
        progressBar.setProgress(100);

        capturedSamples = sensorService.getSampleBuffer();
        float sr = sensorService.getActualSampleRateHz();
        tvSamples.setText("Muestras: " + capturedSamples.size() + " | SR: "
                + String.format("%.1f Hz", sr));

        if (capturedSamples.size() < 8) {
            tvStatus.setText("Error: pocas muestras");
            btnStart.setEnabled(true);
            return;
        }

        diagResult = FFTProcessor.analyze(capturedSamples, sr);

        float f0  = storageManager.getBaseFrequency();
        float f1  = diagResult.dominantFrequencyHz;
        float dev = FFTProcessor.calculateDeviationPercent(f0, f1);
        int   cls = FFTProcessor.classifyStructure(dev);

        String[] labels = {"ESTABLE", "ALERTA", "PELIGRO"};
        tvFreqResult.setText(String.format("f₁ = %.4f Hz (eje %s) | Δ = %.2f%% → %s", f1, diagResult.dominantAxis, dev, labels[cls]));
        tvStatus.setText("Listo. Pulse 'Analizar y comparar' para el reporte completo.");

        updateSpectrumChart(diagResult.frequencies, diagResult.amplitudes, f1);

        btnSave.setEnabled(true);
        btnSave.setVisibility(View.VISIBLE);
        btnStart.setEnabled(true);
    }

    private void goToResult() {
        if (diagResult == null) return;
        float f0  = storageManager.getBaseFrequency();
        float f1  = diagResult.dominantFrequencyHz;
        float dev = FFTProcessor.calculateDeviationPercent(f0, f1);
        int   cls = FFTProcessor.classifyStructure(dev);

        Intent intent = new Intent(this, ResultActivity.class);
        intent.putExtra(ResultActivity.EXTRA_BASE_FREQ,   f0);
        intent.putExtra(ResultActivity.EXTRA_NEW_FREQ,    f1);
        intent.putExtra(ResultActivity.EXTRA_DEVIATION,   dev);
        intent.putExtra(ResultActivity.EXTRA_CLASS,       cls);
        intent.putExtra(ResultActivity.EXTRA_SAMPLE_RATE, diagResult.sampleRateHz);
        intent.putExtra(ResultActivity.EXTRA_FREQUENCIES, diagResult.frequencies);
        intent.putExtra(ResultActivity.EXTRA_AMPLITUDES,  diagResult.amplitudes);
        startActivity(intent);
    }

    // ---- SensorDataListener ----

    @Override
    public void onSampleReceived(final float x, final float y, final float z, long ts) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                float mag = (float) Math.sqrt(x*x + y*y + z*z);
                waveformEntries.add(new Entry(waveformIndex++, mag));
                if (waveformEntries.size() > MAX_CHART_POINTS) waveformEntries.remove(0);
                refreshWaveformChart();
            }
        });
    }

    @Override
    public void onSensorUnavailable() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvStatus.setText("Sensor no disponible");
                btnStart.setEnabled(true);
            }
        });
    }

    private void refreshWaveformChart() {
        if (waveformEntries.isEmpty()) return;
        LineDataSet ds = new LineDataSet(new ArrayList<Entry>(waveformEntries), "");
        ds.setDrawCircles(false);
        ds.setDrawValues(false);
        ds.setColor(0xFF1565C0);
        ds.setLineWidth(1f);
        chartWaveform.setData(new LineData(ds));
        chartWaveform.invalidate();
    }

    private void updateSpectrumChart(float[] freqs, float[] amps, float domFreq) {
        List<Entry> entries = new ArrayList<Entry>();
        for (int i = 0; i < freqs.length; i++) entries.add(new Entry(freqs[i], amps[i]));
        LineDataSet ds = new LineDataSet(entries, "");
        ds.setDrawCircles(false);
        ds.setDrawValues(false);
        ds.setColor(0xFFB71C1C);
        ds.setLineWidth(1.5f);
        chartSpectrum.setData(new LineData(ds));
        chartSpectrum.getDescription().setEnabled(true);
        chartSpectrum.getDescription().setText(String.format("Pico: %.2f Hz", domFreq));
        chartSpectrum.invalidate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        sensorService.stopCapture();
    }
}
