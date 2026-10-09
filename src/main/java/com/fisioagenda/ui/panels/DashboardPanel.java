package com.fisioagenda.ui.panels;

import com.fisioagenda.model.Cita;
import com.fisioagenda.model.EstadoCita;
import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Usuario;
import com.fisioagenda.service.ServiceLocator;
import com.fisioagenda.ui.Tareas;
import com.fisioagenda.ui.Tema;
import com.fisioagenda.ui.components.ChipRenderer;
import com.fisioagenda.ui.components.Componentes;
import com.fisioagenda.ui.components.GraficoBarras;
import com.fisioagenda.ui.components.Iconos;
import com.fisioagenda.ui.components.ModeloTabla;
import com.fisioagenda.ui.components.StatCard;
import com.fisioagenda.ui.components.Tabla;
import com.fisioagenda.ui.components.Tarjeta;
import com.fisioagenda.util.Formatos;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Panel de inicio: saludo, cuatro tarjetas de estadística, la tabla con las citas de hoy y un
 * pequeño gráfico de barras con las citas de cada día de la semana. Para un fisioterapeuta las
 * cifras de citas se refieren solo a su agenda. Las citas canceladas no cuentan ni en la tarjeta
 * "Citas hoy" ni en la tabla, para que ambas cifras coincidan.
 * <p>
 * En ventanas estrechas las tarjetas pasan a una rejilla de 2x2 en modo compacto y el gráfico se
 * estrecha, de modo que la tabla de citas conserva varias filas visibles incluso a 960x600.
 */
public class DashboardPanel extends JPanel implements Seccion {

    private static final Locale ES = Locale.forLanguageTag("es-ES");
    private static final int ANCHO_GRAFICO = 270;
    private static final int ANCHO_GRAFICO_COMPACTO = 230;

    private final Usuario usuario;
    private final Integer filtroFisio;
    private final JLabel lblSaludo = new JLabel();
    private final JLabel lblResumen = Componentes.secundaria(" ");
    private final JPanel rejilla = new JPanel(new GridLayout(1, 4, 16, 16));
    private final StatCard cardPacientes;
    private final StatCard cardHoy;
    private final StatCard cardPendientes;
    private final StatCard cardAsistencia;
    private final ModeloTabla<Cita> modelo = new ModeloTabla<>();
    private final Tabla<Cita> tabla;
    private final JLabel lblContadorHoy = Componentes.secundaria(" ");
    private final GraficoBarras grafico = new GraficoBarras();
    private final Tarjeta tarjetaGrafico = new Tarjeta(new BorderLayout(0, 12), 16);
    private int columnasRejilla = 4;

    /** Crea el panel para el usuario de la sesión. */
    public DashboardPanel(Usuario usuario) {
        super(new BorderLayout(0, 16));
        this.usuario = usuario;
        this.filtroFisio = usuario.getRol() == Rol.FISIOTERAPEUTA ? usuario.getId() : null;
        setOpaque(false);

        boolean propio = filtroFisio != null;
        cardPacientes = new StatCard("Pacientes", Iconos.Tipo.PACIENTES, Tema.PRIMARIO, "Total registrados");
        cardHoy = new StatCard("Citas hoy", Iconos.Tipo.AGENDA, Tema.INFO,
                propio ? "En su agenda" : "En la agenda");
        cardPendientes = new StatCard("Pendientes 7 días", Iconos.Tipo.RELOJ, Tema.AVISO,
                propio ? "Solo sus citas" : "Citas programadas");
        cardAsistencia = new StatCard("Asistencia", Iconos.Tipo.CHECK_CIRCULO, Tema.EXITO, "Últimos 30 días");

        modelo.columna("Hora", String.class, c -> Formatos.HORA.format(c.getFechaHora()))
                .columna("Paciente", String.class, c -> c.getPaciente().getNombreCompleto())
                .columna("Tratamiento", String.class, c -> c.getTratamiento().getNombre());
        if (!propio) {
            // Un fisioterapeuta solo ve sus citas: la columna sería redundante.
            modelo.columna("Profesional", String.class, c -> nombreCorto(c.getFisioterapeuta().getNombreCompleto()));
        }
        modelo.columna("Estado", EstadoCita.class, Cita::getEstado);
        tabla = new Tabla<>(modelo);
        tabla.setMensajeVacio("Cargando...");
        int columnaEstado = modelo.getColumnCount() - 1;
        tabla.setAnchoColumna(0, 64);
        tabla.setAnchoColumna(1, 180);
        tabla.setAnchoColumna(2, 170);
        if (!propio) {
            tabla.setAnchoColumna(3, 100);
            tabla.getColumnModel().getColumn(3).setMinWidth(96);
        }
        tabla.setAnchoColumna(columnaEstado, 122);
        // La hora y el chip de estado nunca deben recortarse; el resto de columnas cede espacio.
        tabla.getColumnModel().getColumn(0).setMinWidth(64);
        tabla.getColumnModel().getColumn(columnaEstado).setMinWidth(118);
        tabla.setRenderizador(columnaEstado, ChipRenderer.paraEstados());
        tabla.setDefaultRenderer(String.class, new RenderizadorAtenuado());
        tabla.getAccessibleContext().setAccessibleName("Próximas citas de hoy");

        add(crearCabecera(), BorderLayout.NORTH);
        add(crearCuerpo(), BorderLayout.CENTER);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                ajustarRejilla();
            }
        });
    }

    private JPanel crearCabecera() {
        JPanel norte = new JPanel();
        norte.setLayout(new BoxLayout(norte, BoxLayout.Y_AXIS));
        norte.setOpaque(false);
        lblSaludo.setFont(Tema.TITULO.deriveFont(20f));
        lblSaludo.setForeground(Tema.TEXTO);
        lblSaludo.setAlignmentX(LEFT_ALIGNMENT);
        lblResumen.setAlignmentX(LEFT_ALIGNMENT);
        lblResumen.setFont(Tema.NORMAL);
        norte.add(lblSaludo);
        norte.add(Box.createVerticalStrut(2));
        norte.add(lblResumen);
        norte.add(Box.createVerticalStrut(16));
        rejilla.setOpaque(false);
        rejilla.setAlignmentX(LEFT_ALIGNMENT);
        rejilla.add(cardPacientes);
        rejilla.add(cardHoy);
        rejilla.add(cardPendientes);
        rejilla.add(cardAsistencia);
        norte.add(rejilla);
        return norte;
    }

    private JPanel crearCuerpo() {
        Tarjeta tarjetaTabla = new Tarjeta(new BorderLayout(0, 12), 16);
        JPanel cabTabla = new JPanel(new BorderLayout());
        cabTabla.setOpaque(false);
        cabTabla.add(Componentes.titulo("Próximas citas de hoy"), BorderLayout.WEST);
        cabTabla.add(lblContadorHoy, BorderLayout.EAST);
        tarjetaTabla.add(cabTabla, BorderLayout.NORTH);
        tarjetaTabla.add(Componentes.scroll(tabla), BorderLayout.CENTER);

        tarjetaGrafico.add(Componentes.titulo("Citas esta semana"), BorderLayout.NORTH);
        tarjetaGrafico.add(grafico, BorderLayout.CENTER);
        JLabel nota = Componentes.secundaria("De lunes a sábado · hoy resaltado");
        tarjetaGrafico.add(nota, BorderLayout.SOUTH);

        JPanel cuerpo = new JPanel(new GridBagLayout());
        cuerpo.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.gridx = 0;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 16);
        cuerpo.add(tarjetaTabla, c);
        // El gráfico mantiene un ancho fijo: el espacio sobrante es para la tabla, que lo aprovecha mejor.
        c.gridx = 1;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 0, 0);
        cuerpo.add(tarjetaGrafico, c);
        tarjetaTabla.setPreferredSize(new Dimension(440, 260));
        tarjetaGrafico.setPreferredSize(new Dimension(ANCHO_GRAFICO, 260));
        tarjetaGrafico.setMinimumSize(new Dimension(ANCHO_GRAFICO_COMPACTO, 200));
        return cuerpo;
    }

    /** Abrevia un nombre completo a "Nombre I." para ahorrar espacio en la tabla del panel. */
    private static String nombreCorto(String nombreCompleto) {
        if (nombreCompleto == null) {
            return "";
        }
        String[] partes = nombreCompleto.trim().split("\\s+");
        if (partes.length < 2 || partes[1].isEmpty()) {
            return nombreCompleto.trim();
        }
        return partes[0] + " " + Character.toUpperCase(partes[1].charAt(0)) + ".";
    }

    /**
     * Pasa la rejilla de tarjetas de 4 a 2 columnas cuando la ventana mide menos de 1000 px
     * (o el panel mide menos de 900 px, demasiado estrecho para que las cuatro tarjetas se lean bien).
     * Con 2 columnas las tarjetas se compactan, se oculta la frase de resumen y el gráfico se estrecha:
     * así la tabla de citas de hoy gana alto y ancho.
     */
    private void ajustarRejilla() {
        Window ventana = SwingUtilities.getWindowAncestor(this);
        int anchoVentana = ventana != null ? ventana.getWidth() : getWidth();
        int columnas = anchoVentana < 1000 || getWidth() < 900 ? 2 : 4;
        if (columnas != columnasRejilla) {
            columnasRejilla = columnas;
            boolean compacto = columnas == 2;
            rejilla.setLayout(new GridLayout(4 / columnas, columnas, compacto ? 12 : 16, compacto ? 12 : 16));
            for (StatCard card : new StatCard[]{cardPacientes, cardHoy, cardPendientes, cardAsistencia}) {
                card.setCompacta(compacto);
            }
            lblResumen.setVisible(!compacto);
            tarjetaGrafico.setPreferredSize(new Dimension(compacto ? ANCHO_GRAFICO_COMPACTO : ANCHO_GRAFICO, 260));
            revalidate();
            repaint();
        }
    }

    @Override
    public String getTitulo() {
        return "Inicio";
    }

    @Override
    public void refrescar() {
        int hora = LocalTime.now().getHour();
        String saludo = hora >= 6 && hora < 14 ? "Buenos días" : (hora >= 14 && hora < 21 ? "Buenas tardes" : "Buenas noches");
        String nombre = usuario.getNombreCompleto().split("\\s+")[0];
        lblSaludo.setText(saludo + ", " + nombre);
        lblResumen.setText(filtroFisio != null
                ? "Este es el resumen de su actividad en la clínica."
                : "Este es el resumen de la actividad de la clínica.");
        Tareas.ejecutar(this, this::cargarDatos, this::mostrarDatos);
    }

    private Datos cargarDatos() {
        Datos d = new Datos();
        d.pacientes = ServiceLocator.estadisticas().pacientesTotales();
        d.citasHoy = ServiceLocator.estadisticas().citasHoy(filtroFisio);
        d.pendientes = ServiceLocator.estadisticas().citasPendientesSemana(filtroFisio);
        d.asistencia = ServiceLocator.estadisticas().tasaAsistenciaUltimos30Dias();
        d.semana = ServiceLocator.estadisticas().citasPorDiaSemanaActual();
        // Sin las canceladas: así el total de la tabla coincide con la tarjeta "Citas hoy".
        d.listaHoy = ServiceLocator.citas().citasDelDia(LocalDate.now(), filtroFisio);
        d.listaHoy.removeIf(c -> c.getEstado() == EstadoCita.CANCELADA);
        return d;
    }

    private void mostrarDatos(Datos d) {
        cardPacientes.setValor(d.pacientes);
        cardHoy.setValor(d.citasHoy);
        cardPendientes.setValor(d.pendientes);
        cardAsistencia.setValor(d.asistencia, v -> String.format(ES, "%.0f %%", v));

        tabla.setFilas(d.listaHoy);
        tabla.setMensajeVacio("No hay citas para hoy");
        long pendientes = d.listaHoy.stream()
                .filter(c -> c.getEstado() == EstadoCita.PROGRAMADA && c.getFechaHoraFin().isAfter(LocalDateTime.now()))
                .count();
        lblContadorHoy.setText(d.listaHoy.isEmpty() ? " "
                : pendientes + (pendientes == 1 ? " pendiente de " : " pendientes de ") + d.listaHoy.size());
        seleccionarProxima();

        List<String> etiquetas = new ArrayList<>();
        List<String> descripciones = new ArrayList<>();
        List<Integer> valores = new ArrayList<>();
        int resaltado = -1;
        DayOfWeek hoy = LocalDate.now().getDayOfWeek();
        for (DayOfWeek dia = DayOfWeek.MONDAY; dia != DayOfWeek.SUNDAY; dia = dia.plus(1)) {
            etiquetas.add(capitalizar(dia.getDisplayName(TextStyle.SHORT, ES).replace(".", "")));
            descripciones.add(capitalizar(dia.getDisplayName(TextStyle.FULL, ES)));
            Map<DayOfWeek, Integer> semana = d.semana;
            Integer v = semana != null ? semana.get(dia) : null;
            valores.add(v != null ? v : 0);
            if (dia == hoy) {
                resaltado = valores.size() - 1;
            }
        }
        grafico.setDatos(etiquetas, descripciones, valores, resaltado);
    }

    /** Desplaza la tabla hasta la primera cita que aún no ha terminado. */
    private void seleccionarProxima() {
        LocalDateTime ahora = LocalDateTime.now();
        for (int i = 0; i < tabla.getRowCount(); i++) {
            Cita c = modelo.getFila(tabla.convertRowIndexToModel(i));
            if (c.getFechaHoraFin().isAfter(ahora)) {
                tabla.scrollRectToVisible(tabla.getCellRect(i, 0, true));
                return;
            }
        }
    }

    private static String capitalizar(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Resultado de la carga en segundo plano. */
    private static final class Datos {
        private int pacientes;
        private int citasHoy;
        private int pendientes;
        private double asistencia;
        private Map<DayOfWeek, Integer> semana;
        private List<Cita> listaHoy = new ArrayList<>();
    }

    /** Muestra en gris las citas de hoy que ya han terminado. */
    private final class RenderizadorAtenuado extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            Cita cita = modelo.getFila(table.convertRowIndexToModel(row));
            boolean pasada = cita.getFechaHoraFin().isBefore(LocalDateTime.now());
            int columnaModelo = table.convertColumnIndexToModel(column);
            setFont(columnaModelo == 0 ? Tema.NORMAL_NEGRITA : Tema.NORMAL);
            if (!isSelected) {
                setForeground(pasada ? Tema.TEXTO_SECUNDARIO : Tema.TEXTO);
            }
            String ayuda = pasada ? "Cita ya finalizada" : null;
            if (filtroFisio == null && columnaModelo == 3) {
                // La columna muestra el nombre abreviado: el completo se ofrece como ayuda.
                ayuda = cita.getFisioterapeuta().getNombreCompleto() + (pasada ? " · cita ya finalizada" : "");
            }
            setToolTipText(ayuda);
            return this;
        }
    }
}
