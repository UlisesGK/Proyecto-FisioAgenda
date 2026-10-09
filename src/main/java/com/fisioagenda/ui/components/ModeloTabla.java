package com.fisioagenda.ui.components;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Modelo de tabla genérico y de solo lectura: cada columna se define con un nombre, un tipo
 * (para ordenar correctamente) y una función que extrae el valor de cada objeto de la fila.
 *
 * @param <T> tipo de objeto mostrado en cada fila
 */
public class ModeloTabla<T> extends AbstractTableModel {

    private final List<String> nombres = new ArrayList<>();
    private final List<Class<?>> tipos = new ArrayList<>();
    private final List<Function<T, Object>> extractores = new ArrayList<>();
    private List<T> filas = Collections.emptyList();

    /** Añade una columna y devuelve el propio modelo para encadenar llamadas. */
    public ModeloTabla<T> columna(String nombre, Class<?> tipo, Function<T, Object> extractor) {
        nombres.add(nombre);
        tipos.add(tipo);
        extractores.add(extractor);
        return this;
    }

    /** Sustituye todas las filas y notifica a la tabla. */
    public void setFilas(List<T> nuevas) {
        this.filas = nuevas == null ? Collections.emptyList() : new ArrayList<>(nuevas);
        fireTableDataChanged();
    }

    /** Objeto de la fila indicada (índice del modelo, no de la vista). */
    public T getFila(int indiceModelo) {
        return filas.get(indiceModelo);
    }

    /** Copia de todas las filas del modelo. */
    public List<T> getFilas() {
        return new ArrayList<>(filas);
    }

    @Override
    public int getRowCount() {
        return filas.size();
    }

    @Override
    public int getColumnCount() {
        return nombres.size();
    }

    @Override
    public String getColumnName(int columna) {
        return nombres.get(columna);
    }

    @Override
    public Class<?> getColumnClass(int columna) {
        return tipos.get(columna);
    }

    @Override
    public Object getValueAt(int fila, int columna) {
        return extractores.get(columna).apply(filas.get(fila));
    }
}
