package com.fisioagenda.service;

import com.fisioagenda.dao.PacienteDAO;
import com.fisioagenda.model.Paciente;
import com.fisioagenda.model.Rol;
import com.fisioagenda.util.Validador;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Gestión de pacientes. Todos los roles pueden consultar y editar; dar de alta solo ADMIN y RECEPCION;
 * eliminar solo ADMIN.
 */
public class PacienteService {

    private static final int MAX_NOMBRE = 60;
    private static final int MAX_APELLIDOS = 100;
    private static final int MAX_OBSERVACIONES = 1000;
    private static final LocalDate NACIMIENTO_MINIMO = LocalDate.of(1900, 1, 1);

    private final PacienteDAO pacienteDAO;
    private final Clock reloj;

    /** Crea el servicio con el reloj del sistema. */
    public PacienteService(PacienteDAO pacienteDAO) {
        this(pacienteDAO, Clock.systemDefaultZone());
    }

    /** Crea el servicio con un reloj concreto (pruebas). */
    public PacienteService(PacienteDAO pacienteDAO, Clock reloj) {
        this.pacienteDAO = pacienteDAO;
        this.reloj = reloj;
    }

    /** Todos los pacientes ordenados por apellidos y nombre. */
    public List<Paciente> listar() {
        Permisos.exigirSesion();
        return pacienteDAO.listarTodos();
    }

    /**
     * Pacientes cuyo nombre, apellidos, DNI o teléfono contienen todas las palabras del texto, sin distinguir
     * mayúsculas ni tildes (las mismas reglas que el buscador de la interfaz); si está vacío, todos.
     */
    public List<Paciente> buscar(String texto) {
        Permisos.exigirSesion();
        return Validador.noVacio(texto) ? pacienteDAO.buscar(texto.trim()) : pacienteDAO.listarTodos();
    }

    /** Busca un paciente por su id. */
    public Optional<Paciente> buscarPorId(int id) {
        Permisos.exigirSesion();
        return pacienteDAO.buscarPorId(id);
    }

    /**
     * Valida y guarda un paciente: con id 0 lo da de alta (fecha de alta = hoy) y en otro caso lo actualiza
     * conservando su fecha de alta original. Normaliza el DNI (mayúsculas, sin guiones) y el teléfono (sin espacios).
     *
     * @return el paciente guardado, con su id asignado
     * @throws ServiceException si algún dato no es válido, el DNI ya existe o falta el permiso
     */
    public Paciente guardar(Paciente p) {
        if (p == null) {
            throw new ServiceException("Faltan los datos del paciente.");
        }
        boolean nuevo = p.getId() == 0;
        if (nuevo) {
            Permisos.exigirRol("dar de alta pacientes", Rol.ADMIN, Rol.RECEPCION);
        } else {
            Permisos.exigirRol("editar pacientes", Rol.ADMIN, Rol.FISIOTERAPEUTA, Rol.RECEPCION);
        }
        normalizar(p);
        validar(p);

        Optional<Paciente> mismoDni = pacienteDAO.buscarPorDni(p.getDni());
        if (mismoDni.isPresent() && mismoDni.get().getId() != p.getId()) {
            throw new ServiceException("Ya existe un paciente con el DNI/NIE " + p.getDni() + ".");
        }
        if (nuevo) {
            p.setFechaAlta(LocalDate.now(reloj));
            return pacienteDAO.insertar(p);
        }
        Paciente existente = pacienteDAO.buscarPorId(p.getId())
                .orElseThrow(() -> new ServiceException("El paciente no existe o ha sido eliminado."));
        p.setFechaAlta(existente.getFechaAlta());
        pacienteDAO.actualizar(p);
        return p;
    }

    /**
     * Elimina un paciente junto con sus citas y su historial (solo ADMIN).
     *
     * @throws ServiceException si no tiene permisos o el paciente no existe
     */
    public void eliminar(int id) {
        Permisos.exigirRol("eliminar pacientes", Rol.ADMIN);
        if (pacienteDAO.buscarPorId(id).isEmpty()) {
            throw new ServiceException("El paciente no existe o ya ha sido eliminado.");
        }
        pacienteDAO.eliminar(id);
    }

    private static void normalizar(Paciente p) {
        p.setDni(Validador.normalizarDni(p.getDni()));
        p.setNombre(recortar(p.getNombre()));
        p.setApellidos(recortar(p.getApellidos()));
        p.setTelefono(Validador.normalizarTelefono(p.getTelefono()));
        p.setEmail(vacioANulo(p.getEmail()));
        p.setObservaciones(vacioANulo(p.getObservaciones()));
    }

    private void validar(Paciente p) {
        if (!Validador.esDniNieValido(p.getDni())) {
            throw new ServiceException("El DNI/NIE no es válido. Compruebe los números y la letra.");
        }
        if (!Validador.noVacio(p.getNombre())) {
            throw new ServiceException("El nombre es obligatorio.");
        }
        if (p.getNombre().length() > MAX_NOMBRE) {
            throw new ServiceException("El nombre no puede superar " + MAX_NOMBRE + " caracteres.");
        }
        if (!Validador.noVacio(p.getApellidos())) {
            throw new ServiceException("Los apellidos son obligatorios.");
        }
        if (p.getApellidos().length() > MAX_APELLIDOS) {
            throw new ServiceException("Los apellidos no pueden superar " + MAX_APELLIDOS + " caracteres.");
        }
        if (!Validador.esTelefonoValido(p.getTelefono())) {
            throw new ServiceException("El teléfono no es válido: " + Validador.MENSAJE_TELEFONO + ".");
        }
        if (p.getEmail() != null && !Validador.esEmailValido(p.getEmail())) {
            throw new ServiceException("El email no tiene un formato válido.");
        }
        LocalDate nacimiento = p.getFechaNacimiento();
        if (nacimiento != null && nacimiento.isAfter(LocalDate.now(reloj))) {
            throw new ServiceException("La fecha de nacimiento no puede ser futura.");
        }
        if (nacimiento != null && nacimiento.isBefore(NACIMIENTO_MINIMO)) {
            throw new ServiceException("La fecha de nacimiento no es válida.");
        }
        if (p.getObservaciones() != null && p.getObservaciones().length() > MAX_OBSERVACIONES) {
            throw new ServiceException("Las observaciones no pueden superar " + MAX_OBSERVACIONES + " caracteres.");
        }
    }

    private static String recortar(String texto) {
        return texto == null ? null : texto.trim();
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
