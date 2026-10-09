package com.fisioagenda.ui.panels;

import com.fisioagenda.model.Cita;
import com.fisioagenda.model.EstadoCita;
import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Usuario;
import com.fisioagenda.service.ServiceLocator;
import com.fisioagenda.ui.Avisos;
import com.fisioagenda.ui.Tareas;
import com.fisioagenda.ui.Tema;
import com.fisioagenda.ui.components.ChipRenderer;
import com.fisioagenda.ui.components.Componentes;
import com.fisioagenda.ui.components.Componentes.EstiloBoton;
import com.fisioagenda.ui.components.Iconos;
import com.fisioagenda.ui.components.ModeloTabla;
import com.fisioagenda.ui.components.Tabla;
import com.fisioagenda.ui.components.Tarjeta;
import com.fisioagenda.ui.components.Toast;
import com.fisioagenda.ui.dialogs.CitaDialog;
import com.fisioagenda.ui.dialogs.HistorialDialog;
import com.fisioagenda.util.Formatos;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.KeyStroke;
import javax.swing.SpinnerDateModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Agenda diaria: navegación por días, filtro por fisioterapeuta (un fisioterapeuta ve por defecto
 * la suya), tabla de citas con el estado en chips de color y acciones para crear, editar, marcar
 * como realizada / no asistida, cancelar y añadir notas clínicas.
 */
public class AgendaPanel extends JPanel implements Seccion {

    private static final DateTimeFormatter FORMATO_DIA =
            DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.forLanguageTag("es-ES"));
    /** Elemento del combo que representa "todos los fisioterapeutas". */
    private static final Usuario TODOS = new Usuario(0, "", "Todos los fisioterapeutas", null);

    private final Usuario usuario;
    private final boolean esFisio;
    private final boolean puedeMarcar;
    private final JSpinner spFecha = new JSpinner(new SpinnerDateModel(new Date(), null, null, Calendar.DAY_OF_MONTH));
    private final JLabel lblDia = new JLabel();
    private final JComboBox<Usuario> cmbFisio = new JComboBox<>();
    private final ModeloTabla<Cita> modelo = new ModeloTabla<>();
    private final Tabla<Cita> tabla;
    private final JLabel lblResumen = Componentes.secundaria(" ");
    private final JButton btnNueva;
    private final JButton btnEditar;
    private final JButton btnRealizada;
    private final JButton btnNoAsistio;
    private final JButton btnCancelar;
    private final JButton btnNota;
    private boolean ajustando;
    private int secuencia;

    /** Crea la agenda para el usuario de la sesión. */
    public AgendaPanel(Usuario usuario) {
        super(new BorderLayout(0, 16));
        this.usuario = usuario;
        this.esFisio = usuario.getRol() == Rol.FISIOTERAPEUTA;
        this.puedeMarcar = usuario.getRol() == Rol.ADMIN || esFisio;
        setOpaque(false);

        modelo.columna("Hora", String.class, c -> Formatos.HORA.format(c.getFechaHora())
                        + " – " + Formatos.HORA.format(c.getFechaHoraFin()))
                .columna("Paciente", String.class, c -> c.getPaciente().getNombreCompleto())
                .columna("Tratamiento", String.class, c -> c.getTratamiento().getNombre())
                .columna("Fisioterapeuta", String.class, c -> c.getFisioterapeuta().getNombreCompleto())
                .columna("Estado", EstadoCita.class, Cita::getEstado)
                .columna("Notas", String.class, c -> c.getNotas() != null ? c.getNotas() : "");
        tabla = new Tabla<>(modelo);
        tabla.setAnchoColumna(0, 120);
        tabla.setAnchoColumna(1, 190);
        tabla.setAnchoColumna(2, 180);
        tabla.setAnchoColumna(3, 170);
        tabla.setAnchoColumna(4, 130);
        tabla.setAnchoColumna(5, 200);
        tabla.setRenderizador(4, ChipRenderer.paraEstados());
        tabla.getColumnModel().getColumn(0).setMinWidth(104);
        tabla.getColumnModel().getColumn(4).setMinWidth(118);
        tabla.setAccionPrincipal(this::editar);
        tabla.getAccessibleContext().setAccessibleName("Citas del día");

        btnNueva = Componentes.boton("Nueva cita", Iconos.Tipo.MAS, EstiloBoton.PRIMARIO, 'N', "Nueva cita (Ctrl+N)");
        btnEditar = Componentes.boton("Editar", Iconos.Tipo.EDITAR, EstiloBoton.SECUNDARIO, 'E',
                "Editar la cita seleccionada (doble clic o Intro)");
        btnRealizada = Componentes.boton("Marcar realizada", Iconos.Tipo.CHECK, EstiloBoton.SECUNDARIO, 'R',
                "Marcar la cita como realizada");
        btnNoAsistio = Componentes.boton("No asistió", Iconos.Tipo.AUSENTE, EstiloBoton.SECUNDARIO, 'A',
                "Marcar que el paciente no asistió");
        btnCancelar = Componentes.boton("Cancelar cita", Iconos.Tipo.PROHIBIDO, EstiloBoton.PELIGRO, 'C',
                "Cancelar la cita seleccionada");
        btnNota = Componentes.boton("Añadir nota", Iconos.Tipo.NOTA, EstiloBoton.SECUNDARIO, 'D',
                "Añadir una nota clínica al historial del paciente");
        btnNueva.addActionListener(e -> nuevo());
        btnEditar.addActionListener(e -> editar());
        btnRealizada.addActionListener(e -> cambiarEstado(EstadoCita.REALIZADA));
        btnNoAsistio.addActionListener(e -> cambiarEstado(EstadoCita.NO_ASISTIDA));
        btnCancelar.addActionListener(e -> cambiarEstado(EstadoCita.CANCELADA));
        btnNota.addActionListener(e -> anadirNota());
        btnRealizada.setVisible(puedeMarcar);
        btnNoAsistio.setVisible(puedeMarcar);
        btnNota.setVisible(esFisio);

        add(crearBarra(), BorderLayout.NORTH);
        // Tarjeta de la agenda: día mostrado arriba, tabla y, en el pie, el recuento por estados.
        JPanel cabecera = new JPanel(new BorderLayout());
        cabecera.setOpaque(false);
        cabecera.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        cabecera.add(lblDia, BorderLayout.WEST);
        lblResumen.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Tema.BORDE),
                BorderFactory.createEmptyBorder(10, 8, 2, 8)));
        Tarjeta tarjeta = new Tarjeta(new BorderLayout(0, 4), 8);
        tarjeta.add(cabecera, BorderLayout.NORTH);
        tarjeta.add(Componentes.scroll(tabla), BorderLayout.CENTER);
        tarjeta.add(lblResumen, BorderLayout.SOUTH);
        add(tarjeta, BorderLayout.CENTER);

        tabla.setMenuContextual(crearMenu());
        tabla.getSelectionModel().addListSelectionListener(e -> actualizarBotones());
        cmbFisio.addItem(TODOS);
        if (esFisio) {
            cmbFisio.addItem(usuario);
            cmbFisio.setSelectedItem(usuario);
        }
        cmbFisio.addActionListener(e -> {
            if (!ajustando) {
                cargar();
            }
        });
        spFecha.addChangeListener(e -> {
            actualizarEtiquetaDia();
            if (!ajustando) {
                cargar();
            }
        });
        registrarAtajos();
        actualizarEtiquetaDia();
        actualizarBotones();
    }

    private JComponent crearBarra() {
        JButton btnAnterior = Componentes.botonIcono(Iconos.Tipo.IZQUIERDA, "Día anterior (Alt+Flecha izquierda)");
        JButton btnSiguiente = Componentes.botonIcono(Iconos.Tipo.DERECHA, "Día siguiente (Alt+Flecha derecha)");
        JButton btnHoy = Componentes.boton("Hoy", Iconos.Tipo.HOY, EstiloBoton.SECUNDARIO, 'H', "Ir a hoy (Alt+H)");
        btnAnterior.addActionListener(e -> moverDias(-1));
        btnSiguiente.addActionListener(e -> moverDias(1));
        btnHoy.addActionListener(e -> setFecha(LocalDate.now()));

        spFecha.setEditor(new JSpinner.DateEditor(spFecha, "dd/MM/yyyy"));
        spFecha.setPreferredSize(new Dimension(130, 34));
        spFecha.setToolTipText("Fecha de la agenda");
        spFecha.getAccessibleContext().setAccessibleName("Fecha de la agenda");
        lblDia.setFont(Tema.SUBTITULO);
        lblDia.setForeground(Tema.TEXTO);

        JPanel navegacion = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        navegacion.setOpaque(false);
        navegacion.add(btnAnterior);
        navegacion.add(btnHoy);
        navegacion.add(btnSiguiente);
        navegacion.add(spFecha);

        JLabel lblFisio = new JLabel("Fisioterapeuta:");
        lblFisio.setFont(Tema.NORMAL_NEGRITA);
        lblFisio.setDisplayedMnemonic('F');
        lblFisio.setLabelFor(cmbFisio);
        cmbFisio.setPreferredSize(new Dimension(230, 34));
        cmbFisio.setToolTipText("Filtrar la agenda por profesional");
        JPanel filtro = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filtro.setOpaque(false);
        filtro.add(lblFisio);
        filtro.add(cmbFisio);
        filtro.add(btnNueva);

        JPanel fila1 = new JPanel(new BorderLayout(12, 0));
        fila1.setOpaque(false);
        fila1.add(navegacion, BorderLayout.CENTER);
        fila1.add(filtro, BorderLayout.EAST);

        JPanel fila2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        fila2.setOpaque(false);
        fila2.add(btnEditar);
        fila2.add(btnRealizada);
        fila2.add(btnNoAsistio);
        fila2.add(btnCancelar);
        fila2.add(btnNota);

        Tarjeta barra = new Tarjeta(new BorderLayout(0, 12), 12);
        barra.add(fila1, BorderLayout.NORTH);
        JSeparator separador = new JSeparator();
        separador.setForeground(Tema.BORDE);
        barra.add(separador, BorderLayout.CENTER);
        barra.add(fila2, BorderLayout.SOUTH);
        return barra;
    }

    private JPopupMenu crearMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(item("Editar", Iconos.Tipo.EDITAR, this::editar));
        if (puedeMarcar) {
            menu.add(item("Marcar realizada", Iconos.Tipo.CHECK, () -> cambiarEstado(EstadoCita.REALIZADA)));
            menu.add(item("No asistió", Iconos.Tipo.AUSENTE, () -> cambiarEstado(EstadoCita.NO_ASISTIDA)));
        }
        menu.add(item("Cancelar cita", Iconos.Tipo.PROHIBIDO, () -> cambiarEstado(EstadoCita.CANCELADA)));
        if (esFisio) {
            menu.addSeparator();
            menu.add(item("Añadir nota", Iconos.Tipo.NOTA, this::anadirNota));
        }
        menu.addSeparator();
        menu.add(item("Nueva cita", Iconos.Tipo.MAS, this::nuevo));
        return menu;
    }

    private static JMenuItem item(String texto, Iconos.Tipo icono, Runnable accion) {
        JMenuItem item = new JMenuItem(texto, Iconos.de(icono, 16));
        item.addActionListener(e -> accion.run());
        return item;
    }

    private void registrarAtajos() {
        vincular(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK), "diaAnterior", () -> moverDias(-1));
        vincular(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK), "diaSiguiente", () -> moverDias(1));
    }

    private void vincular(KeyStroke tecla, String nombre, Runnable accion) {
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(tecla, nombre);
        getActionMap().put(nombre, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                accion.run();
            }
        });
    }

    private LocalDate getFecha() {
        Date fecha = (Date) spFecha.getValue();
        return fecha.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private void setFecha(LocalDate dia) {
        spFecha.setValue(Date.from(dia.atStartOfDay(ZoneId.systemDefault()).toInstant()));
    }

    /** Avanza o retrocede días saltando los domingos (la clínica cierra). */
    private void moverDias(int delta) {
        LocalDate dia = getFecha().plusDays(delta);
        if (dia.getDayOfWeek() == DayOfWeek.SUNDAY) {
            dia = dia.plusDays(delta);
        }
        setFecha(dia);
    }

    private void actualizarEtiquetaDia() {
        LocalDate dia = getFecha();
        String texto = dia.format(FORMATO_DIA);
        texto = Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
        if (dia.equals(LocalDate.now())) {
            texto += " · Hoy";
        }
        lblDia.setText(texto);
    }

    private Integer filtroFisio() {
        Usuario seleccionado = (Usuario) cmbFisio.getSelectedItem();
        return seleccionado == null || seleccionado == TODOS ? null : seleccionado.getId();
    }

    @Override
    public String getTitulo() {
        return "Agenda";
    }

    /** Recarga la lista de fisioterapeutas del filtro (pueden haberse creado o desactivado) y las citas. */
    @Override
    public void refrescar() {
        cargar(true);
    }

    /** Recarga solo las citas (al cambiar de día o de profesional). */
    private void cargar() {
        cargar(false);
    }

    /**
     * Carga en segundo plano las citas del día y, si se pide, la lista de fisioterapeutas activos. Si el
     * profesional elegido en el filtro ya no está activo, el filtro pasa a "Todos".
     */
    private void cargar(boolean conFisios) {
        final int miSecuencia = ++secuencia;
        final LocalDate dia = getFecha();
        final Integer fisioElegido = filtroFisio();
        Optional<Integer> seleccionada = tabla.getSeleccionado().map(Cita::getId);
        tabla.setMensajeVacio("Cargando citas...");
        Tareas.ejecutar(() -> {
            Carga c = new Carga();
            c.fisioId = fisioElegido;
            if (conFisios) {
                c.fisios = ServiceLocator.usuarios().listarFisioterapeutas();
                if (fisioElegido != null && c.fisios.stream().noneMatch(f -> f.getId() == fisioElegido)) {
                    c.fisioId = null;
                }
            }
            c.citas = dia.getDayOfWeek() == DayOfWeek.SUNDAY ? new ArrayList<>()
                    : ServiceLocator.citas().citasDelDia(dia, c.fisioId);
            return c;
        }, c -> {
            if (miSecuencia != secuencia) {
                return; // Llegó una carga más reciente: se descarta esta.
            }
            if (c.fisios != null) {
                rellenarFisios(c.fisios, c.fisioId);
            }
            mostrarCitas(c.citas, dia);
            seleccionada.ifPresent(this::seleccionarPorId);
        }, error -> {
            if (miSecuencia == secuencia) {
                tabla.setFilas(new ArrayList<>());
                tabla.setMensajeVacio("No se pudo cargar la agenda");
                Tareas.mostrarError(this, error);
            }
        });
    }

    /** Sustituye las opciones del filtro y deja elegido el profesional indicado ({@code null} = "Todos"). */
    private void rellenarFisios(List<Usuario> fisios, Integer elegido) {
        ajustando = true;
        try {
            cmbFisio.removeAllItems();
            cmbFisio.addItem(TODOS);
            cmbFisio.setSelectedItem(TODOS);
            for (Usuario f : fisios) {
                cmbFisio.addItem(f);
                if (elegido != null && f.getId() == elegido) {
                    cmbFisio.setSelectedItem(f);
                }
            }
        } finally {
            ajustando = false;
        }
    }

    private void mostrarCitas(List<Cita> citas, LocalDate dia) {
        tabla.setFilas(citas);
        if (dia.getDayOfWeek() == DayOfWeek.SUNDAY) {
            tabla.setMensajeVacio("Domingo: la clínica permanece cerrada");
        } else {
            tabla.setMensajeVacio(filtroFisio() != null ? "Este profesional no tiene citas este día"
                    : "No hay citas para este día");
        }
        long realizadas = citas.stream().filter(c -> c.getEstado() == EstadoCita.REALIZADA).count();
        long pendientes = citas.stream().filter(c -> c.getEstado() == EstadoCita.PROGRAMADA).count();
        long canceladas = citas.stream().filter(c -> c.getEstado() == EstadoCita.CANCELADA).count();
        long ausencias = citas.stream().filter(c -> c.getEstado() == EstadoCita.NO_ASISTIDA).count();
        String resumen = citas.size() + (citas.size() == 1 ? " cita" : " citas") + " · " + realizadas
                + (realizadas == 1 ? " realizada" : " realizadas") + " · " + pendientes
                + (pendientes == 1 ? " pendiente" : " pendientes");
        if (canceladas > 0) {
            resumen += " · " + canceladas + (canceladas == 1 ? " cancelada" : " canceladas");
        }
        if (ausencias > 0) {
            resumen += " · " + ausencias + " sin asistir";
        }
        lblResumen.setText(resumen);
        actualizarBotones();
    }

    private void seleccionarPorId(int id) {
        for (int i = 0; i < tabla.getRowCount(); i++) {
            if (modelo.getFila(tabla.convertRowIndexToModel(i)).getId() == id) {
                tabla.setRowSelectionInterval(i, i);
                tabla.scrollRectToVisible(tabla.getCellRect(i, 0, true));
                return;
            }
        }
    }

    private void actualizarBotones() {
        Optional<Cita> sel = tabla.getSeleccionado();
        boolean programada = sel.map(c -> c.getEstado() == EstadoCita.PROGRAMADA).orElse(false);
        // La asistencia se registra a partir de la hora de inicio de la cita (misma regla que el servicio).
        boolean marcable = programada && ServiceLocator.citas().puedeMarcarseAsistencia(sel.get());
        btnEditar.setEnabled(programada);
        btnRealizada.setEnabled(marcable);
        btnNoAsistio.setEnabled(marcable);
        btnCancelar.setEnabled(programada);
        btnNota.setEnabled(sel.map(c -> c.getEstado() != EstadoCita.CANCELADA).orElse(false));
    }

    @Override
    public void nuevo() {
        Usuario preferido = (Usuario) cmbFisio.getSelectedItem();
        CitaDialog.mostrar(this, null, getFecha(), preferido == TODOS ? null : preferido, usuario)
                .ifPresent(this::trasGuardar);
    }

    @Override
    public void enfocarBusqueda() {
        // La agenda no tiene buscador de texto: Ctrl+F lleva al selector de fecha.
        ((JSpinner.DefaultEditor) spFecha.getEditor()).getTextField().requestFocusInWindow();
    }

    private void editar() {
        Optional<Cita> sel = tabla.getSeleccionado();
        if (sel.isEmpty()) {
            return;
        }
        if (sel.get().getEstado() != EstadoCita.PROGRAMADA) {
            Toast.mostrar(this, "Solo se pueden editar las citas programadas", Toast.Tipo.INFO);
            return;
        }
        CitaDialog.mostrar(this, sel.get(), null, null, usuario).ifPresent(this::trasGuardar);
    }

    private void trasGuardar(Cita guardada) {
        Toast.mostrar(this, "Cita guardada");
        LocalDate dia = guardada.getFechaHora().toLocalDate();
        if (!dia.equals(getFecha())) {
            ajustando = true;
            setFecha(dia);
            ajustando = false;
        }
        final int id = guardada.getId();
        final int miSecuencia = ++secuencia;
        final Integer fisioId = filtroFisio();
        Tareas.ejecutar(this, () -> ServiceLocator.citas().citasDelDia(dia, fisioId), citas -> {
            if (miSecuencia == secuencia) {
                mostrarCitas(citas, dia);
                seleccionarPorId(id);
            }
        });
    }

    private void cambiarEstado(EstadoCita nuevo) {
        Optional<Cita> sel = tabla.getSeleccionado();
        if (sel.isEmpty() || sel.get().getEstado() != EstadoCita.PROGRAMADA) {
            return;
        }
        Cita cita = sel.get();
        String descripcion = cita.getPaciente().getNombreCompleto() + " a las " + Formatos.HORA.format(cita.getFechaHora());
        if (nuevo == EstadoCita.CANCELADA && !Avisos.confirmar(this, "Cancelar cita",
                "¿Seguro que desea cancelar la cita de " + descripcion + "?")) {
            return;
        }
        if (Tareas.enEdt(this, () -> ServiceLocator.citas().cambiarEstado(cita.getId(), nuevo))) {
            String mensaje;
            switch (nuevo) {
                case REALIZADA:
                    mensaje = "Cita marcada como realizada";
                    break;
                case NO_ASISTIDA:
                    mensaje = "Cita marcada como no asistida";
                    break;
                default:
                    mensaje = "Cita cancelada";
                    break;
            }
            Toast.mostrar(this, mensaje);
            cargar();
        }
    }

    private void anadirNota() {
        tabla.getSeleccionado().ifPresent(c -> HistorialDialog.mostrar(this, usuario, c.getPaciente(), c.getId()));
    }

    /** Resultado de una carga en segundo plano. */
    private static final class Carga {
        private List<Usuario> fisios;
        private Integer fisioId;
        private List<Cita> citas;
    }
}
