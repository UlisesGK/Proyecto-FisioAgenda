package com.fisioagenda.ui.dialogs;

import com.fisioagenda.model.Paciente;
import com.fisioagenda.service.ServiceException;
import com.fisioagenda.service.ServiceLocator;
import com.fisioagenda.ui.Avisos;
import com.fisioagenda.ui.components.Componentes;
import com.fisioagenda.ui.components.FormBuilder;
import com.fisioagenda.ui.components.Iconos;
import com.fisioagenda.util.Formatos;
import com.fisioagenda.util.Validador;

import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.Component;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

/**
 * Formulario modal para dar de alta o editar un paciente. Valida cada campo al guardar y marca
 * en rojo los incorrectos con un mensaje bajo el campo. Esc cancela e Intro guarda.
 */
public final class PacienteDialog extends DialogoBase {

    private final Paciente original;
    private final FormBuilder form = new FormBuilder(2);
    private final JTextField txtDni = new JTextField();
    private final JTextField txtTelefono = new JTextField();
    private final JTextField txtNombre = new JTextField();
    private final JTextField txtApellidos = new JTextField();
    private final JTextField txtEmail = new JTextField();
    private final JTextField txtNacimiento = new JTextField();
    private final JTextArea txtObservaciones = Componentes.areaTexto(4);
    private Paciente resultado;

    private PacienteDialog(Component padre, Paciente paciente) {
        super(padre, paciente == null ? "Nuevo paciente" : "Editar paciente",
                paciente == null ? "Los campos marcados con * son obligatorios"
                        : "Modifique los datos de " + paciente.getNombreCompleto(),
                Iconos.Tipo.PACIENTES);
        this.original = paciente;

        txtDni.putClientProperty("JTextField.placeholderText", "12345678Z");
        txtTelefono.putClientProperty("JTextField.placeholderText", "612345678 o +32 470123456");
        // Al salir del campo, los espacios del número se recolocan solos.
        txtTelefono.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                if (Validador.esTelefonoValido(txtTelefono.getText())) {
                    txtTelefono.setText(Validador.normalizarTelefono(txtTelefono.getText()));
                }
            }
        });
        txtEmail.putClientProperty("JTextField.placeholderText", "nombre@ejemplo.com");
        txtNacimiento.putClientProperty("JTextField.placeholderText", "dd/mm/aaaa");
        txtNacimiento.setToolTipText("Fecha de nacimiento en formato dd/mm/aaaa (opcional)");

        form.campo("DNI / NIE *", 'D', txtDni);
        form.campo("Teléfono *", 'T', txtTelefono);
        form.campo("Nombre *", 'N', txtNombre);
        form.campo("Apellidos *", 'A', txtApellidos);
        form.campo("Email", 'E', txtEmail);
        form.campo("Fecha de nacimiento", 'F', txtNacimiento);
        form.campoCompuesto("Observaciones", 'O', txtObservaciones, new JScrollPane(txtObservaciones), true);
        setContenido(form.construir());

        agregarBotonCancelar("Cancelar");
        agregarBotonPrincipal("Guardar", Iconos.Tipo.CHECK, 'G', this::guardar);
        cargar();
    }

    /**
     * Abre el formulario y devuelve el paciente guardado, o vacío si se cancela.
     *
     * @param paciente paciente a editar o {@code null} para crear uno nuevo
     */
    public static Optional<Paciente> mostrar(Component padre, Paciente paciente) {
        PacienteDialog d = new PacienteDialog(padre, paciente);
        d.abrir(620, 0);
        return Optional.ofNullable(d.resultado);
    }

    private void cargar() {
        if (original == null) {
            return;
        }
        txtDni.setText(original.getDni());
        txtTelefono.setText(original.getTelefono());
        txtNombre.setText(original.getNombre());
        txtApellidos.setText(original.getApellidos());
        txtEmail.setText(original.getEmail());
        txtNacimiento.setText(original.getFechaNacimiento() != null ? Formatos.fecha(original.getFechaNacimiento()) : "");
        txtObservaciones.setText(original.getObservaciones());
        txtObservaciones.setCaretPosition(0);
    }

    private void guardar() {
        form.limpiarErrores();
        mostrarError(null);
        String dni = txtDni.getText().trim().toUpperCase(Locale.ROOT);
        String telefono = Validador.normalizarTelefono(txtTelefono.getText());
        String nombre = txtNombre.getText().trim();
        String apellidos = txtApellidos.getText().trim();
        String email = txtEmail.getText().trim();
        String nacimientoTexto = txtNacimiento.getText().trim();
        LocalDate nacimiento = null;

        if (!Validador.esDniNieValido(dni)) {
            form.marcarError(txtDni, "DNI/NIE no válido: 8 dígitos y letra (o X/Y/Z, 7 dígitos y letra)");
        }
        if (!Validador.esTelefonoValido(telefono)) {
            form.marcarError(txtTelefono, Validador.MENSAJE_TELEFONO);
        }
        if (!Validador.noVacio(nombre)) {
            form.marcarError(txtNombre, "El nombre es obligatorio");
        }
        if (!Validador.noVacio(apellidos)) {
            form.marcarError(txtApellidos, "Los apellidos son obligatorios");
        }
        if (!email.isEmpty() && !Validador.esEmailValido(email)) {
            form.marcarError(txtEmail, "Formato de email no válido");
        }
        if (!nacimientoTexto.isEmpty()) {
            Optional<LocalDate> fecha = Formatos.parseFecha(nacimientoTexto);
            if (fecha.isEmpty()) {
                form.marcarError(txtNacimiento, "Use el formato dd/mm/aaaa");
            } else if (fecha.get().isAfter(LocalDate.now())) {
                form.marcarError(txtNacimiento, "No puede ser una fecha futura");
            } else {
                nacimiento = fecha.get();
            }
        }
        if (form.hayErrores()) {
            mostrarError("Revise los campos marcados en rojo.");
            form.enfocarPrimerError();
            return;
        }

        Paciente p = new Paciente();
        p.setId(original != null ? original.getId() : 0);
        p.setFechaAlta(original != null ? original.getFechaAlta() : null);
        p.setDni(dni);
        p.setTelefono(telefono);
        p.setNombre(nombre);
        p.setApellidos(apellidos);
        p.setEmail(email.isEmpty() ? null : email);
        p.setFechaNacimiento(nacimiento);
        String observaciones = txtObservaciones.getText().trim();
        p.setObservaciones(observaciones.isEmpty() ? null : observaciones);
        try {
            resultado = ServiceLocator.pacientes().guardar(p);
            aceptar();
        } catch (ServiceException e) {
            mostrarError(e.getMessage());
            if (e.getMessage() != null && e.getMessage().toUpperCase(Locale.ROOT).contains("DNI")) {
                form.marcarError(txtDni, "Revise este DNI/NIE");
                form.enfocarPrimerError();
            }
        } catch (RuntimeException e) {
            Avisos.errorInesperado(this, e);
        }
    }
}
