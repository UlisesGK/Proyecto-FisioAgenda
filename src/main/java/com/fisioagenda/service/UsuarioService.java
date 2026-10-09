package com.fisioagenda.service;

import com.fisioagenda.dao.UsuarioDAO;
import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Usuario;
import com.fisioagenda.security.PasswordHasher;
import com.fisioagenda.util.Validador;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Gestión de usuarios del personal (solo ADMIN, salvo {@link #listarFisioterapeutas()}).
 * Los usuarios no se borran: se desactivan, para conservar la autoría de citas y notas.
 * Los objetos devueltos no incluyen el hash ni la sal de la contraseña.
 */
public class UsuarioService {

    private static final String ACCION = "gestionar usuarios";
    private static final int MAX_NOMBRE = 100;

    private final UsuarioDAO usuarioDAO;

    /** Crea el servicio sobre el DAO indicado. */
    public UsuarioService(UsuarioDAO usuarioDAO) {
        this.usuarioDAO = usuarioDAO;
    }

    /** Todos los usuarios ordenados por nombre (solo ADMIN). */
    public List<Usuario> listar() {
        Permisos.exigirRol(ACCION, Rol.ADMIN);
        List<Usuario> usuarios = usuarioDAO.listarTodos();
        usuarios.forEach(AuthService::sinCredenciales);
        return usuarios;
    }

    /** Fisioterapeutas activos (cualquier rol, para los selectores de la agenda). */
    public List<Usuario> listarFisioterapeutas() {
        Permisos.exigirSesion();
        List<Usuario> activos = new ArrayList<>();
        for (Usuario u : usuarioDAO.listarPorRol(Rol.FISIOTERAPEUTA)) {
            if (u.isActivo()) {
                activos.add(AuthService.sinCredenciales(u));
            }
        }
        return activos;
    }

    /**
     * Crea un usuario (solo ADMIN). El nombre de usuario se guarda en minúsculas.
     *
     * @param u        datos del usuario (username, nombre completo, rol y activo)
     * @param password contraseña inicial; se borra al terminar
     * @return el usuario creado con su id
     * @throws ServiceException si el nombre de usuario no es válido o ya existe, o la contraseña no es segura
     */
    public Usuario crear(Usuario u, char[] password) {
        try {
            Permisos.exigirRol(ACCION, Rol.ADMIN);
            if (u == null) {
                throw new ServiceException("Faltan los datos del usuario.");
            }
            String username = u.getUsername() == null ? "" : u.getUsername().trim().toLowerCase(Locale.ROOT);
            if (!Validador.esUsernameValido(username)) {
                throw new ServiceException("El nombre de usuario debe tener entre 3 y 20 caracteres: letras minúsculas, "
                        + "números, punto o guion bajo.");
            }
            if (usuarioDAO.buscarPorUsername(username).isPresent()) {
                throw new ServiceException("Ya existe un usuario con el nombre «" + username + "».");
            }
            validarDatos(u);
            if (!Validador.esPasswordSegura(password)) {
                throw new ServiceException(Validador.MENSAJE_PASSWORD);
            }
            Usuario nuevo = new Usuario(0, username, u.getNombreCompleto().trim(), u.getRol());
            nuevo.setActivo(u.isActivo());
            String sal = PasswordHasher.generarSal();
            nuevo.setSalt(sal);
            nuevo.setPasswordHash(PasswordHasher.hash(password, sal));
            usuarioDAO.insertar(nuevo);
            return AuthService.sinCredenciales(nuevo);
        } finally {
            limpiar(password);
        }
    }

    /**
     * Actualiza nombre completo, rol y estado activo de un usuario (solo ADMIN). El nombre de usuario y la
     * contraseña no cambian. Un administrador no puede desactivarse ni quitarse el rol a sí mismo.
     */
    public void actualizar(Usuario u) {
        Usuario admin = Permisos.exigirRol(ACCION, Rol.ADMIN);
        if (u == null) {
            throw new ServiceException("Faltan los datos del usuario.");
        }
        Usuario existente = cargar(u.getId());
        validarDatos(u);
        if (existente.getId() == admin.getId()) {
            if (!u.isActivo()) {
                throw new ServiceException("No puede desactivar su propia cuenta.");
            }
            if (u.getRol() != Rol.ADMIN) {
                throw new ServiceException("No puede quitarse a sí mismo el rol de administrador.");
            }
        }
        existente.setNombreCompleto(u.getNombreCompleto().trim());
        existente.setRol(u.getRol());
        existente.setActivo(u.isActivo());
        usuarioDAO.actualizar(existente);
    }

    /**
     * Asigna una nueva contraseña a un usuario (solo ADMIN) y le quita el bloqueo por intentos fallidos.
     *
     * @param nueva nueva contraseña; se borra al terminar
     */
    public void restablecerPassword(int usuarioId, char[] nueva) {
        try {
            Permisos.exigirRol(ACCION, Rol.ADMIN);
            Usuario usuario = cargar(usuarioId);
            if (!Validador.esPasswordSegura(nueva)) {
                throw new ServiceException(Validador.MENSAJE_PASSWORD);
            }
            String sal = PasswordHasher.generarSal();
            usuario.setSalt(sal);
            usuario.setPasswordHash(PasswordHasher.hash(nueva, sal));
            usuarioDAO.actualizar(usuario);
            usuario.setIntentosFallidos(0);
            usuario.setBloqueadoHasta(null);
            usuarioDAO.actualizarControlAcceso(usuario);
        } finally {
            limpiar(nueva);
        }
    }

    /** Quita el bloqueo temporal y pone a cero los intentos fallidos de un usuario (solo ADMIN). */
    public void desbloquear(int usuarioId) {
        Permisos.exigirRol(ACCION, Rol.ADMIN);
        Usuario usuario = cargar(usuarioId);
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        usuarioDAO.actualizarControlAcceso(usuario);
    }

    private Usuario cargar(int id) {
        return usuarioDAO.buscarPorId(id).orElseThrow(() -> new ServiceException("El usuario no existe."));
    }

    private static void validarDatos(Usuario u) {
        if (!Validador.noVacio(u.getNombreCompleto())) {
            throw new ServiceException("El nombre completo es obligatorio.");
        }
        if (u.getNombreCompleto().trim().length() > MAX_NOMBRE) {
            throw new ServiceException("El nombre completo no puede superar " + MAX_NOMBRE + " caracteres.");
        }
        if (u.getRol() == null) {
            throw new ServiceException("Seleccione el rol del usuario.");
        }
    }

    private static void limpiar(char[] datos) {
        if (datos != null) {
            Arrays.fill(datos, '\0');
        }
    }
}
