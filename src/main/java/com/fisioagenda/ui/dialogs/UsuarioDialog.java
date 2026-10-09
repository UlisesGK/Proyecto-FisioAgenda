package com.fisioagenda.ui.dialogs;

import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Usuario;
import com.fisioagenda.service.ServiceLocator;
import com.fisioagenda.ui.components.Componentes;
import com.fisioagenda.ui.components.FormBuilder;
import com.fisioagenda.ui.components.Iconos;
import com.fisioagenda.ui.components.IndicadorFortaleza;
import com.fisioagenda.util.Validador;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Component;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Formulario modal de usuarios (solo administración). Al crear pide nombre de usuario y contraseña
 * (con indicador de fortaleza); al editar permite cambiar el nombre, el rol y si la cuenta está activa.
 */
public final class UsuarioDialog extends DialogoBase {

    private final Usuario original;
    private final Usuario sesion;
    private final FormBuilder form = new FormBuilder(2);
    private final JTextField txtUsername = new JTextField();
    private final JTextField txtNombre = new JTextField();
    private final JComboBox<Rol> cmbRol = new JComboBox<>(Rol.values());
    private final JCheckBox chkActivo = new JCheckBox("Cuenta activa (puede iniciar sesión)", true);
    private final JPasswordField txtPassword = new JPasswordField();
    private final JPasswordField txtRepetir = new JPasswordField();
    private final IndicadorFortaleza indicador = new IndicadorFortaleza();
    private Usuario resultado;

    private UsuarioDialog(Component padre, Usuario usuario, Usuario sesion) {
        super(padre, usuario == null ? "Nuevo usuario" : "Editar usuario",
                usuario == null ? "Cree el acceso de un profesional de la clínica"
                        : "Usuario: " + usuario.getUsername(),
                Iconos.Tipo.USUARIO);
        this.original = usuario;
        this.sesion = sesion;
        boolean nuevo = usuario == null;

        txtUsername.putClientProperty("JTextField.placeholderText", "minúsculas, números, . o _");
        txtUsername.setToolTipText("Entre 3 y 20 caracteres: letras minúsculas, números, punto o guion bajo");
        txtNombre.putClientProperty("JTextField.placeholderText", "Nombre y apellidos");
        chkActivo.setOpaque(false);
        chkActivo.setMnemonic('v');

        form.campo("Usuario *", 'U', txtUsername);
        form.campo("Rol *", 'R', cmbRol);
        form.campoAncho("Nombre completo *", 'N', txtNombre);
        if (nuevo) {
            for (JPasswordField campo : new JPasswordField[]{txtPassword, txtRepetir}) {
                campo.putClientProperty("FlatLaf.style", "showRevealButton: true");
            }
            form.campo("Contraseña *", 'C', txtPassword);
            form.campo("Repetir contraseña *", 'E', txtRepetir);
            form.fila(indicador);
            JLabel ayuda = Componentes.secundaria(
                    "Mínimo 8 caracteres con mayúscula, minúscula, número y símbolo.");
            form.fila(ayuda);
            txtPassword.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    evaluarFortaleza();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    evaluarFortaleza();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    evaluarFortaleza();
                }
            });
        } else {
            form.fila(chkActivo);
        }
        setContenido(form.construir());
        agregarBotonCancelar("Cancelar");
        agregarBotonPrincipal(nuevo ? "Crear usuario" : "Guardar", Iconos.Tipo.CHECK, 'G', this::guardar);

        if (!nuevo) {
            txtUsername.setText(usuario.getUsername());
            txtUsername.setEditable(false);
            txtUsername.setToolTipText("El nombre de usuario no se puede cambiar");
            txtNombre.setText(usuario.getNombreCompleto());
            cmbRol.setSelectedItem(usuario.getRol());
            chkActivo.setSelected(usuario.isActivo());
            if (sesion != null && sesion.getId() == usuario.getId()) {
                chkActivo.setEnabled(false);
                chkActivo.setToolTipText("No puede desactivar su propia cuenta");
                cmbRol.setEnabled(false);
                cmbRol.setToolTipText("No puede cambiar su propio rol");
            }
        } else {
            cmbRol.setSelectedItem(Rol.FISIOTERAPEUTA);
        }
    }

    /**
     * Abre el formulario y devuelve el usuario creado o actualizado, o vacío si se cancela.
     *
     * @param usuario usuario a editar o {@code null} para crear uno nuevo
     * @param sesion  usuario que tiene la sesión iniciada
     */
    public static Optional<Usuario> mostrar(Component padre, Usuario usuario, Usuario sesion) {
        UsuarioDialog d = new UsuarioDialog(padre, usuario, sesion);
        d.abrir(560, 0);
        return Optional.ofNullable(d.resultado);
    }

    private void evaluarFortaleza() {
        char[] password = txtPassword.getPassword();
        indicador.evaluar(password);
        Arrays.fill(password, '\0');
    }

    private void guardar() {
        form.limpiarErrores();
        String username = txtUsername.getText().trim().toLowerCase(Locale.ROOT);
        String nombre = txtNombre.getText().trim();
        Rol rol = (Rol) cmbRol.getSelectedItem();
        if (original == null && !Validador.esUsernameValido(username)) {
            form.marcarError(txtUsername, "3-20 caracteres: a-z, 0-9, punto o guion bajo");
        }
        if (!Validador.noVacio(nombre)) {
            form.marcarError(txtNombre, "El nombre es obligatorio");
        }
        if (original == null) {
            guardarNuevo(username, nombre, rol);
        } else {
            guardarCambios(nombre, rol);
        }
    }

    private void guardarNuevo(String username, String nombre, Rol rol) {
        char[] password = txtPassword.getPassword();
        char[] repetida = txtRepetir.getPassword();
        try {
            if (!Validador.esPasswordSegura(password)) {
                form.marcarError(txtPassword, "No cumple la política de seguridad");
            } else if (!Arrays.equals(password, repetida)) {
                form.marcarError(txtRepetir, "Las contraseñas no coinciden");
            }
            if (form.hayErrores()) {
                mostrarError("Revise los campos marcados en rojo.");
                form.enfocarPrimerError();
                return;
            }
            Usuario u = new Usuario(0, username, nombre, rol);
            u.setActivo(true);
            Usuario creado = ejecutar(() -> ServiceLocator.usuarios().crear(u, password));
            if (creado != null) {
                resultado = creado;
                aceptar();
            }
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(repetida, '\0');
        }
    }

    private void guardarCambios(String nombre, Rol rol) {
        if (form.hayErrores()) {
            mostrarError("Revise los campos marcados en rojo.");
            form.enfocarPrimerError();
            return;
        }
        Usuario u = new Usuario(original.getId(), original.getUsername(), nombre, rol);
        u.setActivo(chkActivo.isSelected());
        u.setIntentosFallidos(original.getIntentosFallidos());
        u.setBloqueadoHasta(original.getBloqueadoHasta());
        Boolean ok = ejecutar(() -> {
            ServiceLocator.usuarios().actualizar(u);
            return Boolean.TRUE;
        });
        if (ok != null) {
            resultado = u;
            aceptar();
        }
    }
}
