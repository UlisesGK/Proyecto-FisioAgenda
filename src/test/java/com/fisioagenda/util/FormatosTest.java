package com.fisioagenda.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormatosTest {

    @Test
    void formateaFechasYHoras() {
        LocalDateTime momento = LocalDateTime.of(2026, 3, 9, 8, 5);
        assertEquals("09/03/2026", Formatos.fecha(momento.toLocalDate()));
        assertEquals("08:05", Formatos.hora(momento));
        assertEquals("17:30", Formatos.hora(LocalTime.of(17, 30)));
        assertEquals("09/03/2026 08:05", Formatos.fechaHora(momento));
        assertEquals("", Formatos.fecha(null));
    }

    @Test
    void formateaImportesEnEuros() {
        String texto = Formatos.moneda(1234.5).replace(' ', ' ').replace(' ', ' ');
        assertTrue(texto.contains("1234,50") || texto.contains("1.234,50"), texto);
        assertTrue(texto.endsWith("€"), texto);
    }

    @Test
    void parseFechaAdmiteFormatoEspanol() {
        assertEquals(Optional.of(LocalDate.of(2026, 10, 8)), Formatos.parseFecha("08/10/2026"));
        assertEquals(Optional.of(LocalDate.of(2026, 10, 8)), Formatos.parseFecha(" 8/10/2026 "));
    }

    @ParameterizedTest(name = "fecha no válida: \"{0}\"")
    @ValueSource(strings = {"", "31/02/2026", "2026-10-08", "8 de octubre", "32/01/2026", "10/13/2026"})
    void parseFechaRechazaTextosNoValidos(String texto) {
        assertTrue(Formatos.parseFecha(texto).isEmpty());
    }
}
