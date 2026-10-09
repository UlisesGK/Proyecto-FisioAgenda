package com.fisioagenda.db;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Conversión entre los tipos de fecha de Java y el texto ISO-8601 con el que se guardan en SQLite.
 * Al ser texto ISO, el orden alfabético coincide con el cronológico y las comparaciones en SQL funcionan.
 */
public final class FechasBD {

    private static final DateTimeFormatter FECHA_HORA_MINUTOS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private FechasBD() {
    }

    /** Fecha como {@code yyyy-MM-dd}, o {@code null}. */
    public static String texto(LocalDate fecha) {
        return fecha == null ? null : fecha.toString();
    }

    /** Fecha-hora con precisión de minutos ({@code yyyy-MM-dd'T'HH:mm}), formato de citas y notas. */
    public static String textoMinutos(LocalDateTime fechaHora) {
        return fechaHora == null ? null : FECHA_HORA_MINUTOS.format(fechaHora);
    }

    /**
     * Fecha-hora con precisión de segundos ({@code yyyy-MM-dd'T'HH:mm[:ss]}). Se usa para el bloqueo de
     * cuentas y como límite en consultas por intervalo; compara correctamente con los valores en minutos.
     */
    public static String textoPreciso(LocalDateTime fechaHora) {
        return fechaHora == null ? null : fechaHora.truncatedTo(ChronoUnit.SECONDS).toString();
    }

    /** Interpreta una fecha {@code yyyy-MM-dd}; devuelve {@code null} si el texto es nulo o vacío. */
    public static LocalDate fecha(String texto) {
        return texto == null || texto.isBlank() ? null : LocalDate.parse(texto);
    }

    /** Interpreta una fecha-hora ISO (con o sin segundos); devuelve {@code null} si el texto es nulo o vacío. */
    public static LocalDateTime fechaHora(String texto) {
        return texto == null || texto.isBlank() ? null : LocalDateTime.parse(texto);
    }
}
