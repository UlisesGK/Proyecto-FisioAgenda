package com.fisioagenda.ui;

import com.fisioagenda.model.EstadoCita;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Paleta de colores y tipografías de la aplicación. Centraliza el aspecto visual para que
 * todas las pantallas compartan la misma identidad (verde azulado sanitario).
 */
public final class Tema {

    /** Color de acento principal (#0F766E). */
    public static final Color PRIMARIO = new Color(0x0F766E);
    /** Variante oscura del acento, para degradados y estados pulsados. */
    public static final Color PRIMARIO_OSCURO = new Color(0x115E59);
    /** Variante muy clara del acento, para fondos suaves. */
    public static final Color PRIMARIO_CLARO = new Color(0xCCFBF1);
    /** Fondo general de las pantallas. */
    public static final Color FONDO = new Color(0xF4F7F8);
    /** Fondo de las tarjetas y superficies elevadas. */
    public static final Color SUPERFICIE = Color.WHITE;
    /** Fondo de la barra lateral. */
    public static final Color SIDEBAR = new Color(0x0B3B3A);
    /** Texto de la barra lateral en reposo. */
    public static final Color SIDEBAR_TEXTO = new Color(0xC7DEDC);
    /** Texto principal. */
    public static final Color TEXTO = new Color(0x1E293B);
    /** Texto secundario (contraste AA sobre blanco). */
    public static final Color TEXTO_SECUNDARIO = new Color(0x5B6B7F);
    /** Bordes y separadores suaves. */
    public static final Color BORDE = new Color(0xE2E8F0);
    /** Color de éxito (confirmaciones, citas realizadas). */
    public static final Color EXITO = new Color(0x15803D);
    /** Color de aviso. */
    public static final Color AVISO = new Color(0xB45309);
    /** Color de error (validaciones, acciones destructivas). */
    public static final Color ERROR = new Color(0xB91C1C);
    /** Color informativo. */
    public static final Color INFO = new Color(0x1D4ED8);

    /** Color de una cita programada. */
    public static final Color ESTADO_PROGRAMADA = new Color(0x1D4ED8);
    /** Color de una cita realizada. */
    public static final Color ESTADO_REALIZADA = new Color(0x15803D);
    /** Color de una cita cancelada. */
    public static final Color ESTADO_CANCELADA = new Color(0x475569);
    /** Color de una cita a la que el paciente no asistió. */
    public static final Color ESTADO_NO_ASISTIDA = new Color(0xB91C1C);

    /** Familia tipográfica base (Segoe UI si está instalada, si no Dialog). */
    public static final String FAMILIA = elegirFamilia();

    /** Títulos de sección (22 negrita). */
    public static final Font TITULO = new Font(FAMILIA, Font.BOLD, 22);
    /** Subtítulos y cabeceras de tarjeta (15 negrita). */
    public static final Font SUBTITULO = new Font(FAMILIA, Font.BOLD, 15);
    /** Texto normal (13). */
    public static final Font NORMAL = new Font(FAMILIA, Font.PLAIN, 13);
    /** Texto normal en negrita (13). */
    public static final Font NORMAL_NEGRITA = new Font(FAMILIA, Font.BOLD, 13);
    /** Texto pequeño para ayudas y pies (12). */
    public static final Font PEQUENA = new Font(FAMILIA, Font.PLAIN, 12);
    /** Cifras destacadas de las tarjetas de estadística (28 negrita). */
    public static final Font CIFRA = new Font(FAMILIA, Font.BOLD, 28);

    private Tema() {
    }

    private static String elegirFamilia() {
        try {
            Set<String> disponibles = new HashSet<>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            if (disponibles.contains("Segoe UI")) {
                return "Segoe UI";
            }
        } catch (RuntimeException ignorada) {
            // Entorno sin fuentes accesibles: se usa la lógica por defecto.
        }
        return Font.DIALOG;
    }

    /** Color principal asociado a un estado de cita (texto del chip). */
    public static Color colorEstado(EstadoCita estado) {
        if (estado == null) {
            return TEXTO_SECUNDARIO;
        }
        switch (estado) {
            case REALIZADA:
                return ESTADO_REALIZADA;
            case CANCELADA:
                return ESTADO_CANCELADA;
            case NO_ASISTIDA:
                return ESTADO_NO_ASISTIDA;
            case PROGRAMADA:
            default:
                return ESTADO_PROGRAMADA;
        }
    }

    /** Color de fondo suave asociado a un estado de cita (relleno del chip). */
    public static Color fondoEstado(EstadoCita estado) {
        return suavizar(colorEstado(estado), 0.86);
    }

    /**
     * Color según la escala visual analógica de dolor (EVA 0-10): verde para dolor leve,
     * ámbar para moderado y rojo para intenso.
     */
    public static Color colorDolor(int eva) {
        int v = Math.max(0, Math.min(10, eva));
        if (v <= 3) {
            return EXITO;
        }
        if (v <= 6) {
            return AVISO;
        }
        return ERROR;
    }

    /** Descripción textual de un valor EVA. */
    public static String descripcionDolor(int eva) {
        if (eva == 0) {
            return "Sin dolor";
        }
        if (eva <= 3) {
            return "Leve";
        }
        if (eva <= 6) {
            return "Moderado";
        }
        if (eva <= 9) {
            return "Intenso";
        }
        return "Insoportable";
    }

    /** Mezcla un color con blanco: {@code cantidad} 0 devuelve el color y 1 devuelve blanco. */
    public static Color suavizar(Color color, double cantidad) {
        return mezclar(color, Color.WHITE, cantidad);
    }

    /** Interpolación lineal entre dos colores ({@code t} entre 0 y 1). */
    public static Color mezclar(Color a, Color b, double t) {
        double p = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * p);
        int g = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * p);
        int bl = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * p);
        int al = (int) Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * p);
        return new Color(r, g, bl, al);
    }

    /** Devuelve el color con la opacidad indicada (0-255). */
    public static Color conAlfa(Color color, int alfa) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alfa)));
    }

    /** Representación hexadecimal (#RRGGBB) para usar en estilos de FlatLaf. */
    public static String hex(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }
}
