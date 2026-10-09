package com.fisioagenda.ui;

import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Usuario;
import com.fisioagenda.service.ServiceLocator;
import com.fisioagenda.ui.components.AnimatedCardPanel;
import com.fisioagenda.ui.components.Avatar;
import com.fisioagenda.ui.components.Animador;
import com.fisioagenda.ui.components.ComponenteDibujado;
import com.fisioagenda.ui.components.Componentes;
import com.fisioagenda.ui.components.Iconos;
import com.fisioagenda.ui.components.SidebarButton;
import com.fisioagenda.ui.components.Toast;
import com.fisioagenda.ui.dialogs.CambiarPasswordDialog;
import com.fisioagenda.ui.panels.AgendaPanel;
import com.fisioagenda.ui.panels.DashboardPanel;
import com.fisioagenda.ui.panels.PacientesPanel;
import com.fisioagenda.ui.panels.Seccion;
import com.fisioagenda.ui.panels.TratamientosPanel;
import com.fisioagenda.ui.panels.UsuariosPanel;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ventana principal tras iniciar sesión: barra lateral de navegación (que se colapsa a solo iconos
 * cuando la ventana es estrecha), cabecera con la sección y el usuario, y contenido con transiciones
 * animadas. Gestiona los atajos de teclado globales y el cierre de sesión por inactividad.
 */
public class MainFrame extends JFrame {

    private static final Logger LOG = Logger.getLogger(MainFrame.class.getName());
    private static final int ANCHO_SIDEBAR = 220;
    private static final int ANCHO_SIDEBAR_COLAPSADO = 64;
    private static final int UMBRAL_COLAPSO = 1100;
    private static final int INACTIVIDAD_MS = 15 * 60 * 1000;
    private static final DateTimeFormatter FECHA_LARGA =
            DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-ES"));

    private final Usuario usuario;
    private final AnimatedCardPanel contenido = new AnimatedCardPanel();
    private final List<EntradaSeccion> secciones = new ArrayList<>();
    private final List<SidebarButton> botonesSidebar = new ArrayList<>();
    private final JLabel lblTitulo = new JLabel();
    private final Sidebar sidebar = new Sidebar();
    private final Timer temporizadorInactividad;
    private final AWTEventListener oyenteActividad;
    private EntradaSeccion actual;
    private boolean colapsado;
    private Timer animacionSidebar;
    private boolean finalizada;

    /** Crea la ventana principal para el usuario que ha iniciado sesión. */
    public MainFrame(Usuario usuario) {
        super("FisioAgenda — " + usuario.getNombreCompleto() + " (" + usuario.getRol().getEtiqueta() + ")");
        this.usuario = usuario;
        setIconImages(Iconos.imagenesVentana());
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setSize(1200, 760);
        setMinimumSize(new Dimension(960, 600));
        setLocationRelativeTo(null);

        crearSecciones();
        JPanel raiz = new JPanel(new BorderLayout());
        raiz.setBackground(Tema.FONDO);
        raiz.add(sidebar, BorderLayout.WEST);
        JPanel derecha = new JPanel(new BorderLayout());
        derecha.setBackground(Tema.FONDO);
        derecha.add(crearCabecera(), BorderLayout.NORTH);
        contenido.setBorder(BorderFactory.createEmptyBorder(20, 24, 24, 24));
        derecha.add(contenido, BorderLayout.CENTER);
        raiz.add(derecha, BorderLayout.CENTER);
        setContentPane(raiz);

        registrarAtajos();
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                ajustarSidebar(true);
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                salir();
            }
        });

        temporizadorInactividad = new Timer(INACTIVIDAD_MS, e -> cerrarSesion(
                "Su sesión se ha cerrado tras 15 minutos de inactividad. Vuelva a identificarse para continuar."));
        temporizadorInactividad.setRepeats(false);
        oyenteActividad = evento -> {
            if (!finalizada) {
                temporizadorInactividad.restart();
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(oyenteActividad, AWTEvent.KEY_EVENT_MASK
                | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_WHEEL_EVENT_MASK);
        temporizadorInactividad.start();

        ajustarSidebar(false);
        mostrarSeccion(0);
    }

    private void crearSecciones() {
        agregarSeccion("inicio", "Inicio", Iconos.Tipo.INICIO, new DashboardPanel(usuario));
        agregarSeccion("pacientes", "Pacientes", Iconos.Tipo.PACIENTES, new PacientesPanel(usuario));
        agregarSeccion("agenda", "Agenda", Iconos.Tipo.AGENDA, new AgendaPanel(usuario));
        agregarSeccion("tratamientos", "Tratamientos", Iconos.Tipo.TRATAMIENTOS, new TratamientosPanel(usuario));
        if (usuario.getRol() == Rol.ADMIN) {
            agregarSeccion("usuarios", "Usuarios", Iconos.Tipo.USUARIOS, new UsuariosPanel(usuario));
        }

        SidebarButton btnPassword = new SidebarButton("Cambiar contraseña", Iconos.Tipo.LLAVE,
                "Cambiar mi contraseña");
        btnPassword.addActionListener(e -> cambiarPassword());
        SidebarButton btnSalir = new SidebarButton("Cerrar sesión", Iconos.Tipo.SALIR, "Cerrar sesión (Ctrl+Q)");
        btnSalir.addActionListener(e -> confirmarCierreSesion());
        sidebar.agregarInferior(btnPassword);
        sidebar.agregarInferior(btnSalir);
        botonesSidebar.add(btnPassword);
        botonesSidebar.add(btnSalir);
    }

    private <P extends JComponent & Seccion> void agregarSeccion(String id, String texto, Iconos.Tipo icono, P panel) {
        int numero = secciones.size() + 1;
        SidebarButton boton = new SidebarButton(texto, icono, texto + " (Ctrl+" + numero + ")");
        EntradaSeccion entrada = new EntradaSeccion(id, panel, boton);
        boton.addActionListener(e -> mostrarSeccion(secciones.indexOf(entrada)));
        secciones.add(entrada);
        botonesSidebar.add(boton);
        sidebar.agregarNavegacion(boton);
        contenido.agregar(id, panel);
    }

    private JComponent crearCabecera() {
        JPanel cabecera = new JPanel(new BorderLayout(16, 0));
        cabecera.setBackground(Tema.SUPERFICIE);
        cabecera.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Tema.BORDE),
                BorderFactory.createEmptyBorder(14, 24, 14, 24)));

        JPanel titulos = new JPanel();
        titulos.setLayout(new BoxLayout(titulos, BoxLayout.Y_AXIS));
        titulos.setOpaque(false);
        lblTitulo.setFont(Tema.TITULO);
        lblTitulo.setForeground(Tema.TEXTO);
        String hoy = LocalDate.now().format(FECHA_LARGA);
        JLabel lblFecha = Componentes.secundaria(Character.toUpperCase(hoy.charAt(0)) + hoy.substring(1));
        titulos.add(lblTitulo);
        titulos.add(Box.createVerticalStrut(2));
        titulos.add(lblFecha);
        cabecera.add(titulos, BorderLayout.WEST);

        JPanel perfil = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        perfil.setOpaque(false);
        JPanel datos = new JPanel();
        datos.setLayout(new BoxLayout(datos, BoxLayout.Y_AXIS));
        datos.setOpaque(false);
        JLabel lblNombre = new JLabel(usuario.getNombreCompleto());
        lblNombre.setFont(Tema.NORMAL_NEGRITA);
        lblNombre.setForeground(Tema.TEXTO);
        lblNombre.setAlignmentX(Component.RIGHT_ALIGNMENT);
        JLabel lblRol = Componentes.secundaria(usuario.getRol().getEtiqueta());
        lblRol.setAlignmentX(Component.RIGHT_ALIGNMENT);
        datos.add(lblNombre);
        datos.add(lblRol);
        perfil.add(datos);
        perfil.add(new Avatar(usuario.getNombreCompleto(), 40));
        perfil.setToolTipText("Sesión iniciada como " + usuario.getUsername());
        cabecera.add(perfil, BorderLayout.EAST);
        return cabecera;
    }

    private void registrarAtajos() {
        JRootPane raiz = getRootPane();
        InputMap entradas = raiz.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        int ctrl = InputEvent.CTRL_DOWN_MASK;
        String[] ids = {"inicio", "pacientes", "agenda", "tratamientos", "usuarios"};
        for (int i = 0; i < ids.length; i++) {
            final String id = ids[i];
            String accion = "seccion-" + id;
            entradas.put(KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, ctrl), accion);
            raiz.getActionMap().put(accion, accion(() -> mostrarSeccion(indiceDe(id))));
        }
        vincular(raiz, entradas, KeyStroke.getKeyStroke(KeyEvent.VK_N, ctrl), "nuevo",
                () -> actual.seccion.nuevo());
        vincular(raiz, entradas, KeyStroke.getKeyStroke(KeyEvent.VK_F, ctrl), "buscar",
                () -> actual.seccion.enfocarBusqueda());
        vincular(raiz, entradas, KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "refrescar",
                () -> actual.seccion.refrescar());
        vincular(raiz, entradas, KeyStroke.getKeyStroke(KeyEvent.VK_Q, ctrl), "cerrarSesion",
                this::confirmarCierreSesion);
    }

    private void vincular(JRootPane raiz, InputMap entradas, KeyStroke tecla, String nombre, Runnable accion) {
        entradas.put(tecla, nombre);
        raiz.getActionMap().put(nombre, accion(accion));
    }

    private static AbstractAction accion(Runnable r) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        };
    }

    private int indiceDe(String id) {
        for (int i = 0; i < secciones.size(); i++) {
            if (secciones.get(i).id.equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /** Muestra la sección indicada por su posición (0 = Inicio) y recarga sus datos. */
    private void mostrarSeccion(int indice) {
        if (indice < 0 || indice >= secciones.size()) {
            return;
        }
        EntradaSeccion entrada = secciones.get(indice);
        for (EntradaSeccion e : secciones) {
            e.boton.setSeleccionado(e == entrada);
        }
        // Si se navega por la barra lateral con el teclado, el foco acompaña a la sección elegida con el atajo:
        // así el contorno de foco y la selección no quedan en botones distintos.
        Component foco = getFocusOwner();
        boolean focoEnNavegacion = secciones.stream().anyMatch(e -> e.boton == foco);
        if (focoEnNavegacion && foco != entrada.boton && ((SidebarButton) foco).tieneFocoTeclado()) {
            entrada.boton.enfocarConTeclado();
        }
        boolean cambia = actual != entrada;
        actual = entrada;
        lblTitulo.setText(entrada.seccion.getTitulo());
        if (cambia) {
            contenido.mostrar(entrada.id);
        }
        entrada.seccion.refrescar();
    }

    /** Colapsa o expande la barra lateral según el ancho de la ventana. */
    private void ajustarSidebar(boolean animar) {
        boolean debeColapsar = getWidth() < UMBRAL_COLAPSO;
        if (debeColapsar == colapsado) {
            return;
        }
        colapsado = debeColapsar;
        int destino = colapsado ? ANCHO_SIDEBAR_COLAPSADO : ANCHO_SIDEBAR;
        if (colapsado) {
            botonesSidebar.forEach(b -> b.setColapsado(true));
        }
        Animador.detener(animacionSidebar);
        if (!animar || !isShowing()) {
            sidebar.setAncho(destino);
            botonesSidebar.forEach(b -> b.setColapsado(colapsado));
            animacionSidebar = null;
            return;
        }
        final int inicio = sidebar.ancho;
        animacionSidebar = Animador.animar(180, p -> sidebar.setAncho((int) Math.round(Animador.interpolar(inicio, destino, p))),
                () -> {
                    botonesSidebar.forEach(b -> b.setColapsado(colapsado));
                    animacionSidebar = null;
                });
    }

    private void cambiarPassword() {
        if (CambiarPasswordDialog.paraUsuarioActual(this)) {
            Toast.mostrar(getRootPane(), "Contraseña actualizada");
        }
    }

    private void confirmarCierreSesion() {
        if (Avisos.confirmar(this, "Cerrar sesión", "¿Desea cerrar la sesión actual?")) {
            cerrarSesion(null);
        }
    }

    private void salir() {
        if (Avisos.confirmar(this, "Salir de FisioAgenda", "¿Desea cerrar la sesión y salir de la aplicación?")) {
            finalizar();
            dispose();
            System.exit(0);
        }
    }

    /** Cierra la sesión y vuelve a la pantalla de acceso, con un aviso opcional. */
    private void cerrarSesion(String aviso) {
        if (finalizada) {
            return;
        }
        finalizar();
        for (Window hija : getOwnedWindows()) {
            hija.dispose();
        }
        dispose();
        new LoginFrame(aviso).mostrar();
    }

    /** Detiene temporizadores y oyentes globales y cierra la sesión en el servicio. */
    private void finalizar() {
        finalizada = true;
        temporizadorInactividad.stop();
        Animador.detener(animacionSidebar);
        Toolkit.getDefaultToolkit().removeAWTEventListener(oyenteActividad);
        try {
            ServiceLocator.auth().logout();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "No se pudo cerrar la sesión correctamente", e);
        }
    }

    /** Sección registrada en la barra lateral. */
    private static final class EntradaSeccion {
        private final String id;
        private final Seccion seccion;
        private final SidebarButton boton;

        EntradaSeccion(String id, Seccion seccion, SidebarButton boton) {
            this.id = id;
            this.seccion = seccion;
            this.boton = boton;
        }
    }

    /** Barra lateral oscura con el logotipo, la navegación y las acciones de cuenta. */
    private final class Sidebar extends JPanel {
        private final JPanel navegacion = new JPanel();
        private final JPanel inferior = new JPanel();
        private int ancho = ANCHO_SIDEBAR;

        Sidebar() {
            super(new BorderLayout());
            setBackground(Tema.SIDEBAR);
            setBorder(BorderFactory.createEmptyBorder(18, 10, 18, 10));
            add(new Logo(), BorderLayout.NORTH);
            for (JPanel p : new JPanel[]{navegacion, inferior}) {
                p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
                p.setOpaque(false);
            }
            navegacion.setBorder(BorderFactory.createEmptyBorder(22, 0, 0, 0));
            add(navegacion, BorderLayout.CENTER);
            inferior.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(255, 255, 255, 30)),
                    BorderFactory.createEmptyBorder(12, 0, 0, 0)));
            add(inferior, BorderLayout.SOUTH);
            getAccessibleContext().setAccessibleName("Navegación principal");
        }

        void agregarNavegacion(SidebarButton boton) {
            if (navegacion.getComponentCount() > 0) {
                navegacion.add(Box.createVerticalStrut(4));
            }
            boton.setAlignmentX(LEFT_ALIGNMENT);
            navegacion.add(boton);
        }

        void agregarInferior(SidebarButton boton) {
            if (inferior.getComponentCount() > 0) {
                inferior.add(Box.createVerticalStrut(4));
            }
            boton.setAlignmentX(LEFT_ALIGNMENT);
            inferior.add(boton);
        }

        void setAncho(int nuevo) {
            this.ancho = nuevo;
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(ancho, super.getPreferredSize().height);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(ancho, 0);
        }
    }

    /** Logotipo de la barra lateral: baldosa y, si hay espacio, el nombre de la aplicación. */
    private final class Logo extends ComponenteDibujado {
        Logo() {
            getAccessibleContext().setAccessibleName("FisioAgenda");
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
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int tam = 38;
                int x = getWidth() < 120 ? (getWidth() - tam) / 2 : 6;
                int y = (getHeight() - tam) / 2;
                Iconos.pintarLogo(g2, x, y, tam, Tema.PRIMARIO, Color.WHITE, Tema.PRIMARIO);
                if (getWidth() >= 150) {
                    g2.setFont(new Font(Tema.FAMILIA, Font.BOLD, 18));
                    g2.setColor(Color.WHITE);
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString("FisioAgenda", x + tam + 12, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                }
            } finally {
                g2.dispose();
            }
        }
    }
}
