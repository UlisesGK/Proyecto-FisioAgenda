package com.fisioagenda.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Comparación de textos para las búsquedas: sin distinguir mayúsculas ni tildes ("Álvaro Núñez" se
 * encuentra escribiendo "alvaro nunez"). La usan tanto el servicio de pacientes como los filtros de la
 * interfaz, para que todas las búsquedas de la aplicación se comporten igual.
 */
public final class Textos {

    private Textos() {
    }

    /** Recorta, pasa a minúsculas y elimina tildes y diéresis; {@code null} se trata como texto vacío. */
    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT);
    }

    /**
     * Indica si un contenido incluye todas las palabras buscadas, en cualquier orden y sin distinguir
     * mayúsculas ni tildes. Una búsqueda vacía coincide con cualquier contenido.
     *
     * @param contenido texto en el que se busca (por ejemplo, nombre, DNI y teléfono juntos)
     * @param busqueda  texto escrito por el usuario
     */
    public static boolean contienePalabras(String contenido, String busqueda) {
        String buscado = normalizar(busqueda);
        if (buscado.isEmpty()) {
            return true;
        }
        String normalizado = normalizar(contenido);
        for (String palabra : buscado.split("\\s+")) {
            if (!normalizado.contains(palabra)) {
                return false;
            }
        }
        return true;
    }
}
