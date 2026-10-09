package com.fisioagenda.ui.components;

import com.fisioagenda.ui.Tema;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * Panel con fondo blanco, esquinas redondeadas y borde suave: la "tarjeta" sobre la que se
 * colocan tablas, formularios y gráficos para separar visualmente los bloques de cada pantalla.
 */
public class Tarjeta extends JPanel {

    private static final int ARCO = 16;
    private Color colorFondo = Tema.SUPERFICIE;
    private Color colorBorde = Tema.BORDE;

    /** Tarjeta con {@link BorderLayout} y un margen interior de 16 px. */
    public Tarjeta() {
        this(new BorderLayout(), 16);
    }

    /** Tarjeta con el layout y el margen interior indicados. */
    public Tarjeta(LayoutManager layout, int margen) {
        super(layout);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(margen, margen, margen, margen));
    }

    /** Cambia el color de fondo de la tarjeta. */
    public void setColorFondo(Color color) {
        this.colorFondo = color;
        repaint();
    }

    /** Cambia el color del borde (o {@code null} para no pintarlo). */
    public void setColorBorde(Color color) {
        this.colorBorde = color;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D forma = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1.0, getHeight() - 1.0, ARCO, ARCO);
            g2.setColor(colorFondo);
            g2.fill(forma);
            if (colorBorde != null) {
                g2.setColor(colorBorde);
                g2.draw(forma);
            }
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
