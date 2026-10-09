package com.fisioagenda.ui.components;

import com.fisioagenda.ui.Tema;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ayudante para construir formularios con {@link GridBagLayout}: cada campo lleva su etiqueta
 * encima (asociada con {@code setLabelFor} y mnemónico) y un hueco para el mensaje de error debajo.
 * Los errores se marcan con el contorno rojo de FlatLaf ({@code JComponent.outline = error}).
 */
public class FormBuilder {

    private static final int SEPARACION_COLUMNAS = 16;

    private final JPanel panel = new JPanel(new GridBagLayout());
    private final int columnas;
    private int fila;
    private int columna;
    private final Map<JComponent, JLabel> etiquetasError = new HashMap<>();
    private final Map<JComponent, JComponent> visuales = new HashMap<>();
    private final List<JComponent> conError = new ArrayList<>();

    /** @param columnas número de columnas del formulario (1 o 2 habitualmente) */
    public FormBuilder(int columnas) {
        this.columnas = Math.max(1, columnas);
        panel.setOpaque(false);
    }

    /** Añade un campo que ocupa una columna. */
    public <C extends JComponent> C campo(String etiqueta, char mnemonico, C campo) {
        agregar(etiqueta, mnemonico, campo, campo, 1);
        return campo;
    }

    /** Añade un campo que ocupa todo el ancho del formulario. */
    public <C extends JComponent> C campoAncho(String etiqueta, char mnemonico, C campo) {
        agregar(etiqueta, mnemonico, campo, campo, columnas);
        return campo;
    }

    /**
     * Añade un campo cuyo componente visible es distinto del que recibe el foco
     * (por ejemplo, un área de texto dentro de un {@code JScrollPane}).
     */
    public <C extends JComponent> C campoCompuesto(String etiqueta, char mnemonico, C foco, JComponent visual,
                                                   boolean ancho) {
        agregar(etiqueta, mnemonico, foco, visual, ancho ? columnas : 1);
        return foco;
    }

    /** Añade un componente libre que ocupa todo el ancho (texto de ayuda, casillas, indicadores...). */
    public void fila(JComponent componente) {
        if (columna != 0) {
            fila++;
            columna = 0;
        }
        GridBagConstraints c = base(0, fila * 3, columnas);
        c.insets = new Insets(0, 0, 8, 0);
        panel.add(componente, c);
        fila++;
    }

    private void agregar(String texto, char mnemonico, JComponent foco, JComponent visual, int ancho) {
        if (columna + ancho > columnas) {
            fila++;
            columna = 0;
        }
        JLabel etiqueta = new JLabel(texto);
        etiqueta.setFont(Tema.NORMAL_NEGRITA);
        etiqueta.setForeground(Tema.TEXTO);
        etiqueta.setLabelFor(foco);
        if (mnemonico != 0) {
            etiqueta.setDisplayedMnemonic(mnemonico);
        }
        int derecha = columna + ancho < columnas ? SEPARACION_COLUMNAS : 0;

        GridBagConstraints c = base(columna, fila * 3, ancho);
        c.insets = new Insets(0, 0, 5, derecha);
        panel.add(etiqueta, c);

        c = base(columna, fila * 3 + 1, ancho);
        c.insets = new Insets(0, 0, 2, derecha);
        panel.add(visual, c);

        JLabel error = new JLabel(" ");
        error.setFont(Tema.PEQUENA.deriveFont(Font.PLAIN, 11.5f));
        error.setForeground(Tema.ERROR);
        c = base(columna, fila * 3 + 2, ancho);
        c.insets = new Insets(0, 2, 10, derecha);
        panel.add(error, c);

        etiquetasError.put(foco, error);
        visuales.put(foco, visual);
        if (foco instanceof JTextComponent) {
            ((JTextComponent) foco).getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    limpiarError(foco);
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    limpiarError(foco);
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    // Cambios de atributos: no afectan a la validación.
                }
            });
        }
        columna += ancho;
        if (columna >= columnas) {
            columna = 0;
            fila++;
        }
    }

    private GridBagConstraints base(int x, int y, int ancho) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = ancho;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        return c;
    }

    /** Devuelve el panel construido (añade un relleno inferior para empujar los campos arriba). */
    public JPanel construir() {
        GridBagConstraints c = base(0, fila * 3 + 3, columnas);
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        JPanel relleno = new JPanel();
        relleno.setOpaque(false);
        panel.add(relleno, c);
        return panel;
    }

    /** Marca un campo como erróneo con un mensaje bajo él. */
    public void marcarError(JComponent campo, String mensaje) {
        JLabel etiqueta = etiquetasError.get(campo);
        if (etiqueta != null) {
            etiqueta.setText(mensaje);
        }
        // Se marca el componente visible (p. ej. el JScrollPane) y también el propio campo, por si el
        // visible es un panel contenedor que no pinta contorno.
        visuales.getOrDefault(campo, campo).putClientProperty("JComponent.outline", "error");
        campo.putClientProperty("JComponent.outline", "error");
        if (!conError.contains(campo)) {
            conError.add(campo);
        }
    }

    /** Quita la marca de error de un campo. */
    public void limpiarError(JComponent campo) {
        JLabel etiqueta = etiquetasError.get(campo);
        if (etiqueta != null) {
            etiqueta.setText(" ");
        }
        visuales.getOrDefault(campo, campo).putClientProperty("JComponent.outline", null);
        campo.putClientProperty("JComponent.outline", null);
        conError.remove(campo);
    }

    /** Quita todas las marcas de error. */
    public void limpiarErrores() {
        for (JComponent campo : new ArrayList<>(conError)) {
            limpiarError(campo);
        }
    }

    /** Indica si hay algún campo marcado con error. */
    public boolean hayErrores() {
        return !conError.isEmpty();
    }

    /** Lleva el foco al primer campo con error (en orden de marcado). */
    public void enfocarPrimerError() {
        if (!conError.isEmpty()) {
            conError.get(0).requestFocusInWindow();
        }
    }
}
