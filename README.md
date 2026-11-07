# SDA - Structural Dynamic Analyzer (2.0 moderna)

Analizador dinámico estructural: mide la frecuencia natural de una
estructura con el acelerómetro del teléfono. Calibra en estado sano
(f₀), diagnostica después de un evento (f₁) y clasifica con semáforo:
verde < 3% de desviación, amarillo 3–10%, rojo > 10%. Exporta CSV del
espectro y reporte TXT.

## Qué cambió frente a la 1.0 (Java 7 / AIDE)

- **Ya no es Java 7 ni Eclipse**: proyecto Gradle, Java 17, AndroidX,
  `compileSdk`/`targetSdk` 35, `minSdk` 24. Se compila con GitHub
  Actions (workflow `Build APK (debug)`), no con AIDE.
- **Sin JARs a mano**: JTransforms 3.1 llega de Maven Central y
  MPAndroidChart 3.1.0 de JitPack, por Gradle. No hay carpeta `libs/`.
- **MPAndroidChart 3.x, no 2.x**: el README viejo pedía la 2.x pero el
  código usa API de la 3.x (`Entry(x, y)`, `getDescription().setEnabled`,
  `setAxisMinimum`). Ahora la dependencia es la correcta.
- **FFT por eje, no por magnitud**: la 1.0 hacía el FFT sobre la
  magnitud 3D y una vibración en un solo eje salía al doble de
  frecuencia (|seno| tiene su fundamental en 2f). Ahora se analiza
  cada eje (X, Y, Z) por separado y se reporta el eje con el pico más
  fuerte, así el valor en Hz es el real. La pantalla muestra el eje
  ("f dominante = 4.32 Hz (eje X)"). La desviación % entre f₀ y f₁
  no cambiaba con ese error, pero el número en Hz sí.
- **Permisos**: ya no se pide `WRITE_EXTERNAL_STORAGE`; los exports
  van a la carpeta propia de la app (`getExternalFilesDir`), que no
  necesita permiso.
- **Manifest al día**: `android:exported` en las actividades, exigido
  desde Android 12 (targetSdk 35).

## Compilar

1. Crea el repo `sda` en GitHub (vacío, público).
2. Sube este proyecto (`git add -A`, commit, push a `main`).
3. En la pestaña **Actions** descarga el artifact `app-debug` y
   prueba el APK en el teléfono.

## Flujo de uso

1. **Calibrar**: apoye el dispositivo sobre la estructura en estado
   sano. Pulse "Calibrar" → "Iniciar captura" (5 segundos). La app
   captura el FFT y guarda f₀ en SharedPreferences.
2. **Diagnosticar**: tras un evento de estrés o sismo simulado.
   Pulse "Diagnosticar" → "Iniciar captura" (5 segundos). La app
   calcula f₁ y la desviación porcentual.
3. **Resultado**: semáforo automático (verde < 3%, amarillo 3–10%,
   rojo > 10%).
4. **Exportar**: CSV del espectro y reporte TXT en
   `/sdcard/Android/data/com.proyecto.sda/files/SDA_exports/`.

## Notas técnicas

- **Sensor**: TYPE_LINEAR_ACCELERATION (ignora gravedad), fallback a
  TYPE_ACCELEROMETER.
- **Frecuencia de muestreo**: SENSOR_DELAY_FASTEST (~200 Hz típico).
- **FFT**: JTransforms DoubleFFT_1D con ventana Hanning +
  interpolación parabólica del pico, un FFT por eje.
- **Versiones fijadas**: Java 17 (Temurin), Gradle 8.9, AGP 8.7.3,
  `compileSdk`/`targetSdk` 35, `minSdk` 24 (Android 7.0+).

## Algoritmo de clasificación (principio IPN)

```
Desviación = |f₀ - f₁| / f₀ × 100%

Si f₁ < f₀ → pérdida de rigidez (fisuras reducen masa efectiva/rigidez)
Si f₁ > f₀ → improbable en daño real (posible error de medición)
```
