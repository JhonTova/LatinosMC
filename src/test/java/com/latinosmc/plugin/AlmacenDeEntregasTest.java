package com.latinosmc.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AlmacenDeEntregasTest {

    private static final Logger LOG = Logger.getLogger("test");
    private static final UUID UUID_STEVE = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @TempDir
    File carpeta;

    private AlmacenDeEntregas nuevoAlmacen() {
        AlmacenDeEntregas almacen = new AlmacenDeEntregas(carpeta, LOG);
        almacen.cargar();
        return almacen;
    }

    private static IdentidadDeJugador nombre(String valor) {
        return IdentidadDeJugador.porNombre(valor);
    }

    private static IdentidadDeJugador uuid(UUID valor) {
        return IdentidadDeJugador.porUuid(valor);
    }

    /**
     * El test mas importante del plugin.
     *
     * <p>La plataforma entrega al-menos-una-vez: si el servidor se cae despues de
     * dar la recompensa pero antes de confirmarla, la reenviara. Si la memoria de
     * lo ya aplicado viviera solo en RAM, cada reinicio repartiria recompensas
     * duplicadas — un fallo silencioso, dificil de diagnosticar y explotable a
     * proposito por el dueno del servidor.
     */
    @Test
    @DisplayName("recuerda lo ya entregado despues de reiniciar el servidor")
    void noDuplicaTrasReinicio() {
        AlmacenDeEntregas antes = nuevoAlmacen();
        antes.marcarAplicada("recompensa-1");
        antes.guardar();

        // Simula el reinicio: instancia nueva leyendo del mismo disco.
        AlmacenDeEntregas despues = nuevoAlmacen();

        assertTrue(despues.yaAplicada("recompensa-1"), "tras reiniciar debe seguir recordando lo entregado");
        assertFalse(despues.yaAplicada("recompensa-2"));
    }

    /**
     * El caso mas comun: se vota desde la web, no desde el juego. El jugador casi
     * nunca esta conectado cuando llega su recompensa.
     */
    @Test
    @DisplayName("guarda recompensas de jugadores desconectados y sobreviven al reinicio")
    void pendientesSobrevivenAlReinicio() {
        AlmacenDeEntregas antes = nuevoAlmacen();
        antes.guardarPendiente(nombre("Steve"), new AlmacenDeEntregas.RecompensaPendiente("r1", "DEFAULT"));
        antes.guardarPendiente(nombre("Steve"), new AlmacenDeEntregas.RecompensaPendiente("r2", "DEFAULT"));
        antes.guardar();

        AlmacenDeEntregas despues = nuevoAlmacen();
        var deuda = despues.tomarPendientes(nombre("Steve"));

        assertEquals(2, deuda.size());
        assertEquals("r1", deuda.get(0).id());
    }

    @Test
    @DisplayName("tomar las pendientes las descuenta")
    void tomarVaciaLaDeuda() {
        AlmacenDeEntregas almacen = nuevoAlmacen();
        almacen.guardarPendiente(nombre("Steve"), new AlmacenDeEntregas.RecompensaPendiente("r1", "DEFAULT"));

        assertEquals(1, almacen.tomarPendientes(nombre("Steve")).size());
        assertEquals(0, almacen.tomarPendientes(nombre("Steve")).size(), "no debe entregarse dos veces");
    }

    /** Igual que en el servidor, "Steve" y "steve" son el mismo jugador. */
    @Test
    @DisplayName("las mayusculas del nombre no pierden la recompensa")
    void insensibleAMayusculas() {
        AlmacenDeEntregas almacen = nuevoAlmacen();
        almacen.guardarPendiente(nombre("Steve"), new AlmacenDeEntregas.RecompensaPendiente("r1", "DEFAULT"));

        assertEquals(1, almacen.tomarPendientes(nombre("STEVE")).size());
    }

    @Test
    @DisplayName("un jugador sin recompensas devuelve lista vacia, no error")
    void jugadorSinDeuda() {
        assertTrue(nuevoAlmacen().tomarPendientes(nombre("Desconocido")).isEmpty());
    }

    @Test
    @DisplayName("arranca sin problemas cuando no existe el archivo")
    void primeraEjecucion() {
        AlmacenDeEntregas almacen = nuevoAlmacen();
        assertEquals(0, almacen.totalPendientes());
        assertFalse(almacen.yaAplicada("cualquiera"));
    }

    // --- Identidad -----------------------------------------------------------

    /**
     * El servidor en online-mode identifica por UUID; el de offline-mode, por
     * nombre. Si las dos claves se mezclaran, un jugador podria recoger la
     * recompensa de otro por tener un nombre parecido a un UUID, y —mucho mas
     * probable— la suya propia se guardaria donde nadie la busca.
     */
    @Test
    @DisplayName("el UUID y el nombre son cajones distintos")
    void identidadesNoSeMezclan() {
        AlmacenDeEntregas almacen = nuevoAlmacen();
        almacen.guardarPendiente(uuid(UUID_STEVE), new AlmacenDeEntregas.RecompensaPendiente("r-uuid", "DEFAULT"));
        almacen.guardarPendiente(nombre("Steve"), new AlmacenDeEntregas.RecompensaPendiente("r-nombre", "DEFAULT"));

        assertEquals(1, almacen.tomarPendientes(uuid(UUID_STEVE)).size());
        assertEquals(1, almacen.tomarPendientes(nombre("Steve")).size());
    }

    /**
     * Lo que se rompia antes de existir este test: en online-mode la plataforma
     * manda el UUID, se guardaba con esa clave y al entrar se buscaba por nombre.
     * Nunca coincidian, asi que en un servidor premium —la mayoria— no llegaba
     * ni una sola recompensa.
     */
    @Test
    @DisplayName("al entrar se recoge la deuda este guardada por UUID o por nombre")
    void alEntrarSeMiranLasDosIdentidades() {
        AlmacenDeEntregas almacen = nuevoAlmacen();
        almacen.guardarPendiente(uuid(UUID_STEVE), new AlmacenDeEntregas.RecompensaPendiente("r-uuid", "DEFAULT"));
        almacen.guardarPendiente(nombre("Steve"), new AlmacenDeEntregas.RecompensaPendiente("r-nombre", "DEFAULT"));

        var deuda = almacen.tomarPendientes(uuid(UUID_STEVE), nombre("Steve"));

        assertEquals(2, deuda.size(), "un jugador que entra debe recoger las dos");
        assertEquals(0, almacen.totalPendientes());
    }

    /**
     * Un servidor que ya tenia el plugin instalado guarda su archivo con el
     * formato antiguo: la clave era solo el nombre. Si al actualizar dejaran de
     * leerse, esas recompensas desaparecerian sin que nadie se entere — ya
     * estaban confirmadas a la plataforma.
     */
    @Test
    @DisplayName("lee el archivo de una version anterior sin perder nada")
    void compatibilidadConElFormatoAntiguo() throws IOException {
        // La fecha se calcula: escrita a mano quedaria vieja con el tiempo y la
        // poda de 30 dias se la llevaria, haciendo fallar el test por el motivo
        // equivocado.
        Files.writeString(
                new File(carpeta, "entregas.yml").toPath(),
                """
                aplicadas:
                  vieja-1: %d
                pendientes:
                  steve:
                  - id: r-antigua
                    tipo: DEFAULT
                """
                        .formatted(Instant.now().getEpochSecond()));

        AlmacenDeEntregas almacen = nuevoAlmacen();

        assertTrue(almacen.yaAplicada("vieja-1"));
        assertEquals(1, almacen.tomarPendientes(nombre("Steve")).size(), "la deuda antigua tiene que seguir ahi");
    }

    /**
     * El caso concreto del servidor que ya tiene el plugin instalado con el
     * fallo: al ser premium, todo lo que guardo son UUID sueltos como clave. Al
     * actualizar hay que reconocerlos por su forma, o el arreglo no le devuelve
     * nada de lo acumulado.
     */
    @Test
    @DisplayName("las claves antiguas con forma de UUID se recuperan como UUID")
    void migracionDeClavesConFormaDeUuid() throws IOException {
        Files.writeString(
                new File(carpeta, "entregas.yml").toPath(),
                """
                pendientes:
                  %s:
                  - id: r-atrapada
                    tipo: DEFAULT
                """
                        .formatted(UUID_STEVE));

        AlmacenDeEntregas almacen = nuevoAlmacen();

        assertEquals(
                1,
                almacen.tomarPendientes(uuid(UUID_STEVE)).size(),
                "lo guardado por la version con el fallo tiene que poder entregarse");
    }

    // --- Poda ----------------------------------------------------------------

    @Test
    @DisplayName("una recompensa que lleva anos esperando se descarta")
    void laDeudaNoCreceParaSiempre() {
        AlmacenDeEntregas almacen = nuevoAlmacen();
        almacen.guardarPendiente(
                nombre("Fantasma"),
                new AlmacenDeEntregas.RecompensaPendiente(
                        "r-vieja", "DEFAULT", Instant.now().minus(200, ChronoUnit.DAYS)));
        almacen.guardarPendiente(nombre("Steve"), new AlmacenDeEntregas.RecompensaPendiente("r-nueva", "DEFAULT"));

        almacen.guardar();

        assertEquals(1, almacen.totalPendientes(), "solo debe quedar la reciente");
        assertEquals(1, almacen.tomarPendientes(nombre("Steve")).size());
    }

    @Test
    @DisplayName("lo que no se pudo entregar vuelve a la deuda")
    void loNoEntregadoNoSePierde() {
        AlmacenDeEntregas almacen = nuevoAlmacen();
        var sinEntregar = List.of(new AlmacenDeEntregas.RecompensaPendiente("r1", "TIPO_QUE_NO_EXISTE"));

        almacen.devolverPendientes(uuid(UUID_STEVE), sinEntregar);

        assertEquals(1, almacen.tomarPendientes(uuid(UUID_STEVE)).size());
    }
}
