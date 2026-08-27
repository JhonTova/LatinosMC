package com.latinosmc.plugin;

import java.time.Duration;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Player-facing strings, all from config.yml.
 *
 * <p>Falls back to the built-in default when a key is missing, so a plugin
 * update that adds a message cannot leave an existing server silent.
 *
 * <p>Reads through the plugin instead of holding on to a {@code FileConfiguration}:
 * a reload swaps that object, and a cached reference would keep serving the text
 * from before the reload with nothing to explain why.
 */
public final class Mensajes {
    private static final MiniMessage FORMATO = MiniMessage.miniMessage();

    private final Plugin plugin;

    public Mensajes(Plugin plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }

    public Component linea(String clave, String pordefecto, Object... sustituciones) {
        String plantilla = config().getString("mensajes." + clave, pordefecto);
        return FORMATO.deserialize(sustituir(plantilla, sustituciones));
    }

    public List<Component> bloque(String clave, List<String> pordefecto, Object... sustituciones) {
        FileConfiguration config = config();
        List<String> lineas = config.contains("mensajes." + clave)
                ? config.getStringList("mensajes." + clave)
                : pordefecto;

        return lineas.stream()
                .map(l -> FORMATO.deserialize(sustituir(l, sustituciones)))
                .toList();
    }

    private String sustituir(String plantilla, Object... sustituciones) {
        String resultado = plantilla;
        for (int i = 0; i + 1 < sustituciones.length; i += 2) {
            resultado = resultado.replace("{" + sustituciones[i] + "}", String.valueOf(sustituciones[i + 1]));
        }
        return resultado;
    }

    public static String enPalabras(Duration restante) {
        long horas = restante.toHours();
        long minutos = restante.toMinutesPart();

        if (horas <= 0 && minutos <= 0) {
            return "menos de un minuto";
        }
        if (horas <= 0) {
            return minutos + (minutos == 1 ? " minuto" : " minutos");
        }
        if (minutos <= 0) {
            return horas + (horas == 1 ? " hora" : " horas");
        }
        return horas + (horas == 1 ? " hora y " : " horas y ") + minutos + (minutos == 1 ? " minuto" : " minutos");
    }
}
