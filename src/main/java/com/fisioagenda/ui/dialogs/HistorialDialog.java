package com.fisioagenda.ui.dialogs;

import com.fisioagenda.model.NotaClinica;
import com.fisioagenda.model.Paciente;
import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Usuario;
import com.fisioagenda.service.ServiceException;
import com.fisioagenda.service.ServiceLocator;
import com.fisioagenda.ui.Avisos;
import com.fisioagenda.ui.Tema;
import com.fisioagenda.ui.components.Avatar;
import com.fisioagenda.ui.components.ComponenteDibujado;
import com.fisioagenda.ui.components.Componentes;
import com.fisioagenda.ui.components.GraficoLinea;
import com.fisioagenda.ui.components.Iconos;
import com.fisioagenda.ui.components.Tarjeta;
import com.fisioagenda.ui.components.Toast;
import com.fisioagenda.util.Formatos;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Historial clínico de un paciente: datos básicos, gráfico de evolución del dolor (EVA) y la lista
 * de notas en orden cronológico (de la más reciente a la más antigua). Los fisioterapeutas pueden
 * añadir notas desde el formulario inferior; el administrador solo puede consultarlas.
 */
public final class HistorialDialog extends DialogoBase {

    private static final int MIN_CARACTERES = 5;
    private static final int MAX_CARACTERES = 2000;
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    private final Paciente paciente;
    private final Integer citaId;
    private final boolean puedeAnadir;
    private final ListaNotas listaNotas = new ListaNotas();
    private final GraficoLinea grafico = new GraficoLinea();
    private final JLabel lblContador = Componentes.secundaria(" ");
    private final JTextArea txtNota = Componentes.areaTexto(3);
    private JButton btnCerrar;
    private final JSlider sldDolor = new JSlider(0, 10, 0);
    private final JLabel lblDolor = new JLabel();
    private final JLabel lblCaracteres = Componentes.secundaria(" ");
    private final JLabel lblErrorNota = Componentes.etiquetaError();

    private HistorialDialog(Component padre, Usuario sesion, Paciente paciente, Integer citaId) {
        super(padre, "Historial clínico", paciente.getNombreCompleto() + " · DNI " + paciente.getDni(),
                Iconos.Tipo.HISTORIAL);
        this.paciente = paciente;
        this.citaId = citaId;
        this.puedeAnadir = sesion != null && sesion.getRol() == Rol.FISIOTERAPEUTA;

        JPanel contenido = new JPanel(new BorderLayout(0, 14));
        contenido.setOpaque(false);
        contenido.add(crearResumen(), BorderLayout.NORTH);
        contenido.add(crearListado(), BorderLayout.CENTER);
        if (puedeAnadir) {
            contenido.add(crearFormulario(), BorderLayout.SOUTH);
        }
        setContenido(contenido);
        btnCerrar = agregarBotonCancelar("Cerrar");
    }

    /**
     * Abre el historial del paciente. Si el usuario no tiene permiso, el servicio lo impide y se
     * muestra el motivo.
     *
     * @param citaId cita a la que se asociarán las notas nuevas (o {@code null})
     */
    public static void mostrar(Component padre, Usuario sesion, Paciente paciente, Integer citaId) {
        List<NotaClinica> notas;
        try {
            notas = ServiceLocator.historial().notasDe(paciente.getId());
        } catch (ServiceException e) {
            Avisos.error(padre, e.getMessage());
            return;
        } catch (RuntimeException e) {
            Avisos.errorInesperado(padre, e);
            return;
        }
        HistorialDialog d = new HistorialDialog(padre, sesion, paciente, citaId);
        d.mostrarNotas(notas);
        // Foco inicial: el formulario de nueva nota o, en modo consulta, el botón Cerrar.
        SwingUtilities.invokeLater(d.puedeAnadir ? d.txtNota::requestFocusInWindow : d.btnCerrar::requestFocusInWindow);
        d.abrir(880, 720);
    }

    private JComponent crearResumen() {
        Tarjeta datos = new Tarjeta(new BorderLayout(14, 0), 16);
        JPanel columnaAvatar = new JPanel(new BorderLayout());
        columnaAvatar.setOpaque(false);
        columnaAvatar.add(new Avatar(paciente.getNombreCompleto(), 52), BorderLayout.NORTH);
        datos.add(columnaAvatar, BorderLayout.WEST);
        JPanel textos = new JPanel();
        textos.setOpaque(false);
        textos.setLayout(new BoxLayout(textos, BoxLayout.Y_AXIS));
        JLabel nombre = new JLabel(paciente.getNombreCompleto());
        nombre.setFont(Tema.SUBTITULO);
        nombre.setForeground(Tema.TEXTO);
        textos.add(nombre);
        textos.add(Box.createVerticalStrut(4));
        String edad = paciente.getFechaNacimiento() != null
                ? Period.between(paciente.getFechaNacimiento(), LocalDate.now()).getYears() + " años"
                : "Edad no registrada";
        textos.add(Componentes.secundaria(edad + "  ·  Tel. " + paciente.getTelefono()));
        if (paciente.getEmail() != null) {
            // El email va en su propia línea para que no se recorte en la tarjeta.
            textos.add(Box.createVerticalStrut(2));
            textos.add(Componentes.secundaria(paciente.getEmail()));
        }
        textos.add(Box.createVerticalStrut(8));
        String obs = paciente.getObservaciones();
        JLabel lblObs = new JLabel("<html><b>Observaciones:</b> "
                + (obs == null || obs.isBlank() ? "<i>sin observaciones</i>" : Componentes.escaparHtml(obs)) + "</html>");
        lblObs.setFont(Tema.PEQUENA);
        lblObs.setForeground(Tema.TEXTO);
        lblObs.setVerticalAlignment(SwingConstants.TOP);
        textos.add(lblObs);
        for (Component c : textos.getComponents()) {
            ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
        }
        datos.add(textos, BorderLayout.CENTER);

        Tarjeta tarjetaGrafico = new Tarjeta(new BorderLayout(0, 6), 14);
        tarjetaGrafico.add(Componentes.titulo("Evolución del dolor (EVA)"), BorderLayout.NORTH);
        grafico.setMensajeVacio("Aún no hay notas con valoración del dolor");
        grafico.setPreferredSize(new Dimension(360, 130));
        tarjetaGrafico.add(grafico, BorderLayout.CENTER);

        JPanel fila = new JPanel(new GridBagLayout());
        fila.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.weightx = 0.45;
        c.insets = new Insets(0, 0, 0, 14);
        fila.add(datos, c);
        c.gridx = 1;
        c.weightx = 0.55;
        c.insets = new Insets(0, 0, 0, 0);
        fila.add(tarjetaGrafico, c);
        datos.setPreferredSize(new Dimension(300, 175));
        tarjetaGrafico.setPreferredSize(new Dimension(380, 175));
        return fila;
    }

    private JComponent crearListado() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        JPanel cabecera = new JPanel(new BorderLayout());
        cabecera.setOpaque(false);
        cabecera.add(Componentes.titulo("Notas clínicas"), BorderLayout.WEST);
        cabecera.add(lblContador, BorderLayout.EAST);
        panel.add(cabecera, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(listaNotas);
        scroll.getViewport().setBackground(Tema.FONDO);
        scroll.setBorder(BorderFactory.createLineBorder(Tema.BORDE));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(600, 220));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JComponent crearFormulario() {
        Tarjeta tarjeta = new Tarjeta(new BorderLayout(0, 8), 14);
        tarjeta.setColorFondo(Tema.suavizar(Tema.PRIMARIO, 0.95));
        tarjeta.setColorBorde(Tema.suavizar(Tema.PRIMARIO, 0.75));
        JLabel titulo = Componentes.titulo(citaId != null ? "Nueva nota de la sesión" : "Nueva nota");
        tarjeta.add(titulo, BorderLayout.NORTH);

        // FlatLaf no pinta texto de ejemplo en un JTextArea: la sugerencia se ofrece como ayuda emergente.
        txtNota.setToolTipText("Evolución, tratamiento aplicado, ejercicios pautados…");
        txtNota.getAccessibleContext().setAccessibleName("Contenido de la nota");
        JScrollPane scrollNota = new JScrollPane(txtNota);
        JLabel lblTexto = new JLabel("Contenido de la nota");
        lblTexto.setDisplayedMnemonic('C');
        lblTexto.setLabelFor(txtNota);
        lblTexto.setFont(Tema.NORMAL_NEGRITA);
        JPanel texto = new JPanel(new BorderLayout(0, 4));
        texto.setOpaque(false);
        texto.add(lblTexto, BorderLayout.NORTH);
        texto.add(scrollNota, BorderLayout.CENTER);
        JPanel bajoTexto = new JPanel(new BorderLayout());
        bajoTexto.setOpaque(false);
        bajoTexto.add(lblErrorNota, BorderLayout.CENTER);
        bajoTexto.add(lblCaracteres, BorderLayout.EAST);
        texto.add(bajoTexto, BorderLayout.SOUTH);
        tarjeta.add(texto, BorderLayout.CENTER);

        sldDolor.setMajorTickSpacing(1);
        sldDolor.setPaintTicks(true);
        sldDolor.setPaintLabels(true);
        sldDolor.setSnapToTicks(true);
        sldDolor.setOpaque(false);
        sldDolor.setToolTipText("Escala visual analógica: 0 sin dolor, 10 dolor máximo");
        sldDolor.getAccessibleContext().setAccessibleName("Dolor según escala EVA");
        sldDolor.addChangeListener(e -> actualizarDolor());
        JLabel lblSlider = new JLabel("Dolor (EVA)");
        lblSlider.setFont(Tema.NORMAL_NEGRITA);
        lblSlider.setDisplayedMnemonic('D');
        lblSlider.setLabelFor(sldDolor);
        lblDolor.setFont(Tema.NORMAL_NEGRITA);
        lblDolor.setPreferredSize(new Dimension(130, 24));

        JButton btnAnadir = Componentes.boton("Añadir nota", Iconos.Tipo.MAS, Componentes.EstiloBoton.PRIMARIO, 'A',
                "Guardar la nota en el historial");
        btnAnadir.addActionListener(e -> anadirNota());

        JPanel fila = new JPanel(new BorderLayout(12, 0));
        fila.setOpaque(false);
        fila.add(lblSlider, BorderLayout.WEST);
        JPanel centroSlider = new JPanel(new BorderLayout(10, 0));
        centroSlider.setOpaque(false);
        centroSlider.add(sldDolor, BorderLayout.CENTER);
        centroSlider.add(lblDolor, BorderLayout.EAST);
        fila.add(centroSlider, BorderLayout.CENTER);
        JPanel boton = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        boton.setOpaque(false);
        boton.add(btnAnadir);
        fila.add(boton, BorderLayout.EAST);
        tarjeta.add(fila, BorderLayout.SOUTH);

        txtNota.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                actualizarContador();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                actualizarContador();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                actualizarContador();
            }
        });
        actualizarDolor();
        actualizarContador();
        return tarjeta;
    }

    private void actualizarDolor() {
        int v = sldDolor.getValue();
        Color color = Tema.colorDolor(v);
        lblDolor.setText(v + " · " + Tema.descripcionDolor(v));
        lblDolor.setForeground(color);
        sldDolor.putClientProperty("FlatLaf.style", "trackValueColor: " + Tema.hex(color) + "; thumbColor: " + Tema.hex(color));
    }

    private void actualizarContador() {
        int n = txtNota.getText().trim().length();
        lblCaracteres.setText(n + " / " + MAX_CARACTERES);
        lblCaracteres.setForeground(n > MAX_CARACTERES ? Tema.ERROR : Tema.TEXTO_SECUNDARIO);
        if (n >= MIN_CARACTERES) {
            Componentes.mostrarError(lblErrorNota, null);
        }
    }

    private void mostrarNotas(List<NotaClinica> notas) {
        List<NotaClinica> ordenadas = new ArrayList<>(notas);
        ordenadas.sort(Comparator.comparing(NotaClinica::getFecha).reversed());
        listaNotas.removeAll();
        if (ordenadas.isEmpty()) {
            JLabel vacio = new JLabel("Este paciente todavía no tiene notas clínicas.", SwingConstants.CENTER);
            vacio.setForeground(Tema.TEXTO_SECUNDARIO);
            vacio.setBorder(BorderFactory.createEmptyBorder(30, 0, 30, 0));
            vacio.setAlignmentX(LEFT_ALIGNMENT);
            vacio.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
            listaNotas.add(vacio);
        }
        for (NotaClinica nota : ordenadas) {
            JComponent tarjeta = crearTarjetaNota(nota);
            tarjeta.setAlignmentX(LEFT_ALIGNMENT);
            listaNotas.add(tarjeta);
            listaNotas.add(Box.createVerticalStrut(10));
        }
        listaNotas.revalidate();
        listaNotas.repaint();
        SwingUtilities.invokeLater(() -> listaNotas.scrollRectToVisible(new Rectangle(0, 0, 1, 1)));
        lblContador.setText(ordenadas.size() == 1 ? "1 nota" : ordenadas.size() + " notas");

        // El gráfico va de la nota más antigua a la más reciente.
        List<String> etiquetas = new ArrayList<>();
        List<Integer> valores = new ArrayList<>();
        for (int i = ordenadas.size() - 1; i >= 0; i--) {
            NotaClinica n = ordenadas.get(i);
            etiquetas.add(n.getFecha().format(DIA_MES));
            valores.add(n.getEscalaDolor());
        }
        grafico.setPuntos(etiquetas, valores);
    }

    private JComponent crearTarjetaNota(NotaClinica nota) {
        Tarjeta t = new Tarjeta(new BorderLayout(0, 8), 14);
        JPanel cab = new JPanel(new BorderLayout(12, 0));
        cab.setOpaque(false);
        String autor = nota.getAutor() != null ? nota.getAutor().getNombreCompleto() : "Autor desconocido";
        JLabel lblFecha = new JLabel("<html><b>" + Formatos.fecha(nota.getFecha().toLocalDate()) + "</b>  "
                + Formatos.HORA.format(nota.getFecha()) + "&nbsp;&nbsp;<font color='" + Tema.hex(Tema.TEXTO_SECUNDARIO) + "'>· "
                + Componentes.escaparHtml(autor) + (nota.getCitaId() != null ? " · sesión de cita" : "") + "</font></html>");
        lblFecha.setFont(Tema.NORMAL);
        lblFecha.setForeground(Tema.TEXTO);
        lblFecha.setIcon(Iconos.de(Iconos.Tipo.NOTA, 16, Tema.PRIMARIO));
        lblFecha.setIconTextGap(8);
        cab.add(lblFecha, BorderLayout.CENTER);
        cab.add(new BarraEva(nota.getEscalaDolor()), BorderLayout.EAST);
        t.add(cab, BorderLayout.NORTH);

        JTextArea contenido = new JTextArea(nota.getContenido());
        contenido.setEditable(false);
        contenido.setLineWrap(true);
        contenido.setWrapStyleWord(true);
        contenido.setOpaque(false);
        contenido.setFont(Tema.NORMAL);
        contenido.setForeground(Tema.TEXTO);
        contenido.setBorder(BorderFactory.createEmptyBorder());
        contenido.setFocusable(true);
        contenido.getAccessibleContext().setAccessibleName("Nota del " + Formatos.fecha(nota.getFecha().toLocalDate()));
        t.add(contenido, BorderLayout.CENTER);
        return t;
    }

    private void anadirNota() {
        String contenido = txtNota.getText().trim();
        if (contenido.length() < MIN_CARACTERES || contenido.length() > MAX_CARACTERES) {
            Componentes.mostrarError(lblErrorNota, "La nota debe tener entre " + MIN_CARACTERES + " y "
                    + MAX_CARACTERES + " caracteres.");
            txtNota.requestFocusInWindow();
            return;
        }
        int eva = sldDolor.getValue();
        NotaClinica nueva = ejecutar(() -> ServiceLocator.historial().anadirNota(paciente.getId(), citaId, contenido, eva));
        if (nueva == null) {
            return;
        }
        List<NotaClinica> notas = ejecutar(() -> ServiceLocator.historial().notasDe(paciente.getId()));
        if (notas != null) {
            mostrarNotas(notas);
        }
        txtNota.setText("");
        sldDolor.setValue(0);
        Toast.mostrar(getRootPane(), "Nota añadida al historial");
    }

    @Override
    protected void cancelar() {
        if (puedeAnadir && !txtNota.getText().isBlank()
                && !Avisos.confirmar(this, "Descartar nota", "Hay una nota sin guardar. ¿Desea cerrar y descartarla?")) {
            return;
        }
        super.cancelar();
    }

    /** Panel de notas que se ajusta al ancho del visor para que el texto se reparta en líneas. */
    private static final class ListaNotas extends JPanel implements Scrollable {
        ListaNotas() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBackground(Tema.FONDO);
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientacion, int direccion) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientacion, int direccion) {
            return Math.max(16, visible.height - 32);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /** Barra de 10 segmentos con el valor EVA coloreado según la intensidad. */
    private static final class BarraEva extends ComponenteDibujado {
        private final int valor;

        BarraEva(int valor) {
            this.valor = Math.max(0, Math.min(10, valor));
            setToolTipText("Dolor EVA " + this.valor + "/10 · " + Tema.descripcionDolor(this.valor));
            getAccessibleContext().setAccessibleName("Dolor EVA " + this.valor + " de 10");
            setFont(Tema.PEQUENA.deriveFont(Font.BOLD));
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(10 * 11 + 70, 20);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                Color color = Tema.colorDolor(valor);
                int y = (getHeight() - 8) / 2;
                for (int i = 0; i < 10; i++) {
                    g2.setColor(i < valor ? color : Tema.BORDE);
                    g2.fillRoundRect(i * 11, y, 9, 8, 4, 4);
                }
                g2.setFont(getFont());
                g2.setColor(color);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("EVA " + valor + "/10", 10 * 11 + 8, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            } finally {
                g2.dispose();
            }
        }
    }
}
