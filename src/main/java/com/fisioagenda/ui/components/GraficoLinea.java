package com.fisioagenda.ui.components;

import com.fisioagenda.ui.Tema;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gráfico de línea pequeño (Java2D) para mostrar la evolución del dolor (escala EVA 0-10) a lo
 * largo de las notas clínicas. Cada punto se colorea según la intensidad del dolor.
 */
public class GraficoLinea extends ComponenteDibujado {

    private static final int IZQUIERDA = 34;
    private static final int DERECHA = 16;
    private static final int ARRIBA = 14;
    private static final int ABAJO = 26;
    private static final int MAXIMO = 10;

    private List<String> etiquetas = Collections.emptyList();
    private List<Integer> valores = Collections.emptyList();
    private String mensajeVacio = "Sin datos";

    /** Crea el gráfico vacío. */
    public GraficoLinea() {
        setOpaque(false);
        setFont(Tema.PEQUENA);
        setToolTipText("");
        setPreferredSize(new Dimension(360, 150));
        getAccessibleContext().setAccessibleName("Gráfico de evolución del dolor");
    }

    /**
     * Establece los puntos en orden cronológico.
     *
     * @param etiquetas texto de cada punto (p. ej. la fecha)
     * @param valores   valor EVA de cada punto (0-10)
     */
    public void setPuntos(List<String> etiquetas, List<Integer> valores) {
        this.etiquetas = new ArrayList<>(etiquetas);
        this.valores = new ArrayList<>(valores);
        StringBuilder resumen = new StringBuilder();
        for (int i = 0; i < valores.size(); i++) {
            resumen.append(etiquetas.get(i)).append(": ").append(valores.get(i)).append(". ");
        }
        getAccessibleContext().setAccessibleDescription(resumen.toString());
        repaint();
    }

    /** Texto que se muestra cuando no hay puntos. */
    public void setMensajeVacio(String mensaje) {
        this.mensajeVacio = mensaje;
        repaint();
    }

    private double xDe(int i) {
        int ancho = getWidth() - IZQUIERDA - DERECHA;
        if (valores.size() <= 1) {
            return IZQUIERDA + ancho / 2.0;
        }
        return IZQUIERDA + ancho * i / (double) (valores.size() - 1);
    }

    private double yDe(int valor) {
        int alto = getHeight() - ARRIBA - ABAJO;
        return ARRIBA + alto * (1 - valor / (double) MAXIMO);
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        int masCercano = -1;
        double distancia = Double.MAX_VALUE;
        for (int i = 0; i < valores.size(); i++) {
            double d = Math.abs(xDe(i) - e.getX());
            if (d < distancia) {
                distancia = d;
                masCercano = i;
            }
        }
        if (masCercano < 0 || distancia > 24) {
            return null;
        }
        int v = valores.get(masCercano);
        return etiquetas.get(masCercano) + " · EVA " + v + " (" + Tema.descripcionDolor(v) + ")";
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int w = getWidth();

            // Rejilla horizontal con las marcas 0, 5 y 10.
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{4f, 4f}, 0f));
            for (int marca = 0; marca <= MAXIMO; marca += 5) {
                int y = (int) Math.round(yDe(marca));
                g2.setColor(Tema.BORDE);
                g2.drawLine(IZQUIERDA, y, w - DERECHA, y);
                g2.setColor(Tema.TEXTO_SECUNDARIO);
                String texto = String.valueOf(marca);
                g2.drawString(texto, IZQUIERDA - 10 - fm.stringWidth(texto), y + fm.getAscent() / 2 - 1);
            }

            if (valores.isEmpty()) {
                g2.setColor(Tema.TEXTO_SECUNDARIO);
                int x = (w - fm.stringWidth(mensajeVacio)) / 2;
                g2.drawString(mensajeVacio, Math.max(IZQUIERDA, x), (int) yDe(5) - 6);
                return;
            }

            // Área bajo la curva con degradado y línea principal.
            Path2D linea = new Path2D.Double();
            Path2D area = new Path2D.Double();
            double base = yDe(0);
            for (int i = 0; i < valores.size(); i++) {
                double x = xDe(i);
                double y = yDe(valores.get(i));
                if (i == 0) {
                    linea.moveTo(x, y);
                    area.moveTo(x, base);
                    area.lineTo(x, y);
                } else {
                    linea.lineTo(x, y);
                    area.lineTo(x, y);
                }
            }
            area.lineTo(xDe(valores.size() - 1), base);
            area.closePath();
            if (valores.size() > 1) {
                g2.setPaint(new GradientPaint(0, ARRIBA, Tema.conAlfa(Tema.PRIMARIO, 70),
                        0, (float) base, Tema.conAlfa(Tema.PRIMARIO, 0)));
                g2.fill(area);
            }
            g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(Tema.PRIMARIO);
            g2.draw(linea);

            // Puntos coloreados según la intensidad y etiquetas del eje X sin solaparse.
            int ultimaEtiquetaFin = Integer.MIN_VALUE;
            for (int i = 0; i < valores.size(); i++) {
                double x = xDe(i);
                double y = yDe(valores.get(i));
                g2.setColor(Color.WHITE);
                g2.fill(new Ellipse2D.Double(x - 6, y - 6, 12, 12));
                g2.setColor(Tema.colorDolor(valores.get(i)));
                g2.fill(new Ellipse2D.Double(x - 4.5, y - 4.5, 9, 9));
                String etiqueta = etiquetas.get(i);
                int ancho = fm.stringWidth(etiqueta);
                int xe = (int) Math.round(x - ancho / 2.0);
                xe = Math.max(0, Math.min(w - ancho, xe));
                if (xe > ultimaEtiquetaFin + 8) {
                    g2.setColor(Tema.TEXTO_SECUNDARIO);
                    g2.drawString(etiqueta, xe, (int) base + 8 + fm.getAscent());
                    ultimaEtiquetaFin = xe + ancho;
                }
            }
        } finally {
            g2.dispose();
        }
    }
}
