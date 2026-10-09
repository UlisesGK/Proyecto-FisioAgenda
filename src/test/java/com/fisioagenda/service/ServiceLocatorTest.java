package com.fisioagenda.service;

import com.fisioagenda.BdPruebas;
import com.fisioagenda.db.Database;
import com.fisioagenda.db.EsquemaBD;
import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceLocatorTest {

    @TempDir
    Path carpeta;

    @AfterEach
    void tearDown() {
        ServiceLocator.reiniciar();
        BdPruebas.cerrar();
    }

    @Test
    void construyeLosServiciosUnaSolaVezSobreLaBaseDeDatosConfigurada() {
        Database.configurar(carpeta.resolve("app.db").toString());
        EsquemaBD.inicializar();
        ServiceLocator.reiniciar();

        assertSame(ServiceLocator.citas(), ServiceLocator.citas());
        Usuario usuario = ServiceLocator.auth().login("recepcion", "Recep1234!".toCharArray());
        assertEquals(Rol.RECEPCION, usuario.getRol());
        assertEquals(12, ServiceLocator.estadisticas().pacientesTotales());
        assertEquals(7, ServiceLocator.tratamientos().listarActivos().size());
        assertEquals(2, ServiceLocator.usuarios().listarFisioterapeutas().size());
        LocalDate diaDemo = EsquemaBD.diaDeReferencia(LocalDate.now());
        assertEquals(8, ServiceLocator.citas().citasDelDia(diaDemo, null).size());
        assertThrows(ServiceException.class, () -> ServiceLocator.historial().notasDe(1),
                "Recepción no accede al historial clínico");
        ServiceLocator.auth().logout();
    }
}
