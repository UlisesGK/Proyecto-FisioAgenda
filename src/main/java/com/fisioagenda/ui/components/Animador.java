package com.fisioagenda.ui.components;

import javax.swing.Timer;
import java.util.function.DoubleConsumer;

/**
 * Utilidad para animaciones sencillas basadas en {@link javax.swing.Timer} (se ejecutan en el EDT).
 * El progreso entregado ya va suavizado con una curva "ease-out" para que el movimiento sea natural.
 */
public final class Animador {

    /** Intervalo entre fotogramas (~60 fps). */
    private static final int INTERVALO_MS = 15;

    private Animador() {
    }

    /**
     * Inicia una animación.
     *
     * @param duracionMs  duración total en milisegundos
     * @param alAvanzar   recibe el progreso suavizado (0 a 1) en cada fotograma
     * @param alTerminar  acción opcional al finalizar (puede ser {@code null})
     * @return el temporizador en marcha, por si se quiere detener antes de tiempo
     */
    public static Timer animar(int duracionMs, DoubleConsumer alAvanzar, Runnable alTerminar) {
        final long inicio = System.nanoTime();
        final double duracion = Math.max(1, duracionMs);
        Timer timer = new Timer(INTERVALO_MS, null);
        timer.addActionListener(e -> {
            double t = (System.nanoTime() - inicio) / 1_000_000.0 / duracion;
            if (t >= 1) {
                timer.stop();
                alAvanzar.accept(1.0);
                if (alTerminar != null) {
                    alTerminar.run();
                }
            } else {
                alAvanzar.accept(suavizar(t));
            }
        });
        timer.setInitialDelay(0);
        timer.start();
        return timer;
    }

    /** Detiene un temporizador si está en marcha (admite {@code null}). */
    public static void detener(Timer timer) {
        if (timer != null && timer.isRunning()) {
            timer.stop();
        }
    }

    /** Curva cúbica de salida: rápida al principio y suave al final. */
    public static double suavizar(double t) {
        double p = Math.max(0, Math.min(1, t));
        double inv = 1 - p;
        return 1 - inv * inv * inv;
    }

    /** Interpolación lineal entre dos valores. */
    public static double interpolar(double desde, double hasta, double progreso) {
        return desde + (hasta - desde) * progreso;
    }
}
