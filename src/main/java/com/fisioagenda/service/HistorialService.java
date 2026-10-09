package com.fisioagenda.service;

import com.fisioagenda.dao.CitaDAO;
import com.fisioagenda.dao.NotaClinicaDAO;
import com.fisioagenda.dao.PacienteDAO;
import com.fisioagenda.model.Cita;
import com.fisioagenda.model.NotaClinica;
import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Usuario;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Historial clínico de los pacientes. Son datos de salud (categoría especial del RGPD): solo ADMIN y
 * FISIOTERAPEUTA pueden consultarlo y solo los fisioterapeutas pueden añadir notas.
 */
public class HistorialService {

    /** Longitud mínima del contenido de una nota. */
    public static final int MIN_CONTENIDO = 5;
    /** Longitud máxima del contenido de una nota. */
    public static final int MAX_CONTENIDO = 2000;

    private final NotaClinicaDAO notaDAO;
    private final PacienteDAO pacienteDAO;
    private final CitaDAO citaDAO;
    private final Clock reloj;

    /** Crea el servicio con el reloj del sistema. */
    public HistorialService(NotaClinicaDAO notaDAO, PacienteDAO pacienteDAO, CitaDAO citaDAO) {
        this(notaDAO, pacienteDAO, citaDAO, Clock.systemDefaultZone());
    }

    /** Crea el servicio con un reloj concreto (pruebas). */
    public HistorialService(NotaClinicaDAO notaDAO, PacienteDAO pacienteDAO, CitaDAO citaDAO, Clock reloj) {
        this.notaDAO = notaDAO;
        this.pacienteDAO = pacienteDAO;
        this.citaDAO = citaDAO;
        this.reloj = reloj;
    }

    /**
     * Notas clínicas del paciente, de la más reciente a la más antigua (ADMIN o FISIOTERAPEUTA).
     *
     * @throws ServiceException si el usuario no tiene permiso
     */
    public List<NotaClinica> notasDe(int pacienteId) {
        Permisos.exigirRol("consultar el historial clínico", Rol.ADMIN, Rol.FISIOTERAPEUTA);
        return notaDAO.listarPorPaciente(pacienteId);
    }

    /**
     * Añade una nota al historial del paciente (solo FISIOTERAPEUTA). El autor es el usuario de la sesión
     * y la fecha, el momento actual.
     *
     * @param citaId      cita (sesión) a la que se refiere la nota, o {@code null} si es una nota general
     * @param contenido   texto de la nota, entre 5 y 2000 caracteres
     * @param escalaDolor dolor en la escala EVA, de 0 a 10
     * @return la nota guardada
     * @throws ServiceException si falta el permiso o algún dato no es válido
     */
    public NotaClinica anadirNota(int pacienteId, Integer citaId, String contenido, int escalaDolor) {
        Usuario autor = Permisos.exigirRol("añadir notas clínicas", Rol.FISIOTERAPEUTA);
        if (pacienteDAO.buscarPorId(pacienteId).isEmpty()) {
            throw new ServiceException("El paciente no existe.");
        }
        String texto = contenido == null ? "" : contenido.trim();
        if (texto.length() < MIN_CONTENIDO || texto.length() > MAX_CONTENIDO) {
            throw new ServiceException("La nota debe tener entre " + MIN_CONTENIDO + " y " + MAX_CONTENIDO
                    + " caracteres.");
        }
        if (escalaDolor < 0 || escalaDolor > 10) {
            throw new ServiceException("La escala de dolor (EVA) debe estar entre 0 y 10.");
        }
        if (citaId != null) {
            Cita cita = citaDAO.buscarPorId(citaId)
                    .orElseThrow(() -> new ServiceException("La cita indicada no existe."));
            if (cita.getPaciente().getId() != pacienteId) {
                throw new ServiceException("La cita indicada no pertenece a este paciente.");
            }
        }
        NotaClinica nota = new NotaClinica();
        nota.setPacienteId(pacienteId);
        nota.setCitaId(citaId);
        nota.setAutor(autor);
        nota.setFecha(LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MINUTES));
        nota.setContenido(texto);
        nota.setEscalaDolor(escalaDolor);
        return notaDAO.insertar(nota);
    }
}
