package com.fisioagenda.ui.components;

import com.fisioagenda.ui.Tema;
import com.fisioagenda.util.Formatos;

import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.AWTKeyStroke;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Tabla de solo lectura con el estilo de la aplicación: filas altas y alternas, cabecera en negrita,
 * ordenación por columnas, mensaje cuando está vacía, doble clic / Intro para la acción principal
 * y menú contextual que selecciona la fila bajo el ratón.
 *
 * @param <T> tipo de objeto de cada fila
 */
public class Tabla<T> extends JTable {

    private final ModeloTabla<T> modelo;
    private final TableRowSorter<ModeloTabla<T>> ordenador;
    private final Map<Integer, Integer> alineacionCabecera = new HashMap<>();
    private String mensajeVacio = "No hay datos que mostrar";
    private Runnable accionPrincipal;

    /** Crea la tabla para el modelo indicado. */
    public Tabla(ModeloTabla<T> modelo) {
        super(modelo);
        this.modelo = modelo;
        this.ordenador = new TableRowSorter<>(modelo);
        setRowSorter(ordenador);
        configurarAspecto();
        configurarTeclado();
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)
                        && rowAtPoint(e.getPoint()) >= 0 && accionPrincipal != null) {
                    accionPrincipal.run();
                }
            }
        });
    }

    private void configurarAspecto() {
        setRowHeight(38);
        setShowGrid(false);
        setIntercellSpacing(new Dimension(0, 0));
        setFillsViewportHeight(true);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setAutoCreateRowSorter(false);
        setBackground(Tema.SUPERFICIE);
        setForeground(Tema.TEXTO);
        setFont(Tema.NORMAL);
        JTableHeader cabecera = getTableHeader();
        cabecera.setReorderingAllowed(false);
        cabecera.setFont(Tema.NORMAL_NEGRITA);
        cabecera.setPreferredSize(new Dimension(cabecera.getPreferredSize().width, 36));
        // Títulos alineados a la izquierda salvo las columnas indicadas con alinearCabecera (p. ej. importes).
        TableCellRenderer base = cabecera.getDefaultRenderer();
        cabecera.setDefaultRenderer((tabla, valor, seleccionada, foco, fila, columna) -> {
            Component c = base.getTableCellRendererComponent(tabla, valor, seleccionada, foco, fila, columna);
            if (c instanceof JLabel) {
                int columnaModelo = columna >= 0 ? tabla.convertColumnIndexToModel(columna) : -1;
                ((JLabel) c).setHorizontalAlignment(
                        alineacionCabecera.getOrDefault(columnaModelo, SwingConstants.LEADING));
            }
            return c;
        });
        setDefaultRenderer(LocalDate.class, new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object valor) {
                setText(valor instanceof LocalDate ? Formatos.fecha((LocalDate) valor) : "");
            }
        });
        setDefaultRenderer(LocalDateTime.class, new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object valor) {
                setText(valor instanceof LocalDateTime ? formatear((LocalDateTime) valor) : "");
            }
        });
        getAccessibleContext().setAccessibleName("Tabla de datos");
    }

    private void configurarTeclado() {
        // Intro ejecuta la acción principal (normalmente editar) en vez de bajar de fila.
        InputMap mapa = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        mapa.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "accionPrincipal");
        getActionMap().put("accionPrincipal", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (accionPrincipal != null && getSelectedRow() >= 0) {
                    accionPrincipal.run();
                }
            }
        });
        // Tabulador sale de la tabla (es de solo lectura, no hace falta navegar por celdas).
        mapa.remove(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));
        mapa.remove(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK));
        Set<AWTKeyStroke> adelante = new HashSet<>();
        adelante.add(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));
        adelante.add(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK));
        Set<AWTKeyStroke> atras = new HashSet<>();
        atras.add(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK));
        atras.add(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK));
        setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, adelante);
        setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, atras);
    }

    /** Formato de fecha y hora usado en las tablas (dd/MM/yyyy HH:mm). */
    public static String formatear(LocalDateTime fechaHora) {
        return Formatos.fecha(fechaHora.toLocalDate()) + "  " + Formatos.HORA.format(fechaHora);
    }

    /** Modelo de datos de la tabla. */
    public ModeloTabla<T> getModelo() {
        return modelo;
    }

    /** Ordenador/filtro de filas asociado. */
    public TableRowSorter<ModeloTabla<T>> getOrdenador() {
        return ordenador;
    }

    /** Sustituye las filas (se conserva la ordenación elegida por el usuario). */
    public void setFilas(List<T> filas) {
        modelo.setFilas(filas);
    }

    /** Objeto de la fila seleccionada, si la hay. */
    public Optional<T> getSeleccionado() {
        int fila = getSelectedRow();
        if (fila < 0) {
            return Optional.empty();
        }
        return Optional.of(modelo.getFila(convertRowIndexToModel(fila)));
    }

    /** Texto que se muestra centrado cuando no hay filas. */
    public void setMensajeVacio(String mensaje) {
        this.mensajeVacio = mensaje;
        repaint();
    }

    /** Acción al hacer doble clic o pulsar Intro sobre una fila. */
    public void setAccionPrincipal(Runnable accion) {
        this.accionPrincipal = accion;
    }

    /** Asigna un menú contextual; el clic derecho selecciona antes la fila bajo el ratón. */
    public void setMenuContextual(JPopupMenu menu) {
        setComponentPopupMenu(menu);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                seleccionarBajoRaton(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                seleccionarBajoRaton(e);
            }

            private void seleccionarBajoRaton(MouseEvent e) {
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                    int fila = rowAtPoint(e.getPoint());
                    if (fila >= 0) {
                        setRowSelectionInterval(fila, fila);
                    }
                }
            }
        });
    }

    /** Fija el ancho preferido de una columna. */
    public void setAnchoColumna(int columna, int ancho) {
        getColumnModel().getColumn(columna).setPreferredWidth(ancho);
    }

    /**
     * Alinea el título de una columna (por defecto a la izquierda); se usa, por ejemplo, para alinear a la
     * derecha el título de una columna de importes junto con sus valores.
     *
     * @param columna    índice de la columna en el modelo
     * @param alineacion constante de {@link SwingConstants} ({@code LEADING}, {@code CENTER}, {@code TRAILING})
     */
    public void alinearCabecera(int columna, int alineacion) {
        alineacionCabecera.put(columna, alineacion);
        getTableHeader().repaint();
    }

    /** Asigna un renderizador a una columna concreta. */
    public void setRenderizador(int columna, TableCellRenderer renderizador) {
        getColumnModel().getColumn(columna).setCellRenderer(renderizador);
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        // Muestra el contenido completo de la celda cuando no cabe en la columna.
        Point p = e.getPoint();
        int fila = rowAtPoint(p);
        int columna = columnAtPoint(p);
        if (fila < 0 || columna < 0) {
            return null;
        }
        Component c = prepareRenderer(getCellRenderer(fila, columna), fila, columna);
        Rectangle celda = getCellRect(fila, columna, false);
        if (c.getPreferredSize().width > celda.width && c instanceof JLabel) {
            String texto = ((JLabel) c).getText();
            return texto == null || texto.isBlank() ? null : texto;
        }
        return super.getToolTipText(e);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (getRowCount() == 0 && mensajeVacio != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(Tema.NORMAL.deriveFont(Font.PLAIN, 14f));
                g2.setColor(Tema.TEXTO_SECUNDARIO);
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(mensajeVacio)) / 2;
                int y = Math.max(40, getVisibleRect().height / 2) - fm.getHeight() / 2 + fm.getAscent();
                g2.drawString(mensajeVacio, Math.max(12, x), y);
            } finally {
                g2.dispose();
            }
        }
    }
}
