package com.latinosmc.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Lo que /lmcreload tiene que avisar de que NO se ha aplicado.
 *
 * <p>Callarse un cambio de clave es el fallo caro: el dueño la pega, recarga, ve
 * "recargado" en verde y se va a buscar el problema a cualquier otro sitio.
 */
class AjustesDeArranqueTest {

    private static AjustesDeArranque base() {
        return new AjustesDeArranque("clave-vieja", "", 60, 30, 50);
    }

    @Nested
    @DisplayName("cuando no ha cambiado nada")
    class SinCambios {

        @Test
        @DisplayName("no avisa de nada")
        void sinAvisos() {
            assertTrue(base().cambiosSinAplicar(base()).isEmpty());
        }

        @Test
        @DisplayName("trata null y cadena vacía como lo mismo")
        void nullEsVacio() {
            // url-api ausente del config.yml llega como null, y presente pero sin
            // valor llega como "". Distinguirlos avisaria de un cambio que no
            // existe cada vez que alguien toca el archivo.
            var conNull = new AjustesDeArranque("clave-vieja", null, 60, 30, 50);
            assertTrue(conNull.cambiosSinAplicar(base()).isEmpty());
            assertTrue(base().cambiosSinAplicar(conNull).isEmpty());
        }
    }

    @Nested
    @DisplayName("cuando se toca algo que solo se lee al arrancar")
    class ConCambios {

        @Test
        @DisplayName("avisa de la clave de API")
        void claveCambiada() {
            List<String> avisos =
                    base().cambiosSinAplicar(new AjustesDeArranque("clave-nueva", "", 60, 30, 50));

            assertEquals(1, avisos.size());
            assertTrue(avisos.get(0).contains("clave-api"), avisos.get(0));
            assertTrue(avisos.get(0).contains("reinicies"), avisos.get(0));
        }

        @Test
        @DisplayName("avisa de la dirección de la API")
        void urlCambiada() {
            List<String> avisos = base()
                    .cambiosSinAplicar(new AjustesDeArranque("clave-vieja", "http://otra/api", 60, 30, 50));

            assertEquals(1, avisos.size());
            assertTrue(avisos.get(0).contains("url-api"), avisos.get(0));
        }

        @Test
        @DisplayName("avisa de cada intervalo por separado")
        void tiemposCambiados() {
            List<String> avisos =
                    base().cambiosSinAplicar(new AjustesDeArranque("clave-vieja", "", 120, 15, 10));

            assertEquals(3, avisos.size());
            assertTrue(avisos.stream().anyMatch(a -> a.contains("heartbeat-segundos")), avisos.toString());
            assertTrue(avisos.stream().anyMatch(a -> a.contains("sondeo-segundos")), avisos.toString());
            assertTrue(avisos.stream().anyMatch(a -> a.contains("lote-maximo")), avisos.toString());
        }

        @Test
        @DisplayName("los acumula todos, no se queda en el primero")
        void variosALaVez() {
            List<String> avisos = base()
                    .cambiosSinAplicar(new AjustesDeArranque("clave-nueva", "http://otra/api", 120, 30, 50));

            assertEquals(3, avisos.size(), avisos.toString());
        }
    }
}
