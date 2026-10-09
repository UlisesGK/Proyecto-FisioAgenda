package com.fisioagenda.ui.components;

import com.fisioagenda.util.Formatos;
import com.fisioagenda.util.Textos;

import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Filtro de texto libre para tablas: busca cada palabra escrita (sin distinguir mayúsculas ni tildes,
 * con las mismas reglas que {@link Textos}) en todas las columnas visibles. Se conecta a un campo de
 * búsqueda con un retardo ("debounce") de 250 ms para no recalcular el filtro en cada pulsación.
 */
public final class FiltroTexto {

    private static final int RETARDO_MS = 250;

    private FiltroTexto() {
    }

    /**
     * Conecta un campo de búsqueda con una tabla: al escribir se filtra en vivo.
     *
     * @param campo      campo de texto de búsqueda
     * @param tabla      tabla a filtrar
     * @param alFiltrar  acción opcional tras aplicar el filtro (p. ej. actualizar un contador)
     */
    public static void conectar(JTextField campo, Tabla<?> tabla, Runnable alFiltrar) {
        Timer retardo = new Timer(RETARDO_MS, e -> {
            aplicar(tabla, campo.getText());
            if (alFiltrar != null) {
                alFiltrar.run();
            }
        });
        retardo.setRepeats(false);
        campo.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                retardo.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                retardo.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                retardo.restart();
            }
        });
    }

    /** Aplica inmediatamente el filtro con el texto indicado (vacío = sin filtro). */
    public static void aplicar(Tabla<?> tabla, String texto) {
        if (Textos.normalizar(texto).isEmpty()) {
            tabla.getOrdenador().setRowFilter(null);
            return;
        }
        tabla.getOrdenador().setRowFilter(new RowFilter<Object, Integer>() {
            @Override
            public boolean include(Entry<?, ? extends Integer> fila) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < fila.getValueCount(); i++) {
                    Object valor = fila.getValue(i);
                    if (valor instanceof LocalDate) {
                        sb.append(Formatos.fecha((LocalDate) valor));
                    } else if (valor instanceof LocalDateTime) {
                        sb.append(Tabla.formatear((LocalDateTime) valor));
                    } else if (valor != null) {
                        sb.append(valor);
                    }
                    sb.append(' ');
                }
                return Textos.contienePalabras(sb.toString(), texto);
            }
        });
    }
}
