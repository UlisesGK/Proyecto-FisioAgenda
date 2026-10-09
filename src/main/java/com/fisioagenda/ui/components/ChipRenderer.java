package com.fisioagenda.ui.components;

import com.fisioagenda.model.EstadoCita;
import com.fisioagenda.ui.Tema;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.function.Function;

/**
 * Renderizador de celdas que dibuja el valor como un "chip": píldora de color suave con un punto
 * y el texto en el color fuerte. Se usa para el estado de las citas y para valores Sí/No.
 */
public class ChipRenderer extends DefaultTableCellRenderer {

    private final transient Function<Object, String> textoDe;
    private final transient Function<Object, Color> colorDe;
    private Color colorActual = Tema.TEXTO_SECUNDARIO;

    /**
     * @param textoDe función que obtiene el texto del chip a partir del valor de la celda
     * @param colorDe función que obtiene el color del chip (o {@code null} para no dibujar chip)
     */
    public ChipRenderer(Function<Object, String> textoDe, Function<Object, Color> colorDe) {
        this.textoDe = textoDe;
        this.colorDe = colorDe;
    }

    /** Renderizador para columnas de tipo {@link EstadoCita}. */
    public static ChipRenderer paraEstados() {
        return new ChipRenderer(
                v -> v instanceof EstadoCita ? ((EstadoCita) v).getEtiqueta() : "",
                v -> v instanceof EstadoCita ? Tema.colorEstado((EstadoCita) v) : null);
    }

    /** Renderizador para columnas booleanas con los textos indicados para verdadero y falso. */
    public static ChipRenderer paraBooleano(String siVerdadero, Color colorVerdadero, String siFalso, Color colorFalso) {
        return new ChipRenderer(
                v -> Boolean.TRUE.equals(v) ? siVerdadero : siFalso,
                v -> Boolean.TRUE.equals(v) ? colorVerdadero : colorFalso);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        setText(textoDe.apply(value));
        colorActual = colorDe.apply(value);
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());
            String texto = getText();
            if (texto == null || texto.isEmpty()) {
                return;
            }
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Font fuente = Tema.PEQUENA.deriveFont(Font.BOLD);
            g2.setFont(fuente);
            FontMetrics fm = g2.getFontMetrics();
            Color color = colorActual != null ? colorActual : getForeground();
            int alto = Math.min(getHeight() - 8, fm.getHeight() + 8);
            int ancho = fm.stringWidth(texto) + 30;
            int x = 10;
            int y = (getHeight() - alto) / 2;
            g2.setColor(Tema.suavizar(color, 0.86));
            g2.fillRoundRect(x, y, ancho, alto, alto, alto);
            g2.setColor(color);
            int punto = 7;
            g2.fillOval(x + 10, y + (alto - punto) / 2, punto, punto);
            g2.drawString(texto, x + 22, y + (alto - fm.getHeight()) / 2 + fm.getAscent());
        } finally {
            g2.dispose();
        }
    }
}
