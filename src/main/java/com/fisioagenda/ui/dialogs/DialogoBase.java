package com.fisioagenda.ui.dialogs;

import com.fisioagenda.service.ServiceException;
import com.fisioagenda.ui.Avisos;
import com.fisioagenda.ui.Tema;
import com.fisioagenda.ui.components.Componentes;
import com.fisioagenda.ui.components.Iconos;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Supplier;

/**
 * Base común de los diálogos modales: cabecera con icono y título, zona de contenido, pie con
 * mensaje de error y botones. Esc, el botón de cancelar y la X de la ventana pasan todos por
 * {@link #cancelar()} (que un diálogo puede redefinir para pedir confirmación), e Intro ejecuta el
 * botón principal. Las excepciones de negocio se muestran en el pie; los errores inesperados, con un
 * mensaje genérico.
 */
public abstract class DialogoBase extends JDialog {

    private final JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    private final JLabel lblError = Componentes.etiquetaError();
    private final JPanel centro = new JPanel(new BorderLayout());
    private boolean aceptado;

    /**
     * @param padre     componente desde el que se abre (se usa su ventana como propietaria)
     * @param titulo    título del diálogo
     * @param subtitulo texto explicativo bajo el título (puede ser {@code null})
     * @param icono     icono de la cabecera
     */
    protected DialogoBase(Component padre, String titulo, String subtitulo, Iconos.Tipo icono) {
        super(ventanaDe(padre), titulo, ModalityType.APPLICATION_MODAL);
        // La X de la ventana no cierra directamente: pasa por cancelar() como Esc y el botón "Cancelar".
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancelar();
            }
        });
        JPanel raiz = new JPanel(new BorderLayout());
        raiz.setBackground(Tema.SUPERFICIE);
        raiz.add(crearCabecera(titulo, subtitulo, icono), BorderLayout.NORTH);
        centro.setOpaque(false);
        centro.setBorder(BorderFactory.createEmptyBorder(18, 24, 8, 24));
        raiz.add(centro, BorderLayout.CENTER);
        raiz.add(crearPie(), BorderLayout.SOUTH);
        setContentPane(raiz);

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelar");
        getRootPane().getActionMap().put("cancelar", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cancelar();
            }
        });
    }

    /** Ventana propietaria a partir de cualquier componente. */
    protected static Window ventanaDe(Component c) {
        if (c == null) {
            return null;
        }
        return c instanceof Window ? (Window) c : SwingUtilities.getWindowAncestor(c);
    }

    private JComponent crearCabecera(String titulo, String subtitulo, Iconos.Tipo icono) {
        JPanel cabecera = new JPanel(new BorderLayout(14, 0));
        cabecera.setOpaque(false);
        cabecera.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Tema.BORDE),
                BorderFactory.createEmptyBorder(18, 24, 16, 24)));
        cabecera.add(new Baldosa(Iconos.de(icono, 22, Tema.PRIMARIO)), BorderLayout.WEST);
        JPanel textos = new JPanel();
        textos.setOpaque(false);
        textos.setLayout(new BoxLayout(textos, BoxLayout.Y_AXIS));
        JLabel lblTitulo = new JLabel(titulo);
        lblTitulo.setFont(Tema.SUBTITULO.deriveFont(17f));
        lblTitulo.setForeground(Tema.TEXTO);
        textos.add(Box.createVerticalGlue());
        textos.add(lblTitulo);
        if (subtitulo != null) {
            textos.add(Box.createVerticalStrut(2));
            textos.add(Componentes.secundaria(subtitulo));
        }
        textos.add(Box.createVerticalGlue());
        cabecera.add(textos, BorderLayout.CENTER);
        return cabecera;
    }

    private JComponent crearPie() {
        JPanel pie = new JPanel(new BorderLayout(12, 0));
        pie.setBackground(Tema.FONDO);
        pie.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Tema.BORDE),
                BorderFactory.createEmptyBorder(14, 24, 14, 24)));
        botones.setOpaque(false);
        pie.add(lblError, BorderLayout.CENTER);
        pie.add(botones, BorderLayout.EAST);
        return pie;
    }

    /** Coloca el contenido principal del diálogo. */
    protected void setContenido(JComponent contenido) {
        centro.removeAll();
        centro.add(contenido, BorderLayout.CENTER);
    }

    /** Añade el botón de cancelar (cierra sin aceptar). */
    protected JButton agregarBotonCancelar(String texto) {
        JButton b = Componentes.boton(texto, null, Componentes.EstiloBoton.SECUNDARIO, '\0', texto + " (Esc)");
        b.addActionListener(e -> cancelar());
        botones.add(b);
        return b;
    }

    /** Añade un botón secundario con la acción indicada. */
    protected JButton agregarBoton(String texto, Iconos.Tipo icono, char mnemonico, String ayuda, Runnable accion) {
        JButton b = Componentes.boton(texto, icono, Componentes.EstiloBoton.SECUNDARIO, mnemonico, ayuda);
        b.addActionListener(e -> accion.run());
        botones.add(b);
        return b;
    }

    /** Añade el botón principal (botón por defecto: se activa con Intro). */
    protected JButton agregarBotonPrincipal(String texto, Iconos.Tipo icono, char mnemonico, Runnable accion) {
        JButton b = Componentes.boton(texto, icono, Componentes.EstiloBoton.PRIMARIO, mnemonico, texto + " (Intro)");
        b.addActionListener(e -> accion.run());
        botones.add(b);
        getRootPane().setDefaultButton(b);
        return b;
    }

    /** Ejecuta el botón principal del diálogo, como si se pulsara Intro fuera de cualquier campo especial. */
    protected void pulsarBotonPrincipal() {
        JButton principal = getRootPane().getDefaultButton();
        if (principal != null && principal.isEnabled()) {
            principal.doClick();
        }
    }

    /** Muestra un mensaje de error en el pie del diálogo ({@code null} lo borra). */
    protected void mostrarError(String mensaje) {
        Componentes.mostrarError(lblError, mensaje);
    }

    /**
     * Ejecuta una operación de servicio. Si lanza {@link ServiceException} el mensaje se muestra en el
     * pie; si falla de forma inesperada se registra y se avisa con un mensaje genérico.
     *
     * @return el resultado o {@code null} si hubo error
     */
    protected <T> T ejecutar(Supplier<T> operacion) {
        try {
            mostrarError(null);
            return operacion.get();
        } catch (ServiceException e) {
            mostrarError(e.getMessage());
        } catch (RuntimeException e) {
            Avisos.errorInesperado(this, e);
        }
        return null;
    }

    /** Cierra el diálogo marcándolo como aceptado. */
    protected void aceptar() {
        aceptado = true;
        dispose();
    }

    /** Cierra el diálogo sin aceptar. */
    protected void cancelar() {
        aceptado = false;
        dispose();
    }

    /** Indica si el diálogo se cerró aceptando (por ejemplo, tras guardar). */
    public boolean isAceptado() {
        return aceptado;
    }

    /** Ajusta el tamaño, centra el diálogo sobre su ventana y lo muestra (bloquea hasta cerrarse). */
    protected void abrir(int anchoMinimo, int altoMinimo) {
        pack();
        Dimension d = getSize();
        setSize(Math.max(d.width, anchoMinimo), Math.max(d.height, altoMinimo));
        setMinimumSize(new Dimension(Math.min(anchoMinimo, getWidth()), Math.min(altoMinimo, getHeight())));
        setLocationRelativeTo(getOwner());
        setVisible(true);
    }

    /** Baldosa redondeada con el icono de la cabecera. */
    private static final class Baldosa extends JComponent {
        private final Icon icono;

        Baldosa(Icon icono) {
            this.icono = icono;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(44, 44);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int y = (getHeight() - 44) / 2;
                g2.setColor(Tema.suavizar(Tema.PRIMARIO, 0.86));
                g2.fillRoundRect(0, y, 44, 44, 12, 12);
                icono.paintIcon(this, g2, 11, y + 11);
            } finally {
                g2.dispose();
            }
        }
    }
}
