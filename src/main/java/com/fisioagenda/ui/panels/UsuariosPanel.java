package com.fisioagenda.ui.panels;

import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Usuario;
import com.fisioagenda.service.ServiceLocator;
import com.fisioagenda.ui.Tareas;
import com.fisioagenda.ui.Tema;
import com.fisioagenda.ui.components.ChipRenderer;
import com.fisioagenda.ui.components.Componentes;
import com.fisioagenda.ui.components.Componentes.EstiloBoton;
import com.fisioagenda.ui.components.FiltroTexto;
import com.fisioagenda.ui.components.Iconos;
import com.fisioagenda.ui.components.ModeloTabla;
import com.fisioagenda.ui.components.Tabla;
import com.fisioagenda.ui.components.Tarjeta;
import com.fisioagenda.ui.components.Toast;
import com.fisioagenda.ui.dialogs.CambiarPasswordDialog;
import com.fisioagenda.ui.dialogs.UsuarioDialog;
import com.fisioagenda.util.Formatos;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Gestión de usuarios (solo administración): alta, edición de nombre/rol/estado, restablecimiento
 * de contraseña y desbloqueo de cuentas bloqueadas por intentos fallidos.
 */
public class UsuariosPanel extends JPanel implements Seccion {

    private final Usuario sesion;
    private final ModeloTabla<Usuario> modelo = new ModeloTabla<>();
    private final Tabla<Usuario> tabla;
    private final JTextField txtBuscar = Componentes.campoBusqueda("Buscar usuario…");
    private final JLabel lblContador = Componentes.secundaria(" ");
    private final JButton btnNuevo;
    private final JButton btnEditar;
    private final JButton btnPassword;
    private final JButton btnDesbloquear;

    /** Crea el panel; solo se añade a la ventana principal para administradores. */
    public UsuariosPanel(Usuario sesion) {
        super(new BorderLayout(0, 16));
        this.sesion = sesion;
        setOpaque(false);

        modelo.columna("Usuario", String.class, Usuario::getUsername)
                .columna("Nombre", String.class, Usuario::getNombreCompleto)
                .columna("Rol", Rol.class, Usuario::getRol)
                .columna("Activo", Boolean.class, Usuario::isActivo)
                .columna("Bloqueado", String.class, UsuariosPanel::textoBloqueo);
        tabla = new Tabla<>(modelo);
        tabla.setAnchoColumna(0, 140);
        tabla.setAnchoColumna(1, 260);
        tabla.setAnchoColumna(2, 150);
        tabla.setAnchoColumna(3, 110);
        tabla.setAnchoColumna(4, 170);
        tabla.setRenderizador(2, new ChipRenderer(v -> v instanceof Rol ? ((Rol) v).getEtiqueta() : "",
                v -> v == Rol.ADMIN ? Tema.PRIMARIO_OSCURO : (v == Rol.FISIOTERAPEUTA ? Tema.INFO : Tema.AVISO)));
        tabla.setRenderizador(3, ChipRenderer.paraBooleano("Activo", Tema.EXITO, "Inactivo", Tema.ESTADO_CANCELADA));
        tabla.setRenderizador(4, new ChipRenderer(v -> v == null ? "" : v.toString(),
                v -> v == null || v.toString().isEmpty() ? null : Tema.ERROR));
        tabla.setAccionPrincipal(this::editar);
        tabla.getAccessibleContext().setAccessibleName("Usuarios del sistema");

        btnNuevo = Componentes.boton("Nuevo", Iconos.Tipo.MAS, EstiloBoton.PRIMARIO, 'N', "Nuevo usuario (Ctrl+N)");
        btnEditar = Componentes.boton("Editar", Iconos.Tipo.EDITAR, EstiloBoton.SECUNDARIO, 'E',
                "Editar nombre, rol o estado del usuario");
        btnPassword = Componentes.boton("Restablecer contraseña", Iconos.Tipo.LLAVE, EstiloBoton.SECUNDARIO, 'R',
                "Asignar una contraseña nueva al usuario seleccionado");
        btnDesbloquear = Componentes.boton("Desbloquear", Iconos.Tipo.CANDADO_ABIERTO, EstiloBoton.SECUNDARIO, 'D',
                "Quitar el bloqueo por intentos fallidos");
        btnNuevo.addActionListener(e -> nuevo());
        btnEditar.addActionListener(e -> editar());
        btnPassword.addActionListener(e -> restablecer());
        btnDesbloquear.addActionListener(e -> desbloquear());

        add(Componentes.barraHerramientas(txtBuscar, btnDesbloquear, btnPassword, btnEditar, btnNuevo),
                BorderLayout.NORTH);

        Tarjeta tarjeta = new Tarjeta(new BorderLayout(), 8);
        tarjeta.add(Componentes.scroll(tabla), BorderLayout.CENTER);
        add(tarjeta, BorderLayout.CENTER);
        add(lblContador, BorderLayout.SOUTH);

        JPopupMenu menu = new JPopupMenu();
        menu.add(item("Editar", Iconos.Tipo.EDITAR, this::editar));
        menu.add(item("Restablecer contraseña", Iconos.Tipo.LLAVE, this::restablecer));
        menu.add(item("Desbloquear", Iconos.Tipo.CANDADO_ABIERTO, this::desbloquear));
        tabla.setMenuContextual(menu);
        tabla.getSelectionModel().addListSelectionListener(e -> actualizarBotones());
        FiltroTexto.conectar(txtBuscar, tabla, this::actualizarContador);
        actualizarBotones();
    }

    private static JMenuItem item(String texto, Iconos.Tipo icono, Runnable accion) {
        JMenuItem item = new JMenuItem(texto, Iconos.de(icono, 16));
        item.addActionListener(e -> accion.run());
        return item;
    }

    /** Texto de la columna "Bloqueado": vacío si no lo está, o la hora hasta la que dura el bloqueo. */
    private static String textoBloqueo(Usuario u) {
        LocalDateTime ahora = LocalDateTime.now();
        return u.estaBloqueado(ahora) ? "Hasta las " + Formatos.HORA.format(u.getBloqueadoHasta()) : "";
    }

    private void actualizarBotones() {
        Optional<Usuario> sel = tabla.getSeleccionado();
        btnEditar.setEnabled(sel.isPresent());
        btnPassword.setEnabled(sel.isPresent());
        btnDesbloquear.setEnabled(sel.map(u -> u.estaBloqueado(LocalDateTime.now()) || u.getIntentosFallidos() > 0)
                .orElse(false));
    }

    private void actualizarContador() {
        int total = modelo.getRowCount();
        long activos = modelo.getFilas().stream().filter(Usuario::isActivo).count();
        lblContador.setText(total + (total == 1 ? " usuario" : " usuarios") + " · " + activos
                + (activos == 1 ? " activo" : " activos"));
        tabla.setMensajeVacio(total == 0 ? "No hay usuarios" : "Ningún usuario coincide con la búsqueda");
    }

    @Override
    public String getTitulo() {
        return "Usuarios";
    }

    @Override
    public void refrescar() {
        cargar(tabla.getSeleccionado().map(Usuario::getId).orElse(null));
    }

    private void cargar(Integer seleccionarId) {
        if (modelo.getRowCount() == 0) {
            tabla.setMensajeVacio("Cargando usuarios...");
        }
        Tareas.ejecutar(() -> ServiceLocator.usuarios().listar(), lista -> {
            tabla.setFilas(lista);
            FiltroTexto.aplicar(tabla, txtBuscar.getText());
            if (seleccionarId != null) {
                seleccionarPorId(seleccionarId);
            }
            actualizarContador();
            actualizarBotones();
        }, error -> {
            tabla.setMensajeVacio("No se pudieron cargar los usuarios");
            Tareas.mostrarError(this, error);
        });
    }

    private void seleccionarPorId(int id) {
        for (int i = 0; i < tabla.getRowCount(); i++) {
            if (modelo.getFila(tabla.convertRowIndexToModel(i)).getId() == id) {
                tabla.setRowSelectionInterval(i, i);
                return;
            }
        }
    }

    @Override
    public void nuevo() {
        UsuarioDialog.mostrar(this, null, sesion).ifPresent(u -> {
            Toast.mostrar(this, "Usuario creado");
            cargar(u.getId());
        });
    }

    @Override
    public void enfocarBusqueda() {
        txtBuscar.requestFocusInWindow();
        txtBuscar.selectAll();
    }

    private void editar() {
        tabla.getSeleccionado().ifPresent(actual -> UsuarioDialog.mostrar(this, actual, sesion).ifPresent(u -> {
            Toast.mostrar(this, "Usuario actualizado");
            cargar(u.getId());
        }));
    }

    private void restablecer() {
        tabla.getSeleccionado().ifPresent(u -> {
            if (CambiarPasswordDialog.paraRestablecer(this, u)) {
                Toast.mostrar(this, "Contraseña restablecida");
                cargar(u.getId()); // Restablecer también quita el bloqueo: se refresca la columna.
            }
        });
    }

    private void desbloquear() {
        tabla.getSeleccionado().ifPresent(u -> {
            if (Tareas.enEdt(this, () -> ServiceLocator.usuarios().desbloquear(u.getId()))) {
                Toast.mostrar(this, "Usuario desbloqueado");
                cargar(u.getId());
            }
        });
    }
}
