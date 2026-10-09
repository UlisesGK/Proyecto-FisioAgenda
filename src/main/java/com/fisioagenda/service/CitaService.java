package com.fisioagenda.service;

import com.fisioagenda.dao.CitaDAO;
import com.fisioagenda.dao.PacienteDAO;
import com.fisioagenda.dao.TratamientoDAO;
import com.fisioagenda.dao.UsuarioDAO;
import com.fisioagenda.model.Cita;
import com.fisioagenda.model.EstadoCita;
import com.fisioagenda.model.Paciente;
import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Tratamiento;
import com.fisioagenda.model.Usuario;
import com.fisioagenda.util.Formatos;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Agenda de citas: consulta, alta y edición con todas las reglas de negocio, cambios de estado
 * y cálculo de huecos libres.
 * <p>
 * Reglas al guardar: paciente existente, fisioterapeuta con rol FISIOTERAPEUTA y activo, tratamiento activo,
 * fecha y hora no pasadas (solo al crear), de lunes a sábado dentro del horario de apertura
 * (08:00–21:00, la cita debe terminar como tarde al cierre) y sin solaparse con otras citas
 * programadas o realizadas del mismo fisioterapeuta ni del mismo paciente. Solo se editan citas programadas,
 * y una cita no se marca como realizada o no asistida antes de su hora de inicio.
 */
public class CitaService {

    /** Hora de apertura de la clínica. */
    public static final LocalTime APERTURA = LocalTime.of(8, 0);
    /** Hora de cierre: ninguna cita puede terminar después. */
    public static final LocalTime CIERRE = LocalTime.of(21, 0);
    /** Separación entre los huecos que propone {@link #huecosLibres}. */
    public static final int MINUTOS_ENTRE_HUECOS = 15;
    private static final String TEXTO_HORARIO = "08:00–21:00";
    private static final int MAX_NOTAS = 500;

    private final CitaDAO citaDAO;
    private final PacienteDAO pacienteDAO;
    private final UsuarioDAO usuarioDAO;
    private final TratamientoDAO tratamientoDAO;
    private final Clock reloj;

    /** Crea el servicio con el reloj del sistema. */
    public CitaService(CitaDAO citaDAO, PacienteDAO pacienteDAO, UsuarioDAO usuarioDAO, TratamientoDAO tratamientoDAO) {
        this(citaDAO, pacienteDAO, usuarioDAO, tratamientoDAO, Clock.systemDefaultZone());
    }

    /** Crea el servicio con un reloj concreto (pruebas). */
    public CitaService(CitaDAO citaDAO, PacienteDAO pacienteDAO, UsuarioDAO usuarioDAO, TratamientoDAO tratamientoDAO,
                       Clock reloj) {
        this.citaDAO = citaDAO;
        this.pacienteDAO = pacienteDAO;
        this.usuarioDAO = usuarioDAO;
        this.tratamientoDAO = tratamientoDAO;
        this.reloj = reloj;
    }

    /**
     * Citas de un día ordenadas por hora.
     *
     * @param fisioterapeutaId fisioterapeuta cuya agenda se quiere ver, o {@code null} para todos
     */
    public List<Cita> citasDelDia(LocalDate dia, Integer fisioterapeutaId) {
        Permisos.exigirSesion();
        if (dia == null) {
            throw new ServiceException("Indique el día que desea consultar.");
        }
        return citaDAO.listarPorDia(dia, fisioterapeutaId);
    }

    /** Citas de un paciente, de la más reciente a la más antigua. */
    public List<Cita> citasDePaciente(int pacienteId) {
        Permisos.exigirSesion();
        return citaDAO.listarPorPaciente(pacienteId);
    }

    /**
     * Valida y guarda una cita: con id 0 la crea (siempre en estado PROGRAMADA) y en otro caso la actualiza.
     * Solo se pueden editar citas programadas: las realizadas, no asistidas o canceladas ya son historia
     * (los cambios de estado se hacen con {@link #cambiarEstado}).
     *
     * @return la cita guardada, recargada con sus datos relacionados
     * @throws ServiceException si se incumple alguna regla de la agenda
     */
    public Cita guardar(Cita c) {
        Permisos.exigirRol("gestionar citas", Rol.ADMIN, Rol.FISIOTERAPEUTA, Rol.RECEPCION);
        if (c == null) {
            throw new ServiceException("Faltan los datos de la cita.");
        }
        boolean nueva = c.getId() == 0;
        Cita existente = nueva ? null : citaDAO.buscarPorId(c.getId())
                .orElseThrow(() -> new ServiceException("La cita no existe o ha sido eliminada."));
        if (existente != null && existente.getEstado() != EstadoCita.PROGRAMADA) {
            throw new ServiceException("Solo se pueden editar citas programadas. Esta cita figura como «"
                    + existente.getEstado().getEtiqueta() + "».");
        }

        Paciente paciente = cargarPaciente(c);
        Usuario fisio = cargarFisioterapeuta(c, existente);
        Tratamiento tratamiento = cargarTratamiento(c, existente);
        if (c.getFechaHora() == null) {
            throw new ServiceException("Indique la fecha y la hora de la cita.");
        }
        c.setPaciente(paciente);
        c.setFisioterapeuta(fisio);
        c.setTratamiento(tratamiento);
        c.setFechaHora(c.getFechaHora().truncatedTo(ChronoUnit.MINUTES));
        c.setEstado(EstadoCita.PROGRAMADA);
        c.setNotas(c.getNotas() == null || c.getNotas().isBlank() ? null : c.getNotas().trim());
        if (c.getNotas() != null && c.getNotas().length() > MAX_NOTAS) {
            throw new ServiceException("Las notas de la cita no pueden superar " + MAX_NOTAS + " caracteres.");
        }

        if (nueva && c.getFechaHora().isBefore(LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MINUTES))) {
            throw new ServiceException("No se pueden crear citas en una fecha u hora ya pasada.");
        }
        validarHorario(c.getFechaHora(), c.getFechaHoraFin());
        comprobarSolapes(c);

        if (nueva) {
            citaDAO.insertar(c);
        } else {
            citaDAO.actualizar(c);
        }
        return citaDAO.buscarPorId(c.getId()).orElse(c);
    }

    /**
     * Cambia el estado de una cita. Marcarla como REALIZADA o NO_ASISTIDA solo pueden hacerlo ADMIN y
     * FISIOTERAPEUTA, y solo cuando ya ha llegado su hora de inicio (una cita "no asistida" deja libre su franja,
     * así que no puede marcarse antes de tiempo); cancelar puede hacerlo cualquier rol, solo si está programada.
     * Una cita cancelada no cambia de estado y ninguna vuelve a PROGRAMADA.
     *
     * @throws ServiceException si la transición no está permitida
     */
    public void cambiarEstado(int citaId, EstadoCita nuevo) {
        Permisos.exigirSesion();
        if (nuevo == null) {
            throw new ServiceException("Indique el nuevo estado de la cita.");
        }
        if (nuevo == EstadoCita.REALIZADA || nuevo == EstadoCita.NO_ASISTIDA) {
            Permisos.exigirRol("marcar citas como realizadas o no asistidas", Rol.ADMIN, Rol.FISIOTERAPEUTA);
        }
        Cita cita = citaDAO.buscarPorId(citaId)
                .orElseThrow(() -> new ServiceException("La cita no existe o ha sido eliminada."));
        EstadoCita actual = cita.getEstado();
        if (actual == nuevo) {
            return;
        }
        if (actual == EstadoCita.CANCELADA) {
            throw new ServiceException("La cita está cancelada y ya no puede cambiar de estado.");
        }
        if (nuevo == EstadoCita.PROGRAMADA) {
            throw new ServiceException("Una cita no puede volver al estado «Programada». Cree una nueva cita.");
        }
        if (nuevo == EstadoCita.CANCELADA) {
            if (actual != EstadoCita.PROGRAMADA) {
                throw new ServiceException("Solo se pueden cancelar citas programadas.");
            }
        } else {
            // REALIZADA o NO_ASISTIDA
            if (!puedeMarcarseAsistencia(cita)) {
                throw new ServiceException("No se puede marcar como «" + nuevo.getEtiqueta()
                        + "» una cita que todavía no ha empezado (empieza el "
                        + Formatos.fecha(cita.getFechaHora().toLocalDate()) + " a las "
                        + Formatos.hora(cita.getFechaHora()) + ").");
            }
            if (bloqueaAgenda(nuevo) && !bloqueaAgenda(actual)) {
                // Pasar de NO_ASISTIDA a REALIZADA vuelve a ocupar la franja: comprobar que sigue libre.
                cita.setEstado(nuevo);
                comprobarSolapes(cita);
            }
        }
        citaDAO.actualizarEstado(citaId, nuevo);
    }

    /**
     * Indica si ya se puede registrar la asistencia (REALIZADA o NO_ASISTIDA) de una cita: a partir de su hora
     * de inicio. La interfaz lo usa para habilitar los botones con el mismo criterio que el servicio.
     */
    public boolean puedeMarcarseAsistencia(Cita cita) {
        return cita != null && cita.getFechaHora() != null
                && !cita.getFechaHora().isAfter(LocalDateTime.now(reloj));
    }

    /**
     * Horas de inicio disponibles (cada {@value #MINUTOS_ENTRE_HUECOS} minutos) para una cita de la duración
     * indicada, dentro del horario de apertura y sin solaparse con las citas del fisioterapeuta.
     * Los domingos y los días pasados no tienen huecos; hoy solo se ofrecen horas que aún no han pasado.
     */
    public List<LocalTime> huecosLibres(LocalDate dia, int fisioterapeutaId, int duracionMin) {
        return huecosLibres(dia, fisioterapeutaId, duracionMin, null);
    }

    /**
     * Igual que {@link #huecosLibres(LocalDate, int, int)} pero ignorando una cita concreta, de modo que al
     * editarla su propia franja aparece como disponible.
     *
     * @param citaExcluidaId id de la cita que se está editando, o {@code null}
     */
    public List<LocalTime> huecosLibres(LocalDate dia, int fisioterapeutaId, int duracionMin, Integer citaExcluidaId) {
        Permisos.exigirSesion();
        if (dia == null) {
            throw new ServiceException("Indique el día.");
        }
        if (duracionMin < 10 || duracionMin > 240) {
            throw new ServiceException("La duración debe estar entre 10 y 240 minutos.");
        }
        List<LocalTime> huecos = new ArrayList<>();
        LocalDateTime ahora = LocalDateTime.now(reloj);
        if (dia.getDayOfWeek() == DayOfWeek.SUNDAY || dia.isBefore(ahora.toLocalDate())) {
            return huecos;
        }
        List<Cita> ocupadas = new ArrayList<>();
        for (Cita c : citaDAO.listarPorDia(dia, fisioterapeutaId)) {
            if (bloqueaAgenda(c.getEstado()) && (citaExcluidaId == null || c.getId() != citaExcluidaId)) {
                ocupadas.add(c);
            }
        }
        LocalDateTime cierre = dia.atTime(CIERRE);
        for (LocalDateTime inicio = dia.atTime(APERTURA); !inicio.plusMinutes(duracionMin).isAfter(cierre);
             inicio = inicio.plusMinutes(MINUTOS_ENTRE_HUECOS)) {
            if (inicio.isBefore(ahora)) {
                continue;
            }
            LocalDateTime fin = inicio.plusMinutes(duracionMin);
            boolean libre = true;
            for (Cita c : ocupadas) {
                if (inicio.isBefore(c.getFechaHoraFin()) && c.getFechaHora().isBefore(fin)) {
                    libre = false;
                    break;
                }
            }
            if (libre) {
                huecos.add(inicio.toLocalTime());
            }
        }
        return huecos;
    }

    // ---------------------------------------------------------------- validaciones

    private Paciente cargarPaciente(Cita c) {
        if (c.getPaciente() == null || c.getPaciente().getId() <= 0) {
            throw new ServiceException("Seleccione un paciente.");
        }
        return pacienteDAO.buscarPorId(c.getPaciente().getId())
                .orElseThrow(() -> new ServiceException("El paciente seleccionado no existe."));
    }

    /**
     * El profesional debe ser un fisioterapeuta activo, salvo que se edite una cita sin cambiarlo: así sus citas
     * siguen pudiéndose editar (por ejemplo, para reasignarlas) aunque después se haya desactivado o haya
     * cambiado de rol.
     */
    private Usuario cargarFisioterapeuta(Cita c, Cita existente) {
        if (c.getFisioterapeuta() == null || c.getFisioterapeuta().getId() <= 0) {
            throw new ServiceException("Seleccione un fisioterapeuta.");
        }
        Usuario fisio = usuarioDAO.buscarPorId(c.getFisioterapeuta().getId())
                .orElseThrow(() -> new ServiceException("El fisioterapeuta seleccionado no existe."));
        boolean sinCambios = existente != null && existente.getFisioterapeuta().getId() == fisio.getId();
        if (!sinCambios && (fisio.getRol() != Rol.FISIOTERAPEUTA || !fisio.isActivo())) {
            throw new ServiceException("El profesional seleccionado no es un fisioterapeuta activo.");
        }
        return AuthService.sinCredenciales(fisio);
    }

    /** El tratamiento debe estar activo salvo que se edite una cita sin cambiarlo. */
    private Tratamiento cargarTratamiento(Cita c, Cita existente) {
        if (c.getTratamiento() == null || c.getTratamiento().getId() <= 0) {
            throw new ServiceException("Seleccione un tratamiento.");
        }
        Tratamiento tratamiento = tratamientoDAO.buscarPorId(c.getTratamiento().getId())
                .orElseThrow(() -> new ServiceException("El tratamiento seleccionado no existe."));
        boolean sinCambios = existente != null && existente.getTratamiento().getId() == tratamiento.getId();
        if (!tratamiento.isActivo() && !sinCambios) {
            throw new ServiceException("El tratamiento «" + tratamiento.getNombre() + "» no está activo.");
        }
        return tratamiento;
    }

    /** Día laborable, inicio no anterior a la apertura y fin no posterior al cierre, con un mensaje para cada caso. */
    private static void validarHorario(LocalDateTime inicio, LocalDateTime fin) {
        if (inicio.getDayOfWeek() == DayOfWeek.SUNDAY) {
            throw new ServiceException("La clínica no abre los domingos. Elija un día de lunes a sábado.");
        }
        LocalTime horaInicio = inicio.toLocalTime();
        if (horaInicio.isBefore(APERTURA)) {
            throw new ServiceException("La clínica abre a las " + Formatos.hora(APERTURA)
                    + ". Elija una hora dentro del horario de apertura (" + TEXTO_HORARIO + ").");
        }
        if (!horaInicio.isBefore(CIERRE)) {
            throw new ServiceException("La clínica cierra a las " + Formatos.hora(CIERRE)
                    + ". Elija una hora dentro del horario de apertura (" + TEXTO_HORARIO + ").");
        }
        if (!fin.toLocalDate().equals(inicio.toLocalDate()) || fin.toLocalTime().isAfter(CIERRE)) {
            throw new ServiceException("La cita debe terminar dentro del horario de apertura (" + TEXTO_HORARIO
                    + "). Con este tratamiento terminaría a las " + Formatos.hora(fin) + ".");
        }
    }

    /** Comprueba que la cita no se solape con otras programadas o realizadas del mismo fisioterapeuta o paciente. */
    private void comprobarSolapes(Cita c) {
        for (Cita otra : citaDAO.listarPorDia(c.getFechaHora().toLocalDate(), null)) {
            if (otra.getId() == c.getId() || !bloqueaAgenda(otra.getEstado()) || !c.seSolapaCon(otra)) {
                continue;
            }
            String franja = Formatos.hora(otra.getFechaHora()) + " a " + Formatos.hora(otra.getFechaHoraFin());
            if (otra.getFisioterapeuta().getId() == c.getFisioterapeuta().getId()) {
                throw new ServiceException("El fisioterapeuta " + otra.getFisioterapeuta().getNombreCompleto()
                        + " ya tiene una cita de " + franja + ". Elija otra hora.");
            }
            if (otra.getPaciente().getId() == c.getPaciente().getId()) {
                throw new ServiceException("El paciente " + otra.getPaciente().getNombreCompleto()
                        + " ya tiene una cita de " + franja + ". Elija otra hora.");
            }
        }
    }

    /** Las citas programadas y realizadas ocupan la agenda; las canceladas y no asistidas no. */
    private static boolean bloqueaAgenda(EstadoCita estado) {
        return estado == EstadoCita.PROGRAMADA || estado == EstadoCita.REALIZADA;
    }
}
