package com.latinosmc.plugin;

import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * The {@code /lmcreload} command.
 *
 * <p>Reloads only what can safely change while the server is running: the reward
 * mapping and the player-facing messages. The API key, the API address and the
 * task intervals are read once at startup, and the command says so explicitly
 * whenever it sees that one of them was edited.
 */
public final class ComandoRecargar implements CommandExecutor {
    private final LatinosMcPlugin plugin;

    public ComandoRecargar(LatinosMcPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender emisor, @NotNull Command comando, @NotNull String alias, @NotNull String[] args) {

        LatinosMcPlugin.Recarga recarga;
        try {
            recarga = plugin.recargar();
        } catch (RuntimeException e) {
            // El caso tipico es un config.yml con la indentacion rota. Se dice
            // donde mirar: el mensaje de SnakeYAML lleva linea y columna, pero
            // solo aparece en la consola.
            plugin.getLogger().log(Level.SEVERE, "No se pudo recargar config.yml", e);
            emisor.sendMessage(Component.text(
                    "No se pudo leer config.yml. Está mal escrito; el motivo exacto sale en la consola.",
                    NamedTextColor.RED));
            emisor.sendMessage(Component.text(
                    "Sigue funcionando la configuración anterior. No se ha perdido nada.", NamedTextColor.GRAY));
            return true;
        }

        emisor.sendMessage(Component.text("Recompensas y mensajes recargados.", NamedTextColor.GREEN));

        if (recarga.tipos() == 0) {
            emisor.sendMessage(Component.text(
                    "No hay ninguna recompensa configurada: los votos no darán nada.", NamedTextColor.RED));
        } else {
            emisor.sendMessage(Component.text(
                    recarga.tipos() + " tipo(s) de recompensa, " + recarga.comandosDefault() + " comando(s) en DEFAULT.",
                    NamedTextColor.GRAY));
        }

        // Lo que NO recarga este comando. Va en rojo y con el motivo, porque es
        // justo lo que alguien da por hecho que se ha aplicado.
        for (String aviso : recarga.avisos()) {
            emisor.sendMessage(Component.text(aviso, NamedTextColor.RED));
            plugin.getLogger().warning(aviso);
        }
        if (!recarga.avisos().isEmpty()) {
            emisor.sendMessage(Component.text("Reinicia el servidor para que eso se aplique.", NamedTextColor.YELLOW));
        }

        return true;
    }
}
