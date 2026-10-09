package com.fisioagenda.service;

import com.fisioagenda.dao.UsuarioDAO;
import com.fisioagenda.model.Usuario;
import com.fisioagenda.security.PasswordHasher;
import com.fisioagenda.security.Sesion;
import com.fisioagenda.util.Validador;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Autenticación de usuarios: inicio y cierre de sesión, bloqueo temporal por intentos fallidos
 * y cambio de la contraseña propia.
 * <p>
 * Tras {@value #MAX_INTENTOS} intentos fallidos seguidos la cuenta queda bloqueada
 * {@value #MINUTOS_BLOQUEO} minutos. Para no revelar si un usuario existe, los mensajes de credenciales son
 * genéricos y un nombre inexistente recibe las mismas respuestas que uno real (también el aviso de bloqueo,
 * gracias a un contador en memoria). Solo quien acierta la contraseña de una cuenta desactivada recibe un
 * mensaje específico. Las contraseñas recibidas se borran de memoria al terminar.
 */
public class AuthService {

    /** Intentos fallidos seguidos que provocan el bloqueo. */
    public static final int MAX_INTENTOS = 5;
    /** Duración del bloqueo, en minutos. */
    public static final int MINUTOS_BLOQUEO = 5;
    /** Mensaje genérico de credenciales incorrectas. */
    public static final String MENSAJE_CREDENCIALES = "Usuario o contraseña incorrectos.";
    /** Días que un dispositivo queda recordado. */
    public static final int DIAS_RECORDAR = 30;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    /** Sal fija para calcular un hash de relleno cuando el usuario no existe (iguala los tiempos de respuesta). */
    private static final String SAL_RELLENO = PasswordHasher.generarSal();
    /** Nombres inexistentes cuyos intentos fallidos se recuerdan, como máximo (evita crecer sin límite). */
    private static final int MAX_NOMBRES_INEXISTENTES = 1000;

    private final UsuarioDAO usuarioDAO;
    private final Clock reloj;
    /** Intentos fallidos de nombres de usuario que no existen, por nombre (el más antiguo se descarta primero). */
    private final Map<String, Usuario> intentosInexistentes = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Usuario> masAntiguo) {
            return size() > MAX_NOMBRES_INEXISTENTES;
        }
    };

    /**
     * Crea el servicio de autenticación.
     *
     * @param usuarioDAO acceso a los usuarios
     * @param reloj      reloj del que se obtiene la hora actual (fijo en las pruebas)
     */
    public AuthService(UsuarioDAO usuarioDAO, Clock reloj) {
        this.usuarioDAO = usuarioDAO;
        this.reloj = reloj;
    }

    /**
     * Comprueba las credenciales e inicia la {@link Sesion}.
     *
     * @param username nombre de usuario (no distingue mayúsculas)
     * @param password contraseña; el array se borra al terminar
     * @return el usuario autenticado (sin hash ni sal)
     * @throws ServiceException si las credenciales son incorrectas, la cuenta está bloqueada o desactivada
     */
    public Usuario login(String username, char[] password) {
        try {
            if (!Validador.noVacio(username) || password == null || password.length == 0) {
                throw new ServiceException("Introduzca el usuario y la contraseña.");
            }
            LocalDateTime ahora = LocalDateTime.now(reloj);
            String nombre = username.trim().toLowerCase(Locale.ROOT);
            Optional<Usuario> encontrado = usuarioDAO.buscarPorUsername(nombre);
            // Un nombre que no existe lleva su propio contador en memoria y recibe exactamente las mismas
            // respuestas que uno real, bloqueo incluido: comparando mensajes no se averigua qué usuarios existen.
            Usuario usuario = encontrado.orElseGet(() -> registroDeInexistente(nombre));
            if (usuario.estaBloqueado(ahora)) {
                throw new ServiceException(mensajeBloqueo(usuario, ahora));
            }
            boolean correcta;
            if (encontrado.isPresent()) {
                correcta = PasswordHasher.verificar(password, usuario.getSalt(), usuario.getPasswordHash());
            } else {
                PasswordHasher.hash(password, SAL_RELLENO);
                correcta = false;
            }
            if (!correcta) {
                registrarFallo(usuario, ahora);
                if (usuario.estaBloqueado(ahora)) {
                    throw new ServiceException("Ha superado el número máximo de intentos. La cuenta queda bloqueada durante "
                            + MINUTOS_BLOQUEO + " minutos.");
                }
                throw new ServiceException(MENSAJE_CREDENCIALES);
            }
            if (!usuario.isActivo()) {
                throw new ServiceException("La cuenta está desactivada. Contacte con el administrador.");
            }
            if (usuario.getIntentosFallidos() != 0 || usuario.getBloqueadoHasta() != null) {
                usuario.setIntentosFallidos(0);
                usuario.setBloqueadoHasta(null);
                usuarioDAO.actualizarControlAcceso(usuario);
            }
            sinCredenciales(usuario);
            Sesion.getInstance().iniciar(usuario);
            return usuario;
        } finally {
            limpiar(password);
        }
    }

    /**
     * Recuerda este dispositivo para el usuario de la sesión durante {@value #DIAS_RECORDAR} días.
     * En la base de datos solo se guarda el hash SHA-256 del token, nunca el token.
     *
     * @return el token que el dispositivo debe guardar para entrar sin contraseña
     */
    public String recordarDispositivo() {
        Usuario usuario = Permisos.exigirSesion();
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime ahora = LocalDateTime.now(reloj);
        usuarioDAO.guardarTokenDispositivo(usuario.getId(), hashToken(token), ahora, ahora.plusDays(DIAS_RECORDAR));
        return token;
    }

    /**
     * Inicia la sesión con el token de un dispositivo recordado. Un token desconocido, caducado o de una
     * cuenta desactivada o bloqueada no sirve y se borra.
     *
     * @return el usuario, o vacío si hay que pedir usuario y contraseña
     */
    public Optional<Usuario> loginConToken(String token) {
        if (!Validador.noVacio(token)) {
            return Optional.empty();
        }
        String hash = hashToken(token);
        LocalDateTime ahora = LocalDateTime.now(reloj);
        Optional<Usuario> usuario = usuarioDAO.buscarUsuarioPorToken(hash, ahora)
                .flatMap(usuarioDAO::buscarPorId)
                .filter(u -> u.isActivo() && !u.estaBloqueado(ahora));
        if (usuario.isEmpty()) {
            usuarioDAO.borrarTokenDispositivo(hash);
            return Optional.empty();
        }
        sinCredenciales(usuario.get());
        Sesion.getInstance().iniciar(usuario.get());
        return usuario;
    }

    /** Olvida un dispositivo recordado (al cerrar sesión de forma explícita). */
    public void olvidarDispositivo(String token) {
        if (Validador.noVacio(token)) {
            usuarioDAO.borrarTokenDispositivo(hashToken(token));
        }
    }

    static String hashToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /** Cierra la sesión actual. */
    public void logout() {
        Sesion.getInstance().cerrar();
    }

    /**
     * Cambia la contraseña del usuario de la sesión. Los tres arrays se borran al terminar.
     *
     * @throws ServiceException si la contraseña actual no es correcta, las nuevas no coinciden
     *                          o no cumplen la política de seguridad
     */
    public void cambiarPassword(char[] actual, char[] nueva, char[] repetida) {
        try {
            Usuario sesion = Permisos.exigirSesion();
            Usuario usuario = usuarioDAO.buscarPorId(sesion.getId())
                    .orElseThrow(() -> new ServiceException("El usuario de la sesión ya no existe."));
            if (actual == null || !PasswordHasher.verificar(actual, usuario.getSalt(), usuario.getPasswordHash())) {
                throw new ServiceException("La contraseña actual no es correcta.");
            }
            if (nueva == null || !Arrays.equals(nueva, repetida)) {
                throw new ServiceException("La nueva contraseña y su repetición no coinciden.");
            }
            if (!Validador.esPasswordSegura(nueva)) {
                throw new ServiceException(Validador.MENSAJE_PASSWORD);
            }
            if (Arrays.equals(actual, nueva)) {
                throw new ServiceException("La nueva contraseña debe ser distinta de la actual.");
            }
            String sal = PasswordHasher.generarSal();
            usuario.setSalt(sal);
            usuario.setPasswordHash(PasswordHasher.hash(nueva, sal));
            usuarioDAO.actualizar(usuario);
            usuarioDAO.borrarTokensDe(usuario.getId());
        } finally {
            limpiar(actual);
            limpiar(nueva);
            limpiar(repetida);
        }
    }

    /**
     * Cuenta un intento fallido y, al llegar a {@value #MAX_INTENTOS}, bloquea la cuenta. Los usuarios reales
     * guardan el contador en la base de datos; los nombres inexistentes, solo en memoria.
     */
    private synchronized void registrarFallo(Usuario usuario, LocalDateTime ahora) {
        int intentos = usuario.getIntentosFallidos() + 1;
        if (intentos >= MAX_INTENTOS) {
            usuario.setIntentosFallidos(0);
            usuario.setBloqueadoHasta(ahora.plusMinutes(MINUTOS_BLOQUEO));
        } else {
            usuario.setIntentosFallidos(intentos);
        }
        if (usuario.getId() > 0) {
            usuarioDAO.actualizarControlAcceso(usuario);
        }
    }

    /**
     * Registro en memoria (sin id ni credenciales) con el contador de intentos de un nombre de usuario que no
     * existe. Se conservan como máximo {@value #MAX_NOMBRES_INEXISTENTES} nombres: los más antiguos se descartan.
     */
    private synchronized Usuario registroDeInexistente(String nombre) {
        return intentosInexistentes.computeIfAbsent(nombre, n -> new Usuario(0, n, n, null));
    }

    private static String mensajeBloqueo(Usuario usuario, LocalDateTime ahora) {
        long segundos = Duration.between(ahora, usuario.getBloqueadoHasta()).getSeconds();
        long minutos = Math.max(1, (segundos + 59) / 60);
        return "Cuenta bloqueada temporalmente por demasiados intentos fallidos. Inténtelo de nuevo en "
                + minutos + (minutos == 1 ? " minuto." : " minutos.");
    }

    /** Elimina el hash y la sal del objeto que se entrega a la interfaz. */
    static Usuario sinCredenciales(Usuario usuario) {
        usuario.setPasswordHash(null);
        usuario.setSalt(null);
        return usuario;
    }

    private static void limpiar(char[] datos) {
        if (datos != null) {
            Arrays.fill(datos, '\0');
        }
    }
}
