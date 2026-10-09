package com.fisioagenda.util;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
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
    private static final Pattern INTERNACIONAL = Pattern.compile("\\+([1-9]\\d{0,2}) ([\\d ]+)");
    /** Separadores que se admiten al escribir un teléfono: espacios, puntos, guiones y paréntesis. */
    private static final String SEPARADORES = "[\\s.()-]";
    /**
     * Prefijos internacionales de dos cifras (UIT-T E.164). Los que empiezan por 1 o 7 tienen una cifra
     * y el resto, tres. Solo se usan cuando el prefijo se escribe pegado al número.
     */
    private static final Set<String> PREFIJOS_DOS_CIFRAS = Set.of(
            "20", "27", "30", "31", "32", "33", "34", "36", "39", "40", "41", "43", "44", "45", "46", "47", "48",
            "49", "51", "52", "53", "54", "55", "56", "57", "58", "60", "61", "62", "63", "64", "65", "66", "81",
            "82", "84", "86", "90", "91", "92", "93", "94", "95", "98");
    /** Longitud máxima de un número internacional sin el "+" (E.164). */
    private static final int MAX_CIFRAS_INTERNACIONAL = 15;
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

    /**
     * Normaliza un teléfono. Uno nacional queda solo con sus cifras ("600 12 34 56" → "600123456"); uno con
     * prefijo internacional ("+" o "00") queda como prefijo, un espacio y el número seguido, por desordenados
     * que estén los espacios ("+32 32 2  3 4 3 34 5" → "+32 322343345").
     *
     * @return el teléfono normalizado, o {@code null} si se recibe {@code null}
     */
    public static String normalizarTelefono(String telefono) {
        if (telefono == null) {
            return null;
        }
        String texto = telefono.trim();
        if (texto.startsWith("00")) {
            texto = "+" + texto.substring(2);
        }
        if (!texto.startsWith("+")) {
            return texto.replaceAll(SEPARADORES, "");
        }
        String resto = texto.substring(1).trim();
        String cifras = resto.replaceAll(SEPARADORES, "");
        if (!cifras.matches("\\d{2,}")) {
            return "+" + cifras;
        }
        // Si el prefijo va separado del número se respeta; si va pegado, se deduce por sus primeras cifras.
        String primerGrupo = resto.split(SEPARADORES + "+")[0];
        String prefijo = primerGrupo.matches("\\d{1,3}") && primerGrupo.length() < cifras.length()
                ? primerGrupo : cifras.substring(0, longitudPrefijo(cifras));
        return "+" + prefijo + " " + cifras.substring(prefijo.length());
    }

    private static int longitudPrefijo(String cifras) {
        if (cifras.charAt(0) == '1' || cifras.charAt(0) == '7') {
            return 1;
        }
        int longitud = PREFIJOS_DOS_CIFRAS.contains(cifras.substring(0, 2)) ? 2 : 3;
        return Math.min(longitud, cifras.length() - 1);
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

    /** Mensaje de ayuda con el formato de teléfono admitido. */
    public static final String MENSAJE_TELEFONO = "9 dígitos que empiecen por 6, 7, 8 o 9, o con prefijo (+32 470123456)";

    /**
     * Comprueba un teléfono: español de 9 dígitos que empiece por 6, 7, 8 o 9 (también con +34), o
     * internacional con prefijo y entre 4 y 15 cifras en total. Se ignoran los espacios y separadores.
     */
    public static boolean esTelefonoValido(String telefono) {
        String valor = normalizarTelefono(telefono);
        if (valor == null) {
            return false;
        }
        if (!valor.startsWith("+")) {
            return TELEFONO.matcher(valor).matches();
        }
        Matcher m = INTERNACIONAL.matcher(valor);
        if (!m.matches()) {
            return false;
        }
        String prefijo = m.group(1);
        String numero = m.group(2).replace(" ", "");
        if (prefijo.equals("34")) {
            return TELEFONO.matcher(numero).matches();
        }
        return numero.length() >= 4 && prefijo.length() + numero.length() <= MAX_CIFRAS_INTERNACIONAL;
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
