package com.fisioagenda.ui.panels;

/**
 * Contrato común de las secciones que se muestran en la ventana principal. Permite a
 * {@code MainFrame} aplicar los atajos de teclado globales (Ctrl+N, Ctrl+F, F5) a la sección activa.
 */
public interface Seccion {

    /** Título que se muestra en la cabecera de la ventana principal. */
    String getTitulo();

    /** Vuelve a cargar los datos de la sección (F5). */
    void refrescar();

    /** Crea un elemento nuevo en la sección, si tiene sentido (Ctrl+N). */
    default void nuevo() {
    }

    /** Lleva el foco al buscador de la sección, si lo tiene (Ctrl+F). */
    default void enfocarBusqueda() {
    }
}
