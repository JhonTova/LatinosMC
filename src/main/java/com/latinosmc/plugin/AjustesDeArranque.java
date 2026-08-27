package com.latinosmc.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The config values that are only read once, when the plugin starts.
 *
 * <p>Everything here is baked into objects that live for the whole session: the
 * API client is built with the key and the URL, and the scheduler is given the
 * intervals. A reload cannot change any of it, so the reload command compares
 * what is on disk against this snapshot and says out loud what will not take
 * effect until the server restarts.
 *
 * <p>Silence would be the dangerous option here. An owner who pastes the API key
 * into config.yml and runs the reload command has every reason to think the
 * plugin picked it up; without this warning they would go looking for the
 * problem anywhere except the one place it is.
 */
record AjustesDeArranque(String claveApi, String urlApi, int heartbeatSegundos, int sondeoSegundos, int loteMaximo) {

    AjustesDeArranque {
        claveApi = claveApi == null ? "" : claveApi;
        urlApi = urlApi == null ? "" : urlApi;
    }

    /**
     * Qué de lo que hay ahora en config.yml no se ha llegado a aplicar.
     *
     * @return una línea por cada ajuste cambiado, vacía si no cambió ninguno
     */
    List<String> cambiosSinAplicar(AjustesDeArranque enDisco) {
        List<String> avisos = new ArrayList<>();

        if (!Objects.equals(claveApi, enDisco.claveApi())) {
            avisos.add("Has cambiado 'plataforma.clave-api'. La clave se lee al arrancar: "
                    + "sigue usándose la anterior hasta que reinicies el servidor.");
        }
        if (!Objects.equals(urlApi, enDisco.urlApi())) {
            avisos.add("Has cambiado 'avanzado.url-api'. La dirección se lee al arrancar: "
                    + "sigue hablándose con la anterior hasta que reinicies el servidor.");
        }
        if (heartbeatSegundos != enDisco.heartbeatSegundos()) {
            avisos.add("Has cambiado 'plataforma.heartbeat-segundos'. Las tareas se programan al "
                    + "arrancar: no cambia hasta que reinicies.");
        }
        if (sondeoSegundos != enDisco.sondeoSegundos()) {
            avisos.add("Has cambiado 'plataforma.sondeo-segundos'. Las tareas se programan al "
                    + "arrancar: no cambia hasta que reinicies.");
        }
        if (loteMaximo != enDisco.loteMaximo()) {
            avisos.add("Has cambiado 'plataforma.lote-maximo'. Se lee al arrancar: no cambia hasta "
                    + "que reinicies.");
        }

        return avisos;
    }
}
