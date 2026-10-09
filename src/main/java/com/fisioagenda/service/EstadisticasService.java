package com.fisioagenda.service;

import com.fisioagenda.dao.CitaDAO;
import com.fisioagenda.dao.PacienteDAO;
import com.fisioagenda.model.Cita;
import com.fisioagenda.model.EstadoCita;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Cifras resumen para el panel de inicio. Las citas canceladas no se cuentan como citas del día.
 * <p>
 * En los métodos sin parámetro de fisioterapeuta, si el usuario de la sesión es FISIOTERAPEUTA las cifras
 * se calculan solo con sus citas; para el resto de roles, con las de toda la clínica.
 */
public class EstadisticasService {

    private final PacienteDAO pacienteDAO;
    private final CitaDAO citaDAO;
    private final Clock reloj;

    /** Crea el servicio con el reloj del sistema. */
    public EstadisticasService(PacienteDAO pacienteDAO, CitaDAO citaDAO) {
        this(pacienteDAO, citaDAO, Clock.systemDefaultZone());
    }

    /** Crea el servicio con un reloj concreto (pruebas). */
    public EstadisticasService(PacienteDAO pacienteDAO, CitaDAO citaDAO, Clock reloj) {
        this.pacienteDAO = pacienteDAO;
        this.citaDAO = citaDAO;
        this.reloj = reloj;
    }

    /** Número total de pacientes registrados. */
    public int pacientesTotales() {
        Permisos.exigirSesion();
        return pacienteDAO.contar();
    }

    /**
     * Citas de hoy no canceladas.
     *
     * @param fisioterapeutaId fisioterapeuta, o {@code null} para toda la clínica
     */
    public int citasHoy(Integer fisioterapeutaId) {
        Permisos.exigirSesion();
        int total = 0;
        for (Cita c : citaDAO.listarPorDia(LocalDate.now(reloj), fisioterapeutaId)) {
            if (c.getEstado() != EstadoCita.CANCELADA) {
                total++;
            }
        }
        return total;
    }

    /**
     * Citas PROGRAMADAS desde este momento hasta dentro de 7 días.
     *
     * @param fisioterapeutaId fisioterapeuta, o {@code null} para toda la clínica
     */
    public int citasPendientesSemana(Integer fisioterapeutaId) {
        Permisos.exigirSesion();
        LocalDateTime ahora = LocalDateTime.now(reloj);
        return contar(citaDAO.listarEntre(ahora, ahora.plusDays(7), fisioterapeutaId), EstadoCita.PROGRAMADA);
    }

    /**
     * Porcentaje de asistencia de los últimos 30 días: REALIZADAS / (REALIZADAS + NO_ASISTIDAS) × 100,
     * o 0 si no hay datos. Para un fisioterapeuta se calcula con sus propias citas.
     */
    public double tasaAsistenciaUltimos30Dias() {
        return tasaAsistenciaUltimos30Dias(Permisos.fisioterapeutaDeLaSesion());
    }

    /**
     * Porcentaje de asistencia de los últimos 30 días.
     *
     * @param fisioterapeutaId fisioterapeuta, o {@code null} para toda la clínica
     */
    public double tasaAsistenciaUltimos30Dias(Integer fisioterapeutaId) {
        Permisos.exigirSesion();
        LocalDateTime ahora = LocalDateTime.now(reloj);
        LocalDateTime desde = ahora.minusDays(30);
        int realizadas;
        int noAsistidas;
        if (fisioterapeutaId == null) {
            realizadas = citaDAO.contarPorEstadoEntre(EstadoCita.REALIZADA, desde, ahora);
            noAsistidas = citaDAO.contarPorEstadoEntre(EstadoCita.NO_ASISTIDA, desde, ahora);
        } else {
            List<Cita> citas = citaDAO.listarEntre(desde, ahora, fisioterapeutaId);
            realizadas = contar(citas, EstadoCita.REALIZADA);
            noAsistidas = contar(citas, EstadoCita.NO_ASISTIDA);
        }
        int total = realizadas + noAsistidas;
        return total == 0 ? 0.0 : realizadas * 100.0 / total;
    }

    /**
     * Citas no canceladas de cada día (lunes a sábado) de la semana actual, en orden.
     * Para un fisioterapeuta se cuentan solo sus citas.
     */
    public Map<DayOfWeek, Integer> citasPorDiaSemanaActual() {
        return citasPorDiaSemanaActual(Permisos.fisioterapeutaDeLaSesion());
    }

    /**
     * Citas no canceladas de cada día (lunes a sábado) de la semana actual.
     *
     * @param fisioterapeutaId fisioterapeuta, o {@code null} para toda la clínica
     * @return mapa ordenado de lunes a sábado (los días sin citas aparecen con 0)
     */
    public Map<DayOfWeek, Integer> citasPorDiaSemanaActual(Integer fisioterapeutaId) {
        Permisos.exigirSesion();
        LocalDate lunes = LocalDate.now(reloj).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<DayOfWeek, Integer> porDia = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek dia = DayOfWeek.MONDAY; dia != DayOfWeek.SUNDAY; dia = dia.plus(1)) {
            porDia.put(dia, 0);
        }
        for (Cita c : citaDAO.listarEntre(lunes.atStartOfDay(), lunes.plusDays(7).atStartOfDay(), fisioterapeutaId)) {
            DayOfWeek dia = c.getFechaHora().getDayOfWeek();
            if (c.getEstado() != EstadoCita.CANCELADA && porDia.containsKey(dia)) {
                porDia.merge(dia, 1, Integer::sum);
            }
        }
        return porDia;
    }

    private static int contar(List<Cita> citas, EstadoCita estado) {
        int total = 0;
        for (Cita c : citas) {
            if (c.getEstado() == estado) {
                total++;
            }
        }
        return total;
    }
}
