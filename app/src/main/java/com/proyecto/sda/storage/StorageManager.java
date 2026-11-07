package com.proyecto.sda.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Persistencia de calibraciones y exportación CSV.
 * Java 7 puro, sin AndroidX.
 */
public class StorageManager {

    private static final String PREFS_NAME    = "sda_calibration";
    private static final String KEY_BASE_FREQ = "base_frequency_hz";
    private static final String KEY_BASE_AMP  = "base_amplitude";
    private static final String KEY_BASE_DATE = "base_date";
    private static final String KEY_BASE_RATE = "base_sample_rate";
    private static final String KEY_HAS_CALIB = "has_calibration";

    private final SharedPreferences prefs;
    private final Context context;

    public StorageManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ---- Calibración (Línea Base) ----

    public void saveCalibration(float freqHz, float amplitude, float sampleRateHz) {
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        prefs.edit()
            .putFloat(KEY_BASE_FREQ, freqHz)
            .putFloat(KEY_BASE_AMP, amplitude)
            .putFloat(KEY_BASE_RATE, sampleRateHz)
            .putString(KEY_BASE_DATE, date)
            .putBoolean(KEY_HAS_CALIB, true)
            .apply();
    }

    public boolean hasCalibration() {
        return prefs.getBoolean(KEY_HAS_CALIB, false);
    }

    public float getBaseFrequency() {
        return prefs.getFloat(KEY_BASE_FREQ, 0f);
    }

    public float getBaseAmplitude() {
        return prefs.getFloat(KEY_BASE_AMP, 0f);
    }

    public float getBaseSampleRate() {
        return prefs.getFloat(KEY_BASE_RATE, 200f);
    }

    public String getCalibrationDate() {
        return prefs.getString(KEY_BASE_DATE, "N/A");
    }

    public void clearCalibration() {
        prefs.edit().clear().apply();
    }

    // ---- Exportación CSV ----

    /**
     * Exporta muestras crudas a CSV en almacenamiento interno (no requiere permiso en API 21+).
     * Formato: timestamp_s,x,y,z,magnitude
     *
     * @param samples Lista de float[]{x, y, z, tiempo_s}
     * @param prefix  Prefijo del nombre de archivo ("calibracion" o "diagnostico")
     * @return Ruta del archivo generado, o null si falla
     */
    public String exportRawCSV(List<float[]> samples, String prefix) {
        File dir = new File(context.getExternalFilesDir(null), "SDA_exports");
        if (!dir.exists()) dir.mkdirs();

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        File file = new File(dir, prefix + "_" + timestamp + ".csv");

        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(file));
            writer.write("timestamp_s,x_ms2,y_ms2,z_ms2,magnitude_ms2\n");

            for (int i = 0; i < samples.size(); i++) {
                float[] s = samples.get(i);
                float mag = (float) Math.sqrt(s[0]*s[0] + s[1]*s[1] + s[2]*s[2]);
                writer.write(String.format(Locale.US, "%.6f,%.6f,%.6f,%.6f,%.6f\n",
                        s[3], s[0], s[1], s[2], mag));
            }
            return file.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (IOException e) { /* ignorar */ }
            }
        }
    }

    /**
     * Exporta el espectro FFT a CSV.
     * Formato: frequency_hz,amplitude
     */
    public String exportSpectrumCSV(float[] frequencies, float[] amplitudes, String prefix) {
        File dir = new File(context.getExternalFilesDir(null), "SDA_exports");
        if (!dir.exists()) dir.mkdirs();

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        File file = new File(dir, prefix + "_espectro_" + timestamp + ".csv");

        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(file));
            writer.write("frequency_hz,amplitude\n");
            for (int i = 0; i < frequencies.length; i++) {
                writer.write(String.format(Locale.US, "%.4f,%.8f\n",
                        frequencies[i], amplitudes[i]));
            }
            return file.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (IOException e) { /* ignorar */ }
            }
        }
    }

    /**
     * Exporta reporte de diagnóstico completo.
     */
    public String exportDiagnosticReport(float baseFreq, float newFreq,
                                          float deviation, int classification,
                                          String baseDate) {
        File dir = new File(context.getExternalFilesDir(null), "SDA_exports");
        if (!dir.exists()) dir.mkdirs();

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        File file = new File(dir, "reporte_" + timestamp + ".txt");

        String[] estados = {"ESTABLE", "ALERTA - Posible pérdida de rigidez", "PELIGRO - Daño severo"};

        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(file));
            writer.write("=== REPORTE SDA - Structural Dynamic Analyzer ===\n");
            writer.write("Fecha de diagnóstico: " + timestamp + "\n\n");
            writer.write("CALIBRACIÓN (Línea Base)\n");
            writer.write("  Fecha calibración: " + baseDate + "\n");
            writer.write(String.format(Locale.US, "  Frecuencia base (f0): %.4f Hz\n", baseFreq));
            writer.write("\nDIAGNÓSTICO ACTUAL\n");
            writer.write(String.format(Locale.US, "  Frecuencia actual (f1): %.4f Hz\n", newFreq));
            writer.write(String.format(Locale.US, "  Desviación: %.2f%%\n", deviation));
            writer.write("\nRESULTADO: " + estados[classification] + "\n");
            writer.write("\nAlgoritmo: FFT con ventana Hanning + interpolación parabólica\n");
            writer.write("Principio: IPN Análisis Dinámico por Vibración\n");
            return file.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (IOException e) { /* ignorar */ }
            }
        }
    }
}
