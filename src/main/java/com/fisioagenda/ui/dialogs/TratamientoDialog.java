package com.fisioagenda.ui.dialogs;

import com.fisioagenda.model.Tratamiento;
import com.fisioagenda.service.ServiceLocator;
import com.fisioagenda.ui.components.Componentes;
import com.fisioagenda.ui.components.FormBuilder;
import com.fisioagenda.ui.components.Iconos;
import com.fisioagenda.util.Validador;

import javax.swing.JCheckBox;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.Component;
import java.text.ParseException;
import java.util.Optional;

/**
 * Formulario modal para crear o editar un tratamiento del catálogo (solo administración).
 * La duración y el precio se introducen con selectores numéricos acotados a los valores válidos.
 */
public final class TratamientoDialog extends DialogoBase {

    private final Tratamiento original;
    private final FormBuilder form = new FormBuilder(2);
    private final JTextField txtNombre = new JTextField();
    private final JSpinner spDuracion = Componentes.selectorNumerico(new SpinnerNumberModel(45, 10, 240, 5), "0");
    private final JSpinner spPrecio = Componentes.selectorNumerico(new SpinnerNumberModel(35.0, 0.0, 9999.0, 0.5),
            "0.00");
    private final JTextArea txtDescripcion = Componentes.areaTexto(3);
    private final JCheckBox chkActivo = new JCheckBox("Tratamiento activo (disponible al crear citas)", true);
    private Tratamiento resultado;

    private TratamientoDialog(Component padre, Tratamiento tratamiento) {
        super(padre, tratamiento == null ? "Nuevo tratamiento" : "Editar tratamiento",
                "Defina la duración y el precio de la sesión", Iconos.Tipo.TRATAMIENTOS);
        this.original = tratamiento;

        txtNombre.putClientProperty("JTextField.placeholderText", "p. ej. Fisioterapia general");
        spDuracion.setToolTipText("Entre 10 y 240 minutos");
        spPrecio.setToolTipText("Precio por sesión en euros (admite coma o punto decimal)");
        chkActivo.setMnemonic('v');
        chkActivo.setOpaque(false);

        form.campoAncho("Nombre *", 'N', txtNombre);
        form.campo("Duración (minutos) *", 'D', spDuracion);
        form.campo("Precio (€) *", 'P', spPrecio);
        form.campoCompuesto("Descripción", 'E', txtDescripcion, new JScrollPane(txtDescripcion), true);
        form.fila(chkActivo);
        setContenido(form.construir());

        agregarBotonCancelar("Cancelar");
        agregarBotonPrincipal("Guardar", Iconos.Tipo.CHECK, 'G', this::guardar);
        if (original != null) {
            txtNombre.setText(original.getNombre());
            spDuracion.setValue(Math.max(10, Math.min(240, original.getDuracionMinutos())));
            spPrecio.setValue(Math.max(0.0, original.getPrecio()));
            txtDescripcion.setText(original.getDescripcion());
            chkActivo.setSelected(original.isActivo());
        }
    }

    /**
     * Abre el formulario y devuelve el tratamiento guardado, o vacío si se cancela.
     *
     * @param tratamiento tratamiento a editar o {@code null} para crear uno nuevo
     */
    public static Optional<Tratamiento> mostrar(Component padre, Tratamiento tratamiento) {
        TratamientoDialog d = new TratamientoDialog(padre, tratamiento);
        d.abrir(560, 0);
        return Optional.ofNullable(d.resultado);
    }

    private void guardar() {
        form.limpiarErrores();
        String nombre = txtNombre.getText().trim();
        if (!Validador.noVacio(nombre)) {
            form.marcarError(txtNombre, "El nombre es obligatorio");
        }
        if (!confirmarValor(spDuracion)) {
            form.marcarError(spDuracion, "Introduzca un número entre 10 y 240");
        }
        if (!confirmarValor(spPrecio)) {
            form.marcarError(spPrecio, "Introduzca un importe válido (0 o más, hasta 2 decimales)");
        }
        if (form.hayErrores()) {
            mostrarError("Revise los campos marcados en rojo.");
            form.enfocarPrimerError();
            return;
        }
        Tratamiento t = new Tratamiento();
        t.setId(original != null ? original.getId() : 0);
        t.setNombre(nombre);
        t.setDuracionMinutos(((Number) spDuracion.getValue()).intValue());
        t.setPrecio(((Number) spPrecio.getValue()).doubleValue());
        String descripcion = txtDescripcion.getText().trim();
        t.setDescripcion(descripcion.isEmpty() ? null : descripcion);
        t.setActivo(chkActivo.isSelected());
        Tratamiento guardado = ejecutar(() -> ServiceLocator.tratamientos().guardar(t));
        if (guardado != null) {
            resultado = guardado;
            aceptar();
        }
    }

    /** Confirma el texto escrito en un selector numérico; devuelve {@code false} si no es válido. */
    private static boolean confirmarValor(JSpinner selector) {
        try {
            selector.commitEdit();
            return true;
        } catch (ParseException e) {
            return false;
        }
    }
}
