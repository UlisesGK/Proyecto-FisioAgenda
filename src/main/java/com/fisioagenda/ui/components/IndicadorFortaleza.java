package com.fisioagenda.ui.components;

import com.fisioagenda.ui.Tema;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Indicador visual de la fortaleza de una contraseña: barra de color animada (rojo, ámbar, verde)
 * y texto descriptivo. Sigue la política de la aplicación: al menos 8 caracteres, mayúscula,
 * minúscula, dígito y símbolo.
 */
public class IndicadorFortaleza extends JPanel {

    private static final String[] NOMBRES = {"", "Débil", "Mejorable", "Segura", "Muy segura"};
    private static final Color[] COLORES = {Tema.BORDE, Tema.ERROR, Tema.AVISO, Tema.EXITO, new Color(0x0F766E)};

    private final Barra barra = new Barra();
    private final JLabel lblNivel = new JLabel(" ");
    private int nivel;
    private double relleno;
    private Timer animacion;

    /** Crea el indicador vacío. */
    public IndicadorFortaleza() {
        super(new BorderLayout(10, 0));
        setOpaque(false);
        lblNivel.setFont(Tema.PEQUENA.deriveFont(Font.BOLD));
        lblNivel.setPreferredSize(new Dimension(90, 18));
        add(barra, BorderLayout.CENTER);
        add(lblNivel, BorderLayout.EAST);
        setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        setToolTipText("Mínimo 8 caracteres con mayúscula, minúscula, número y símbolo");
    }

    /**
     * Evalúa la contraseña y actualiza el indicador. No guarda ni modifica el array recibido
     * (el llamante debe limpiarlo después).
     */
    public void evaluar(char[] password) {
        int nuevo = calcularNivel(password);
        if (nuevo == nivel) {
            return;
        }
        nivel = nuevo;
        lblNivel.setText(nivel == 0 ? " " : NOMBRES[nivel]);
        lblNivel.setForeground(COLORES[nivel]);
        getAccessibleContext().setAccessibleDescription("Fortaleza de la contraseña: " + NOMBRES[nivel]);
        final double inicio = relleno;
        final double destino = nivel / 4.0;
        Animador.detener(animacion);
        animacion = Animador.animar(220, p -> {
            relleno = Animador.interpolar(inicio, destino, p);
            barra.repaint();
        }, null);
    }

    /** Indica si la última contraseña evaluada cumple la política de seguridad. */
    public boolean cumplePolitica() {
        return nivel >= 3;
    }

    /** Calcula el nivel (0 vacío, 1 débil, 2 mejorable, 3 segura, 4 muy segura). */
    public static int calcularNivel(char[] password) {
        if (password == null || password.length == 0) {
            return 0;
        }
        boolean mayus = false;
        boolean minus = false;
        boolean digito = false;
        boolean simbolo = false;
        for (char c : password) {
            if (Character.isUpperCase(c)) {
                mayus = true;
            } else if (Character.isLowerCase(c)) {
                minus = true;
            } else if (Character.isDigit(c)) {
                digito = true;
            } else if (!Character.isWhitespace(c)) {
                simbolo = true;
            }
        }
        int criterios = (password.length >= 8 ? 1 : 0) + (mayus ? 1 : 0) + (minus ? 1 : 0)
                + (digito ? 1 : 0) + (simbolo ? 1 : 0);
        if (criterios == 5) {
            return password.length >= 12 ? 4 : 3;
        }
        return criterios >= 3 ? 2 : 1;
    }

    /** Barra segmentada que se rellena según el nivel. */
    private final class Barra extends JComponent {
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(160, 8);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int alto = 6;
                int y = (getHeight() - alto) / 2;
                g2.setColor(Tema.BORDE);
                g2.fillRoundRect(0, y, getWidth(), alto, alto, alto);
                int ancho = (int) Math.round(getWidth() * relleno);
                if (ancho > 0) {
                    g2.setColor(COLORES[Math.max(1, nivel)]);
                    g2.fillRoundRect(0, y, ancho, alto, alto, alto);
                }
            } finally {
                g2.dispose();
            }
        }
    }
}
