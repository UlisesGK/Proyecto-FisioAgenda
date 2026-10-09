package com.fisioagenda.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cálculo y verificación de hashes de contraseña con PBKDF2WithHmacSHA256.
 * <p>
 * Cada contraseña se combina con una sal aleatoria de 16 bytes y se derivan 256 bits tras
 * 600 000 iteraciones (valor recomendado por OWASP para PBKDF2-HMAC-SHA256). Tanto la sal como el hash se guardan codificados en Base64.
 * La clase no modifica nunca el {@code char[]} recibido: limpiarlo es responsabilidad de quien lo creó.
 */
public final class PasswordHasher {

    /** Número de iteraciones de PBKDF2 (coste de cálculo deliberadamente alto). */
    public static final int ITERACIONES = 600_000;
    /** Longitud de la sal en bytes. */
    public static final int LONGITUD_SAL_BYTES = 16;
    /** Longitud de la clave derivada en bits. */
    public static final int LONGITUD_CLAVE_BITS = 256;

    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private PasswordHasher() {
    }

    /**
     * Genera una sal criptográficamente aleatoria.
     *
     * @return 16 bytes aleatorios codificados en Base64
     */
    public static String generarSal() {
        byte[] sal = new byte[LONGITUD_SAL_BYTES];
        ALEATORIO.nextBytes(sal);
        return Base64.getEncoder().encodeToString(sal);
    }

    /**
     * Calcula el hash PBKDF2 de una contraseña con la sal indicada.
     *
     * @param password contraseña en claro (no se modifica)
     * @param sal      sal en Base64 obtenida con {@link #generarSal()}
     * @return hash de 256 bits codificado en Base64
     */
    public static String hash(char[] password, String sal) {
        return Base64.getEncoder().encodeToString(derivar(password, sal));
    }

    /**
     * Comprueba si una contraseña corresponde al hash guardado, comparando en tiempo constante.
     *
     * @param password     contraseña en claro introducida por el usuario
     * @param sal          sal en Base64 con la que se calculó el hash guardado
     * @param hashEsperado hash guardado en Base64
     * @return {@code true} si la contraseña es correcta
     */
    public static boolean verificar(char[] password, String sal, String hashEsperado) {
        if (password == null || sal == null || hashEsperado == null) {
            return false;
        }
        try {
            byte[] esperado = Base64.getDecoder().decode(hashEsperado);
            byte[] calculado = derivar(password, sal);
            return MessageDigest.isEqual(esperado, calculado);
        } catch (IllegalArgumentException e) {
            // Sal o hash guardados con un formato no válido: se trata como contraseña incorrecta.
            return false;
        }
    }

    private static byte[] derivar(char[] password, String sal) {
        if (password == null || sal == null) {
            throw new IllegalArgumentException("La contraseña y la sal son obligatorias");
        }
        PBEKeySpec spec = new PBEKeySpec(password, Base64.getDecoder().decode(sal), ITERACIONES, LONGITUD_CLAVE_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("El algoritmo " + ALGORITMO + " no está disponible", e);
        } finally {
            spec.clearPassword();
        }
    }
}
