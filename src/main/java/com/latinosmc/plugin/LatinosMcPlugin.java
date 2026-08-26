package com.latinosmc.plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * LatinosMC vote plugin.
 *
 * <p>The platform never sends commands: it sends a reward TYPE that the owner
 * maps to a command in config.yml. That mapping is the security boundary — a
 * compromised platform still cannot run anything on this server.
 */
public final class LatinosMcPlugin extends JavaPlugin implements Listener {
    private static final String URL_OFICIAL = "https://api.latinosmc.com/api/v1";

    private ClienteApi api;
    private AlmacenDeEntregas almacen;
    private EntregadorDeRecompensas entregador;
    private int loteMaximo;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        String url = urlDeLaPlataforma();
        String clave = getConfig().getString("plataforma.clave-api", "");
        this.loteMaximo = getConfig().getInt("plataforma.lote-maximo", 50);

        if (clave.isBlank() || clave.startsWith("PON_AQUI")) {
            getLogger().severe("Falta la clave de API en config.yml. El plugin queda desactivado.");
            getLogger().severe("Copiala del panel de LatinosMC al registrar tu servidor.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.api = new ClienteApi(url, clave, Duration.ofSeconds(10));
        this.almacen = new AlmacenDeEntregas(getDataFolder(), getLogger());
        this.almacen.cargar();
        this.entregador = new EntregadorDeRecompensas(this, getConfig().getConfigurationSection("recompensas"), getLogger());

        if (entregador.tiposConfigurados() == 0) {
            getLogger().warning("No hay recompensas configuradas en config.yml: los votos no daran nada.");
        }

        getServer().getPluginManager().registerEvents(this, this);

        boolean modoOffline = !getServer().getOnlineMode();
        var comando = new ComandoVotar(this, api, new Mensajes(getConfig()), modoOffline);
        for (String nombre : new String[] {"votar", "vote"}) {
            var registrado = getCommand(nombre);
            if (registrado != null) {
                registrado.setExecutor(comando);
            }
        }
        if (modoOffline) {
            getLogger().info("Servidor en offline-mode: los votos se identifican por nombre.");
        }

        programarTareas();

        getLogger().info("LatinosMC activo. Recompensas configuradas: " + entregador.tiposConfigurados()
                + " (" + entregador.comandosDe("DEFAULT") + " comandos en DEFAULT).");
    }

    @Override
    public void onDisable() {
        if (almacen != null) {
            almacen.guardar();
        }
    }

    private String urlDeLaPlataforma() {
        String alternativa = getConfig().getString("avanzado.url-api", "");
        if (alternativa == null || alternativa.isBlank()) {
            return URL_OFICIAL;
        }
        getLogger().warning("Usando una direccion NO OFICIAL: " + alternativa);
        getLogger().warning("Si no has configurado esto a proposito, borra 'avanzado.url-api' del config.yml.");
        return alternativa;
    }

    private void programarTareas() {
        long ticksHeartbeat = 20L * getConfig().getInt("plataforma.heartbeat-segundos", 60);
        long ticksSondeo = 20L * getConfig().getInt("plataforma.sondeo-segundos", 30);

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::latir, 20L, ticksHeartbeat);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::recogerRecompensas, 40L, ticksSondeo);
    }

    private void latir() {
        try {
            api.heartbeat();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            getLogger().fine("Heartbeat fallido: " + e.getMessage());
        }
    }

    void recogerRecompensas() {
        try {
            List<ClienteApi.RecompensaEntrante> pendientes = api.pendientes(loteMaximo);
            if (pendientes.isEmpty()) {
                return;
            }

            List<String> paraConfirmar = new ArrayList<>();

            for (ClienteApi.RecompensaEntrante recompensa : pendientes) {
                if (almacen.yaAplicada(recompensa.id())) {
                    paraConfirmar.add(recompensa.id());
                    continue;
                }

                // La identidad llega con su tipo y hay que respetarlo. En un
                // servidor en online-mode el valor es un UUID: buscar por nombre
                // no encuentra a nadie nunca, y la recompensa se guardaria como
                // pendiente de un jugador que no existe.
                var identidad = new IdentidadDeJugador(recompensa.identidadTipo(), recompensa.identidadValor());
                Player jugador = identidad.buscarConectado();

                if (jugador != null && jugador.isOnline()) {
                    if (entregador.entregar(jugador, recompensa.tipo())) {
                        almacen.marcarAplicada(recompensa.id());
                        paraConfirmar.add(recompensa.id());
                    }
                } else {
                    almacen.guardarPendiente(
                            identidad,
                            new AlmacenDeEntregas.RecompensaPendiente(recompensa.id(), recompensa.tipo()));
                    almacen.marcarAplicada(recompensa.id());
                    paraConfirmar.add(recompensa.id());
                }
            }

            almacen.guardar();
            api.confirmar(paraConfirmar);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "No se pudieron recoger recompensas: " + e.getMessage());
        }
    }

    @EventHandler
    public void alEntrar(PlayerJoinEvent evento) {
        Player jugador = evento.getPlayer();

        // Se pregunta por las dos identidades: la recompensa pudo quedar guardada
        // por UUID o por nombre segun como estuviera el servidor al votar.
        List<AlmacenDeEntregas.RecompensaPendiente> deuda =
                almacen.tomarPendientes(IdentidadDeJugador.de(jugador));
        if (deuda.isEmpty()) {
            return;
        }

        List<AlmacenDeEntregas.RecompensaPendiente> sinEntregar = new ArrayList<>();
        for (AlmacenDeEntregas.RecompensaPendiente recompensa : deuda) {
            if (!entregador.entregar(jugador, recompensa.tipo())) {
                sinEntregar.add(recompensa);
            }
        }

        // Lo que no se pudo entregar vuelve a la deuda. El caso real es un tipo de
        // recompensa que todavia no esta en config.yml: descartarla aqui la
        // perderia para siempre, porque a la plataforma ya se le confirmo.
        if (!sinEntregar.isEmpty()) {
            almacen.devolverPendientes(IdentidadDeJugador.porUuid(jugador.getUniqueId()), sinEntregar);
            getLogger().warning("Quedan " + sinEntregar.size() + " recompensas sin entregar a " + jugador.getName()
                    + ": revisa la seccion 'recompensas' de config.yml.");
        }

        int entregadas = deuda.size() - sinEntregar.size();
        Bukkit.getScheduler().runTaskAsynchronously(this, almacen::guardar);
        if (entregadas > 0) {
            getLogger().info("Entregadas " + entregadas + " recompensas pendientes a " + jugador.getName());
        }
    }
}
