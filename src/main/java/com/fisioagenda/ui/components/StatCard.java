package com.fisioagenda.ui.components;

import com.fisioagenda.ui.Tema;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.function.DoubleFunction;

/**
 * Tarjeta de estadística para el panel de inicio: icono en una baldosa de color, título,
 * cifra grande (con animación de recuento al actualizarse) y una línea de detalle.
 * En modo compacto (ventanas estrechas) ocupa menos alto: márgenes e icono más pequeños y el
 * detalle a la derecha de la cifra en lugar de debajo.
 */
public class StatCard extends Tarjeta {

    private static final int DURACION_MS = 650;
    private static final Font CIFRA_COMPACTA = Tema.CIFRA.deriveFont(22f);

    private final JLabel lblValor = new JLabel("—");
    private final JLabel lblDetalle;
    private final JPanel textos = new JPanel();
    private final JPanel filaValor = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0)) {
        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    };
    private final BaldosaIcono baldosa;
    private final String titulo;
    private boolean compacta;
    private double valorMostrado;
    private Timer animacion;

    /**
     * @param titulo  título de la cifra (p. ej. "Citas hoy")
     * @param tipo    icono representativo
     * @param color   color del acento del icono
     * @param detalle texto pequeño bajo la cifra
     */
    public StatCard(String titulo, Iconos.Tipo tipo, Color color, String detalle) {
        super(new BorderLayout(16, 0), 18);
        this.titulo = titulo;
        baldosa = new BaldosaIcono(Iconos.de(tipo, 22, color), Tema.suavizar(color, 0.85));
        add(baldosa, BorderLayout.WEST);

        textos.setLayout(new BoxLayout(textos, BoxLayout.Y_AXIS));
        textos.setOpaque(false);
        JLabel lblTitulo = new JLabel(titulo);
        lblTitulo.setFont(Tema.NORMAL_NEGRITA);
        lblTitulo.setForeground(Tema.TEXTO_SECUNDARIO);
        lblValor.setFont(Tema.CIFRA);
        lblValor.setForeground(Tema.TEXTO);
        lblDetalle = Componentes.secundaria(detalle);
        // Cifra y detalle comparten la línea base cuando van en la misma fila (modo compacto).
        ((FlowLayout) filaValor.getLayout()).setAlignOnBaseline(true);
        filaValor.setOpaque(false);
        filaValor.add(lblValor);
        for (JComponent c : new JComponent[]{lblTitulo, filaValor, lblDetalle}) {
            c.setAlignmentX(LEFT_ALIGNMENT);
            textos.add(c);
        }
        add(textos, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        getAccessibleContext().setAccessibleName(titulo);
    }

    /**
     * Activa o desactiva el modo compacto, pensado para la rejilla de 2x2 de las ventanas estrechas:
     * la tarjeta pasa de unos 100 px de alto a unos 65 px y deja más sitio a la tabla de citas.
     */
    public void setCompacta(boolean compacta) {
        if (this.compacta == compacta) {
            return;
        }
        this.compacta = compacta;
        lblValor.setFont(compacta ? CIFRA_COMPACTA : Tema.CIFRA);
        if (compacta) {
            textos.remove(lblDetalle);
            lblDetalle.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
            filaValor.add(lblDetalle);
        } else {
            filaValor.remove(lblDetalle);
            lblDetalle.setBorder(null);
            textos.add(lblDetalle);
        }
        baldosa.setLado(compacta ? 36 : 48);
        ((BorderLayout) getLayout()).setHgap(compacta ? 12 : 16);
        setBorder(compacta ? BorderFactory.createEmptyBorder(10, 14, 10, 14)
                : BorderFactory.createEmptyBorder(18, 18, 18, 18));
        revalidate();
        repaint();
    }

    /** Indica si la tarjeta está en modo compacto. */
    public boolean isCompacta() {
        return compacta;
    }

    /** Actualiza la cifra con animación de recuento usando el formato indicado. */
    public void setValor(double valor, DoubleFunction<String> formato) {
        Animador.detener(animacion);
        final double inicio = valorMostrado;
        animacion = Animador.animar(DURACION_MS, p -> {
            valorMostrado = Animador.interpolar(inicio, valor, p);
            lblValor.setText(formato.apply(p >= 1 ? valor : valorMostrado));
        }, null);
        getAccessibleContext().setAccessibleDescription(titulo + ": " + formato.apply(valor));
    }

    /** Muestra un valor entero. */
    public void setValor(int valor) {
        setValor(valor, v -> String.valueOf(Math.round(v)));
    }

    /** Muestra un texto fijo (por ejemplo "—" cuando no hay datos). */
    public void setTexto(String texto) {
        Animador.detener(animacion);
        lblValor.setText(texto);
    }

    /** Cambia la línea de detalle. */
    public void setDetalle(String detalle) {
        lblDetalle.setText(detalle);
    }

    /** Baldosa redondeada con el icono centrado. */
    private static final class BaldosaIcono extends JComponent {
        private final Icon icono;
        private final Color fondo;
        private int lado = 48;

        BaldosaIcono(Icon icono, Color fondo) {
            this.icono = icono;
            this.fondo = fondo;
        }

        void setLado(int lado) {
            this.lado = lado;
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(lado, lado);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int y = (getHeight() - lado) / 2;
                int arco = lado * 14 / 48;
                g2.setColor(fondo);
                g2.fillRoundRect(0, y, lado, lado, arco, arco);
                icono.paintIcon(this, g2, (lado - icono.getIconWidth()) / 2, y + (lado - icono.getIconHeight()) / 2);
            } finally {
                g2.dispose();
            }
        }
    }
}
