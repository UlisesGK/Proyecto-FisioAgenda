package com.fisioagenda.ui.dialogs;

import com.fisioagenda.model.Usuario;
import com.fisioagenda.service.ServiceLocator;
import com.fisioagenda.ui.components.Componentes;
import com.fisioagenda.ui.components.FormBuilder;
import com.fisioagenda.ui.components.Iconos;
import com.fisioagenda.ui.components.IndicadorFortaleza;
import com.fisioagenda.util.Validador;

import javax.swing.JPasswordField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Component;
import java.util.Arrays;

/**
 * Diálogo para cambiar la contraseña. Tiene dos modos: el usuario de la sesión cambia la suya
 * (pide la actual, usa {@code AuthService.cambiarPassword}) o el administrador restablece la de
 * otro usuario (usa {@code UsuarioService.restablecerPassword}). Incluye indicador de fortaleza.
 */
public final class CambiarPasswordDialog extends DialogoBase {

    private final Usuario objetivo;
    private final FormBuilder form = new FormBuilder(1);
    private final JPasswordField txtActual = new JPasswordField();
    private final JPasswordField txtNueva = new JPasswordField();
    private final JPasswordField txtRepetir = new JPasswordField();
    private final IndicadorFortaleza indicador = new IndicadorFortaleza();

    private CambiarPasswordDialog(Component padre, Usuario objetivo) {
        super(padre, objetivo == null ? "Cambiar contraseña" : "Restablecer contraseña",
                objetivo == null ? "Elija una contraseña nueva y segura"
                        : "Nueva contraseña para " + objetivo.getNombreCompleto() + " (" + objetivo.getUsername() + ")",
                Iconos.Tipo.LLAVE);
        this.objetivo = objetivo;
        for (JPasswordField campo : new JPasswordField[]{txtActual, txtNueva, txtRepetir}) {
            campo.putClientProperty("FlatLaf.style", "showRevealButton: true");
        }
        if (objetivo == null) {
            form.campo("Contraseña actual", 'A', txtActual);
        }
        form.campo("Nueva contraseña", 'N', txtNueva);
        form.fila(indicador);
        form.campo("Repetir nueva contraseña", 'R', txtRepetir);
        form.fila(Componentes.secundaria("Mínimo 8 caracteres con mayúscula, minúscula, número y símbolo."));
        setContenido(form.construir());
        agregarBotonCancelar("Cancelar");
        agregarBotonPrincipal(objetivo == null ? "Cambiar" : "Restablecer", Iconos.Tipo.CHECK,
                objetivo == null ? 'm' : 's', this::guardar);

        txtNueva.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                evaluar();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                evaluar();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                evaluar();
            }
        });
    }

    /** Abre el diálogo para que el usuario de la sesión cambie su contraseña. Devuelve si se cambió. */
    public static boolean paraUsuarioActual(Component padre) {
        CambiarPasswordDialog d = new CambiarPasswordDialog(padre, null);
        d.abrir(440, 0);
        return d.isAceptado();
    }

    /** Abre el diálogo para que un administrador restablezca la contraseña de otro usuario. */
    public static boolean paraRestablecer(Component padre, Usuario usuario) {
        CambiarPasswordDialog d = new CambiarPasswordDialog(padre, usuario);
        d.abrir(440, 0);
        return d.isAceptado();
    }

    private void evaluar() {
        char[] p = txtNueva.getPassword();
        indicador.evaluar(p);
        Arrays.fill(p, '\0');
    }

    private void guardar() {
        form.limpiarErrores();
        char[] actual = txtActual.getPassword();
        char[] nueva = txtNueva.getPassword();
        char[] repetida = txtRepetir.getPassword();
        try {
            if (objetivo == null && actual.length == 0) {
                form.marcarError(txtActual, "Introduzca su contraseña actual");
            }
            if (!Validador.esPasswordSegura(nueva)) {
                form.marcarError(txtNueva, "No cumple la política de seguridad");
            } else if (!Arrays.equals(nueva, repetida)) {
                form.marcarError(txtRepetir, "Las contraseñas no coinciden");
            }
            if (form.hayErrores()) {
                mostrarError("Revise los campos marcados en rojo.");
                form.enfocarPrimerError();
                return;
            }
            Boolean ok = ejecutar(() -> {
                if (objetivo == null) {
                    ServiceLocator.auth().cambiarPassword(actual, nueva, repetida);
                } else {
                    ServiceLocator.usuarios().restablecerPassword(objetivo.getId(), nueva);
                }
                return Boolean.TRUE;
            });
            if (ok != null) {
                aceptar();
            } else {
                txtActual.setText("");
            }
        } finally {
            Arrays.fill(actual, '\0');
            Arrays.fill(nueva, '\0');
            Arrays.fill(repetida, '\0');
        }
    }
}
