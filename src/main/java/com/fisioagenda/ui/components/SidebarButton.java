package com.fisioagenda.ui.components;

import com.fisioagenda.ui.Tema;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * Botón de la barra lateral. Se pinta a mano (fondo oscuro) con transición animada de color
 * al pasar el ratón y al seleccionarse. En modo colapsado muestra solo el icono, y el texto pasa
 * a la descripción emergente. Al heredar de {@link JButton} conserva teclado, foco y accesibilidad.
 * <p>
 * El contorno de foco solo se dibuja cuando el foco ha llegado con el teclado (Tab): tras un clic o al
 * abrirse la ventana no se pinta, para que no parezca que hay dos secciones activas a la vez.
 */
public class SidebarButton extends JButton {

    private static final int DURACION_MS = 150;
    private static final Color HOVER = new Color(255, 255, 255, 22);
    private static final Color SELECCION = new Color(0x0F766E);
    private static final Color INDICADOR = new Color(0x5EEAD4);

    private final Icon iconoNormal;
    private final Icon iconoActivo;
    private final String ayuda;
    private boolean seleccionado;
    private boolean colapsado;
    /** El foco actual (o el último) llegó con el teclado: se dibuja el contorno de foco. */
    private boolean focoTeclado;
    private double progresoHover;
    private double progresoSeleccion;
    private Timer animacion;

    /**
     * @param texto  texto del botón
     * @param tipo   icono a mostrar
     * @param ayuda  descripción emergente (por ejemplo, incluyendo el atajo de teclado)
     */
    public SidebarButton(String texto, Iconos.Tipo tipo, String ayuda) {
        super(texto);
        this.ayuda = ayuda;
        this.iconoNormal = Iconos.de(tipo, 20, Tema.SIDEBAR_TEXTO);
        this.iconoActivo = Iconos.de(tipo, 20, Color.WHITE);
        setToolTipText(ayuda);
        getAccessibleContext().setAccessibleName(texto);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFont(Tema.NORMAL.deriveFont(Font.BOLD, 13.5f));
        setHorizontalAlignment(LEADING);
        getModel().addChangeListener(e -> animarHacia());
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                switch (e.getCause()) {
                    case TRAVERSAL:
                    case TRAVERSAL_FORWARD:
                    case TRAVERSAL_BACKWARD:
                    case TRAVERSAL_UP:
                    case TRAVERSAL_DOWN:
                        focoTeclado = true;
                        break;
                    case MOUSE_EVENT:
                        focoTeclado = false;
                        break;
                    default:
                        // Activación de la ventana, foco pedido por código...: se mantiene el estado anterior
                        // (al volver de un diálogo abierto con el teclado el contorno reaparece).
                        break;
                }
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaint();
            }
        });
    }

    /** Marca el botón como sección activa (con animación). */
    public void setSeleccionado(boolean seleccionado) {
        if (this.seleccionado != seleccionado) {
            this.seleccionado = seleccionado;
            getAccessibleContext().setAccessibleDescription(seleccionado ? "Sección actual" : null);
            animarHacia();
        }
    }

    /**
     * Pasa el foco a este botón conservando el contorno de foco de teclado, para que el foco acompañe a la
     * selección cuando se cambia de sección con un atajo mientras se navega por la barra con el teclado.
     */
    public void enfocarConTeclado() {
        focoTeclado = true;
        requestFocusInWindow();
    }

    /** Indica si el foco de teclado está en este botón (y por tanto se dibuja su contorno de foco). */
    public boolean tieneFocoTeclado() {
        return focoTeclado && isFocusOwner();
    }

    /** Indica si es la sección activa. */
    public boolean isSeleccionado() {
        return seleccionado;
    }

    /** Activa o desactiva el modo colapsado (solo icono). */
    public void setColapsado(boolean colapsado) {
        this.colapsado = colapsado;
        setToolTipText(colapsado ? getText() + (ayuda != null && !ayuda.equals(getText()) ? " — " + ayuda : "") : ayuda);
        revalidate();
        repaint();
    }

    private void animarHacia() {
        final double hoverDestino = getModel().isRollover() || getModel().isPressed() ? 1 : 0;
        final double seleccionDestino = seleccionado ? 1 : 0;
        if (hoverDestino == progresoHover && seleccionDestino == progresoSeleccion) {
            return;
        }
        final double hoverInicio = progresoHover;
        final double seleccionInicio = progresoSeleccion;
        Animador.detener(animacion);
        animacion = Animador.animar(DURACION_MS, p -> {
            progresoHover = Animador.interpolar(hoverInicio, hoverDestino, p);
            progresoSeleccion = Animador.interpolar(seleccionInicio, seleccionDestino, p);
            repaint();
        }, null);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(colapsado ? 48 : 196, 44);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, 44);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(44, 44);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int arco = 12;
            if (progresoHover > 0) {
                g2.setColor(Tema.conAlfa(HOVER, (int) (HOVER.getAlpha() * progresoHover)));
                g2.fillRoundRect(0, 0, w, h, arco, arco);
            }
            if (progresoSeleccion > 0) {
                g2.setColor(Tema.conAlfa(SELECCION, (int) (255 * progresoSeleccion)));
                g2.fillRoundRect(0, 0, w, h, arco, arco);
                g2.setColor(Tema.conAlfa(INDICADOR, (int) (255 * progresoSeleccion)));
                g2.fillRoundRect(0, 10, 4, h - 20, 4, 4);
            }
            if (tieneFocoTeclado()) {
                g2.setColor(INDICADOR);
                g2.setStroke(new BasicStroke(1.6f));
                g2.drawRoundRect(1, 1, w - 3, h - 3, arco, arco);
            }
            Icon icono = seleccionado || getModel().isRollover() ? iconoActivo : iconoNormal;
            int yIcono = (h - icono.getIconHeight()) / 2;
            if (colapsado) {
                icono.paintIcon(this, g2, (w - icono.getIconWidth()) / 2, yIcono);
            } else {
                int x = 14;
                icono.paintIcon(this, g2, x, yIcono);
                g2.setFont(getFont());
                g2.setColor(Tema.mezclar(Tema.SIDEBAR_TEXTO, Color.WHITE, Math.max(progresoHover, progresoSeleccion)));
                FontMetrics fm = g2.getFontMetrics();
                int xTexto = x + icono.getIconWidth() + 12;
                String texto = recortar(getText(), fm, w - xTexto - 8);
                g2.drawString(texto, xTexto, (h - fm.getHeight()) / 2 + fm.getAscent());
                int indice = getDisplayedMnemonicIndex();
                if (indice >= 0 && indice < texto.length() && texto.equals(getText())) {
                    int xs = xTexto + fm.stringWidth(texto.substring(0, indice));
                    int ys = (h - fm.getHeight()) / 2 + fm.getAscent() + 2;
                    g2.fillRect(xs, ys, fm.charWidth(texto.charAt(indice)), 1);
                }
            }
        } finally {
            g2.dispose();
        }
    }

    private static String recortar(String texto, FontMetrics fm, int ancho) {
        if (texto == null || fm.stringWidth(texto) <= ancho) {
            return texto == null ? "" : texto;
        }
        String puntos = "…";
        for (int i = texto.length() - 1; i > 0; i--) {
            String parcial = texto.substring(0, i) + puntos;
            if (fm.stringWidth(parcial) <= ancho) {
                return parcial;
            }
        }
        return puntos;
    }
}
