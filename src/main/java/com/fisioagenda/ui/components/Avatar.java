package com.fisioagenda.ui.components;

import com.fisioagenda.ui.Tema;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Círculo con las iniciales de una persona (usuario o paciente), en los colores del tema.
 */
public class Avatar extends ComponenteDibujado {

    private final String iniciales;
    private final int tamano;

    /**
     * @param nombre nombre completo del que se toman las iniciales
     * @param tamano diámetro en píxeles
     */
    public Avatar(String nombre, int tamano) {
        this.iniciales = Componentes.iniciales(nombre);
        this.tamano = tamano;
        getAccessibleContext().setAccessibleName(nombre);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(tamano, tamano);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int y = (getHeight() - tamano) / 2;
            g2.setColor(Tema.PRIMARIO_CLARO);
            g2.fillOval(0, y, tamano, tamano);
            g2.setColor(Tema.PRIMARIO_OSCURO);
            g2.setFont(new Font(Tema.FAMILIA, Font.BOLD, Math.max(11, tamano * 2 / 5)));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(iniciales, (tamano - fm.stringWidth(iniciales)) / 2,
                    y + (tamano - fm.getHeight()) / 2 + fm.getAscent());
        } finally {
            g2.dispose();
        }
    }
}
