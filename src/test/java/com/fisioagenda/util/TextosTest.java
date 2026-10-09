package com.fisioagenda.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas de la normalización de textos usada por todas las búsquedas. */
class TextosTest {

    @Test
    void normalizarQuitaTildesDieresisYMayusculas() {
        assertEquals("alvaro nunez pinguino", Textos.normalizar("  ÁLVARO Núñez Pingüino "));
        assertEquals("", Textos.normalizar(null));
        assertEquals("", Textos.normalizar("   "));
    }

    @ParameterizedTest(name = "\"{1}\" en \"{0}\" -> {2}")
    @CsvSource({
            "'Álvaro Núñez Ortiz 12345678Z', alvaro, true",
            "'Alvaro Nunez Ortiz 12345678Z', álvaro, true",
            "'Álvaro Núñez Ortiz 12345678Z', NÚÑEZ, true",
            "'Álvaro Núñez Ortiz 12345678Z', 'nunez   alv', true",
            "'Álvaro Núñez Ortiz 12345678Z', 'ortiz 5678z', true",
            "'Álvaro Núñez Ortiz 12345678Z', 'álvaro garcía', false",
            "'Álvaro Núñez Ortiz 12345678Z', '%', false",
            "'Álvaro Núñez Ortiz 12345678Z', '', true",
            "'', alvaro, false"
    })
    void contienePalabrasEnCualquierOrdenSinTildes(String contenido, String busqueda, boolean esperado) {
        assertEquals(esperado, Textos.contienePalabras(contenido, busqueda));
    }

    @Test
    void contienePalabrasAdmiteNulos() {
        assertTrue(Textos.contienePalabras("Lucía", null));
        assertFalse(Textos.contienePalabras(null, "lucia"));
    }
}
