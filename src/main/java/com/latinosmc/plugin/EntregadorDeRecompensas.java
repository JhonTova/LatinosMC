package com.latinosmc.plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Runs the commands mapped to a reward type.
 *
 * <p>Only {@code {player}} and {@code {uuid}} are substituted, and only after
 * validating them against their grammar. Command text never comes from the
 * platform.
 */
public final class EntregadorDeRecompensas {
    private static final Pattern NOMBRE_VALIDO = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private final Plugin plugin;
    private final Logger log;

    private final Map<String, List<String>> comandosPorTipo = new LinkedHashMap<>();

    public EntregadorDeRecompensas(Plugin plugin, ConfigurationSection seccionRecompensas, Logger log) {
        this.plugin = plugin;
        this.log = log;

        if (seccionRecompensas == null) {
            return;
        }

        for (String tipo : seccionRecompensas.getKeys(false)) {
            List<String> comandos = leerComandos(seccionRecompensas, tipo);
            if (comandos.isEmpty()) {
                log.warning("La recompensa '" + tipo + "' no tiene ningun comando. No entregara nada.");
                continue;
            }
            comandosPorTipo.put(tipo.toUpperCase(Locale.ROOT), comandos);
        }
    }

    /**
     * Acepta las dos formas de escribir una recompensa en config.yml.
     *
     * <pre>
     *   DEFAULT: "give {player} diamond 1"        # un comando
     *
     *   DEFAULT:                                   # varios, en orden
     *     - "give {player} diamond 1"
     *     - "eco give {player} 100"
     * </pre>
     *
     * <p>La forma de un solo comando se mantiene por los servidores que ya la
     * tienen escrita: una actualizacion del plugin no puede dejar de entregar
     * recompensas porque el formato del archivo haya cambiado.
     */
    private List<String> leerComandos(ConfigurationSection seccion, String tipo) {
        List<String> comandos = new ArrayList<>();

        if (seccion.isList(tipo)) {
            for (String comando : seccion.getStringList(tipo)) {
                anadirSiUtil(comandos, comando, tipo);
            }
        } else {
            anadirSiUtil(comandos, seccion.getString(tipo), tipo);
        }

        return comandos;
    }

    private void anadirSiUtil(List<String> comandos, String comando, String tipo) {
        if (comando == null || comando.isBlank()) {
            return;
        }
        // Una barra al principio es el error tipico al copiar el comando desde el
        // chat. La consola no la lleva, y con ella el comando no existe.
        String limpio = comando.strip();
        if (limpio.startsWith("/")) {
            limpio = limpio.substring(1).strip();
            log.warning("La recompensa '" + tipo + "' tiene un comando que empieza por '/'. Se ejecuta sin ella: "
                    + limpio);
        }
        comandos.add(limpio);
    }

    /**
     * Ejecuta los comandos de una recompensa, en el orden escrito.
     *
     * @return {@code false} si el tipo no esta configurado o el nombre del jugador
     *     no es valido; en ambos casos no se ejecuta nada
     */
    public boolean entregar(Player jugador, String tipoRecompensa) {
        List<String> comandos =
                comandosPara(tipoRecompensa, jugador.getName(), jugador.getUniqueId().toString());
        if (comandos.isEmpty()) {
            return false;
        }

        // Todos en la misma tarea del hilo principal: si la recompensa son tres
        // comandos que dependen del anterior —crear la cuenta, ingresar, avisar—
        // repartirlos en tareas distintas los dejaria a merced de lo que se cuele
        // en medio.
        Bukkit.getScheduler().getMainThreadExecutor(plugin).execute(() -> ejecutar(comandos, tipoRecompensa));

        return true;
    }

    /**
     * Los comandos ya listos para ejecutar, o una lista vacia si no hay nada que
     * entregar.
     *
     * <p>Separado de {@link #entregar} porque es la parte que decide que texto
     * acaba en la consola del servidor, y eso hay que poder probarlo sin
     * levantar un servidor entero.
     */
    List<String> comandosPara(String tipoRecompensa, String nombre, String uuid) {
        List<String> plantillas = comandosPorTipo.get(tipoRecompensa.toUpperCase(Locale.ROOT));
        if (plantillas == null) {
            log.warning("Recompensa de tipo '" + tipoRecompensa
                    + "' recibida pero no configurada en config.yml. No se entrega nada.");
            return List.of();
        }

        if (!NOMBRE_VALIDO.matcher(nombre).matches()) {
            log.severe("Nombre de jugador fuera de la gramatica esperada, no se entrega recompensa: " + nombre);
            return List.of();
        }

        List<String> comandos = new ArrayList<>(plantillas.size());
        for (String plantilla : plantillas) {
            comandos.add(plantilla.replace("{player}", nombre).replace("{uuid}", uuid));
        }
        return comandos;
    }

    private void ejecutar(List<String> comandos, String tipoRecompensa) {
        for (String comando : comandos) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), comando);
            } catch (RuntimeException e) {
                // Un comando que revienta no puede llevarse por delante a los
                // siguientes: si el segundo de tres falla, el jugador debe
                // quedarse igualmente con el primero y el tercero.
                log.severe("Fallo al ejecutar un comando de la recompensa '" + tipoRecompensa + "': " + comando);
                log.severe("Motivo: " + e.getMessage() + ". Comprueba que el plugin que lo atiende esta instalado.");
            }
        }
    }

    public boolean tipoConfigurado(String tipoRecompensa) {
        return comandosPorTipo.containsKey(tipoRecompensa.toUpperCase(Locale.ROOT));
    }

    public int tiposConfigurados() {
        return comandosPorTipo.size();
    }

    /** Cuantos comandos ejecuta un tipo. Se usa al arrancar, para dejarlo en el log. */
    public int comandosDe(String tipoRecompensa) {
        List<String> comandos = comandosPorTipo.get(tipoRecompensa.toUpperCase(Locale.ROOT));
        return comandos == null ? 0 : comandos.size();
    }
}
