package com.fisioagenda.ui.components;

import com.fisioagenda.ui.Tema;

import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gráfico de barras vertical sencillo dibujado con Java2D. Las barras crecen con una animación
 * al recibir datos nuevos, la barra resaltada (p. ej. el día de hoy) usa el color principal y
 * al pasar el ratón se muestra el valor exacto.
 */
public class GraficoBarras extends ComponenteDibujado {

    private static final int ARRIBA = 26;
    private static final int ABAJO = 30;
    private static final int LATERAL = 8;

    private List<String> etiquetas = Collections.emptyList();
    private List<String> descripciones = Collections.emptyList();
    private List<Integer> valores = Collections.emptyList();
    private int resaltado = -1;
    private double progreso = 1;
    private Timer animacion;

    /** Crea el gráfico vacío. */
    public GraficoBarras() {
        setOpaque(false);
        setToolTipText("");
        setFont(Tema.PEQUENA);
        setPreferredSize(new Dimension(320, 220));
        getAccessibleContext().setAccessibleName("Gráfico de barras");
    }

    /**
     * Establece los datos y anima la aparición de las barras.
     *
     * @param etiquetas     texto corto bajo cada barra
     * @param descripciones texto largo para la descripción emergente
     * @param valores       valor de cada barra (no negativos)
     * @param resaltado     índice de la barra a destacar o -1
     */
    public void setDatos(List<String> etiquetas, List<String> descripciones, List<Integer> valores, int resaltado) {
        this.etiquetas = new ArrayList<>(etiquetas);
        this.descripciones = new ArrayList<>(descripciones);
        this.valores = new ArrayList<>(valores);
        this.resaltado = resaltado;
        StringBuilder resumen = new StringBuilder();
        for (int i = 0; i < valores.size(); i++) {
            resumen.append(descripciones.get(i)).append(": ").append(valores.get(i)).append(". ");
        }
        getAccessibleContext().setAccessibleDescription(resumen.toString());
        Animador.detener(animacion);
        progreso = 0;
        animacion = Animador.animar(600, p -> {
            progreso = p;
            repaint();
        }, null);
    }

    private int maximo() {
        int max = 0;
        for (int v : valores) {
            max = Math.max(max, v);
        }
        return Math.max(max, 4);
    }

    private double anchoHueco() {
        return valores.isEmpty() ? 0 : (getWidth() - LATERAL * 2.0) / valores.size();
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        if (valores.isEmpty()) {
            return null;
        }
        int indice = (int) ((e.getX() - LATERAL) / anchoHueco());
        if (indice < 0 || indice >= valores.size()) {
            return null;
        }
        int v = valores.get(indice);
        return descripciones.get(indice) + ": " + v + (v == 1 ? " cita" : " citas");
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int altoUtil = h - ARRIBA - ABAJO;
            int base = h - ABAJO;
            int max = maximo();

            // Líneas guía horizontales.
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{4f, 4f}, 0f));
            g2.setColor(Tema.BORDE);
            for (int i = 1; i <= 2; i++) {
                int y = base - altoUtil * i / 2;
                g2.drawLine(LATERAL, y, w - LATERAL, y);
            }
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(Tema.BORDE.darker());
            g2.drawLine(LATERAL, base, w - LATERAL, base);

            if (valores.isEmpty()) {
                return;
            }
            double hueco = anchoHueco();
            double anchoBarra = Math.min(38, hueco * 0.56);
            Font normal = getFont();
            Font negrita = normal.deriveFont(Font.BOLD);
            for (int i = 0; i < valores.size(); i++) {
                int v = valores.get(i);
                double alto = altoUtil * (v / (double) max) * progreso;
                double x = LATERAL + hueco * i + (hueco - anchoBarra) / 2;
                boolean destacado = i == resaltado;
                Color color = destacado ? Tema.PRIMARIO : Tema.suavizar(Tema.PRIMARIO, 0.62);
                if (alto > 0.5) {
                    // Solo las esquinas superiores redondeadas: se recorta la parte que sobresale de la base.
                    Shape recorte = g2.getClip();
                    g2.clipRect(0, 0, w, base);
                    g2.setColor(color);
                    g2.fill(new RoundRectangle2D.Double(x, base - alto, anchoBarra, alto + 10, 10, 10));
                    g2.setClip(recorte);
                }
                // Valor encima de la barra.
                g2.setFont(negrita);
                FontMetrics fm = g2.getFontMetrics();
                String texto = String.valueOf(v);
                g2.setColor(destacado ? Tema.PRIMARIO_OSCURO : Tema.TEXTO_SECUNDARIO);
                g2.drawString(texto, (float) (x + (anchoBarra - fm.stringWidth(texto)) / 2), (float) (base - alto - 6));
                // Etiqueta bajo la barra.
                g2.setFont(destacado ? negrita : normal);
                fm = g2.getFontMetrics();
                String etiqueta = etiquetas.get(i);
                g2.setColor(destacado ? Tema.TEXTO : Tema.TEXTO_SECUNDARIO);
                g2.drawString(etiqueta, (float) (x + (anchoBarra - fm.stringWidth(etiqueta)) / 2), base + 8 + fm.getAscent());
            }
        } finally {
            g2.dispose();
        }
    }
}
