package com.proyecto.sda.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Maneja el ciclo de vida del acelerómetro lineal.
 * Usa TYPE_LINEAR_ACCELERATION para ignorar gravedad.
 * Compatible Java 7 puro, sin lambdas.
 */
public class SensorService implements SensorEventListener {

    public interface SensorDataListener {
        void onSampleReceived(float x, float y, float z, long timestampNs);
        void onSensorUnavailable();
    }

    private final SensorManager sensorManager;
    private Sensor linearAccelSensor;
    private SensorDataListener listener;

    // Buffer circular para almacenar muestras durante captura
    private final List<float[]> sampleBuffer;
    private boolean capturing;

    // Estadísticas
    private long sampleCount;
    private long captureStartMs;

    public SensorService(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        sampleBuffer = new ArrayList<float[]>();
        capturing = false;
        sampleCount = 0;
    }

    /**
     * Verifica disponibilidad del sensor y registra listener.
     * @return true si el sensor está disponible
     */
    public boolean startCapture(SensorDataListener dataListener) {
        this.listener = dataListener;
        sampleBuffer.clear();
        sampleCount = 0;

        linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        if (linearAccelSensor == null) {
            // Fallback a acelerómetro estándar si no hay LINEAR_ACCELERATION
            linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (linearAccelSensor == null) {
                if (listener != null) listener.onSensorUnavailable();
                return false;
            }
        }

        capturing = true;
        captureStartMs = System.currentTimeMillis();
        sensorManager.registerListener(this, linearAccelSensor, SensorManager.SENSOR_DELAY_FASTEST);
        return true;
    }

    public void stopCapture() {
        capturing = false;
        sensorManager.unregisterListener(this);
    }

    public boolean isCapturing() {
        return capturing;
    }

    public long getSampleCount() {
        return sampleCount;
    }

    public long getElapsedSeconds() {
        if (!capturing) return 0;
        return (System.currentTimeMillis() - captureStartMs) / 1000;
    }

    /**
     * Devuelve copia del buffer de muestras [x, y, z, timestamp_s].
     */
    public List<float[]> getSampleBuffer() {
        List<float[]> copy = new ArrayList<float[]>(sampleBuffer);
        return copy;
    }

    public void clearBuffer() {
        sampleBuffer.clear();
        sampleCount = 0;
    }

    // ---- SensorEventListener ----

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!capturing) return;

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        long ts = event.timestamp; // nanosegundos

        sampleCount++;

        // Guardar en buffer: [x, y, z, tiempo_segundos]
        float timeSec = ts / 1_000_000_000.0f;
        sampleBuffer.add(new float[]{x, y, z, timeSec});

        // Notificar listener para graficado en tiempo real
        if (listener != null) {
            listener.onSampleReceived(x, y, z, ts);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No requerido para este caso de uso
    }

    /**
     * Calcula la frecuencia de muestreo real obtenida.
     * @return Hz aproximados
     */
    public float getActualSampleRateHz() {
        if (sampleBuffer.size() < 2) return 0f;
        float[] first = sampleBuffer.get(0);
        float[] last = sampleBuffer.get(sampleBuffer.size() - 1);
        float duration = last[3] - first[3]; // segundos
        if (duration <= 0) return 0f;
        return sampleBuffer.size() / duration;
    }
}
