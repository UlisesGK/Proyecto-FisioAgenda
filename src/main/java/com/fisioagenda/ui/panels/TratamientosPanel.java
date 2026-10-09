package com.fisioagenda.ui.panels;

import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Tratamiento;
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
import com.fisioagenda.ui.dialogs.TratamientoDialog;
import com.fisioagenda.util.Formatos;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.util.Optional;

/**
 * Catálogo de tratamientos. El administrador puede crear, editar y activar/desactivar
 * tratamientos; el resto de perfiles lo consulta en modo solo lectura.
 */
public class TratamientosPanel extends JPanel implements Seccion {

    private final boolean esAdmin;
    private final ModeloTabla<Tratamiento> modelo = new ModeloTabla<>();
    private final Tabla<Tratamiento> tabla;
    private final JTextField txtBuscar = Componentes.campoBusqueda("Buscar tratamiento…");
    private final JLabel lblContador = Componentes.secundaria(" ");
    private final JButton btnNuevo;
    private final JButton btnEditar;
    private final JButton btnActivar;

    /** Crea el panel para el usuario de la sesión. */
    public TratamientosPanel(Usuario usuario) {
        super(new BorderLayout(0, 16));
        this.esAdmin = usuario.getRol() == Rol.ADMIN;
        setOpaque(false);

        modelo.columna("Nombre", String.class, Tratamiento::getNombre)
                .columna("Duración", Integer.class, Tratamiento::getDuracionMinutos)
                .columna("Precio", Double.class, Tratamiento::getPrecio)
                .columna("Activo", Boolean.class, Tratamiento::isActivo);
        tabla = new Tabla<>(modelo);
        tabla.setAnchoColumna(0, 320);
        tabla.setAnchoColumna(1, 110);
        tabla.setAnchoColumna(2, 110);
        tabla.setAnchoColumna(3, 120);
        // Cifras alineadas a la derecha (también su título) para poder compararlas en columna.
        tabla.setRenderizador(1, new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object valor) {
                setText(valor == null ? "" : valor + " min");
                setHorizontalAlignment(SwingConstants.TRAILING);
            }
        });
        tabla.setRenderizador(2, new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object valor) {
                setText(valor instanceof Number ? Formatos.moneda(((Number) valor).doubleValue()) : "");
                setHorizontalAlignment(SwingConstants.TRAILING);
            }
        });
        tabla.alinearCabecera(1, SwingConstants.TRAILING);
        tabla.alinearCabecera(2, SwingConstants.TRAILING);
        tabla.setRenderizador(3, ChipRenderer.paraBooleano("Activo", Tema.EXITO, "Inactivo", Tema.ESTADO_CANCELADA));
        tabla.getAccessibleContext().setAccessibleName("Catálogo de tratamientos");

        btnNuevo = Componentes.boton("Nuevo", Iconos.Tipo.MAS, EstiloBoton.PRIMARIO, 'N', "Nuevo tratamiento (Ctrl+N)");
        btnEditar = Componentes.boton("Editar", Iconos.Tipo.EDITAR, EstiloBoton.SECUNDARIO, 'E',
                "Editar el tratamiento seleccionado (doble clic o Intro)");
        btnActivar = Componentes.boton("Desactivar", Iconos.Tipo.INTERRUPTOR, EstiloBoton.SECUNDARIO, 'A',
                "Activar o desactivar el tratamiento seleccionado");
        btnNuevo.addActionListener(e -> nuevo());
        btnEditar.addActionListener(e -> editar());
        btnActivar.addActionListener(e -> alternarActivo());
        if (esAdmin) {
            tabla.setAccionPrincipal(this::editar);
        }

        if (esAdmin) {
            add(Componentes.barraHerramientas(txtBuscar, btnActivar, btnEditar, btnNuevo), BorderLayout.NORTH);
        } else {
            JLabel aviso = Componentes.secundaria("Solo lectura: el catálogo lo gestiona la administración");
            aviso.setIcon(Iconos.de(Iconos.Tipo.CANDADO, 14, Tema.TEXTO_SECUNDARIO));
            aviso.setIconTextGap(6);
            aviso.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
            add(Componentes.barraHerramientas(txtBuscar, aviso), BorderLayout.NORTH);
        }

        Tarjeta tarjeta = new Tarjeta(new BorderLayout(), 8);
        tarjeta.add(Componentes.scroll(tabla), BorderLayout.CENTER);
        add(tarjeta, BorderLayout.CENTER);
        add(lblContador, BorderLayout.SOUTH);

        tabla.getSelectionModel().addListSelectionListener(e -> actualizarBotones());
        FiltroTexto.conectar(txtBuscar, tabla, this::actualizarContador);
        actualizarBotones();
    }

    private void actualizarBotones() {
        Optional<Tratamiento> sel = tabla.getSeleccionado();
        btnEditar.setEnabled(sel.isPresent());
        btnActivar.setEnabled(sel.isPresent());
        btnActivar.setText(sel.map(t -> t.isActivo() ? "Desactivar" : "Activar").orElse("Desactivar"));
    }

    private void actualizarContador() {
        int total = modelo.getRowCount();
        long activos = modelo.getFilas().stream().filter(Tratamiento::isActivo).count();
        lblContador.setText(total + (total == 1 ? " tratamiento" : " tratamientos") + " · " + activos
                + (activos == 1 ? " activo" : " activos"));
        tabla.setMensajeVacio(total == 0 ? "No hay tratamientos en el catálogo" : "Ningún tratamiento coincide");
    }

    @Override
    public String getTitulo() {
        return "Tratamientos";
    }

    @Override
    public void refrescar() {
        cargar(null);
    }

    private void cargar(Integer seleccionarId) {
        Optional<Integer> previo = seleccionarId != null ? Optional.of(seleccionarId)
                : tabla.getSeleccionado().map(Tratamiento::getId);
        if (modelo.getRowCount() == 0) {
            tabla.setMensajeVacio("Cargando tratamientos...");
        }
        Tareas.ejecutar(() -> ServiceLocator.tratamientos().listar(), lista -> {
            tabla.setFilas(lista);
            FiltroTexto.aplicar(tabla, txtBuscar.getText());
            previo.ifPresent(this::seleccionarPorId);
            actualizarContador();
            actualizarBotones();
        }, error -> {
            tabla.setMensajeVacio("No se pudo cargar el catálogo");
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
        if (!esAdmin) {
            return;
        }
        TratamientoDialog.mostrar(this, null).ifPresent(t -> {
            Toast.mostrar(this, "Tratamiento guardado");
            cargar(t.getId());
        });
    }

    @Override
    public void enfocarBusqueda() {
        txtBuscar.requestFocusInWindow();
        txtBuscar.selectAll();
    }

    private void editar() {
        tabla.getSeleccionado().ifPresent(actual -> TratamientoDialog.mostrar(this, actual).ifPresent(t -> {
            Toast.mostrar(this, "Tratamiento guardado");
            cargar(t.getId());
        }));
    }

    private void alternarActivo() {
        tabla.getSeleccionado().ifPresent(actual -> {
            Tratamiento copia = new Tratamiento(actual.getId(), actual.getNombre(), actual.getDuracionMinutos(),
                    actual.getPrecio());
            copia.setDescripcion(actual.getDescripcion());
            copia.setActivo(!actual.isActivo());
            if (Tareas.enEdt(this, () -> ServiceLocator.tratamientos().guardar(copia))) {
                Toast.mostrar(this, copia.isActivo() ? "Tratamiento activado" : "Tratamiento desactivado");
                cargar(copia.getId());
            }
        });
    }
}
