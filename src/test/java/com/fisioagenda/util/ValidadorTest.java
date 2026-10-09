package com.fisioagenda.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidadorTest {

    @ParameterizedTest(name = "DNI/NIE válido: {0}")
    @ValueSource(strings = {"12345678Z", "00000000T", "48291537R", "87654321X", "12345678-z", " 11111111 H ",
                            "X1234567L", "Y1234567X", "Z1234567R", "x4827391j"})
    void dniNieValidos(String documento) {
        assertTrue(Validador.esDniNieValido(documento));
    }

    @ParameterizedTest(name = "DNI/NIE no válido: \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"12345678A", "1234567Z", "123456789Z", "ABCDEFGHZ", "12345678", "X1234567A",
                            "W1234567L", "X12345678L", "Z1234567", "12345678Ñ"})
    void dniNieNoValidos(String documento) {
        assertFalse(Validador.esDniNieValido(documento));
    }

    @Test
    void normalizarDniQuitaGuionesYEspaciosYPasaAMayusculas() {
        assertEquals("12345678Z", Validador.normalizarDni(" 12345678-z "));
    }

    @ParameterizedTest(name = "email válido: {0}")
    @ValueSource(strings = {"ana@example.com", "ana.lopez+citas@clinica.example.es", "a_b-c@sub.dominio.org"})
    void emailsValidos(String email) {
        assertTrue(Validador.esEmailValido(email));
    }

    @ParameterizedTest(name = "email no válido: \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"ana", "ana@", "@example.com", "ana@example", "ana lopez@example.com", "ana@@example.com"})
    void emailsNoValidos(String email) {
        assertFalse(Validador.esEmailValido(email));
    }

    @ParameterizedTest(name = "teléfono válido: {0}")
    @ValueSource(strings = {"612345678", "712345678", "812345678", "912345678", "600 12 34 56",
        "+34612345678", "0034 612 345 678", "+32 32 2  3   4 3   34  5", "+1 (212) 555-0198", "+44 7911 123456"})
    void telefonosValidos(String telefono) {
        assertTrue(Validador.esTelefonoValido(telefono));
    }

    @ParameterizedTest(name = "teléfono no válido: \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"512345678", "61234567", "6123456789", "61234567a", "+34 512345678", "+",
        "+0 612345678", "+32 12a456", "+44 12", "+49 1234567890123456"})
    void telefonosNoValidos(String telefono) {
        assertFalse(Validador.esTelefonoValido(telefono));
    }

    @ParameterizedTest(name = "contraseña segura: {0}")
    @ValueSource(strings = {"Admin1234!", "Fisio1234!", "aB3$efgh", "Contraseña_2026"})
    void passwordsSeguras(String password) {
        assertTrue(Validador.esPasswordSegura(password.toCharArray()));
    }

    @ParameterizedTest(name = "contraseña débil: {0}")
    @ValueSource(strings = {"Ab1!", "admin1234!", "ADMIN1234!", "Adminxxxx!", "Admin12345", "Admin 1234"})
    void passwordsDebiles(String password) {
        assertFalse(Validador.esPasswordSegura(password.toCharArray()));
    }

    @Test
    void esPasswordSeguraNoModificaElArrayYRechazaNull() {
        char[] password = "Admin1234!".toCharArray();
        Validador.esPasswordSegura(password);
        assertArrayEquals("Admin1234!".toCharArray(), password);
        assertFalse(Validador.esPasswordSegura(null));
    }

    @ParameterizedTest(name = "username \"{0}\" -> {1}")
    @CsvSource({
        "admin, true", "fisio2, true", "ana.torres, true", "a_b, true", "abcdefghijklmnopqrst, true",
        "ab, false", "abcdefghijklmnopqrstu, false", "Admin, false", "ana torres, false", "ana-torres, false"
    })
    void usernames(String username, boolean esperado) {
        assertEquals(esperado, Validador.esUsernameValido(username));
    }

    @Test
    void noVacio() {
        assertTrue(Validador.noVacio("x"));
        assertFalse(Validador.noVacio("   "));
        assertFalse(Validador.noVacio(null));
    }

    @ParameterizedTest(name = "\"{0}\" se normaliza a \"{1}\"")
    @org.junit.jupiter.params.provider.CsvSource({
        "'600 12 34 56', 600123456",
        "'+32 32 2  3   4 3   34  5', '+32 322343345'",
        "'+34645230920', '+34 645230920'",
        "'0044 7911 123456', '+44 7911123456'",
        "'+1 (212) 555-0198', '+1 2125550198'",
        "'+353861234567', '+353 861234567'"
    })
    void normalizaTelefonos(String entrada, String esperado) {
        assertEquals(esperado, Validador.normalizarTelefono(entrada));
    }
}
