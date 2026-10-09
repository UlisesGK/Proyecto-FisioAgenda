package com.fisioagenda.ui.components;

import com.fisioagenda.ui.Tema;

import javax.swing.Icon;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Iconos vectoriales dibujados con Java2D (sin imágenes externas). Se definen sobre una rejilla
 * de 24x24 unidades y se escalan al tamaño pedido, por lo que se ven nítidos en pantallas HiDPI.
 */
public final class Iconos {

    /** Catálogo de iconos disponibles. */
    public enum Tipo {
        INICIO, PACIENTES, AGENDA, TRATAMIENTOS, USUARIOS, LLAVE, SALIR, MAS, EDITAR, PAPELERA,
        HISTORIAL, BUSCAR, REFRESCAR, CHECK, CHECK_CIRCULO, CRUZ, IZQUIERDA, DERECHA, OJO, OJO_TACHADO,
        RELOJ, NOTA, USUARIO, CANDADO, CANDADO_ABIERTO, PROHIBIDO, AUSENTE, INFO, ALERTA, MINIMIZAR,
        INTERRUPTOR, GRAFICO, HOY
    }

    private Iconos() {
    }

    /**
     * Crea un icono que toma el color de primer plano del componente donde se pinta
     * (y un gris atenuado si el componente está deshabilitado).
     */
    public static Icon de(Tipo tipo, int tamano) {
        return new IconoVectorial(tipo, tamano, null);
    }

    /** Crea un icono con un color fijo. */
    public static Icon de(Tipo tipo, int tamano, Color color) {
        return new IconoVectorial(tipo, tamano, color);
    }

    /**
     * Dibuja el logotipo de la aplicación: una baldosa redondeada con un corazón y una cruz sanitaria.
     *
     * @param fondo    color de la baldosa (puede ser semitransparente)
     * @param corazon  color del corazón
     * @param cruz     color de la cruz interior
     */
    public static void pintarLogo(Graphics2D g, double x, double y, double tamano,
                                  Color fondo, Color corazon, Color cruz) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            g2.setColor(fondo);
            double arco = tamano * 0.3;
            g2.fill(new RoundRectangle2D.Double(0, 0, tamano, tamano, arco, arco));
            double escala = tamano / 24.0 * 0.72;
            double margen = (tamano - 24 * escala) / 2;
            g2.translate(margen, margen + escala * 0.6);
            g2.scale(escala, escala);
            g2.setColor(corazon);
            g2.fill(formaCorazon());
            g2.setColor(cruz);
            g2.fill(new RoundRectangle2D.Double(10.7, 7.4, 2.6, 7.2, 1, 1));
            g2.fill(new RoundRectangle2D.Double(8.4, 9.7, 7.2, 2.6, 1, 1));
        } finally {
            g2.dispose();
        }
    }

    /** Logotipo como imagen (para el icono de las ventanas). */
    public static Image imagenLogo(int tamano) {
        BufferedImage img = new BufferedImage(tamano, tamano, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        pintarLogo(g, 0, 0, tamano, Tema.PRIMARIO, Color.WHITE, Tema.PRIMARIO);
        g.dispose();
        return img;
    }

    /** Lista de imágenes del logotipo en varios tamaños para {@code Window.setIconImages}. */
    public static List<Image> imagenesVentana() {
        List<Image> imagenes = new ArrayList<>();
        for (int t : new int[]{16, 24, 32, 48, 64, 128}) {
            imagenes.add(imagenLogo(t));
        }
        return imagenes;
    }

    private static Shape formaCorazon() {
        Path2D p = new Path2D.Double();
        p.moveTo(12, 20.5);
        p.curveTo(12, 20.5, 3, 15, 3, 9);
        p.curveTo(3, 6, 5.3, 3.8, 8, 3.8);
        p.curveTo(9.8, 3.8, 11.2, 4.8, 12, 6.3);
        p.curveTo(12.8, 4.8, 14.2, 3.8, 16, 3.8);
        p.curveTo(18.7, 3.8, 21, 6, 21, 9);
        p.curveTo(21, 15, 12, 20.5, 12, 20.5);
        p.closePath();
        return p;
    }

    /** Implementación de {@link Icon} que dibuja trazos vectoriales. */
    private static final class IconoVectorial implements Icon {
        private final Tipo tipo;
        private final int tamano;
        private final Color color;

        IconoVectorial(Tipo tipo, int tamano, Color color) {
            this.tipo = tipo;
            this.tamano = tamano;
            this.color = color;
        }

        @Override
        public int getIconWidth() {
            return tamano;
        }

        @Override
        public int getIconHeight() {
            return tamano;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g2.setColor(resolverColor(c));
                g2.translate(x, y);
                double escala = tamano / 24.0;
                g2.scale(escala, escala);
                g2.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                dibujar(g2);
            } finally {
                g2.dispose();
            }
        }

        private Color resolverColor(Component c) {
            if (c != null && !c.isEnabled()) {
                Color deshabilitado = UIManager.getColor("Label.disabledForeground");
                return deshabilitado != null ? deshabilitado : Color.GRAY;
            }
            if (color != null) {
                return color;
            }
            return c != null && c.getForeground() != null ? c.getForeground() : Tema.TEXTO;
        }

        private void dibujar(Graphics2D g) {
            switch (tipo) {
                case INICIO: {
                    g.draw(polilinea(3.5, 11, 12, 4, 20.5, 11));
                    g.draw(polilinea(5.5, 9.5, 5.5, 20, 18.5, 20, 18.5, 9.5));
                    g.draw(polilinea(10, 20, 10, 14.5, 14, 14.5, 14, 20));
                    break;
                }
                case PACIENTES: {
                    g.draw(circulo(9, 8, 3.3));
                    Path2D h = new Path2D.Double();
                    h.moveTo(3, 19.5);
                    h.curveTo(3, 15.8, 5.6, 13.6, 9, 13.6);
                    h.curveTo(12.4, 13.6, 15, 15.8, 15, 19.5);
                    g.draw(h);
                    g.draw(new Arc2D.Double(14, 5.2, 5.6, 6, 110, -230, Arc2D.OPEN));
                    Path2D h2 = new Path2D.Double();
                    h2.moveTo(17, 13.8);
                    h2.curveTo(19.4, 14.3, 21, 16.2, 21, 19);
                    g.draw(h2);
                    break;
                }
                case AGENDA:
                case HOY: {
                    g.draw(new RoundRectangle2D.Double(3.5, 5, 17, 15.5, 4, 4));
                    g.draw(new Line2D.Double(3.5, 10, 20.5, 10));
                    g.draw(new Line2D.Double(8, 3, 8, 6.5));
                    g.draw(new Line2D.Double(16, 3, 16, 6.5));
                    if (tipo == Tipo.HOY) {
                        g.fill(circulo(12, 15.2, 2.2));
                    } else {
                        for (double[] p : new double[][]{{8, 14}, {12, 14}, {16, 14}, {8, 17.3}, {12, 17.3}}) {
                            g.fill(circulo(p[0], p[1], 1.05));
                        }
                    }
                    break;
                }
                case TRATAMIENTOS: {
                    g.draw(new RoundRectangle2D.Double(3, 7.5, 18, 12.5, 4, 4));
                    g.draw(polilinea(9, 7.5, 9, 5, 15, 5, 15, 7.5));
                    g.draw(new Line2D.Double(12, 10.8, 12, 16.8));
                    g.draw(new Line2D.Double(9, 13.8, 15, 13.8));
                    break;
                }
                case USUARIOS: {
                    Path2D s = new Path2D.Double();
                    s.moveTo(12, 3);
                    s.lineTo(19.5, 6);
                    s.lineTo(19.5, 11.5);
                    s.curveTo(19.5, 16, 16.5, 19.4, 12, 21);
                    s.curveTo(7.5, 19.4, 4.5, 16, 4.5, 11.5);
                    s.lineTo(4.5, 6);
                    s.closePath();
                    g.draw(s);
                    g.draw(polilinea(8.8, 12, 11, 14.2, 15.4, 9.8));
                    break;
                }
                case LLAVE: {
                    g.draw(circulo(8, 16, 4));
                    g.draw(new Line2D.Double(10.9, 13.1, 19.5, 4.5));
                    g.draw(new Line2D.Double(16.6, 7.4, 19, 9.8));
                    g.draw(new Line2D.Double(14.2, 9.8, 16.2, 11.8));
                    break;
                }
                case SALIR: {
                    g.draw(polilinea(10, 4, 5.5, 4, 5.5, 20, 10, 20));
                    g.draw(new Line2D.Double(10.5, 12, 20, 12));
                    g.draw(polilinea(16.5, 8.5, 20, 12, 16.5, 15.5));
                    break;
                }
                case MAS: {
                    g.draw(new Line2D.Double(12, 5, 12, 19));
                    g.draw(new Line2D.Double(5, 12, 19, 12));
                    break;
                }
                case EDITAR: {
                    Path2D p = new Path2D.Double();
                    p.moveTo(15.5, 4.5);
                    p.lineTo(19.5, 8.5);
                    p.lineTo(9, 19);
                    p.lineTo(4.5, 19.5);
                    p.lineTo(5, 15);
                    p.closePath();
                    g.draw(p);
                    g.draw(new Line2D.Double(13.3, 6.7, 17.3, 10.7));
                    break;
                }
                case PAPELERA: {
                    g.draw(new Line2D.Double(4, 6.5, 20, 6.5));
                    g.draw(polilinea(9.5, 6.5, 9.5, 4, 14.5, 4, 14.5, 6.5));
                    g.draw(polilinea(6, 6.5, 7, 20, 17, 20, 18, 6.5));
                    g.draw(new Line2D.Double(10, 10.5, 10, 16.5));
                    g.draw(new Line2D.Double(14, 10.5, 14, 16.5));
                    break;
                }
                case HISTORIAL: {
                    g.draw(new RoundRectangle2D.Double(5, 4.5, 14, 16.5, 3, 3));
                    g.draw(new RoundRectangle2D.Double(9, 3, 6, 3.4, 1.5, 1.5));
                    g.draw(new Line2D.Double(8.5, 11, 15.5, 11));
                    g.draw(new Line2D.Double(8.5, 14.5, 15.5, 14.5));
                    g.draw(new Line2D.Double(8.5, 18, 12.5, 18));
                    break;
                }
                case BUSCAR: {
                    g.draw(circulo(10.5, 10.5, 6));
                    g.draw(new Line2D.Double(15, 15, 20, 20));
                    break;
                }
                case REFRESCAR: {
                    g.draw(new Arc2D.Double(4, 4, 16, 16, 40, -320, Arc2D.OPEN));
                    g.draw(polilinea(19.6, 3.6, 19.6, 8.4, 14.8, 8.4));
                    break;
                }
                case CHECK: {
                    g.draw(polilinea(5, 12.5, 10, 17.5, 19, 7));
                    break;
                }
                case CHECK_CIRCULO: {
                    g.draw(circulo(12, 12, 8.8));
                    g.draw(polilinea(8, 12.3, 10.8, 15, 16.2, 9.4));
                    break;
                }
                case CRUZ: {
                    g.draw(new Line2D.Double(6.5, 6.5, 17.5, 17.5));
                    g.draw(new Line2D.Double(17.5, 6.5, 6.5, 17.5));
                    break;
                }
                case IZQUIERDA: {
                    g.draw(polilinea(14.5, 6, 8.5, 12, 14.5, 18));
                    break;
                }
                case DERECHA: {
                    g.draw(polilinea(9.5, 6, 15.5, 12, 9.5, 18));
                    break;
                }
                case OJO:
                case OJO_TACHADO: {
                    Path2D p = new Path2D.Double();
                    p.moveTo(2.5, 12);
                    p.curveTo(5, 7.5, 8.4, 5.5, 12, 5.5);
                    p.curveTo(15.6, 5.5, 19, 7.5, 21.5, 12);
                    p.curveTo(19, 16.5, 15.6, 18.5, 12, 18.5);
                    p.curveTo(8.4, 18.5, 5, 16.5, 2.5, 12);
                    p.closePath();
                    g.draw(p);
                    g.draw(circulo(12, 12, 3));
                    if (tipo == Tipo.OJO_TACHADO) {
                        g.draw(new Line2D.Double(4, 4, 20, 20));
                    }
                    break;
                }
                case RELOJ: {
                    g.draw(circulo(12, 12, 8.5));
                    g.draw(polilinea(12, 7.5, 12, 12, 15, 14));
                    break;
                }
                case NOTA: {
                    g.draw(polilinea(11.5, 4.5, 6.5, 4.5, 4.5, 6.5, 4.5, 19.5, 17.5, 19.5, 19.5, 17.5, 19.5, 12.5));
                    Path2D p = new Path2D.Double();
                    p.moveTo(18, 3.2);
                    p.lineTo(20.8, 6);
                    p.lineTo(13.2, 13.6);
                    p.lineTo(9.8, 14.2);
                    p.lineTo(10.4, 10.8);
                    p.closePath();
                    g.draw(p);
                    break;
                }
                case USUARIO: {
                    g.draw(circulo(12, 8, 3.8));
                    Path2D p = new Path2D.Double();
                    p.moveTo(4.5, 20);
                    p.curveTo(4.5, 15.8, 7.8, 13.6, 12, 13.6);
                    p.curveTo(16.2, 13.6, 19.5, 15.8, 19.5, 20);
                    g.draw(p);
                    break;
                }
                case CANDADO:
                case CANDADO_ABIERTO: {
                    g.draw(new RoundRectangle2D.Double(5, 10.5, 14, 10, 3, 3));
                    Path2D p = new Path2D.Double();
                    p.moveTo(8, 10.5);
                    p.lineTo(8, 7.5);
                    p.curveTo(8, 5, 9.8, 3.5, 12, 3.5);
                    if (tipo == Tipo.CANDADO) {
                        p.curveTo(14.2, 3.5, 16, 5, 16, 7.5);
                        p.lineTo(16, 10.5);
                    } else {
                        p.curveTo(14, 3.5, 15.6, 4.8, 15.9, 6.8);
                    }
                    g.draw(p);
                    g.fill(circulo(12, 15.5, 1.4));
                    break;
                }
                case PROHIBIDO: {
                    g.draw(circulo(12, 12, 8.5));
                    g.draw(new Line2D.Double(6, 6, 18, 18));
                    break;
                }
                case AUSENTE: {
                    g.draw(circulo(9, 8, 3.3));
                    Path2D p = new Path2D.Double();
                    p.moveTo(3, 19.5);
                    p.curveTo(3, 15.8, 5.6, 13.6, 9, 13.6);
                    p.curveTo(12.4, 13.6, 15, 15.8, 15, 19.5);
                    g.draw(p);
                    g.draw(new Line2D.Double(16.5, 8, 20.5, 12));
                    g.draw(new Line2D.Double(20.5, 8, 16.5, 12));
                    break;
                }
                case INFO: {
                    g.draw(circulo(12, 12, 8.5));
                    g.draw(new Line2D.Double(12, 11, 12, 16.5));
                    g.fill(circulo(12, 7.8, 1.2));
                    break;
                }
                case ALERTA: {
                    Path2D p = new Path2D.Double();
                    p.moveTo(12, 4);
                    p.lineTo(21, 19.5);
                    p.lineTo(3, 19.5);
                    p.closePath();
                    g.draw(p);
                    g.draw(new Line2D.Double(12, 10, 12, 14));
                    g.fill(circulo(12, 16.8, 1.1));
                    break;
                }
                case MINIMIZAR: {
                    g.draw(new Line2D.Double(6, 12, 18, 12));
                    break;
                }
                case INTERRUPTOR: {
                    g.draw(new RoundRectangle2D.Double(2.5, 7, 19, 10, 10, 10));
                    g.fill(circulo(16.5, 12, 2.8));
                    break;
                }
                case GRAFICO: {
                    g.draw(new Line2D.Double(4, 20, 20, 20));
                    g.draw(new Line2D.Double(7, 17, 7, 12));
                    g.draw(new Line2D.Double(12, 17, 12, 6));
                    g.draw(new Line2D.Double(17, 17, 17, 9.5));
                    break;
                }
                default:
                    g.draw(new Rectangle2D.Double(4, 4, 16, 16));
            }
        }

        private static Shape circulo(double cx, double cy, double r) {
            return new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
        }

        private static Shape polilinea(double... puntos) {
            Path2D p = new Path2D.Double();
            p.moveTo(puntos[0], puntos[1]);
            for (int i = 2; i < puntos.length; i += 2) {
                p.lineTo(puntos[i], puntos[i + 1]);
            }
            return p;
        }
    }
}
