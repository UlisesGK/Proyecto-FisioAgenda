package com.fisioagenda.ui;

import com.fisioagenda.model.Usuario;
import com.fisioagenda.security.DispositivoLocal;
import com.fisioagenda.service.ServiceException;
import com.fisioagenda.service.ServiceLocator;
import com.fisioagenda.ui.components.Animador;
import com.fisioagenda.ui.components.Componentes;
import com.fisioagenda.ui.components.Iconos;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ventana de inicio de sesión (900x560, sin bordes del sistema). A la izquierda muestra la marca
 * con un degradado y el logotipo dibujado con Java2D; a la derecha el formulario de acceso.
 * La comprobación de credenciales (PBKDF2, lenta a propósito) se hace en segundo plano.
 */
public class LoginFrame extends JFrame {

    private static final Logger LOG = Logger.getLogger(LoginFrame.class.getName());
    private static final int ANCHO_CAMPOS = 320;

    private final JTextField txtUsuario = new JTextField();
    private final JPasswordField txtPassword = new JPasswordField();
    private final JCheckBox chkRecordar = new JCheckBox("Recordar este dispositivo");
    private final JButton btnEntrar;
    private final JLabel lblError = Componentes.etiquetaError();
    private final JPanel formulario = new JPanel();
    private final JPanel centro = new JPanel(new GridBagLayout());
    private final char ecoPassword;
    private Timer sacudida;
    private Point origenArrastre;

    /** Crea la ventana de acceso sin ningún aviso previo. */
    public LoginFrame() {
        this(null);
    }

    /**
     * Crea la ventana de acceso.
     *
     * @param aviso mensaje informativo opcional (por ejemplo, "sesión cerrada por inactividad")
     */
    public LoginFrame(String aviso) {
        super("FisioAgenda — Iniciar sesión");
        setUndecorated(true);
        setResizable(false);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setIconImages(Iconos.imagenesVentana());
        setSize(900, 600);
        setLocationRelativeTo(null);

        btnEntrar = Componentes.boton("Entrar", null, Componentes.EstiloBoton.PRIMARIO, 'E',
                "Iniciar sesión (Intro)");
        Object eco = UIManager.get("PasswordField.echoChar");
        ecoPassword = eco instanceof Character ? (Character) eco : '•';

        JPanel raiz = new JPanel(new GridLayout(1, 2));
        raiz.setBorder(BorderFactory.createLineBorder(new Color(0xCBD5E1)));
        PanelMarca marca = new PanelMarca();
        JComponent derecho = crearPanelFormulario(aviso);
        raiz.add(marca);
        raiz.add(derecho);
        setContentPane(raiz);
        getRootPane().setDefaultButton(btnEntrar);
        habilitarArrastre(marca, derecho, centro);
    }

    private JComponent crearPanelFormulario(String aviso) {
        JPanel derecho = new JPanel(new BorderLayout());
        derecho.setBackground(Tema.SUPERFICIE);

        // Controles de ventana (la ventana no tiene barra de título del sistema).
        JPanel controles = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 8));
        controles.setOpaque(false);
        JButton btnMinimizar = Componentes.botonIcono(Iconos.Tipo.MINIMIZAR, "Minimizar");
        btnMinimizar.addActionListener(e -> setExtendedState(Frame.ICONIFIED));
        JButton btnCerrar = Componentes.botonIcono(Iconos.Tipo.CRUZ, "Cerrar FisioAgenda");
        btnCerrar.addActionListener(e -> {
            dispose();
            System.exit(0);
        });
        controles.add(btnMinimizar);
        controles.add(btnCerrar);
        derecho.add(controles, BorderLayout.NORTH);

        formulario.setLayout(new BoxLayout(formulario, BoxLayout.Y_AXIS));
        formulario.setOpaque(false);

        JLabel titulo = new JLabel("Iniciar sesión");
        titulo.setFont(Tema.TITULO.deriveFont(26f));
        titulo.setForeground(Tema.TEXTO);
        agregar(titulo, 0);
        JLabel subtitulo = new JLabel("Introduzca sus credenciales para acceder a la agenda.");
        subtitulo.setFont(Tema.NORMAL);
        subtitulo.setForeground(Tema.TEXTO_SECUNDARIO);
        agregar(subtitulo, 6);

        if (aviso != null && !aviso.isBlank()) {
            JLabel lblAviso = new JLabel("<html>" + Componentes.escaparHtml(aviso) + "</html>",
                    Iconos.de(Iconos.Tipo.INFO, 16, Tema.INFO), SwingConstants.LEADING);
            lblAviso.setForeground(Tema.INFO);
            lblAviso.setFont(Tema.PEQUENA.deriveFont(Font.BOLD));
            lblAviso.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Tema.suavizar(Tema.INFO, 0.7)),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)));
            lblAviso.setOpaque(true);
            lblAviso.setBackground(Tema.suavizar(Tema.INFO, 0.93));
            lblAviso.setVerticalTextPosition(SwingConstants.TOP);
            limitarAncho(lblAviso, ANCHO_CAMPOS);
            agregar(lblAviso, 18);
        }

        JLabel lblUsuario = etiquetaCampo("Usuario", 'U', txtUsuario);
        agregar(lblUsuario, 24);
        txtUsuario.putClientProperty("JTextField.placeholderText", "Nombre de usuario");
        txtUsuario.putClientProperty("JTextField.leadingIcon", Iconos.de(Iconos.Tipo.USUARIO, 16, Tema.TEXTO_SECUNDARIO));
        txtUsuario.getAccessibleContext().setAccessibleName("Usuario");
        agregar(dimensionar(txtUsuario), 6);

        JLabel lblPassword = etiquetaCampo("Contraseña", 'C', txtPassword);
        agregar(lblPassword, 16);
        txtPassword.putClientProperty("JTextField.placeholderText", "Contraseña de acceso");
        txtPassword.putClientProperty("JTextField.leadingIcon", Iconos.de(Iconos.Tipo.CANDADO, 16, Tema.TEXTO_SECUNDARIO));
        txtPassword.putClientProperty("JTextField.trailingComponent", crearBotonMostrar());
        txtPassword.getAccessibleContext().setAccessibleName("Contraseña");
        agregar(dimensionar(txtPassword), 6);

        chkRecordar.setOpaque(false);
        chkRecordar.setMnemonic('R');
        chkRecordar.setToolTipText("No volver a pedir la contraseña en este equipo durante "
                + com.fisioagenda.service.AuthService.DIAS_RECORDAR + " días. Se olvida al cerrar sesión.");
        agregar(chkRecordar, 10);

        agregar(lblError, 10);
        lblError.setPreferredSize(new Dimension(ANCHO_CAMPOS, 34));
        lblError.setMaximumSize(new Dimension(ANCHO_CAMPOS, 34));
        lblError.setVerticalAlignment(SwingConstants.TOP);

        btnEntrar.setFont(Tema.NORMAL_NEGRITA.deriveFont(14f));
        agregar(dimensionar(btnEntrar), 4);
        btnEntrar.setPreferredSize(new Dimension(ANCHO_CAMPOS, 42));
        btnEntrar.setMaximumSize(new Dimension(ANCHO_CAMPOS, 42));
        btnEntrar.addActionListener(e -> entrar());

        centro.setOpaque(false);
        centro.add(formulario);
        derecho.add(centro, BorderLayout.CENTER);

        JLabel pie = Componentes.secundaria("Demo: admin / Admin1234!");
        pie.setHorizontalAlignment(SwingConstants.CENTER);
        pie.setBorder(BorderFactory.createEmptyBorder(0, 0, 22, 0));
        pie.setToolTipText("Otros usuarios de prueba: fisio / Fisio1234!, recepcion / Recep1234!");
        derecho.add(pie, BorderLayout.SOUTH);
        return derecho;
    }

    private JLabel etiquetaCampo(String texto, char mnemonico, JComponent campo) {
        JLabel l = new JLabel(texto);
        l.setFont(Tema.NORMAL_NEGRITA);
        l.setForeground(Tema.TEXTO);
        l.setDisplayedMnemonic(mnemonico);
        l.setLabelFor(campo);
        return l;
    }

    /**
     * Fija el ancho de una etiqueta HTML y le da el alto que necesita su texto repartido en varias líneas,
     * para que no sobresalga de la columna del formulario.
     */
    private static void limitarAncho(JLabel etiqueta, int ancho) {
        Insets margen = etiqueta.getInsets();
        Icon icono = etiqueta.getIcon();
        int anchoTexto = ancho - margen.left - margen.right
                - (icono != null ? icono.getIconWidth() + etiqueta.getIconTextGap() : 0);
        int altoTexto = etiqueta.getPreferredSize().height - margen.top - margen.bottom;
        Object vista = etiqueta.getClientProperty(BasicHTML.propertyKey);
        if (vista instanceof View) {
            ((View) vista).setSize(anchoTexto, 0);
            altoTexto = (int) Math.ceil(((View) vista).getPreferredSpan(View.Y_AXIS));
        }
        int altoIcono = icono != null ? icono.getIconHeight() : 0;
        Dimension tamano = new Dimension(ancho, Math.max(altoTexto, altoIcono) + margen.top + margen.bottom);
        etiqueta.setPreferredSize(tamano);
        etiqueta.setMaximumSize(tamano);
    }

    private JComponent dimensionar(JComponent c) {
        c.setPreferredSize(new Dimension(ANCHO_CAMPOS, 38));
        c.setMaximumSize(new Dimension(ANCHO_CAMPOS, 38));
        return c;
    }

    private void agregar(JComponent c, int espacioAntes) {
        if (espacioAntes > 0) {
            formulario.add(Box.createVerticalStrut(espacioAntes));
        }
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        formulario.add(c);
    }

    private JButton crearBotonMostrar() {
        JButton b = Componentes.botonIcono(Iconos.Tipo.OJO, "Mostrar contraseña");
        b.setFocusable(true);
        b.addActionListener(e -> {
            boolean oculta = txtPassword.getEchoChar() != 0;
            txtPassword.setEchoChar(oculta ? (char) 0 : ecoPassword);
            b.setIcon(Iconos.de(oculta ? Iconos.Tipo.OJO_TACHADO : Iconos.Tipo.OJO, 16));
            String ayuda = oculta ? "Ocultar contraseña" : "Mostrar contraseña";
            b.setToolTipText(ayuda);
            b.getAccessibleContext().setAccessibleName(ayuda);
            txtPassword.requestFocusInWindow();
        });
        return b;
    }

    /** Muestra la ventana con un fundido de entrada si el sistema admite ventanas translúcidas. */
    public void mostrar() {
        boolean fundido = false;
        try {
            GraphicsConfiguration gc = getGraphicsConfiguration();
            GraphicsDevice dispositivo = gc != null ? gc.getDevice() : null;
            if (dispositivo != null
                    && dispositivo.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
                setOpacity(0f);
                fundido = true;
            }
        } catch (RuntimeException e) {
            fundido = false;
        }
        setVisible(true);
        txtUsuario.requestFocusInWindow();
        if (fundido) {
            Animador.animar(300, p -> {
                try {
                    setOpacity((float) p);
                } catch (RuntimeException e) {
                    LOG.log(Level.FINE, "No se pudo cambiar la opacidad", e);
                }
            }, null);
        }
    }

    private void entrar() {
        String usuario = txtUsuario.getText().trim();
        char[] password = txtPassword.getPassword();
        if (usuario.isEmpty() || password.length == 0) {
            Arrays.fill(password, '\0');
            fallar("Introduzca su usuario y su contraseña.");
            (usuario.isEmpty() ? txtUsuario : txtPassword).requestFocusInWindow();
            return;
        }
        Componentes.mostrarError(lblError, null);
        habilitarFormulario(false);
        boolean recordar = chkRecordar.isSelected();
        Tareas.ejecutar(() -> {
            try {
                Usuario autenticado = ServiceLocator.auth().login(usuario, password);
                if (recordar) {
                    recordarEsteDispositivo();
                }
                return autenticado;
            } finally {
                Arrays.fill(password, '\0');
            }
        }, this::abrirAplicacion, error -> {
            habilitarFormulario(true);
            if (error instanceof ServiceException) {
                fallar(error.getMessage());
            } else {
                LOG.log(Level.SEVERE, "Error inesperado al iniciar sesión", error);
                fallar("No se pudo iniciar sesión por un error inesperado. Inténtelo de nuevo.");
            }
            txtPassword.setText("");
            txtPassword.requestFocusInWindow();
        });
    }

    /** Guarda el token de este equipo; si falla, el acceso sigue adelante y la próxima vez se pide la contraseña. */
    private static void recordarEsteDispositivo() {
        try {
            DispositivoLocal.guardar(ServiceLocator.auth().recordarDispositivo());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "No se pudo recordar el dispositivo", e);
        }
    }

    /** Abre la aplicación directamente si este equipo está recordado; si no, muestra la pantalla de acceso. */
    public static void iniciar() {
        Optional<String> token = DispositivoLocal.leer();
        if (token.isPresent()) {
            try {
                Optional<Usuario> usuario = ServiceLocator.auth().loginConToken(token.get());
                if (usuario.isPresent()) {
                    new MainFrame(usuario.get()).setVisible(true);
                    return;
                }
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "No se pudo entrar con el dispositivo recordado", e);
            }
            DispositivoLocal.borrar();
        }
        new LoginFrame().mostrar();
    }

    private void abrirAplicacion(Usuario usuario) {
        try {
            MainFrame principal = new MainFrame(usuario);
            principal.setVisible(true);
            dispose();
        } catch (RuntimeException e) {
            habilitarFormulario(true);
            Avisos.errorInesperado(this, e);
        }
    }

    private void habilitarFormulario(boolean habilitado) {
        txtUsuario.setEnabled(habilitado);
        txtPassword.setEnabled(habilitado);
        chkRecordar.setEnabled(habilitado);
        btnEntrar.setEnabled(habilitado);
        btnEntrar.setText(habilitado ? "Entrar" : "Comprobando...");
    }

    private void fallar(String mensaje) {
        Componentes.mostrarError(lblError, mensaje);
        sacudir();
    }

    /** Animación de "sacudida" horizontal del formulario para indicar el error. */
    private void sacudir() {
        Animador.detener(sacudida);
        centro.validate();
        final int xBase = formulario.getX();
        final int y = formulario.getY();
        sacudida = Animador.animar(420, p -> {
            double amplitud = 12 * (1 - p);
            int desplazamiento = (int) Math.round(Math.sin(p * Math.PI * 6) * amplitud);
            formulario.setLocation(xBase + desplazamiento, y);
        }, () -> {
            formulario.setLocation(xBase, y);
            centro.revalidate();
        });
    }

    /** Permite mover la ventana (que no tiene barra de título) arrastrando las zonas indicadas. */
    private void habilitarArrastre(Component... zonas) {
        MouseAdapter arrastre = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Point p = e.getLocationOnScreen();
                origenArrastre = new Point(p.x - getX(), p.y - getY());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (origenArrastre != null) {
                    Point p = e.getLocationOnScreen();
                    setLocation(p.x - origenArrastre.x, p.y - origenArrastre.y);
                }
            }
        };
        for (Component zona : zonas) {
            zona.addMouseListener(arrastre);
            zona.addMouseMotionListener(arrastre);
        }
    }

    /** Panel izquierdo con degradado, logotipo y lema, dibujado íntegramente con Java2D. */
    private static final class PanelMarca extends JPanel {

        PanelMarca() {
            setOpaque(true);
            getAccessibleContext().setAccessibleName("FisioAgenda. Tu clínica, organizada");
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                g2.setPaint(new GradientPaint(0, 0, Tema.PRIMARIO, w, h, Tema.PRIMARIO_OSCURO));
                g2.fillRect(0, 0, w, h);

                // Formas decorativas translúcidas.
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.08f));
                g2.setColor(Color.WHITE);
                g2.fillOval(w - 170, -120, 340, 340);
                g2.fillOval(-140, h - 200, 320, 320);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.06f));
                g2.fillOval(w - 120, h - 150, 200, 200);
                g2.setComposite(AlphaComposite.SrcOver);

                int x = 56;
                Iconos.pintarLogo(g2, x, 150, 76, new Color(255, 255, 255, 46), Color.WHITE, Tema.PRIMARIO);

                g2.setColor(Color.WHITE);
                g2.setFont(new Font(Tema.FAMILIA, Font.BOLD, 34));
                FontMetrics fm = g2.getFontMetrics();
                int y = 150 + 76 + 28 + fm.getAscent();
                g2.drawString("FisioAgenda", x, y);

                g2.setFont(new Font(Tema.FAMILIA, Font.PLAIN, 18));
                g2.setColor(new Color(255, 255, 255, 225));
                y += 34;
                g2.drawString("Tu clínica, organizada", x, y);

                g2.setFont(new Font(Tema.FAMILIA, Font.PLAIN, 13));
                g2.setColor(new Color(255, 255, 255, 200));
                String[] puntos = {"Agenda sin solapes ni huecos perdidos", "Historial clínico con evolución del dolor",
                        "Acceso seguro según el rol de cada profesional"};
                y += 44;
                for (String punto : puntos) {
                    Iconos.de(Iconos.Tipo.CHECK_CIRCULO, 16, new Color(0x99F6E4)).paintIcon(this, g2, x, y - 13);
                    g2.drawString(punto, x + 26, y);
                    y += 28;
                }
            } finally {
                g2.dispose();
            }
        }
    }
}
