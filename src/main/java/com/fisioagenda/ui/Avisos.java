package com.fisioagenda.ui;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Window;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Diálogos de aviso estándar (error, información, confirmación). Centraliza los textos y el
 * tratamiento de errores inesperados: al usuario se le muestra un mensaje genérico y el detalle
 * técnico queda en el registro (log).
 */
public final class Avisos {

    private static final Logger LOG = Logger.getLogger(Avisos.class.getName());

    /** Mensaje mostrado ante cualquier error no previsto. */
    public static final String MENSAJE_INESPERADO =
            "Se ha producido un error inesperado. Inténtelo de nuevo y, si el problema persiste, "
                    + "contacte con el administrador.";

    private Avisos() {
    }

    /** Muestra un mensaje de error de negocio (por ejemplo, el de una {@code ServiceException}). */
    public static void error(Component padre, String mensaje) {
        if (ventanaCerrada(padre)) {
            return;
        }
        JOptionPane.showMessageDialog(padre, mensaje, "No se pudo completar la acción", JOptionPane.ERROR_MESSAGE);
    }

    /** Registra un error no previsto y muestra al usuario un mensaje genérico. */
    public static void errorInesperado(Component padre, Throwable causa) {
        LOG.log(Level.SEVERE, "Error inesperado en la interfaz", causa);
        if (ventanaCerrada(padre)) {
            return;
        }
        JOptionPane.showMessageDialog(padre, MENSAJE_INESPERADO, "Error", JOptionPane.ERROR_MESSAGE);
    }

    /** Muestra un mensaje informativo. */
    public static void info(Component padre, String titulo, String mensaje) {
        if (ventanaCerrada(padre)) {
            return;
        }
        JOptionPane.showMessageDialog(padre, mensaje, titulo, JOptionPane.INFORMATION_MESSAGE);
    }

    /** Pide confirmación con botones Sí/No; devuelve {@code true} si el usuario acepta. */
    public static boolean confirmar(Component padre, String titulo, String mensaje) {
        if (ventanaCerrada(padre)) {
            return false;
        }
        Object[] opciones = {"Sí", "No"};
        int r = JOptionPane.showOptionDialog(padre, mensaje, titulo, JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, opciones, opciones[1]);
        return r == 0;
    }

    /** Evita mostrar diálogos sobre ventanas ya cerradas (por ejemplo, tras un cierre por inactividad). */
    private static boolean ventanaCerrada(Component padre) {
        if (padre == null) {
            return false;
        }
        Window ventana = padre instanceof Window ? (Window) padre : SwingUtilities.getWindowAncestor(padre);
        return ventana != null && !ventana.isDisplayable();
    }
}
