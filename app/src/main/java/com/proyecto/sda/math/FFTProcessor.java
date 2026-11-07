package com.proyecto.sda.math;

import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;

import java.util.List;

/**
 * Procesamiento FFT usando JTransforms 3.1 (Maven Central).
 *
 * 2.0: analiza cada eje (X, Y, Z) por separado y reporta el eje con el
 * pico dominante más fuerte. Así la frecuencia en Hz es la real: con
 * la magnitud 3D, una vibración en un solo eje sale al doble de
 * frecuencia porque |seno| tiene su fundamental en 2f. La desviación
 * % entre f0 y f1 no cambiaba con ese error, pero el valor en Hz sí.
 *
 * Java 17, sin lambdas (estilo del proyecto original).
 */
public class FFTProcessor {

    /** Resultado del análisis espectral */
    public static class FFTResult {
        public final float dominantFrequencyHz;
        public final float dominantAmplitude;
        public final String dominantAxis;   // "X", "Y" o "Z"
        public final float[] frequencies;   // eje X del espectro
        public final float[] amplitudes;    // eje Y del espectro
        public final float sampleRateHz;
        public final int sampleCount;

        public FFTResult(float domFreq, float domAmp, String domAxis,
                         float[] frequencies, float[] amplitudes,
                         float sampleRate, int samples) {
            this.dominantFrequencyHz = domFreq;
            this.dominantAmplitude = domAmp;
            this.dominantAxis = domAxis;
            this.frequencies = frequencies;
            this.amplitudes = amplitudes;
            this.sampleRateHz = sampleRate;
            this.sampleCount = samples;
        }
    }

    /**
     * Ejecuta FFT sobre los datos del acelerómetro, eje por eje.
     *
     * @param samples Lista de float[]{x, y, z, tiempo}
     * @param sampleRateHz Frecuencia de muestreo real
     * @return FFTResult del eje con el pico dominante más fuerte
     */
    public static FFTResult analyze(List<float[]> samples, float sampleRateHz) {
        int n = nextPowerOfTwo(samples.size());
        if (n > samples.size()) n = samples.size();
        n = nextPowerOfTwo(n);
        if (n > samples.size()) n = previousPowerOfTwo(samples.size());
        if (n < 4) n = 4;

        String[] axes = {"X", "Y", "Z"};
        float bestAmp = -1f;
        float bestFreq = 0f;
        String bestAxis = "X";
        float[] bestFreqs = null;
        float[] bestAmps = null;

        for (int a = 0; a < 3; a++) {
            double[] signal = new double[n * 2]; // JTransforms usa interleaved real/imag
            for (int i = 0; i < n; i++) {
                signal[2 * i]     = samples.get(i)[a]; // eje a
                signal[2 * i + 1] = 0.0;               // parte imaginaria = 0
            }

            // Aplicar ventana de Hanning para reducir spectral leakage
            applyHanningWindow(signal, n);

            // FFT compleja
            DoubleFFT_1D fft = new DoubleFFT_1D(n);
            fft.complexForward(signal);

            // Calcular amplitudes (solo primera mitad: frecuencias 0..Nyquist)
            int halfN = n / 2;
            float[] freqs = new float[halfN];
            float[] amps  = new float[halfN];

            float domFreq = 0f;
            float domAmp  = 0f;
            int domIdx    = 0;

            for (int k = 0; k < halfN; k++) {
                double re = signal[2 * k];
                double im = signal[2 * k + 1];
                float amp = (float) Math.sqrt(re * re + im * im) / n;

                freqs[k] = (float) k * sampleRateHz / n;
                amps[k]  = amp;

                // Ignorar DC (k=0)
                if (k > 0 && amp > domAmp) {
                    domAmp  = amp;
                    domFreq = freqs[k];
                    domIdx  = k;
                }
            }

            // Refinamiento por interpolación parabólica del pico dominante
            if (domIdx > 0 && domIdx < halfN - 1) {
                float alpha = amps[domIdx - 1];
                float beta  = amps[domIdx];
                float gamma = amps[domIdx + 1];
                float denom = alpha - 2f * beta + gamma;
                if (Math.abs(denom) > 1e-10f) {
                    float offset = 0.5f * (alpha - gamma) / denom;
                    domFreq = (domIdx + offset) * sampleRateHz / n;
                }
            }

            if (domAmp > bestAmp) {
                bestAmp = domAmp;
                bestFreq = domFreq;
                bestAxis = axes[a];
                bestFreqs = freqs;
                bestAmps = amps;
            }
        }

        return new FFTResult(bestFreq, bestAmp, bestAxis, bestFreqs, bestAmps, sampleRateHz, n);
    }

    /**
     * Compara dos firmas espectrales y calcula % de desviación.
     * @param baseFreq frecuencia de referencia (estado sano)
     * @param newFreq  frecuencia actual (estado a diagnosticar)
     * @return desviación porcentual (positivo = pérdida de rigidez)
     */
    public static float calculateDeviationPercent(float baseFreq, float newFreq) {
        if (baseFreq <= 0) return 0f;
        return Math.abs((baseFreq - newFreq) / baseFreq) * 100f;
    }

    /**
     * Clasifica el estado estructural según desviación.
     * @return 0=Estable, 1=Alerta, 2=Peligro
     */
    public static int classifyStructure(float deviationPercent) {
        if (deviationPercent < 3f)  return 0; // Estable
        if (deviationPercent < 10f) return 1; // Alerta
        return 2;                              // Peligro
    }

    // ---- Utilidades internas ----

    private static void applyHanningWindow(double[] signal, int n) {
        for (int i = 0; i < n; i++) {
            double w = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (n - 1)));
            signal[2 * i] *= w;
        }
    }

    private static int nextPowerOfTwo(int n) {
        int p = 1;
        while (p < n) p <<= 1;
        return p;
    }

    private static int previousPowerOfTwo(int n) {
        int p = 1;
        int prev = p;
        while (p <= n) { prev = p; p <<= 1; }
        return prev;
    }
}
