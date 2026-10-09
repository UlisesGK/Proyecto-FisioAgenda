package com.fisioagenda.security;

import com.fisioagenda.db.Database;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Guarda en este equipo el token de "Recordar este dispositivo", en un fichero junto a la base de datos.
 * Si el fichero no se puede leer o escribir, simplemente se vuelve a pedir la contraseña.
 */
public final class DispositivoLocal {

    private static final Logger LOG = Logger.getLogger(DispositivoLocal.class.getName());
    private static final String FICHERO = "dispositivo.token";

    private DispositivoLocal() {
    }

    private static Path ruta() {
        Path bd = Paths.get(Database.getInstance().getRuta()).toAbsolutePath();
        return bd.resolveSibling(FICHERO);
    }

    /** Token guardado, si lo hay. */
    public static Optional<String> leer() {
        try {
            Path ruta = ruta();
            if (!Files.isRegularFile(ruta)) {
                return Optional.empty();
            }
            String token = Files.readString(ruta, StandardCharsets.UTF_8).trim();
            return token.isEmpty() ? Optional.empty() : Optional.of(token);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "No se pudo leer el dispositivo recordado", e);
            return Optional.empty();
        }
    }

    /** Guarda el token, legible solo por el usuario del sistema cuando el sistema de ficheros lo permite. */
    public static void guardar(String token) {
        try {
            Path ruta = ruta();
            Files.writeString(ruta, token, StandardCharsets.UTF_8);
            try {
                Files.setPosixFilePermissions(ruta, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException e) {
                // Windows: el fichero ya queda dentro de la carpeta personal del usuario.
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "No se pudo recordar el dispositivo", e);
        }
    }

    /** Borra el token guardado. */
    public static void borrar() {
        try {
            Files.deleteIfExists(ruta());
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "No se pudo olvidar el dispositivo", e);
        }
    }
}
