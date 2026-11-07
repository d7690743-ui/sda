package com.proyecto.sda.ui;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
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
 * Captura la vibración de referencia y guarda la calibración.
 * Duración de captura: 5 segundos por defecto.
 */
public class CalibrationActivity extends AppCompatActivity implements SensorService.SensorDataListener {

    private static final int CAPTURE_DURATION_MS = 5000;
    private static final int MAX_CHART_POINTS    = 200;

    private SensorService sensorService;
    private StorageManager storageManager;
    private Handler handler;

    // UI
    private Button btnStart;
    private Button btnSave;
    private TextView tvStatus;
    private TextView tvSamples;
    private TextView tvFreqResult;
    private ProgressBar progressBar;
    private LineChart chartWaveform;
    private LineChart chartSpectrum;

    // Estado
    private FFTProcessor.FFTResult lastResult;
    private boolean captureComplete;

    // Datos para gráfica en tiempo real
    private List<Entry> waveformEntries;
    private int waveformIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture);

        sensorService  = new SensorService(this);
        storageManager = new StorageManager(this);
        handler        = new Handler();
        waveformEntries = new ArrayList<Entry>();
        waveformIndex  = 0;

        // Views
        btnStart     = (Button)      findViewById(R.id.btn_start);
        btnSave      = (Button)      findViewById(R.id.btn_save);
        tvStatus     = (TextView)    findViewById(R.id.tv_status);
        tvSamples    = (TextView)    findViewById(R.id.tv_samples);
        tvFreqResult = (TextView)    findViewById(R.id.tv_freq_result);
        progressBar  = (ProgressBar) findViewById(R.id.progress_bar);
        chartWaveform = (LineChart)  findViewById(R.id.chart_waveform);
        chartSpectrum = (LineChart)  findViewById(R.id.chart_spectrum);

        setupCharts();

        btnSave.setEnabled(false);
        btnSave.setVisibility(View.GONE);

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCapture();
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCalibration();
            }
        });
    }

    private void setupCharts() {
        configureLineChart(chartWaveform, "Aceleración (m/s²)");
        configureLineChart(chartSpectrum, "Amplitud");
    }

    private void configureLineChart(LineChart chart, String yLabel) {
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setLabelCount(4, true);
        chart.setNoDataText("Sin datos aún");
    }

    private void startCapture() {
        waveformEntries.clear();
        waveformIndex = 0;
        captureComplete = false;
        lastResult = null;
        btnStart.setEnabled(false);
        btnSave.setEnabled(false);
        btnSave.setVisibility(View.GONE);
        progressBar.setProgress(0);
        tvStatus.setText("Capturando... coloque el dispositivo sobre la estructura");
        tvFreqResult.setText("—");

        boolean ok = sensorService.startCapture(this);
        if (!ok) {
            tvStatus.setText("Error: acelerómetro no disponible");
            btnStart.setEnabled(true);
            return;
        }

        // Detener captura tras CAPTURE_DURATION_MS
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopAndProcess();
            }
        }, CAPTURE_DURATION_MS);

        // Actualizar progreso cada 250ms
        updateProgress(0);
    }

    private void updateProgress(final int elapsed) {
        if (elapsed >= CAPTURE_DURATION_MS) return;
        int pct = (elapsed * 100) / CAPTURE_DURATION_MS;
        progressBar.setProgress(pct);
        tvSamples.setText("Muestras: " + sensorService.getSampleCount());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateProgress(elapsed + 250);
            }
        }, 250);
    }

    private void stopAndProcess() {
        sensorService.stopCapture();
        progressBar.setProgress(100);
        tvStatus.setText("Procesando FFT...");

        List<float[]> samples = sensorService.getSampleBuffer();
        float sr = sensorService.getActualSampleRateHz();
        tvSamples.setText("Muestras: " + samples.size() + " | SR: " + String.format("%.1f Hz", sr));

        if (samples.size() < 8) {
            tvStatus.setText("Error: pocas muestras. Intente nuevamente.");
            btnStart.setEnabled(true);
            return;
        }

        lastResult = FFTProcessor.analyze(samples, sr);
        captureComplete = true;

        tvFreqResult.setText(String.format("f dominante = %.4f Hz (eje %s)", lastResult.dominantFrequencyHz, lastResult.dominantAxis));
        tvStatus.setText("Análisis completado");

        updateSpectrumChart(lastResult.frequencies, lastResult.amplitudes, lastResult.dominantFrequencyHz);

        btnSave.setEnabled(true);
        btnSave.setVisibility(View.VISIBLE);
        btnStart.setEnabled(true);
    }

    private void saveCalibration() {
        if (lastResult == null) return;
        storageManager.saveCalibration(
                lastResult.dominantFrequencyHz,
                lastResult.dominantAmplitude,
                lastResult.sampleRateHz);
        Toast.makeText(this,
                String.format("Calibración guardada: %.4f Hz", lastResult.dominantFrequencyHz),
                Toast.LENGTH_LONG).show();
        finish();
    }

    // ---- SensorDataListener ----

    @Override
    public void onSampleReceived(final float x, final float y, final float z, long timestampNs) {
        // Actualizar gráfica de onda en hilo principal
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                float mag = (float) Math.sqrt(x*x + y*y + z*z);
                waveformEntries.add(new Entry(waveformIndex++, mag));

                // Limitar puntos en pantalla
                if (waveformEntries.size() > MAX_CHART_POINTS) {
                    waveformEntries.remove(0);
                }
                refreshWaveformChart();
            }
        });
    }

    @Override
    public void onSensorUnavailable() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvStatus.setText("Sensor no disponible en este dispositivo");
                btnStart.setEnabled(true);
            }
        });
    }

    private void refreshWaveformChart() {
        if (waveformEntries.isEmpty()) return;
        LineDataSet ds = new LineDataSet(new ArrayList<Entry>(waveformEntries), "Magnitud");
        ds.setDrawCircles(false);
        ds.setDrawValues(false);
        ds.setColor(0xFF1565C0);
        ds.setLineWidth(1f);
        chartWaveform.setData(new LineData(ds));
        chartWaveform.invalidate();
    }

    private void updateSpectrumChart(float[] freqs, float[] amps, float domFreq) {
        List<Entry> entries = new ArrayList<Entry>();
        for (int i = 0; i < freqs.length; i++) {
            entries.add(new Entry(freqs[i], amps[i]));
        }
        LineDataSet ds = new LineDataSet(entries, "Espectro");
        ds.setDrawCircles(false);
        ds.setDrawValues(false);
        ds.setColor(0xFFB71C1C);
        ds.setLineWidth(1.5f);

        chartSpectrum.setData(new LineData(ds));
        chartSpectrum.getXAxis().setAxisMinimum(0f);
        chartSpectrum.invalidate();

        // Marcar frecuencia dominante con descripción
        chartSpectrum.getDescription().setEnabled(true);
        chartSpectrum.getDescription().setText(String.format("Pico: %.2f Hz", domFreq));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        sensorService.stopCapture();
    }
}
