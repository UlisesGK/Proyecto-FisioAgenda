package com.fisioagenda.ui.dialogs;

import com.fisioagenda.model.Cita;
import com.fisioagenda.model.EstadoCita;
import com.fisioagenda.model.Paciente;
import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Tratamiento;
import com.fisioagenda.model.Usuario;
import com.fisioagenda.service.ServiceException;
import com.fisioagenda.service.ServiceLocator;
import com.fisioagenda.ui.Avisos;
import com.fisioagenda.ui.Tema;
import com.fisioagenda.ui.components.Componentes;
import com.fisioagenda.ui.components.FormBuilder;
import com.fisioagenda.ui.components.Iconos;
import com.fisioagenda.util.Formatos;
import com.fisioagenda.util.Textos;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerDateModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Formulario modal para crear o editar una cita. El paciente se elige con un buscador sobre la lista,
 * y la hora se escoge entre los <b>huecos libres</b> que calcula {@code CitaService.huecosLibres},
 * recalculados al cambiar de fisioterapeuta, fecha o tratamiento. Se muestra la hora de fin prevista.
 */
public final class CitaDialog extends DialogoBase {

    private final Cita original;
    private final List<Paciente> pacientes;
    private final FormBuilder form = new FormBuilder(2);
    private final JTextField txtBuscarPaciente = new JTextField();
    private final DefaultListModel<Paciente> modeloPacientes = new DefaultListModel<>();
    private final JList<Paciente> lstPacientes = new JList<>(modeloPacientes);
    private final JComboBox<Usuario> cmbFisio = new JComboBox<>();
    private final JComboBox<Tratamiento> cmbTratamiento = new JComboBox<>();
    private final JSpinner spFecha = new JSpinner(new SpinnerDateModel(new Date(), null, null, Calendar.DAY_OF_MONTH));
    private final DefaultComboBoxModel<LocalTime> modeloHuecos = new DefaultComboBoxModel<>();
    private final JComboBox<LocalTime> cmbHueco = new JComboBox<>(modeloHuecos);
    private final JLabel lblResumen = new JLabel(" ");
    private final JTextArea txtNotas = Componentes.areaTexto(3);
    private boolean iniciando = true;
    private Cita resultado;

    private CitaDialog(Component padre, Cita cita, List<Paciente> pacientes, List<Usuario> fisios,
                       List<Tratamiento> tratamientos) {
        super(padre, cita == null ? "Nueva cita" : "Editar cita",
                "Elija paciente, profesional, tratamiento y un hueco libre", Iconos.Tipo.AGENDA);
        this.original = cita;
        this.pacientes = pacientes;

        configurarPaciente();
        fisios.forEach(cmbFisio::addItem);
        tratamientos.forEach(cmbTratamiento::addItem);
        cmbTratamiento.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Tratamiento) {
                    Tratamiento t = (Tratamiento) value;
                    setText(t.getNombre() + "  ·  " + t.getDuracionMinutos() + " min  ·  " + Formatos.moneda(t.getPrecio()));
                }
                return this;
            }
        });
        cmbHueco.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(value instanceof LocalTime ? Formatos.HORA.format((LocalTime) value) : "Sin huecos libres");
                return this;
            }
        });
        cmbHueco.setMaximumRowCount(12);
        spFecha.setEditor(new JSpinner.DateEditor(spFecha, "dd/MM/yyyy"));
        spFecha.setToolTipText("Fecha de la cita (lunes a sábado)");
        lblResumen.setFont(Tema.NORMAL);
        lblResumen.setForeground(Tema.TEXTO_SECUNDARIO);
        lblResumen.setIcon(Iconos.de(Iconos.Tipo.RELOJ, 16, Tema.PRIMARIO));
        lblResumen.setIconTextGap(8);
        lblResumen.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        txtNotas.setToolTipText("Notas internas de la cita (opcional)");

        // Buscador y lista forman un único campo: el mensaje de error aparece debajo de la lista.
        JPanel selectorPaciente = new JPanel(new BorderLayout(0, 6));
        selectorPaciente.setOpaque(false);
        selectorPaciente.add(txtBuscarPaciente, BorderLayout.NORTH);
        selectorPaciente.add(new JScrollPane(lstPacientes), BorderLayout.CENTER);
        form.campoCompuesto("Paciente *", 'P', txtBuscarPaciente, selectorPaciente, true);
        form.campo("Fisioterapeuta *", 'F', cmbFisio);
        form.campo("Tratamiento *", 'T', cmbTratamiento);
        form.campo("Fecha *", 'E', spFecha);
        form.campo("Hora (huecos libres) *", 'H', cmbHueco);
        form.fila(lblResumen);
        form.campoCompuesto("Notas", 'N', txtNotas, new JScrollPane(txtNotas), true);
        setContenido(form.construir());

        agregarBotonCancelar("Cancelar");
        agregarBotonPrincipal("Guardar cita", Iconos.Tipo.CHECK, 'G', this::guardar);

        cmbFisio.addActionListener(e -> recalcularHuecos());
        cmbTratamiento.addActionListener(e -> recalcularHuecos());
        spFecha.addChangeListener(e -> recalcularHuecos());
        cmbHueco.addActionListener(e -> actualizarResumen());
    }

    /**
     * Abre el formulario de cita.
     *
     * @param padre          componente desde el que se abre
     * @param cita           cita a editar o {@code null} para crear una nueva
     * @param dia            día propuesto para una cita nueva (o {@code null} para hoy)
     * @param fisioPreferido fisioterapeuta propuesto (o {@code null})
     * @param sesion         usuario de la sesión (si es fisioterapeuta se propone él mismo)
     * @return la cita guardada o vacío si se cancela
     */
    public static Optional<Cita> mostrar(Component padre, Cita cita, LocalDate dia, Usuario fisioPreferido,
                                         Usuario sesion) {
        List<Paciente> pacientes;
        List<Usuario> fisios;
        List<Tratamiento> tratamientos;
        try {
            pacientes = new ArrayList<>(ServiceLocator.pacientes().listar());
            fisios = new ArrayList<>(ServiceLocator.usuarios().listarFisioterapeutas());
            tratamientos = new ArrayList<>(ServiceLocator.tratamientos().listarActivos());
        } catch (ServiceException e) {
            Avisos.error(padre, e.getMessage());
            return Optional.empty();
        } catch (RuntimeException e) {
            Avisos.errorInesperado(padre, e);
            return Optional.empty();
        }
        if (cita != null) {
            // Al editar se conservan el profesional y el tratamiento aunque ya no estén activos.
            if (fisios.stream().noneMatch(f -> f.getId() == cita.getFisioterapeuta().getId())) {
                fisios.add(cita.getFisioterapeuta());
            }
            if (tratamientos.stream().noneMatch(t -> t.getId() == cita.getTratamiento().getId())) {
                tratamientos.add(cita.getTratamiento());
            }
        }
        if (pacientes.isEmpty() || fisios.isEmpty() || tratamientos.isEmpty()) {
            Avisos.info(padre, "No se puede crear la cita",
                    "Para dar una cita hace falta al menos un paciente, un fisioterapeuta activo y un tratamiento activo.");
            return Optional.empty();
        }
        CitaDialog d = new CitaDialog(padre, cita, pacientes, fisios, tratamientos);
        d.inicializar(dia, fisioPreferido, sesion);
        d.abrir(640, 0);
        return Optional.ofNullable(d.resultado);
    }

    private void configurarPaciente() {
        txtBuscarPaciente.putClientProperty("JTextField.placeholderText", "Buscar por nombre, DNI o teléfono…");
        txtBuscarPaciente.putClientProperty("JTextField.leadingIcon", Iconos.de(Iconos.Tipo.BUSCAR, 16, Tema.TEXTO_SECUNDARIO));
        txtBuscarPaciente.putClientProperty("JTextField.showClearButton", true);
        txtBuscarPaciente.setToolTipText("Escriba para filtrar; flecha abajo para elegir en la lista");
        lstPacientes.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lstPacientes.setVisibleRowCount(4);
        lstPacientes.getAccessibleContext().setAccessibleName("Pacientes");
        lstPacientes.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Paciente) {
                    Paciente p = (Paciente) value;
                    String gris = isSelected ? "" : " color='" + Tema.hex(Tema.TEXTO_SECUNDARIO) + "'";
                    setText("<html><b>" + Componentes.escaparHtml(p.getNombreCompleto()) + "</b>"
                            + "&nbsp;&nbsp;<font" + gris + ">" + Componentes.escaparHtml(p.getDni())
                            + " · " + Componentes.escaparHtml(p.getTelefono()) + "</font></html>");
                }
                setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                return this;
            }
        });
        lstPacientes.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && lstPacientes.getSelectedValue() != null) {
                form.limpiarError(txtBuscarPaciente);
            }
        });
        txtBuscarPaciente.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filtrarPacientes();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filtrarPacientes();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filtrarPacientes();
            }
        });
        // Intro en el buscador: con un paciente ya elegido guarda la cita, como en el resto del formulario.
        // Si aún no hay ninguno elegido, elige el primer resultado (sin guardar, para que se vea cuál es).
        txtBuscarPaciente.addActionListener(e -> {
            if (lstPacientes.getSelectedValue() == null && !modeloPacientes.isEmpty()) {
                lstPacientes.setSelectedIndex(0);
                lstPacientes.ensureIndexIsVisible(0);
            } else {
                pulsarBotonPrincipal();
            }
        });
        // Flecha abajo baja a la lista de resultados.
        txtBuscarPaciente.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "bajarLista");
        txtBuscarPaciente.getActionMap().put("bajarLista", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!modeloPacientes.isEmpty()) {
                    lstPacientes.requestFocusInWindow();
                    if (lstPacientes.getSelectedIndex() < 0) {
                        lstPacientes.setSelectedIndex(0);
                    }
                }
            }
        });
    }

    private void inicializar(LocalDate dia, Usuario fisioPreferido, Usuario sesion) {
        filtrarPacientes();
        if (original != null) {
            seleccionarPaciente(original.getPaciente().getId());
            seleccionarPorId(cmbFisio, original.getFisioterapeuta().getId());
            seleccionarTratamiento(original.getTratamiento().getId());
            setFecha(original.getFechaHora().toLocalDate());
            txtNotas.setText(original.getNotas());
        } else {
            LocalDate propuesto = dia != null ? dia : LocalDate.now();
            if (propuesto.getDayOfWeek() == DayOfWeek.SUNDAY) {
                propuesto = propuesto.plusDays(1);
            }
            setFecha(propuesto);
            Usuario fisio = fisioPreferido != null ? fisioPreferido
                    : (sesion != null && sesion.getRol() == Rol.FISIOTERAPEUTA ? sesion : null);
            if (fisio != null) {
                seleccionarPorId(cmbFisio, fisio.getId());
            }
        }
        iniciando = false;
        recalcularHuecos();
        if (original != null) {
            modeloHuecos.setSelectedItem(original.getFechaHora().toLocalTime());
            actualizarResumen();
        }
    }

    /** Filtra la lista con el texto del buscador conservando el paciente elegido si sigue en ella. */
    private void filtrarPacientes() {
        Paciente seleccionado = lstPacientes.getSelectedValue();
        String texto = txtBuscarPaciente.getText();
        modeloPacientes.clear();
        for (Paciente p : pacientes) {
            if (Textos.contienePalabras(p.getNombreCompleto() + " " + p.getDni() + " " + p.getTelefono(), texto)) {
                modeloPacientes.addElement(p);
            }
        }
        if (seleccionado != null && modeloPacientes.contains(seleccionado)) {
            lstPacientes.setSelectedValue(seleccionado, true);
        } else if (modeloPacientes.size() == 1) {
            lstPacientes.setSelectedIndex(0);
        }
    }

    private void seleccionarPaciente(int id) {
        for (int i = 0; i < modeloPacientes.size(); i++) {
            if (modeloPacientes.get(i).getId() == id) {
                lstPacientes.setSelectedIndex(i);
                lstPacientes.ensureIndexIsVisible(i);
                return;
            }
        }
    }

    private static void seleccionarPorId(JComboBox<Usuario> combo, int id) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).getId() == id) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private void seleccionarTratamiento(int id) {
        for (int i = 0; i < cmbTratamiento.getItemCount(); i++) {
            if (cmbTratamiento.getItemAt(i).getId() == id) {
                cmbTratamiento.setSelectedIndex(i);
                return;
            }
        }
    }

    private LocalDate getFecha() {
        Date fecha = (Date) spFecha.getValue();
        return fecha.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private void setFecha(LocalDate dia) {
        spFecha.setValue(Date.from(dia.atStartOfDay(ZoneId.systemDefault()).toInstant()));
    }

    /** Vuelve a pedir al servicio los huecos libres para el profesional, el día y la duración elegidos. */
    private void recalcularHuecos() {
        if (iniciando) {
            return;
        }
        LocalTime anterior = (LocalTime) cmbHueco.getSelectedItem();
        modeloHuecos.removeAllElements();
        LocalDate dia = getFecha();
        Usuario fisio = (Usuario) cmbFisio.getSelectedItem();
        Tratamiento tratamiento = (Tratamiento) cmbTratamiento.getSelectedItem();
        List<LocalTime> huecos = new ArrayList<>();
        if (dia.getDayOfWeek() != DayOfWeek.SUNDAY && fisio != null && tratamiento != null) {
            try {
                // Al editar se excluye la propia cita para que no bloquee su franja ni las contiguas.
                Integer excluida = original != null ? original.getId() : null;
                huecos.addAll(ServiceLocator.citas().huecosLibres(dia, fisio.getId(),
                        tratamiento.getDuracionMinutos(), excluida));
                mostrarError(null);
            } catch (ServiceException e) {
                mostrarError(e.getMessage());
            } catch (RuntimeException e) {
                Avisos.errorInesperado(this, e);
            }
            if (original != null && original.getFechaHora().toLocalDate().equals(dia)
                    && original.getFisioterapeuta().getId() == fisio.getId()
                    && original.getTratamiento().getDuracionMinutos() == tratamiento.getDuracionMinutos()) {
                // Mismo día, profesional y duración: la franja original sigue siendo válida.
                LocalTime propia = original.getFechaHora().toLocalTime();
                if (!huecos.contains(propia)) {
                    huecos.add(propia);
                    Collections.sort(huecos);
                }
            }
            if (original == null && dia.equals(LocalDate.now())) {
                LocalTime ahora = LocalTime.now();
                huecos.removeIf(h -> !h.isAfter(ahora));
            }
        }
        huecos.forEach(modeloHuecos::addElement);
        if (anterior != null && huecos.contains(anterior)) {
            modeloHuecos.setSelectedItem(anterior);
        } else if (!huecos.isEmpty()) {
            modeloHuecos.setSelectedItem(huecos.get(0));
        }
        cmbHueco.setEnabled(!huecos.isEmpty());
        if (!huecos.isEmpty()) {
            form.limpiarError(cmbHueco);
        }
        actualizarResumen();
    }

    private void actualizarResumen() {
        LocalDate dia = getFecha();
        Tratamiento t = (Tratamiento) cmbTratamiento.getSelectedItem();
        LocalTime hora = (LocalTime) cmbHueco.getSelectedItem();
        if (dia.getDayOfWeek() == DayOfWeek.SUNDAY) {
            lblResumen.setText("La clínica cierra los domingos: elija otro día.");
            lblResumen.setForeground(Tema.AVISO);
            return;
        }
        if (hora == null || t == null) {
            lblResumen.setText(dia.isBefore(LocalDate.now()) && original == null
                    ? "No se pueden dar citas en fechas pasadas."
                    : "No quedan huecos libres para ese profesional y duración en el día elegido.");
            lblResumen.setForeground(Tema.AVISO);
            return;
        }
        LocalTime fin = hora.plusMinutes(t.getDuracionMinutos());
        int libres = modeloHuecos.getSize();
        lblResumen.setText("<html>De <b>" + Formatos.HORA.format(hora) + "</b> a <b>" + Formatos.HORA.format(fin)
                + "</b> (" + t.getDuracionMinutos() + " min) · " + Formatos.moneda(t.getPrecio())
                + " · " + libres + (libres == 1 ? " hueco libre" : " huecos libres") + " ese día</html>");
        lblResumen.setForeground(Tema.TEXTO);
    }

    private void guardar() {
        form.limpiarErrores();
        Paciente paciente = lstPacientes.getSelectedValue();
        Usuario fisio = (Usuario) cmbFisio.getSelectedItem();
        Tratamiento tratamiento = (Tratamiento) cmbTratamiento.getSelectedItem();
        LocalTime hora = (LocalTime) cmbHueco.getSelectedItem();
        if (paciente == null) {
            form.marcarError(txtBuscarPaciente, "Seleccione un paciente de la lista");
        }
        if (fisio == null) {
            form.marcarError(cmbFisio, "Elija un fisioterapeuta");
        }
        if (tratamiento == null) {
            form.marcarError(cmbTratamiento, "Elija un tratamiento");
        }
        if (hora == null) {
            form.marcarError(cmbHueco, "No hay hora disponible");
        }
        if (form.hayErrores()) {
            mostrarError("Revise los campos marcados en rojo.");
            form.enfocarPrimerError();
            return;
        }
        Cita c = new Cita();
        c.setId(original != null ? original.getId() : 0);
        c.setPaciente(paciente);
        c.setFisioterapeuta(fisio);
        c.setTratamiento(tratamiento);
        c.setFechaHora(LocalDateTime.of(getFecha(), hora));
        c.setEstado(original != null ? original.getEstado() : EstadoCita.PROGRAMADA);
        String notas = txtNotas.getText().trim();
        c.setNotas(notas.isEmpty() ? null : notas);
        Cita guardada = ejecutar(() -> ServiceLocator.citas().guardar(c));
        if (guardada != null) {
            resultado = guardada;
            aceptar();
        }
    }
}
