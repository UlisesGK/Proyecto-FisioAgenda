package com.fisioagenda.util;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Optional;

/**
 * Formatos de presentación comunes (fechas, horas e importes) con convenciones españolas.
 * Los métodos aceptan {@code null} y devuelven entonces una cadena vacía.
 */
public final class Formatos {

    /** Configuración regional de la aplicación (español de España). */
    public static final Locale LOCALE = Locale.forLanguageTag("es-ES");
    /** Fecha corta: 08/10/2026. */
    public static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy", LOCALE);
    /** Hora: 09:30. */
    public static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm", LOCALE);
    /** Fecha y hora: 08/10/2026 09:30. */
    public static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", LOCALE);
    /**
     * Formato de moneda en euros (35,00 €). {@link NumberFormat} no es seguro entre hilos:
     * desde código concurrente usar {@link #moneda(double)}, que sincroniza el acceso.
     */
    public static final NumberFormat MONEDA = NumberFormat.getCurrencyInstance(LOCALE);

    /** Formato de lectura estricto: admite 8/10/2026 y 08/10/2026 y rechaza fechas imposibles (31/02). */
    private static final DateTimeFormatter LECTURA_FECHA =
            DateTimeFormatter.ofPattern("d/M/uuuu", LOCALE).withResolverStyle(ResolverStyle.STRICT);

    private Formatos() {
    }

    /** Formatea una fecha como dd/MM/yyyy. */
    public static String fecha(LocalDate fecha) {
        return fecha == null ? "" : FECHA.format(fecha);
    }

    /** Formatea la hora (HH:mm). */
    public static String hora(LocalTime hora) {
        return hora == null ? "" : HORA.format(hora);
    }

    /** Formatea la hora (HH:mm) de una fecha-hora. */
    public static String hora(LocalDateTime fechaHora) {
        return fechaHora == null ? "" : HORA.format(fechaHora);
    }

    /** Formatea una fecha-hora como dd/MM/yyyy HH:mm. */
    public static String fechaHora(LocalDateTime fechaHora) {
        return fechaHora == null ? "" : FECHA_HORA.format(fechaHora);
    }

    /** Formatea un importe en euros con dos decimales (p. ej. "35,00 €"). */
    public static String moneda(double importe) {
        synchronized (MONEDA) {
            return MONEDA.format(importe);
        }
    }

    /**
     * Interpreta una fecha escrita como dd/MM/yyyy.
     *
     * @return la fecha, o vacío si el texto es nulo, está vacío o no es una fecha válida
     */
    public static Optional<LocalDate> parseFecha(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(texto.trim(), LECTURA_FECHA));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
