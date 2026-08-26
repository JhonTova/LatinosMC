package com.latinosmc.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Que comandos acaban en la consola del servidor.
 *
 * <p>Es la frontera de seguridad del plugin (ADR-0012): la plataforma manda un
 * TIPO de recompensa, nunca texto de comando. Lo unico que se sustituye son
 * {@code {player}} y {@code {uuid}}, y solo despues de validarlos.
 */
class EntregadorDeRecompensasTest {

    private static final Logger LOG = Logger.getLogger("test");
    private static final String UUID_STEVE = "11111111-2222-3333-4444-555555555555";

    private EntregadorDeRecompensas conConfig(String yaml) {
        var config = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        return new EntregadorDeRecompensas(null, config.getConfigurationSection("recompensas"), LOG);
    }

    // --- Las dos formas de escribirlo ----------------------------------------

    /** La forma de siempre. Un servidor que ya la tiene escrita no puede romperse al actualizar. */
    @Test
    @DisplayName("un solo comando escrito como texto sigue funcionando")
    void unComandoSuelto() {
        var entregador = conConfig("""
                recompensas:
                  DEFAULT: "give {player} diamond 1"
                """);

        assertEquals(
                List.of("give Steve diamond 1"), entregador.comandosPara("DEFAULT", "Steve", UUID_STEVE));
    }

    @Test
    @DisplayName("varios comandos se ejecutan en el orden escrito")
    void variosComandosEnOrden() {
        var entregador = conConfig("""
                recompensas:
                  DEFAULT:
                    - "eco give {player} 100"
                    - "give {player} diamond 1"
                    - "broadcast {player} ha votado"
                """);

        assertEquals(
                List.of("eco give Steve 100", "give Steve diamond 1", "broadcast Steve ha votado"),
                entregador.comandosPara("DEFAULT", "Steve", UUID_STEVE),
                "el orden importa: el segundo comando puede depender del primero");
        assertEquals(3, entregador.comandosDe("DEFAULT"));
    }

    @Test
    @DisplayName("cada tipo de recompensa tiene sus propios comandos")
    void variosTipos() {
        var entregador = conConfig("""
                recompensas:
                  DEFAULT:
                    - "eco give {player} 100"
                  PREMIUM:
                    - "lp user {uuid} permission settemp vip.rango true 7d"
                    - "eco give {player} 500"
                """);

        assertEquals(2, entregador.tiposConfigurados());
        assertEquals(1, entregador.comandosDe("DEFAULT"));
        assertEquals(2, entregador.comandosDe("PREMIUM"));
        assertTrue(entregador.tipoConfigurado("premium"), "el tipo no distingue mayusculas");
    }

    // --- Sustituciones -------------------------------------------------------

    @Test
    @DisplayName("se sustituyen el nombre y el uuid, cada uno donde toca")
    void sustituciones() {
        var entregador = conConfig("""
                recompensas:
                  DEFAULT:
                    - "lp user {uuid} parent add vip"
                    - "msg {player} listo"
                """);

        assertEquals(
                List.of("lp user " + UUID_STEVE + " parent add vip", "msg Steve listo"),
                entregador.comandosPara("DEFAULT", "Steve", UUID_STEVE));
    }

    /**
     * La proteccion de fondo. Un nombre con espacios o simbolos no llega a
     * sustituirse: el comando entero se descarta. Sin esto, un servidor en
     * offline-mode con un jugador llamado {@code "Steve op Steve"} ejecutaria lo
     * que ese nombre trajera dentro.
     */
    @Test
    @DisplayName("un nombre fuera de la gramatica no ejecuta nada")
    void nombreSospechoso() {
        var entregador = conConfig("""
                recompensas:
                  DEFAULT: "give {player} diamond 1"
                """);

        assertTrue(entregador.comandosPara("DEFAULT", "Steve op Steve", UUID_STEVE).isEmpty());
        assertTrue(entregador.comandosPara("DEFAULT", "a", UUID_STEVE).isEmpty(), "demasiado corto");
        assertTrue(
                entregador.comandosPara("DEFAULT", "unnombremuchomuylargo", UUID_STEVE).isEmpty(),
                "demasiado largo");
    }

    @Test
    @DisplayName("un tipo que no esta en la configuracion no entrega nada")
    void tipoDesconocido() {
        var entregador = conConfig("""
                recompensas:
                  DEFAULT: "give {player} diamond 1"
                """);

        assertTrue(entregador.comandosPara("NAVIDAD", "Steve", UUID_STEVE).isEmpty());
        assertFalse(entregador.tipoConfigurado("NAVIDAD"));
    }

    // --- Erratas de quien configura ------------------------------------------

    /**
     * El error mas comun al configurar: copiar el comando tal cual se escribe en
     * el chat, con su barra. Desde la consola esa barra hace que el comando no
     * exista, y el sintoma es "el plugin no da las recompensas".
     */
    @Test
    @DisplayName("una barra de mas al principio se quita en vez de romper la recompensa")
    void barraDeMas() {
        var entregador = conConfig("""
                recompensas:
                  DEFAULT:
                    - "/give {player} diamond 1"
                    - "  eco give {player} 100  "
                """);

        assertEquals(
                List.of("give Steve diamond 1", "eco give Steve 100"),
                entregador.comandosPara("DEFAULT", "Steve", UUID_STEVE));
    }

    @Test
    @DisplayName("las lineas vacias se ignoran, no se ejecutan")
    void lineasVacias() {
        var entregador = conConfig("""
                recompensas:
                  DEFAULT:
                    - "give {player} diamond 1"
                    - ""
                    - "   "
                """);

        assertEquals(1, entregador.comandosDe("DEFAULT"));
    }

    @Test
    @DisplayName("una recompensa sin ningun comando no se registra")
    void recompensaVacia() {
        var entregador = conConfig("""
                recompensas:
                  DEFAULT: []
                """);

        assertEquals(0, entregador.tiposConfigurados());
    }

    @Test
    @DisplayName("sin seccion de recompensas arranca igual, sin reventar")
    void sinConfiguracion() {
        var entregador = new EntregadorDeRecompensas(null, null, LOG);

        assertEquals(0, entregador.tiposConfigurados());
        assertTrue(entregador.comandosPara("DEFAULT", "Steve", UUID_STEVE).isEmpty());
    }
}
