package com.fisioagenda.ui.components;

import com.fisioagenda.ui.Tema;

import javax.swing.Icon;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Notificación no modal que aparece abajo a la derecha de la ventana con un fundido, permanece
 * unos 2,5 segundos y desaparece sola. Se dibuja en la capa emergente de la ventana, por lo que
 * no roba el foco ni bloquea la interacción.
 */
public final class Toast extends ComponenteDibujado {

    /** Tipo de notificación, que determina el icono y el color del acento. */
    public enum Tipo {
        EXITO, ERROR, INFO
    }

    private static final int VISIBLE_MS = 2500;
    private static final int FUNDIDO_MS = 250;
    private static final int MARGEN = 24;
    private static final String CLAVE_ACTUAL = "fisioagenda.toastActual";

    private final String mensaje;
    private final Icon icono;
    private final Color acento;
    private float opacidad;
    private Timer animacion;
    private Timer espera;
    private JLayeredPane capa;
    private ComponentAdapter recolocador;

    private Toast(String mensaje, Tipo tipo) {
        this.mensaje = mensaje;
        switch (tipo) {
            case ERROR:
                acento = new Color(0xFCA5A5);
                icono = Iconos.de(Iconos.Tipo.ALERTA, 18, acento);
                break;
            case INFO:
                acento = new Color(0x93C5FD);
                icono = Iconos.de(Iconos.Tipo.INFO, 18, acento);
                break;
            case EXITO:
            default:
                acento = new Color(0x5EEAD4);
                icono = Iconos.de(Iconos.Tipo.CHECK_CIRCULO, 18, acento);
                break;
        }
        setOpaque(false);
        setFont(Tema.NORMAL_NEGRITA);
        getAccessibleContext().setAccessibleName(mensaje);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                ocultar();
            }
        });
    }

    /** Muestra una notificación de éxito en la ventana que contiene al componente indicado. */
    public static void mostrar(Component origen, String mensaje) {
        mostrar(origen, mensaje, Tipo.EXITO);
    }

    /** Muestra una notificación del tipo indicado en la ventana que contiene al componente. */
    public static void mostrar(Component origen, String mensaje, Tipo tipo) {
        JRootPane raiz = origen instanceof JRootPane ? (JRootPane) origen : SwingUtilities.getRootPane(origen);
        if (raiz == null || mensaje == null) {
            return;
        }
        JLayeredPane capa = raiz.getLayeredPane();
        Object anterior = capa.getClientProperty(CLAVE_ACTUAL);
        if (anterior instanceof Toast) {
            ((Toast) anterior).retirar();
        }
        Toast toast = new Toast(mensaje, tipo);
        toast.instalar(capa);
    }

    private void instalar(JLayeredPane capaDestino) {
        this.capa = capaDestino;
        capa.putClientProperty(CLAVE_ACTUAL, this);
        capa.add(this, JLayeredPane.POPUP_LAYER);
        colocar();
        recolocador = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                colocar();
            }
        };
        capa.addComponentListener(recolocador);
        animacion = Animador.animar(FUNDIDO_MS, p -> {
            opacidad = (float) p;
            repaint();
        }, () -> {
            espera = new Timer(VISIBLE_MS, e -> ocultar());
            espera.setRepeats(false);
            espera.start();
        });
    }

    private void colocar() {
        Dimension d = getPreferredSize();
        setBounds(capa.getWidth() - d.width - MARGEN, capa.getHeight() - d.height - MARGEN, d.width, d.height);
    }

    private void ocultar() {
        Animador.detener(espera);
        Animador.detener(animacion);
        final float inicio = opacidad;
        animacion = Animador.animar(FUNDIDO_MS, p -> {
            opacidad = (float) (inicio * (1 - p));
            repaint();
        }, this::retirar);
    }

    private void retirar() {
        Animador.detener(espera);
        Animador.detener(animacion);
        if (capa != null) {
            capa.removeComponentListener(recolocador);
            if (capa.getClientProperty(CLAVE_ACTUAL) == this) {
                capa.putClientProperty(CLAVE_ACTUAL, null);
            }
            Component padre = getParent();
            capa.remove(this);
            if (padre != null) {
                padre.repaint(getX(), getY(), getWidth(), getHeight());
            }
            capa = null;
        }
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int ancho = Math.min(420, fm.stringWidth(mensaje) + icono.getIconWidth() + 52);
        return new Dimension(Math.max(220, ancho), 48);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, opacidad))));
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(new Color(0, 0, 0, 40));
            g2.fillRoundRect(2, 4, w - 4, h - 4, 14, 14);
            g2.setColor(new Color(0x1E293B));
            g2.fillRoundRect(0, 0, w - 2, h - 4, 14, 14);
            g2.setColor(acento);
            g2.fillRoundRect(0, 0, 5, h - 4, 4, 4);
            icono.paintIcon(this, g2, 18, (h - 4 - icono.getIconHeight()) / 2);
            g2.setColor(Color.WHITE);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int xTexto = 18 + icono.getIconWidth() + 12;
            String texto = mensaje;
            int disponible = w - xTexto - 16;
            while (fm.stringWidth(texto) > disponible && texto.length() > 4) {
                texto = texto.substring(0, texto.length() - 4) + "…";
            }
            g2.drawString(texto, xTexto, (h - 4 - fm.getHeight()) / 2 + fm.getAscent());
        } finally {
            g2.dispose();
        }
    }
}
