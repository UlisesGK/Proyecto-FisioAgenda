package com.fisioagenda.ui;

import com.fisioagenda.service.ServiceException;

import javax.swing.SwingWorker;
import java.awt.Component;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Ejecuta operaciones de base de datos fuera del hilo de eventos (EDT) con {@link SwingWorker}
 * y entrega el resultado de vuelta en el EDT. Los errores de negocio ({@link ServiceException})
 * se muestran tal cual; cualquier otro error se registra y se muestra un mensaje genérico.
 */
public final class Tareas {

    private Tareas() {
    }

    /**
     * Ejecuta {@code trabajo} en segundo plano y, al terminar, llama a {@code alTerminar} en el EDT.
     * Los errores se muestran en un diálogo sobre {@code padre}.
     */
    public static <T> SwingWorker<T, Void> ejecutar(Component padre, Callable<T> trabajo, Consumer<T> alTerminar) {
        return ejecutar(trabajo, alTerminar, error -> mostrarError(padre, error));
    }

    /**
     * Variante con tratamiento de errores propio: {@code alFallar} recibe la excepción original
     * (también en el EDT).
     */
    public static <T> SwingWorker<T, Void> ejecutar(Callable<T> trabajo, Consumer<T> alTerminar,
                                                    Consumer<Throwable> alFallar) {
        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override
            protected T doInBackground() throws Exception {
                return trabajo.call();
            }

            @Override
            protected void done() {
                T resultado;
                try {
                    resultado = get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (ExecutionException e) {
                    alFallar.accept(e.getCause() != null ? e.getCause() : e);
                    return;
                } catch (CancellationException e) {
                    return;
                }
                alTerminar.accept(resultado);
            }
        };
        worker.execute();
        return worker;
    }

    /** Muestra un error: el mensaje de negocio si es una {@link ServiceException}, o uno genérico. */
    public static void mostrarError(Component padre, Throwable error) {
        if (error instanceof ServiceException) {
            Avisos.error(padre, error.getMessage());
        } else {
            Avisos.errorInesperado(padre, error);
        }
    }

    /**
     * Ejecuta una acción corta directamente en el EDT capturando los errores de la misma forma.
     *
     * @return {@code true} si la acción terminó sin errores
     */
    public static boolean enEdt(Component padre, Runnable accion) {
        try {
            accion.run();
            return true;
        } catch (ServiceException e) {
            Avisos.error(padre, e.getMessage());
        } catch (RuntimeException e) {
            Avisos.errorInesperado(padre, e);
        }
        return false;
    }
}
