package com.fisioagenda.dao;

/**
 * Excepción no comprobada que envuelve los errores de acceso a datos (SQLException),
 * para que las capas superiores no dependan de JDBC.
 */
public class DataAccessException extends RuntimeException {
    public DataAccessException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
