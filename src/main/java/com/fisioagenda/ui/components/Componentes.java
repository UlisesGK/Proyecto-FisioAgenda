package com.fisioagenda.ui.components;

import com.fisioagenda.ui.Tema;

import com.fisioagenda.util.Formatos;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.text.ParsePosition;

/**
 * Fábrica de componentes con el estilo común de la aplicación (botones, etiquetas, buscadores...).
 * Usa las propiedades de cliente de FlatLaf para no tener que pintar a mano lo que la librería ya resuelve.
 */
public final class Componentes {

    /** Estilos de botón disponibles. */
    public enum EstiloBoton {
        /** Acción principal: fondo de acento y texto blanco. */
        PRIMARIO,
        /** Acción secundaria: botón estándar con borde. */
        SECUNDARIO,
        /** Acción destructiva: texto y borde rojos. */
        PELIGRO,
        /** Botón sin borde para barras de herramientas. */
        SIN_BORDE
    }

    private static final String ESTILO_PRIMARIO =
            "background: " + Tema.hex(Tema.PRIMARIO) + "; foreground: #FFFFFF;"
                    + " hoverBackground: " + Tema.hex(Tema.PRIMARIO_OSCURO) + ";"
                    + " pressedBackground: #0B4F4B; focusedBackground: " + Tema.hex(Tema.PRIMARIO) + ";"
                    + " borderColor: " + Tema.hex(Tema.PRIMARIO) + "; focusedBorderColor: #5EEAD4;"
                    + " hoverBorderColor: " + Tema.hex(Tema.PRIMARIO_OSCURO) + "; font: bold";

    private static final int ANCHO_BUSQUEDA = 300;
    private static final int ANCHO_MINIMO_BUSQUEDA = 180;

    private static final String ESTILO_PELIGRO =
            "foreground: " + Tema.hex(Tema.ERROR) + "; borderColor: #F5B5B5;"
                    + " hoverBackground: #FEF2F2; pressedBackground: #FDE2E2;"
                    + " hoverBorderColor: " + Tema.hex(Tema.ERROR) + "; focusedBorderColor: " + Tema.hex(Tema.ERROR);

    private Componentes() {
    }

    /**
     * Crea un botón con icono, mnemónico y descripción emergente.
     *
     * @param texto      texto visible (puede ser {@code null} para un botón solo con icono)
     * @param icono      tipo de icono o {@code null}
     * @param estilo     estilo visual
     * @param mnemonico  tecla Alt+letra o {@code 0} si no tiene
     * @param ayuda      texto de la descripción emergente (tooltip)
     */
    public static JButton boton(String texto, Iconos.Tipo icono, EstiloBoton estilo, char mnemonico, String ayuda) {
        JButton b = new JButton(texto);
        if (icono != null) {
            b.setIcon(Iconos.de(icono, 16));
            b.setIconTextGap(7);
        }
        if (mnemonico != 0) {
            b.setMnemonic(mnemonico);
        }
        if (ayuda != null) {
            b.setToolTipText(ayuda);
        }
        b.setMargin(new Insets(6, 14, 6, 14));
        switch (estilo) {
            case PRIMARIO:
                b.putClientProperty("FlatLaf.style", ESTILO_PRIMARIO);
                break;
            case PELIGRO:
                b.putClientProperty("FlatLaf.style", ESTILO_PELIGRO);
                break;
            case SIN_BORDE:
                b.putClientProperty("JButton.buttonType", "borderless");
                break;
            case SECUNDARIO:
            default:
                break;
        }
        return b;
    }

    /** Botón cuadrado solo con icono (la descripción se usa como tooltip y nombre accesible). */
    public static JButton botonIcono(Iconos.Tipo icono, String ayuda) {
        JButton b = new JButton(Iconos.de(icono, 16));
        b.setToolTipText(ayuda);
        b.getAccessibleContext().setAccessibleName(ayuda);
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        b.putClientProperty("JButton.squareSize", true);
        b.setMargin(new Insets(6, 6, 6, 6));
        return b;
    }

    /**
     * Campo de búsqueda con icono de lupa, texto de ejemplo y botón para borrar. Mide 300 px de ancho y,
     * si falta sitio en la barra, puede estrecharse hasta 180 px.
     */
    public static JTextField campoBusqueda(String ejemplo) {
        JTextField campo = new JTextField(24);
        campo.putClientProperty("JTextField.placeholderText", ejemplo);
        campo.putClientProperty("JTextField.leadingIcon", Iconos.de(Iconos.Tipo.BUSCAR, 16, Tema.TEXTO_SECUNDARIO));
        campo.putClientProperty("JTextField.showClearButton", true);
        campo.putClientProperty("JComponent.roundRect", true);
        campo.getAccessibleContext().setAccessibleName("Buscar");
        campo.setToolTipText(ejemplo + " (Ctrl+F)");
        campo.setPreferredSize(new Dimension(ANCHO_BUSQUEDA, 34));
        campo.setMinimumSize(new Dimension(ANCHO_MINIMO_BUSQUEDA, 34));
        campo.setMaximumSize(new Dimension(ANCHO_BUSQUEDA, 34));
        return campo;
    }

    /**
     * Barra superior de las secciones con tabla: buscador a la izquierda y acciones a la derecha, dentro de una
     * tarjeta. Las acciones conservan siempre su tamaño; cuando la ventana se estrecha es el buscador el que
     * cede ancho (hasta su mínimo), de modo que nunca se solapa con los botones.
     *
     * @param buscador campo creado con {@link #campoBusqueda(String)}
     * @param acciones botones (o etiquetas) que se alinean a la derecha, en este orden
     */
    public static Tarjeta barraHerramientas(JTextField buscador, JComponent... acciones) {
        Tarjeta barra = new Tarjeta(null, 12);
        barra.setLayout(new BoxLayout(barra, BoxLayout.X_AXIS));
        JPanel derecha = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)) {
            @Override
            public Dimension getMinimumSize() {
                return getPreferredSize();
            }

            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        derecha.setOpaque(false);
        for (JComponent accion : acciones) {
            derecha.add(accion);
        }
        barra.add(buscador);
        barra.add(Box.createHorizontalStrut(16));
        barra.add(Box.createHorizontalGlue());
        barra.add(derecha);
        return barra;
    }

    /**
     * Área de texto multilínea con ajuste de palabras. El tabulador mueve el foco al siguiente
     * campo (en lugar de insertar una tabulación), como en el resto del formulario.
     */
    public static JTextArea areaTexto(int filas) {
        JTextArea area = new JTextArea(filas, 20);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(Tema.NORMAL);
        area.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        area.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
        return area;
    }

    /**
     * Selector numérico (JSpinner) con lectura estricta del texto escrito y Intro de un solo paso.
     * <ul>
     *   <li>Se rechaza (borde de error al confirmar) cualquier texto que no sea un número completo: con el
     *   formato estándar, "12abc" se guardaba como 12 y "35.5" como 35 sin avisar.</li>
     *   <li>Se admite el punto como separador decimal además de la coma ("35.5" = 35,50) y se rechazan más
     *   decimales de los que muestra el patrón; en los selectores de enteros no se admiten decimales.</li>
     *   <li>Intro confirma el valor y, si es válido, pulsa el botón por defecto de la ventana (sin esto el
     *   primer Intro solo confirmaba el valor y había que pulsar otra vez).</li>
     *   <li>Al salir del campo con un texto no válido, el texto se conserva (en lugar de volver en silencio al
     *   valor anterior) para que el formulario pueda marcar el error al guardar.</li>
     * </ul>
     *
     * @param modelo modelo con el valor inicial, los límites y el paso
     * @param patron patrón de {@link DecimalFormat} con el que se muestra el valor (p. ej. {@code "0.00"})
     */
    public static JSpinner selectorNumerico(SpinnerNumberModel modelo, String patron) {
        JSpinner selector = new JSpinner(modelo);
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(selector, patron);
        selector.setEditor(editor);
        JFormattedTextField campo = editor.getTextField();
        DecimalFormat formato = new DecimalFormat(patron, DecimalFormatSymbols.getInstance(Formatos.LOCALE));
        campo.setFormatterFactory(new DefaultFormatterFactory(new FormatoNumericoEstricto(formato, modelo)));
        campo.setFocusLostBehavior(JFormattedTextField.COMMIT);
        // El primer Intro sobre un valor editado lo confirma (y consume la tecla): se pasa al botón por defecto.
        campo.addActionListener(e -> {
            JRootPane raiz = SwingUtilities.getRootPane(campo);
            JButton porDefecto = raiz != null ? raiz.getDefaultButton() : null;
            if (porDefecto != null && porDefecto.isEnabled()) {
                porDefecto.doClick();
            }
        });
        return selector;
    }

    /** Etiqueta de título de sección o tarjeta. */
    public static JLabel titulo(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(Tema.SUBTITULO);
        l.setForeground(Tema.TEXTO);
        return l;
    }

    /** Etiqueta de texto secundario (gris, tamaño pequeño). */
    public static JLabel secundaria(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(Tema.PEQUENA);
        l.setForeground(Tema.TEXTO_SECUNDARIO);
        return l;
    }

    /** Etiqueta para mensajes de error (roja, con icono de alerta, vacía al principio). */
    public static JLabel etiquetaError() {
        JLabel l = new JLabel(" ");
        l.setFont(Tema.PEQUENA.deriveFont(Font.BOLD));
        l.setForeground(Tema.ERROR);
        return l;
    }

    /** Muestra u oculta un mensaje en una etiqueta de error creada con {@link #etiquetaError()}. */
    public static void mostrarError(JLabel etiqueta, String mensaje) {
        if (mensaje == null || mensaje.isBlank()) {
            etiqueta.setText(" ");
            etiqueta.setIcon(null);
        } else {
            etiqueta.setText("<html>" + escaparHtml(mensaje) + "</html>");
            etiqueta.setIcon(Iconos.de(Iconos.Tipo.ALERTA, 14, Tema.ERROR));
        }
    }

    /** Panel de desplazamiento sin borde para tablas y listas dentro de tarjetas. */
    public static JScrollPane scroll(JComponent contenido) {
        JScrollPane sp = new JScrollPane(contenido);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(Tema.SUPERFICIE);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    /** Borde vacío con los márgenes indicados. */
    public static Border margen(int arriba, int izquierda, int abajo, int derecha) {
        return BorderFactory.createEmptyBorder(arriba, izquierda, abajo, derecha);
    }

    /** Etiqueta "chip" de texto con fondo suave, para indicar un estado fuera de las tablas. */
    public static JLabel chip(String texto, Color color) {
        JLabel l = new JLabel(texto, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Tema.suavizar(getForeground(), 0.86));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        l.setForeground(color);
        l.setFont(Tema.PEQUENA.deriveFont(Font.BOLD));
        l.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
        return l;
    }

    /** Escapa los caracteres especiales de HTML para mostrar texto de usuario dentro de etiquetas HTML. */
    public static String escaparHtml(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    /**
     * Formateador numérico que solo acepta el texto si se interpreta entero (sin restos sin leer) y dentro de
     * los límites del modelo del selector.
     */
    private static final class FormatoNumericoEstricto extends NumberFormatter {
        private final SpinnerNumberModel modelo;
        private final boolean entero;

        FormatoNumericoEstricto(DecimalFormat formato, SpinnerNumberModel modelo) {
            super(formato);
            this.modelo = modelo;
            this.entero = modelo.getValue() instanceof Integer || modelo.getValue() instanceof Long;
            formato.setParseIntegerOnly(entero);
            setValueClass(modelo.getValue().getClass());
        }

        @Override
        @SuppressWarnings("rawtypes")
        public Comparable getMinimum() {
            return modelo.getMinimum();
        }

        @Override
        @SuppressWarnings("rawtypes")
        public Comparable getMaximum() {
            return modelo.getMaximum();
        }

        @Override
        public Object stringToValue(String texto) throws ParseException {
            DecimalFormat formato = (DecimalFormat) getFormat();
            String limpio = conSeparadorDecimal(texto == null ? "" : texto.trim(), formato);
            ParsePosition posicion = new ParsePosition(0);
            if (limpio.isEmpty() || formato.parse(limpio, posicion) == null || posicion.getIndex() != limpio.length()) {
                throw new ParseException("No es un número válido: " + texto, posicion.getIndex());
            }
            char separador = formato.getDecimalFormatSymbols().getDecimalSeparator();
            int decimales = limpio.indexOf(separador) < 0 ? 0 : limpio.length() - limpio.indexOf(separador) - 1;
            if (decimales > formato.getMaximumFractionDigits()) {
                throw new ParseException("Demasiados decimales: " + texto, limpio.indexOf(separador));
            }
            return super.stringToValue(limpio);
        }

        /** Acepta también el otro separador decimal habitual (punto o coma) si es el único que aparece. */
        private String conSeparadorDecimal(String texto, DecimalFormat formato) {
            if (entero) {
                return texto;
            }
            char separador = formato.getDecimalFormatSymbols().getDecimalSeparator();
            char alternativo = separador == ',' ? '.' : ',';
            boolean soloAlternativo = texto.indexOf(separador) < 0
                    && texto.indexOf(alternativo) == texto.lastIndexOf(alternativo);
            return soloAlternativo ? texto.replace(alternativo, separador) : texto;
        }
    }

    /** Iniciales (máximo dos letras) de un nombre completo, para avatares. */
    public static String iniciales(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "?";
        }
        String[] partes = nombre.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        sb.append(Character.toUpperCase(partes[0].charAt(0)));
        if (partes.length > 1) {
            sb.append(Character.toUpperCase(partes[1].charAt(0)));
        }
        return sb.toString();
    }
}
