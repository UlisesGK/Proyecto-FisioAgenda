package com.fisioagenda.service;

/**
 * Error de negocio con un mensaje pensado para mostrarse directamente al usuario
 * (validaciones, conflictos de agenda, permisos, credenciales incorrectas...).
 */
public class ServiceException extends RuntimeException {
    public ServiceException(String mensaje) {
        super(mensaje);
    }
}
