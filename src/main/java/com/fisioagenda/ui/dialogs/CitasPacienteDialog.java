package com.fisioagenda.ui.dialogs;

import com.fisioagenda.model.Cita;
import com.fisioagenda.model.EstadoCita;
import com.fisioagenda.model.Paciente;
import com.fisioagenda.service.ServiceLocator;
import com.fisioagenda.ui.Tareas;
import com.fisioagenda.ui.components.ChipRenderer;
import com.fisioagenda.ui.components.Componentes;
import com.fisioagenda.ui.components.Iconos;
import com.fisioagenda.ui.components.ModeloTabla;
import com.fisioagenda.ui.components.Tabla;
import com.fisioagenda.ui.components.Tarjeta;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Diálogo de consulta con todas las citas de un paciente (pasadas y futuras), de la más reciente
 * a la más antigua, con su estado en forma de chip de color.
 */
public final class CitasPacienteDialog extends DialogoBase {

    private final ModeloTabla<Cita> modelo = new ModeloTabla<>();
    private final Tabla<Cita> tabla;
    private final JLabel lblResumen = Componentes.secundaria(" ");

    private CitasPacienteDialog(Component padre, Paciente paciente) {
        super(padre, "Citas del paciente", paciente.getNombreCompleto() + " · Tel. " + paciente.getTelefono(),
                Iconos.Tipo.AGENDA);
        modelo.columna("Fecha y hora", LocalDateTime.class, Cita::getFechaHora)
                .columna("Tratamiento", String.class, c -> c.getTratamiento().getNombre())
                .columna("Fisioterapeuta", String.class, c -> c.getFisioterapeuta().getNombreCompleto())
                .columna("Estado", EstadoCita.class, Cita::getEstado)
                .columna("Notas", String.class, c -> c.getNotas() != null ? c.getNotas() : "");
        tabla = new Tabla<>(modelo);
        tabla.setMensajeVacio("Cargando...");
        tabla.setRenderizador(3, ChipRenderer.paraEstados());
        tabla.setAnchoColumna(0, 150);
        tabla.setAnchoColumna(1, 190);
        tabla.setAnchoColumna(2, 170);
        tabla.setAnchoColumna(3, 120);
        tabla.setAnchoColumna(4, 140);
        tabla.getColumnModel().getColumn(0).setMinWidth(144);
        tabla.getColumnModel().getColumn(3).setMinWidth(118);
        tabla.getOrdenador().setSortKeys(Collections.singletonList(new RowSorter.SortKey(0, SortOrder.DESCENDING)));

        Tarjeta tarjeta = new Tarjeta(new BorderLayout(), 8);
        tarjeta.add(Componentes.scroll(tabla), BorderLayout.CENTER);
        JPanel contenido = new JPanel(new BorderLayout(0, 10));
        contenido.setOpaque(false);
        contenido.add(tarjeta, BorderLayout.CENTER);
        contenido.add(lblResumen, BorderLayout.SOUTH);
        setContenido(contenido);
        agregarBotonCancelar("Cerrar");

        Tareas.ejecutar(this, () -> ServiceLocator.citas().citasDePaciente(paciente.getId()), this::mostrar);
    }

    /** Abre el diálogo con las citas del paciente. */
    public static void mostrar(Component padre, Paciente paciente) {
        new CitasPacienteDialog(padre, paciente).abrir(820, 520);
    }

    private void mostrar(List<Cita> citas) {
        tabla.setFilas(citas);
        tabla.setMensajeVacio("Este paciente no tiene citas registradas");
        long realizadas = citas.stream().filter(c -> c.getEstado() == EstadoCita.REALIZADA).count();
        long pendientes = citas.stream().filter(c -> c.getEstado() == EstadoCita.PROGRAMADA).count();
        long faltas = citas.stream().filter(c -> c.getEstado() == EstadoCita.NO_ASISTIDA).count();
        lblResumen.setText(plural(citas.size(), "cita", "citas") + " · " + plural(realizadas, "realizada", "realizadas")
                + " · " + plural(pendientes, "programada", "programadas") + " · " + faltas + " sin asistir");
    }

    private static String plural(long n, String singular, String plural) {
        return n + " " + (n == 1 ? singular : plural);
    }
}
