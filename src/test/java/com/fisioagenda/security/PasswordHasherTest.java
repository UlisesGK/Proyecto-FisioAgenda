package com.fisioagenda.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    private static final char[] PASSWORD = "Secreta123!".toCharArray();

    @Test
    void laSalTiene16BytesAleatorios() {
        String sal = PasswordHasher.generarSal();
        assertEquals(16, Base64.getDecoder().decode(sal).length);
        assertNotEquals(sal, PasswordHasher.generarSal());
    }

    @Test
    void elHashEsDe256BitsYDeterministaConLaMismaSal() {
        String sal = PasswordHasher.generarSal();
        String hash = PasswordHasher.hash(PASSWORD, sal);
        assertEquals(32, Base64.getDecoder().decode(hash).length);
        assertEquals(hash, PasswordHasher.hash(PASSWORD, sal));
    }

    @Test
    void salesDistintasProducenHashesDistintos() {
        String hash1 = PasswordHasher.hash(PASSWORD, PasswordHasher.generarSal());
        String hash2 = PasswordHasher.hash(PASSWORD, PasswordHasher.generarSal());
        assertNotEquals(hash1, hash2);
    }

    @Test
    void verificarAceptaLaContrasenaCorrecta() {
        String sal = PasswordHasher.generarSal();
        String hash = PasswordHasher.hash(PASSWORD, sal);
        assertTrue(PasswordHasher.verificar("Secreta123!".toCharArray(), sal, hash));
    }

    @Test
    void verificarRechazaUnaContrasenaIncorrecta() {
        String sal = PasswordHasher.generarSal();
        String hash = PasswordHasher.hash(PASSWORD, sal);
        assertFalse(PasswordHasher.verificar("secreta123!".toCharArray(), sal, hash));
        assertFalse(PasswordHasher.verificar("Secreta123!".toCharArray(), PasswordHasher.generarSal(), hash));
    }

    @Test
    void verificarDevuelveFalsoConDatosNulosOCorruptos() {
        String sal = PasswordHasher.generarSal();
        assertFalse(PasswordHasher.verificar(null, sal, "abc"));
        assertFalse(PasswordHasher.verificar(PASSWORD, sal, null));
        assertFalse(PasswordHasher.verificar(PASSWORD, sal, "esto no es base64 ***"));
    }

    @Test
    void hashNoModificaElArrayRecibido() {
        char[] password = "Secreta123!".toCharArray();
        PasswordHasher.hash(password, PasswordHasher.generarSal());
        assertArrayEquals("Secreta123!".toCharArray(), password);
    }
}
