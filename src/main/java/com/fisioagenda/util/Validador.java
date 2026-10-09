package com.fisioagenda.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validaciones de formato reutilizables por la interfaz (validación en el formulario)
 * y por los servicios (validación definitiva antes de guardar).
 */
public final class Validador {

    /** Mensaje que describe la política de contraseñas seguras. */
    public static final String MENSAJE_PASSWORD =
            "La contraseña debe tener al menos 8 caracteres e incluir mayúsculas, minúsculas, números y algún símbolo.";

    /** Letras de control del DNI/NIE, indexadas por el resto de dividir el número entre 23. */
    private static final String LETRAS_DNI = "TRWAGMYFPDXBNJZSQVHLCKE";
    private static final Pattern DNI = Pattern.compile("\\d{8}[A-Z]");
    private static final Pattern NIE = Pattern.compile("[XYZ]\\d{7}[A-Z]");
    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");
    private static final Pattern TELEFONO = Pattern.compile("[6789]\\d{8}");
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._]{3,20}");
    private static final int LONGITUD_MINIMA_PASSWORD = 8;

    private Validador() {
    }

    /**
     * Normaliza un DNI/NIE: elimina espacios y guiones y pasa a mayúsculas ("12345678-z" → "12345678Z").
     *
     * @return el documento normalizado, o {@code null} si se recibe {@code null}
     */
    public static String normalizarDni(String dni) {
        return dni == null ? null : dni.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    /** Elimina los espacios de un teléfono ("600 12 34 56" → "600123456"). */
    public static String normalizarTelefono(String telefono) {
        return telefono == null ? null : telefono.replaceAll("\\s", "");
    }

    /**
     * Comprueba un DNI (8 dígitos + letra) o NIE (X/Y/Z + 7 dígitos + letra) con el algoritmo
     * oficial del módulo 23. Se toleran minúsculas, espacios y guiones.
     */
    public static boolean esDniNieValido(String documento) {
        String valor = normalizarDni(documento);
        if (valor == null) {
            return false;
        }
        String numero;
        if (DNI.matcher(valor).matches()) {
            numero = valor.substring(0, 8);
        } else if (NIE.matcher(valor).matches()) {
            numero = "XYZ".indexOf(valor.charAt(0)) + valor.substring(1, 8);
        } else {
            return false;
        }
        char letraEsperada = LETRAS_DNI.charAt(Integer.parseInt(numero) % 23);
        return valor.charAt(valor.length() - 1) == letraEsperada;
    }

    /** Comprueba el formato básico de una dirección de email (usuario@dominio.tld). */
    public static boolean esEmailValido(String email) {
        return email != null && email.length() <= 254 && EMAIL.matcher(email.trim()).matches();
    }

    /** Comprueba un teléfono español de 9 dígitos que empiece por 6, 7, 8 o 9 (se ignoran los espacios). */
    public static boolean esTelefonoValido(String telefono) {
        String valor = normalizarTelefono(telefono);
        return valor != null && TELEFONO.matcher(valor).matches();
    }

    /**
     * Comprueba la política de contraseñas: al menos 8 caracteres, una mayúscula, una minúscula,
     * un dígito y un símbolo. No modifica el array recibido.
     */
    public static boolean esPasswordSegura(char[] password) {
        if (password == null || password.length < LONGITUD_MINIMA_PASSWORD) {
            return false;
        }
        boolean mayuscula = false;
        boolean minuscula = false;
        boolean digito = false;
        boolean simbolo = false;
        for (char c : password) {
            if (Character.isUpperCase(c)) {
                mayuscula = true;
            } else if (Character.isLowerCase(c)) {
                minuscula = true;
            } else if (Character.isDigit(c)) {
                digito = true;
            } else if (!Character.isWhitespace(c)) {
                simbolo = true;
            }
        }
        return mayuscula && minuscula && digito && simbolo;
    }

    /** Comprueba un nombre de usuario: de 3 a 20 caracteres entre minúsculas, dígitos, punto y guion bajo. */
    public static boolean esUsernameValido(String username) {
        return username != null && USERNAME.matcher(username).matches();
    }

    /** Indica si el texto contiene algún carácter distinto de espacio en blanco. */
    public static boolean noVacio(String texto) {
        return texto != null && !texto.isBlank();
    }
}
