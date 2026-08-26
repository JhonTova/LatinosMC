package com.latinosmc.plugin;

import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Como identifica la plataforma al jugador que voto.
 *
 * <p>Existe porque el plugin no puede tratar las dos formas igual. Un servidor
 * en online-mode identifica por UUID de Mojang; uno en offline-mode, por nombre.
 * Buscar al jugador por el valor equivocado devuelve siempre {@code null} y la
 * recompensa no llega nunca — sin ningun error visible, porque el codigo
 * "funciona": simplemente decide que el jugador no esta conectado.
 *
 * @param tipo {@code MOJANG_UUID} u {@code OFFLINE_NAME}, tal como lo manda la
 *     plataforma
 * @param valor el UUID o el nombre, segun el tipo
 */
public record IdentidadDeJugador(String tipo, String valor) {

    public static final String MOJANG_UUID = "MOJANG_UUID";
    public static final String OFFLINE_NAME = "OFFLINE_NAME";

    public IdentidadDeJugador {
        tipo = tipo == null ? OFFLINE_NAME : tipo.toUpperCase(Locale.ROOT);
        valor = valor == null ? "" : valor;
    }

    /** Identidad por UUID: la de un servidor en online-mode. */
    public static IdentidadDeJugador porUuid(UUID uuid) {
        return new IdentidadDeJugador(MOJANG_UUID, uuid.toString());
    }

    /** Identidad por nombre: la de un servidor en offline-mode. */
    public static IdentidadDeJugador porNombre(String nombre) {
        return new IdentidadDeJugador(OFFLINE_NAME, nombre);
    }

    /**
     * Clave con la que se guarda en disco.
     *
     * <p>Lleva el tipo delante para que un UUID y un nombre no puedan colisionar
     * nunca, y va en minusculas porque el servidor tampoco distingue mayusculas
     * en los nombres.
     */
    public String clave() {
        return tipo + ":" + valor.toLowerCase(Locale.ROOT);
    }

    /**
     * Busca al jugador conectado que corresponde a esta identidad.
     *
     * @return el jugador, o {@code null} si no esta conectado
     */
    public Player buscarConectado() {
        if (MOJANG_UUID.equals(tipo)) {
            try {
                return Bukkit.getPlayer(UUID.fromString(valor));
            } catch (IllegalArgumentException e) {
                // Un UUID mal formado no es un jugador desconectado: es un dato
                // que no deberia haber llegado. Se distingue al llamar.
                return null;
            }
        }
        return Bukkit.getPlayerExact(valor);
    }

    /** Las dos identidades con las que puede aparecer un jugador que acaba de entrar. */
    public static IdentidadDeJugador[] de(Player jugador) {
        return new IdentidadDeJugador[] {porUuid(jugador.getUniqueId()), porNombre(jugador.getName())};
    }
}
