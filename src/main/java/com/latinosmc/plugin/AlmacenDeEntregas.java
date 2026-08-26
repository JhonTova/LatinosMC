package com.latinosmc.plugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * On-disk record of delivered rewards.
 *
 * <p>Must survive restarts. Delivery is at-least-once: the platform retries
 * until acknowledged, so a memory-only store would duplicate rewards after
 * every restart.
 */
public final class AlmacenDeEntregas {
    private static final int DIAS_MEMORIA = 30;

    /**
     * Cuanto se guarda la recompensa de alguien que no vuelve.
     *
     * <p>Mas generoso que la memoria de entregas: quien vota y se va de vacaciones
     * merece encontrarse su recompensa al volver. Pasado ese plazo se descarta y
     * se deja escrito en el log, porque el archivo no puede crecer para siempre.
     */
    private static final int DIAS_ESPERANDO = 90;

    private final File archivo;
    private final Logger log;

    private final Map<String, Instant> aplicadas = new ConcurrentHashMap<>();

    private final Map<String, List<RecompensaPendiente>> pendientes = new ConcurrentHashMap<>();

    public AlmacenDeEntregas(File carpetaDelPlugin, Logger log) {
        this.archivo = new File(carpetaDelPlugin, "entregas.yml");
        this.log = log;
    }

    public boolean yaAplicada(String recompensaId) {
        return aplicadas.containsKey(recompensaId);
    }

    public void marcarAplicada(String recompensaId) {
        aplicadas.put(recompensaId, Instant.now());
    }

    public void guardarPendiente(IdentidadDeJugador identidad, RecompensaPendiente recompensa) {
        pendientes
                .computeIfAbsent(identidad.clave(), k -> new ArrayList<>())
                .add(recompensa);
    }

    /**
     * Recoge y descuenta la deuda de un jugador.
     *
     * <p>Se consultan sus dos identidades posibles porque el servidor puede haber
     * cambiado de online-mode a offline-mode —o al reves— entre el voto y la
     * vuelta del jugador. Su recompensa quedo guardada con la identidad de
     * entonces, y perderla por un cambio de configuracion del dueno seria un
     * fallo nuestro, no suyo.
     */
    public List<RecompensaPendiente> tomarPendientes(IdentidadDeJugador... identidades) {
        List<RecompensaPendiente> todas = new ArrayList<>();
        for (IdentidadDeJugador identidad : identidades) {
            List<RecompensaPendiente> lista = pendientes.remove(identidad.clave());
            if (lista != null) {
                todas.addAll(lista);
            }
        }
        return todas;
    }

    /** Devuelve una deuda que no se pudo entregar, para no perderla. */
    public void devolverPendientes(IdentidadDeJugador identidad, List<RecompensaPendiente> sinEntregar) {
        if (sinEntregar.isEmpty()) {
            return;
        }
        pendientes
                .computeIfAbsent(identidad.clave(), k -> new ArrayList<>())
                .addAll(sinEntregar);
    }

    public int totalPendientes() {
        return pendientes.values().stream().mapToInt(List::size).sum();
    }

    public void cargar() {
        if (!archivo.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(archivo);

        ConfigurationSection seccionAplicadas = yaml.getConfigurationSection("aplicadas");
        if (seccionAplicadas != null) {
            for (String id : seccionAplicadas.getKeys(false)) {
                aplicadas.put(id, Instant.ofEpochSecond(seccionAplicadas.getLong(id)));
            }
        }

        ConfigurationSection seccionPendientes = yaml.getConfigurationSection("pendientes");
        if (seccionPendientes != null) {
            for (String clave : seccionPendientes.getKeys(false)) {
                List<RecompensaPendiente> lista = new ArrayList<>();
                for (Map<?, ?> mapa : seccionPendientes.getMapList(clave)) {
                    lista.add(new RecompensaPendiente(
                            String.valueOf(mapa.get("id")),
                            String.valueOf(mapa.get("tipo")),
                            momentoDe(mapa.get("guardada"))));
                }
                if (!lista.isEmpty()) {
                    pendientes.put(clavePosiblementeAntigua(clave), lista);
                }
            }
        }

        podar();
        log.info("Estado cargado: " + aplicadas.size() + " entregas recordadas, " + totalPendientes()
                + " pendientes.");
    }

    public synchronized void guardar() {
        podar();

        YamlConfiguration yaml = new YamlConfiguration();

        Map<String, Object> mapaAplicadas = new LinkedHashMap<>();
        aplicadas.forEach((id, momento) -> mapaAplicadas.put(id, momento.getEpochSecond()));
        yaml.createSection("aplicadas", mapaAplicadas);

        ConfigurationSection seccion = yaml.createSection("pendientes");
        pendientes.forEach((clave, lista) -> {
            List<Map<String, Object>> serializada = new ArrayList<>();
            for (RecompensaPendiente r : lista) {
                Map<String, Object> mapa = new HashMap<>();
                mapa.put("id", r.id());
                mapa.put("tipo", r.tipo());
                mapa.put("guardada", r.guardadaEn().getEpochSecond());
                serializada.add(mapa);
            }
            seccion.set(clave, serializada);
        });

        try {
            yaml.save(archivo);
        } catch (IOException e) {
            log.log(Level.SEVERE, "No se pudo guardar entregas.yml. Riesgo de recompensas duplicadas al reiniciar.", e);
        }
    }

    private void podar() {
        Instant limiteAplicadas = Instant.now().minus(DIAS_MEMORIA, ChronoUnit.DAYS);
        Set<String> caducados = aplicadas.entrySet().stream()
                .filter(e -> e.getValue().isBefore(limiteAplicadas))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        caducados.forEach(aplicadas::remove);

        Instant limiteEspera = Instant.now().minus(DIAS_ESPERANDO, ChronoUnit.DAYS);
        int descartadas = 0;
        for (Map.Entry<String, List<RecompensaPendiente>> entrada : pendientes.entrySet()) {
            List<RecompensaPendiente> vivas = entrada.getValue().stream()
                    .filter(r -> r.guardadaEn().isAfter(limiteEspera))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

            descartadas += entrada.getValue().size() - vivas.size();

            if (vivas.isEmpty()) {
                pendientes.remove(entrada.getKey());
            } else if (vivas.size() != entrada.getValue().size()) {
                pendientes.put(entrada.getKey(), vivas);
            }
        }
        if (descartadas > 0) {
            log.info("Descartadas " + descartadas + " recompensas que llevaban mas de " + DIAS_ESPERANDO
                    + " dias esperando a un jugador que no volvio.");
        }
    }

    private static Instant momentoDe(Object valor) {
        if (valor instanceof Number numero) {
            return Instant.ofEpochSecond(numero.longValue());
        }
        // Archivo escrito por una version anterior, que no guardaba la fecha. Se
        // toma como recien guardada: mejor conservarla de mas que tirarla.
        return Instant.now();
    }

    /**
     * Traduce las claves del formato antiguo, que no llevaban el tipo delante.
     *
     * <p>Un servidor que actualiza el plugin tiene su archivo escrito asi. Sin
     * esta traduccion, esas recompensas quedarian guardadas con una clave que ya
     * nadie consulta: lo que el jugador se gano con su voto y nunca recibe.
     *
     * <p>El tipo se deduce de la forma del valor. La version anterior guardaba
     * <em>tal cual</em> lo que mandaba la plataforma, asi que en un servidor
     * premium esas claves son UUID y hay que reconocerlas como tales: tomarlas
     * por nombres las dejaria en un cajon que nadie abre.
     */
    private static String clavePosiblementeAntigua(String clave) {
        boolean yaTieneTipo = clave.startsWith(IdentidadDeJugador.MOJANG_UUID + ":")
                || clave.startsWith(IdentidadDeJugador.OFFLINE_NAME + ":");
        if (yaTieneTipo) {
            return clave;
        }

        String tipo = FORMA_DE_UUID.matcher(clave).matches()
                ? IdentidadDeJugador.MOJANG_UUID
                : IdentidadDeJugador.OFFLINE_NAME;

        return tipo + ":" + clave.toLowerCase(Locale.ROOT);
    }

    private static final java.util.regex.Pattern FORMA_DE_UUID = java.util.regex.Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /**
     * @param guardadaEn cuando quedo esperando; sirve para no guardarla eternamente
     */
    public record RecompensaPendiente(String id, String tipo, Instant guardadaEn) {

        public RecompensaPendiente(String id, String tipo) {
            this(id, tipo, Instant.now());
        }
    }
}
