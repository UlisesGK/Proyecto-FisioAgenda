package com.fisioagenda.service;

import com.fisioagenda.dao.CitaDAO;
import com.fisioagenda.dao.TratamientoDAO;
import com.fisioagenda.model.Cita;
import com.fisioagenda.model.EstadoCita;
import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Tratamiento;
import com.fisioagenda.util.Validador;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Catálogo de tratamientos. Todos los roles pueden consultarlo; solo ADMIN puede crearlos, editarlos
 * o activarlos/desactivarlos (los tratamientos no se borran para conservar el histórico de citas).
 * <p>
 * La hora de fin de una cita se calcula con la duración de su tratamiento, así que la duración no puede
 * cambiar mientras el tratamiento tenga citas programadas de hoy en adelante: esas citas se alargarían o
 * acortarían sin aviso y podrían solaparse o terminar fuera del horario de apertura.
 */
public class TratamientoService {

    /** Duración mínima de un tratamiento, en minutos. */
    public static final int DURACION_MINIMA = 10;
    /** Duración máxima de un tratamiento, en minutos. */
    public static final int DURACION_MAXIMA = 240;
    private static final int MAX_NOMBRE = 80;
    private static final int MAX_DESCRIPCION = 500;

    /** Límite superior al buscar citas programadas "de hoy en adelante". */
    private static final LocalDateTime SIN_LIMITE = LocalDate.of(9999, 12, 31).atStartOfDay();

    private final TratamientoDAO tratamientoDAO;
    private final CitaDAO citaDAO;
    private final Clock reloj;

    /** Crea el servicio con el reloj del sistema. */
    public TratamientoService(TratamientoDAO tratamientoDAO, CitaDAO citaDAO) {
        this(tratamientoDAO, citaDAO, Clock.systemDefaultZone());
    }

    /** Crea el servicio con un reloj concreto (pruebas). */
    public TratamientoService(TratamientoDAO tratamientoDAO, CitaDAO citaDAO, Clock reloj) {
        this.tratamientoDAO = tratamientoDAO;
        this.citaDAO = citaDAO;
        this.reloj = reloj;
    }

    /** Todos los tratamientos (activos e inactivos) por orden alfabético. */
    public List<Tratamiento> listar() {
        Permisos.exigirSesion();
        return tratamientoDAO.listarTodos();
    }

    /** Tratamientos activos, los únicos que se pueden asignar a citas nuevas. */
    public List<Tratamiento> listarActivos() {
        Permisos.exigirSesion();
        return tratamientoDAO.listarActivos();
    }

    /**
     * Valida y guarda un tratamiento (id 0 = nuevo). Solo ADMIN.
     *
     * @return el tratamiento guardado, con su id asignado
     * @throws ServiceException si falta el permiso, algún dato no es válido, el nombre ya existe o se intenta
     *                          cambiar la duración de un tratamiento con citas programadas de hoy en adelante
     */
    public Tratamiento guardar(Tratamiento t) {
        Permisos.exigirRol("gestionar tratamientos", Rol.ADMIN);
        if (t == null) {
            throw new ServiceException("Faltan los datos del tratamiento.");
        }
        t.setNombre(t.getNombre() == null ? null : t.getNombre().trim());
        t.setDescripcion(t.getDescripcion() == null || t.getDescripcion().isBlank() ? null : t.getDescripcion().trim());
        if (!Validador.noVacio(t.getNombre())) {
            throw new ServiceException("El nombre del tratamiento es obligatorio.");
        }
        if (t.getNombre().length() > MAX_NOMBRE) {
            throw new ServiceException("El nombre no puede superar " + MAX_NOMBRE + " caracteres.");
        }
        if (t.getDescripcion() != null && t.getDescripcion().length() > MAX_DESCRIPCION) {
            throw new ServiceException("La descripción no puede superar " + MAX_DESCRIPCION + " caracteres.");
        }
        if (t.getDuracionMinutos() < DURACION_MINIMA || t.getDuracionMinutos() > DURACION_MAXIMA) {
            throw new ServiceException("La duración debe estar entre " + DURACION_MINIMA + " y " + DURACION_MAXIMA
                    + " minutos.");
        }
        if (Double.isNaN(t.getPrecio()) || Double.isInfinite(t.getPrecio()) || t.getPrecio() < 0) {
            throw new ServiceException("El precio no puede ser negativo.");
        }
        for (Tratamiento otro : tratamientoDAO.listarTodos()) {
            if (otro.getId() != t.getId() && otro.getNombre().equalsIgnoreCase(t.getNombre())) {
                throw new ServiceException("Ya existe un tratamiento llamado «" + otro.getNombre() + "».");
            }
        }
        if (t.getId() == 0) {
            return tratamientoDAO.insertar(t);
        }
        Tratamiento existente = tratamientoDAO.buscarPorId(t.getId())
                .orElseThrow(() -> new ServiceException("El tratamiento no existe."));
        if (existente.getDuracionMinutos() != t.getDuracionMinutos()) {
            int programadas = citasProgramadasDesdeHoy(t.getId());
            if (programadas > 0) {
                throw new ServiceException("No se puede cambiar la duración: el tratamiento tiene " + programadas
                        + (programadas == 1 ? " cita programada" : " citas programadas")
                        + " que cambiarían de hora de fin. Cree un tratamiento nuevo con la duración deseada"
                        + " y desactive este.");
            }
        }
        tratamientoDAO.actualizar(t);
        return t;
    }

    /** Citas PROGRAMADAS del tratamiento desde el comienzo de hoy (las de días pasados ya no ocupan agenda). */
    private int citasProgramadasDesdeHoy(int tratamientoId) {
        int total = 0;
        for (Cita c : citaDAO.listarEntre(LocalDate.now(reloj).atStartOfDay(), SIN_LIMITE, null)) {
            if (c.getEstado() == EstadoCita.PROGRAMADA && c.getTratamiento().getId() == tratamientoId) {
                total++;
            }
        }
        return total;
    }
}
