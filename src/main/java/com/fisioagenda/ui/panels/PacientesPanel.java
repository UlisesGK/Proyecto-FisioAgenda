package com.fisioagenda.ui.panels;

import com.fisioagenda.model.Paciente;
import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Usuario;
import com.fisioagenda.service.ServiceLocator;
import com.fisioagenda.ui.Avisos;
import com.fisioagenda.ui.Tareas;
import com.fisioagenda.ui.components.Componentes;
import com.fisioagenda.ui.components.Componentes.EstiloBoton;
import com.fisioagenda.ui.components.FiltroTexto;
import com.fisioagenda.ui.components.Iconos;
import com.fisioagenda.ui.components.ModeloTabla;
import com.fisioagenda.ui.components.Tabla;
import com.fisioagenda.ui.components.Tarjeta;
import com.fisioagenda.ui.components.Toast;
import com.fisioagenda.ui.dialogs.CitasPacienteDialog;
import com.fisioagenda.ui.dialogs.HistorialDialog;
import com.fisioagenda.ui.dialogs.PacienteDialog;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Gestión de pacientes: buscador con filtrado en vivo, tabla ordenable y acciones (nuevo, editar,
 * eliminar, historial y citas) disponibles también desde el menú contextual. Los botones se muestran
 * según el rol, y el servicio vuelve a comprobar los permisos.
 */
public class PacientesPanel extends JPanel implements Seccion {

    private final Usuario usuario;
    private final ModeloTabla<Paciente> modelo = new ModeloTabla<>();
    private final Tabla<Paciente> tabla;
    private final JTextField txtBuscar = Componentes.campoBusqueda("Buscar por nombre, DNI, teléfono o email…");
    private final JLabel lblContador = Componentes.secundaria(" ");
    private final JButton btnNuevo;
    private final JButton btnEditar;
    private final JButton btnEliminar;
    private final JButton btnHistorial;
    private final JButton btnCitas;
    private final boolean puedeCrear;
    private final boolean puedeEliminar;
    private final boolean puedeVerHistorial;
    private boolean cargando;

    /** Crea el panel para el usuario de la sesión. */
    public PacientesPanel(Usuario usuario) {
        super(new BorderLayout(0, 16));
        this.usuario = usuario;
        // Según la tabla de permisos, el fisioterapeuta consulta y edita pacientes, pero no los da de alta.
        this.puedeCrear = usuario.getRol() != Rol.FISIOTERAPEUTA;
        this.puedeEliminar = usuario.getRol() == Rol.ADMIN;
        this.puedeVerHistorial = usuario.getRol() == Rol.ADMIN || usuario.getRol() == Rol.FISIOTERAPEUTA;
        setOpaque(false);

        modelo.columna("DNI", String.class, Paciente::getDni)
                .columna("Nombre", String.class, Paciente::getNombre)
                .columna("Apellidos", String.class, Paciente::getApellidos)
                .columna("Teléfono", String.class, Paciente::getTelefono)
                .columna("Email", String.class, p -> p.getEmail() != null ? p.getEmail() : "")
                .columna("Alta", LocalDate.class, Paciente::getFechaAlta);
        tabla = new Tabla<>(modelo);
        tabla.setAnchoColumna(0, 110);
        tabla.setAnchoColumna(1, 130);
        tabla.setAnchoColumna(2, 190);
        tabla.setAnchoColumna(3, 110);
        tabla.setAnchoColumna(4, 220);
        tabla.setAnchoColumna(5, 100);
        tabla.setAccionPrincipal(this::editar);
        tabla.getAccessibleContext().setAccessibleName("Listado de pacientes");

        btnNuevo = Componentes.boton("Nuevo", Iconos.Tipo.MAS, EstiloBoton.PRIMARIO, 'N', "Nuevo paciente (Ctrl+N)");
        btnEditar = Componentes.boton("Editar", Iconos.Tipo.EDITAR, EstiloBoton.SECUNDARIO, 'E',
                "Editar el paciente seleccionado (doble clic o Intro)");
        btnEliminar = Componentes.boton("Eliminar", Iconos.Tipo.PAPELERA, EstiloBoton.PELIGRO, 'L',
                "Eliminar el paciente seleccionado y sus citas");
        btnHistorial = Componentes.boton("Historial", Iconos.Tipo.HISTORIAL, EstiloBoton.SECUNDARIO, 'H',
                "Ver el historial clínico del paciente");
        btnCitas = Componentes.boton("Ver citas", Iconos.Tipo.AGENDA, EstiloBoton.SECUNDARIO, 'V',
                "Ver todas las citas del paciente");
        btnNuevo.addActionListener(e -> nuevo());
        btnEditar.addActionListener(e -> editar());
        btnEliminar.addActionListener(e -> eliminar());
        btnHistorial.addActionListener(e -> verHistorial());
        btnCitas.addActionListener(e -> verCitas());
        btnNuevo.setVisible(puedeCrear);
        btnEliminar.setVisible(puedeEliminar);
        btnHistorial.setVisible(puedeVerHistorial);

        add(Componentes.barraHerramientas(txtBuscar, btnCitas, btnHistorial, btnEliminar, btnEditar, btnNuevo),
                BorderLayout.NORTH);
        Tarjeta tarjeta = new Tarjeta(new BorderLayout(), 8);
        tarjeta.add(Componentes.scroll(tabla), BorderLayout.CENTER);
        add(tarjeta, BorderLayout.CENTER);
        add(lblContador, BorderLayout.SOUTH);

        tabla.setMenuContextual(crearMenu());
        tabla.getSelectionModel().addListSelectionListener(e -> actualizarBotones());
        FiltroTexto.conectar(txtBuscar, tabla, this::actualizarContador);
        actualizarBotones();
    }

    private JPopupMenu crearMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(item("Editar", Iconos.Tipo.EDITAR, this::editar));
        menu.add(item("Ver citas", Iconos.Tipo.AGENDA, this::verCitas));
        if (puedeVerHistorial) {
            menu.add(item("Historial clínico", Iconos.Tipo.HISTORIAL, this::verHistorial));
        }
        if (puedeCrear) {
            menu.addSeparator();
            menu.add(item("Nuevo paciente", Iconos.Tipo.MAS, this::nuevo));
        }
        if (puedeEliminar) {
            menu.addSeparator();
            menu.add(item("Eliminar", Iconos.Tipo.PAPELERA, this::eliminar));
        }
        return menu;
    }

    private static JMenuItem item(String texto, Iconos.Tipo icono, Runnable accion) {
        JMenuItem item = new JMenuItem(texto, Iconos.de(icono, 16));
        item.addActionListener(e -> accion.run());
        return item;
    }

    private void actualizarBotones() {
        boolean haySeleccion = tabla.getSelectedRow() >= 0;
        btnEditar.setEnabled(haySeleccion);
        btnEliminar.setEnabled(haySeleccion);
        btnHistorial.setEnabled(haySeleccion);
        btnCitas.setEnabled(haySeleccion);
    }

    private void actualizarContador() {
        int total = modelo.getRowCount();
        int visibles = tabla.getRowCount();
        String texto = total == 1 ? "1 paciente" : total + " pacientes";
        if (visibles != total) {
            texto = visibles + " de " + texto;
        }
        lblContador.setText(texto);
        tabla.setMensajeVacio(total == 0 ? "No hay pacientes registrados" : "Ningún paciente coincide con la búsqueda");
    }

    @Override
    public String getTitulo() {
        return "Pacientes";
    }

    @Override
    public void refrescar() {
        if (cargando) {
            return;
        }
        cargando = true;
        if (modelo.getRowCount() == 0) {
            tabla.setMensajeVacio("Cargando pacientes...");
        }
        Optional<Integer> seleccionado = tabla.getSeleccionado().map(Paciente::getId);
        Tareas.ejecutar(() -> ServiceLocator.pacientes().listar(), lista -> {
            cargando = false;
            tabla.setFilas(lista);
            FiltroTexto.aplicar(tabla, txtBuscar.getText());
            seleccionado.ifPresent(this::seleccionarPorId);
            actualizarContador();
            actualizarBotones();
        }, error -> {
            cargando = false;
            tabla.setMensajeVacio("No se pudieron cargar los pacientes");
            Tareas.mostrarError(this, error);
        });
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

    @Override
    public void nuevo() {
        if (!puedeCrear) {
            return;
        }
        PacienteDialog.mostrar(this, null).ifPresent(p -> {
            Toast.mostrar(this, "Paciente guardado");
            refrescarYSeleccionar(p.getId());
        });
    }

    @Override
    public void enfocarBusqueda() {
        txtBuscar.requestFocusInWindow();
        txtBuscar.selectAll();
    }

    private void editar() {
        tabla.getSeleccionado().ifPresent(actual -> PacienteDialog.mostrar(this, actual).ifPresent(p -> {
            Toast.mostrar(this, "Paciente guardado");
            refrescarYSeleccionar(p.getId());
        }));
    }

    private void refrescarYSeleccionar(int id) {
        Tareas.ejecutar(this, () -> ServiceLocator.pacientes().listar(), lista -> {
            tabla.setFilas(lista);
            FiltroTexto.aplicar(tabla, txtBuscar.getText());
            seleccionarPorId(id);
            actualizarContador();
        });
    }

    private void eliminar() {
        Optional<Paciente> seleccionado = tabla.getSeleccionado();
        if (seleccionado.isEmpty()) {
            return;
        }
        Paciente p = seleccionado.get();
        if (!Avisos.confirmar(this, "Eliminar paciente", "<html>¿Eliminar a <b>" + Componentes.escaparHtml(p.getNombreCompleto())
                + "</b>?<br>También se borrarán sus citas y su historial clínico. Esta acción no se puede deshacer.</html>")) {
            return;
        }
        if (Tareas.enEdt(this, () -> ServiceLocator.pacientes().eliminar(p.getId()))) {
            Toast.mostrar(this, "Paciente eliminado");
            refrescar();
        }
    }

    private void verHistorial() {
        tabla.getSeleccionado().ifPresent(p -> HistorialDialog.mostrar(this, usuario, p, null));
    }

    private void verCitas() {
        tabla.getSeleccionado().ifPresent(p -> CitasPacienteDialog.mostrar(this, p));
    }
}
