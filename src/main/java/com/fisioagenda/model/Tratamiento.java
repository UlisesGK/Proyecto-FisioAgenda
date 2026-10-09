package com.fisioagenda.model;

/**
 * Tipo de tratamiento ofrecido por la clínica (catálogo de servicios).
 */
public class Tratamiento {
    private int id;
    private String nombre;
    private String descripcion;
    private int duracionMinutos;
    private double precio;
    private boolean activo = true;

    public Tratamiento() {
    }

    public Tratamiento(int id, String nombre, int duracionMinutos, double precio) {
        this.id = id;
        this.nombre = nombre;
        this.duracionMinutos = duracionMinutos;
        this.precio = precio;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public int getDuracionMinutos() { return duracionMinutos; }
    public void setDuracionMinutos(int duracionMinutos) { this.duracionMinutos = duracionMinutos; }
    public double getPrecio() { return precio; }
    public void setPrecio(double precio) { this.precio = precio; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    @Override
    public String toString() {
        return nombre + " (" + duracionMinutos + " min)";
    }
}
