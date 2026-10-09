package com.fisioagenda.ui.components;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contenedor con {@link CardLayout} que anima el cambio de tarjeta con un deslizamiento lateral
 * combinado con un fundido (~220 ms). Durante la transición se pintan instantáneas de la tarjeta
 * saliente y la entrante, de modo que la animación es fluida aunque las tarjetas sean complejas.
 */
public class AnimatedCardPanel extends JPanel {

    private static final int DURACION_MS = 220;
    private static final int DESPLAZAMIENTO = 48;

    private final CardLayout layout = new CardLayout();
    private final Map<String, Component> tarjetas = new LinkedHashMap<>();
    private String actual;

    private Timer animacion;
    private BufferedImage imagenSaliente;
    private BufferedImage imagenEntrante;
    private double progreso = 1;
    private int direccion = 1;

    /** Crea el panel vacío. */
    public AnimatedCardPanel() {
        setLayout(layout);
        setOpaque(false);
    }

    /** Añade una tarjeta identificada por un nombre; la primera añadida es la visible. */
    public void agregar(String nombre, Component componente) {
        tarjetas.put(nombre, componente);
        add(componente, nombre);
        if (actual == null) {
            actual = nombre;
        }
    }

    /** Nombre de la tarjeta visible. */
    public String getActual() {
        return actual;
    }

    /** Muestra la tarjeta indicada con animación (o directamente si el panel aún no es visible). */
    public void mostrar(String nombre) {
        if (!tarjetas.containsKey(nombre) || nombre.equals(actual)) {
            return;
        }
        terminarAnimacion();
        List<String> orden = new ArrayList<>(tarjetas.keySet());
        direccion = orden.indexOf(nombre) >= orden.indexOf(actual) ? 1 : -1;
        Component saliente = tarjetas.get(actual);
        boolean animable = isShowing() && getWidth() > 0 && getHeight() > 0;
        if (animable) {
            imagenSaliente = capturar(saliente);
        }
        layout.show(this, nombre);
        actual = nombre;
        if (!animable) {
            return;
        }
        validate();
        imagenEntrante = capturar(tarjetas.get(nombre));
        progreso = 0;
        animacion = Animador.animar(DURACION_MS, p -> {
            progreso = p;
            repaint();
        }, this::terminarAnimacion);
    }

    private void terminarAnimacion() {
        Animador.detener(animacion);
        animacion = null;
        imagenSaliente = null;
        imagenEntrante = null;
        progreso = 1;
        revalidate();
        // Se repinta la ventana completa para no dejar restos de la transición en otras zonas.
        java.awt.Window ventana = javax.swing.SwingUtilities.getWindowAncestor(this);
        if (ventana != null) {
            ventana.repaint();
        } else {
            repaint();
        }
    }

    private BufferedImage capturar(Component c) {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        double escala = 1;
        GraphicsConfiguration gc = getGraphicsConfiguration();
        if (gc != null) {
            escala = Math.max(1, gc.getDefaultTransform().getScaleX());
        }
        BufferedImage img = new BufferedImage((int) Math.ceil(w * escala), (int) Math.ceil(h * escala),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.scale(escala, escala);
            g.translate(c.getX(), c.getY());
            // printAll pinta directamente en la imagen sin pasar por el búfer de ventana de Swing;
            // paint() podía dejar ese búfer desplazado en Windows y la ventana se veía descolocada.
            c.printAll(g);
        } finally {
            g.dispose();
        }
        return img;
    }

    /**
     * Mientras dura la transición, cualquier repintado de una tarjeta se redirige a este panel,
     * para que se sigan pintando las instantáneas y no la tarjeta real en su posición final.
     */
    @Override
    protected boolean isPaintingOrigin() {
        return imagenEntrante != null;
    }

    @Override
    protected void paintChildren(Graphics g) {
        if (imagenEntrante == null || imagenSaliente == null) {
            super.paintChildren(g);
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int w = getWidth();
            int h = getHeight();
            int xSaliente = (int) Math.round(-direccion * DESPLAZAMIENTO * progreso);
            int xEntrante = (int) Math.round(direccion * DESPLAZAMIENTO * (1 - progreso));
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, 1 - progreso * 1.4)));
            g2.drawImage(imagenSaliente, xSaliente, 0, w, h, null);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) progreso));
            g2.drawImage(imagenEntrante, xEntrante, 0, w, h, null);
        } finally {
            g2.dispose();
        }
    }
}
