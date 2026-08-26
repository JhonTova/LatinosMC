package com.latinosmc.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP client for the LatinosMC API.
 *
 * <p>The base URL is compiled in on purpose. If it were configurable, someone
 * could publish an "install guide" pointing the official plugin at a rogue
 * server and harvest API keys.
 */
public final class ClienteApi {
    private final HttpClient http;
    private final String urlBase;
    private final String claveApi;

    public ClienteApi(String urlBase, String claveApi, Duration timeoutConexion) {
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
        this.claveApi = claveApi;
        this.http = HttpClient.newBuilder().connectTimeout(timeoutConexion).build();
    }

    public boolean heartbeat() throws IOException, InterruptedException {
        return enviar(peticion("/plugin/heartbeat").POST(HttpRequest.BodyPublishers.noBody()))
                        .statusCode()
                == 200;
    }

    public List<RecompensaEntrante> pendientes(int limite) throws IOException, InterruptedException {
        HttpResponse<String> respuesta = enviar(peticion("/plugin/rewards/pending?limite=" + limite).GET());
        if (respuesta.statusCode() != 200) {
            throw new IOException("La plataforma respondio " + respuesta.statusCode());
        }

        List<RecompensaEntrante> lista = new ArrayList<>();
        JsonArray array = JsonParser.parseString(respuesta.body()).getAsJsonArray();
        for (var elemento : array) {
            JsonObject o = elemento.getAsJsonObject();
            lista.add(new RecompensaEntrante(
                    o.get("id").getAsString(),
                    o.get("tipo").getAsString(),
                    o.get("identidadTipo").getAsString(),
                    o.get("identidadValor").getAsString()));
        }
        return lista;
    }

    public int confirmar(List<String> recompensaIds) throws IOException, InterruptedException {
        if (recompensaIds.isEmpty()) {
            return 0;
        }
        JsonObject cuerpo = new JsonObject();
        JsonArray ids = new JsonArray();
        recompensaIds.forEach(ids::add);
        cuerpo.add("recompensaIds", ids);

        HttpResponse<String> respuesta = enviar(peticion("/plugin/rewards/ack")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo.toString())));

        if (respuesta.statusCode() != 200) {
            throw new IOException("La plataforma respondio " + respuesta.statusCode() + " al confirmar");
        }
        return JsonParser.parseString(respuesta.body())
                .getAsJsonObject()
                .get("confirmadas")
                .getAsInt();
    }

    public Voto pedirVoto(String tipoIdentidad, String identidad) throws IOException, InterruptedException {
        JsonObject cuerpo = new JsonObject();
        cuerpo.addProperty("tipoIdentidad", tipoIdentidad);
        cuerpo.addProperty("identidad", identidad);

        HttpResponse<String> respuesta = enviar(peticion("/plugin/vote-tokens")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo.toString())));

        if (respuesta.statusCode() != 200) {
            throw new IOException("La plataforma respondio " + respuesta.statusCode() + " al pedir el enlace");
        }

        JsonObject json = JsonParser.parseString(respuesta.body()).getAsJsonObject();

        if (!json.get("puedeVotar").getAsBoolean()) {
            return Voto.noPuede(
                    texto(json, "motivo"), json.get("segundosRestantes").getAsLong());
        }

        return Voto.puede(texto(json, "token"), texto(json, "url"));
    }

    private static String texto(JsonObject json, String campo) {
        return json.has(campo) && !json.get(campo).isJsonNull() ? json.get(campo).getAsString() : null;
    }

    public record Voto(boolean puedeVotar, String codigo, String url, String motivo, long segundosRestantes) {
        static Voto puede(String codigo, String url) {
            return new Voto(true, codigo, url, null, 0);
        }

        static Voto noPuede(String motivo, long segundosRestantes) {
            return new Voto(false, null, null, motivo, segundosRestantes);
        }
    }

    private HttpRequest.Builder peticion(String ruta) {
        return HttpRequest.newBuilder()
                .uri(URI.create(urlBase + ruta))
                .header("X-Api-Key", claveApi)
                .timeout(Duration.ofSeconds(15));
    }

    private HttpResponse<String> enviar(HttpRequest.Builder builder) throws IOException, InterruptedException {
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    public record RecompensaEntrante(String id, String tipo, String identidadTipo, String identidadValor) {}
}
