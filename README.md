# LatinosMC

Plugin de Paper que entrega las recompensas de los votos que tu servidor recibe
en [latinosmc.com](https://latinosmc.com).

El jugador escribe `/votar`, recibe un enlace y un código, vota en la web, y la
recompensa le llega sola al minuto. No hay un segundo comando que reclamar.

[![build](https://github.com/JhonTova/LatinosMC/actions/workflows/build.yml/badge.svg)](https://github.com/JhonTova/LatinosMC/actions/workflows/build.yml)
[![última versión](https://img.shields.io/github/v/release/JhonTova/LatinosMC?label=versi%C3%B3n)](https://github.com/JhonTova/LatinosMC/releases/latest)
[![descargas](https://img.shields.io/github/downloads/JhonTova/LatinosMC/total?label=descargas)](https://github.com/JhonTova/LatinosMC/releases/latest)

---

## ⬇️ Descargar el plugin

### **[→ Descargar la última versión](https://github.com/JhonTova/LatinosMC/releases/latest)**

Ese enlace abre la última publicación del plugin. Baja hasta el apartado
**Assets**, al final de la página, y descarga el archivo `LatinosMC-<versión>.jar`:

```
▾ Assets
   LatinosMC-0.1.0.jar     ← este es el plugin
   Source code (zip)
   Source code (tar.gz)
```

Es lo único que necesitas. Los dos `Source code` son el código fuente
comprimido: no sirven para instalar, solo para leerlo o compilarlo.

Si prefieres compilarlo tú mismo, está explicado en
[Compilar desde el código](#compilar-desde-el-código).

---

## Requisitos

| Necesitas | Versión |
|---|---|
| Servidor | Paper 1.21 o superior (vale cualquier fork: Purpur, Pufferfish) |
| Java | 21 |
| Cuenta | Un servidor registrado en latinosmc.com, con su clave de API |

Spigot y CraftBukkit no están soportados: el plugin usa la API de Adventure para
los mensajes, y en Spigot esa API no existe.

Folia tampoco está soportado todavía. El plugin no declara `folia-supported`
porque no se ha probado en un servidor con hilos por región, y declararlo sin
probarlo provocaría corrupción de estado.

---

## Instalación

1. Descarga el `.jar` desde la
   [última release](https://github.com/JhonTova/LatinosMC/releases/latest)
   (apartado **Assets**).
2. Déjalo en la carpeta `plugins/` de tu servidor.
3. Arranca el servidor una vez. Se genera `plugins/LatinosMC/config.yml` y el
   plugin se desactiva solo, avisando de que falta la clave.
4. Abre el panel de latinosmc.com, registra tu servidor y copia la clave de API.
5. Pégala en `clave-api`, dentro de `config.yml`.
6. Reinicia el servidor. Tiene que ser un reinicio: la clave se lee al arrancar,
   y `/lmcreload` no la recarga (ver [`/lmcreload`](#lmcreload-qué-recarga-y-qué-no)).

La clave se muestra una sola vez. Trátala como una contraseña: quien la tenga
puede pedir los votos de tu servidor y quedarse con sus recompensas. Si la
pierdes, genera una nueva desde el panel.

---

## Comandos

| Comando | Alias | Permiso | Qué hace |
|---|---|---|---|
| `/votar` | `/votelmc`, `/votarlmc` | ninguno | Genera el enlace y el código de voto del jugador |
| `/vote` | — | ninguno | Lo mismo, para servidores con jugadores en inglés |
| `/lmcreload` | `/latinosmcreload`, `/lmcrecargar` | `latinosmc.reload` (op) | Recarga las recompensas y los mensajes |

`/votar` y `/vote` no declaran ningún permiso: los puede usar cualquiera, que es
justo lo que se busca. Para restringirlos hace falta un bloqueador de comandos
externo; no basta con el gestor de permisos.

Hay una espera de 3 segundos entre dos `/votar` del mismo jugador. No es por
comodidad: cada ejecución pide un código a la plataforma, y la plataforma limita
el tráfico por servidor. Un jugador repitiendo el comando sin parar consumiría el
cupo de todos los demás.

### `/lmcreload`: qué recarga y qué no

> **`/lmcreload` NO recarga la conexión con la plataforma.** Recarga las
> recompensas y los mensajes. La clave de API, la dirección de la API y los
> intervalos se leen **una sola vez, al arrancar el servidor**. Si has tocado
> alguno de esos, **reinicia**: recargar no lo aplica.

| En `config.yml` | ¿Lo aplica `/lmcreload`? |
|---|---|
| `recompensas:` (tipos y comandos) | ✅ Sí, al instante |
| `mensajes:` (todo lo que ve el jugador) | ✅ Sí, al instante |
| `voto.espera-segundos` | ✅ Sí, se lee en cada `/votar` |
| `plataforma.clave-api` | ❌ No — hay que reiniciar |
| `avanzado.url-api` | ❌ No — hay que reiniciar |
| `plataforma.heartbeat-segundos` | ❌ No — hay que reiniciar |
| `plataforma.sondeo-segundos` | ❌ No — hay que reiniciar |
| `plataforma.lote-maximo` | ❌ No — hay que reiniciar |

No hace falta que te acuerdes de esta tabla: el comando compara lo que hay en el
archivo con lo que se cargó al arrancar y **te dice en rojo lo que has cambiado
y no se ha aplicado**. Si no te avisa de nada, es que todo lo que tocaste ya
está en marcha.

La razón de que la conexión no se recargue no es pereza. Rehacer el cliente en
caliente significa cortar el sondeo a mitad de un lote y volver a autenticarse
con una clave que puede estar mal escrita. El síntoma —los votos dejan de
llegar— aparecería mucho después, sin nada que lo relacionase con la recarga.

**Caso especial: pusiste la clave después de arrancar el servidor.** Ahí
`/lmcreload` no te vale, y no porque no quiera: si arrancas sin clave, el plugin
**se desactiva solo**, y un plugin desactivado no atiende ningún comando. Pega la
clave y reinicia.

Si `config.yml` está mal escrito (la indentación suele ser la culpable), la
recarga se cancela, te lo dice y **sigue funcionando la configuración anterior**:
un archivo roto no puede dejarte sin entregar recompensas.

---

## Configuración

Todo vive en `plugins/LatinosMC/config.yml`. El archivo va comentado línea por
línea; aquí queda solo el resumen.

### Recompensas

Es la parte que hay que tocar sí o sí. La plataforma nunca manda comandos: manda
un **tipo** de recompensa, y tú decides aquí qué ejecuta cada tipo.

```yaml
recompensas:
  DEFAULT:
    - "give {player} diamond 1"
    - "eco give {player} 100"
    - "crate give {player} voto 1"
    - "broadcast &a{player} ha votado. &7¡Gracias!"
```

Con un solo comando también vale la forma corta:

```yaml
recompensas:
  DEFAULT: "give {player} diamond 1"
```

Detalles que importan:

- Los comandos se ejecutan **en orden**, de arriba abajo, desde la consola.
- Van **sin la barra** delante. Si la pones, el plugin la quita y lo avisa en el
  log.
- Si uno falla, los demás se ejecutan igual. Que te falte un plugin no puede
  dejar al jugador sin el resto de su recompensa.
- Las únicas sustituciones son `{player}` y `{uuid}`.
- `DEFAULT` es el único tipo que manda la plataforma hoy. Puedes declarar más
  (`PREMIUM`, `NAVIDAD`) para cuando haya recompensas especiales. Un tipo que
  llegue y no esté configurado no entrega nada y queda anotado en el log.
- Al cambiarlas basta con `/lmcreload`; no hace falta reiniciar.

### Mensajes

Todo lo que ve el jugador se edita en la sección `mensajes`, en formato
[MiniMessage](https://docs.advntr.dev/minimessage/format.html). Si borras una
clave, se usa el texto por defecto; si la dejas como lista vacía, no se envía
nada.

Sustituciones disponibles: `{codigo}`, `{sitio}`, `{espera}` y `{restante}`.

### Tiempos

```yaml
plataforma:
  heartbeat-segundos: 60   # cada cuánto confirma el plugin que sigue vivo
  sondeo-segundos: 30      # cada cuánto pregunta por recompensas nuevas
  lote-maximo: 50          # cuántas recoge de una vez

voto:
  espera-segundos: 60      # margen tras dar el código antes de buscar la recompensa
```

Los 60 segundos de `espera-segundos` dan margen de sobra para abrir el navegador
y votar. Bajarlo demasiado hace que el jugador todavía no haya votado cuando se
compruebe, y tenga que esperar al sondeo normal.

---

## Cómo funciona por dentro

### El modelo de seguridad

El plugin corre en hardware que la plataforma no controla, y la plataforma corre
en hardware que el dueño del servidor no controla. El diseño parte de que
ninguno de los dos se fía del otro.

La consecuencia práctica es que **la plataforma nunca envía texto de comando**.
Envía un identificador de tipo (`DEFAULT`) y el plugin lo busca en un mapa que
escribió el dueño del servidor. Si la plataforma quedara comprometida, lo peor
que podría hacer es entregar diamantes de más: no tiene ninguna vía para
ejecutar algo arbitrario en tu consola.

La segunda barrera es el nombre del jugador. Antes de sustituir `{player}` se
valida contra `^[A-Za-z0-9_]{3,16}$`, y un nombre fuera de esa gramática cancela
la entrega entera. Sin eso, un servidor en offline-mode con nombres libres sería
una vía de inyección de comandos directa.

La tercera es la dirección de la API, que va compilada dentro del `.jar`. Solo
se puede cambiar a través de `avanzado.url-api`, y usarlo deja dos avisos en el
log de arranque. Si fuera un campo normal del `config.yml`, bastaría con
publicar una "guía de instalación" que apuntara a un servidor falso para
recolectar las claves de quien la siguiera.

### Identidad del jugador

La plataforma identifica al votante de dos formas, y el plugin tiene que
respetar la que le llegue:

| Tipo | Valor | Cuándo |
|---|---|---|
| `MOJANG_UUID` | El UUID de Mojang | Servidor en online-mode |
| `OFFLINE_NAME` | El nombre | Servidor en offline-mode |

Buscar al jugador por el valor equivocado no da error: devuelve "no está
conectado" y la recompensa se guarda como pendiente de alguien que no existe.
Por eso la identidad viaja siempre con su tipo, y por eso al entrar un jugador
se consultan **las dos** identidades posibles: el servidor pudo cambiar de modo
entre el voto y la vuelta del jugador.

### Entregas pendientes e idempotencia

Un voto puede llegar cuando el jugador ya se ha desconectado. En ese caso la
recompensa se guarda en `plugins/LatinosMC/entregas.yml` y se entrega al entrar.
Se conserva 90 días; pasado ese plazo se descarta y queda anotada en el log,
porque el archivo no puede crecer para siempre.

El mismo archivo guarda los identificadores ya aplicados. La plataforma puede
reenviar una recompensa —es lo correcto: es preferible reenviar a perderla— y el
plugin la reconoce y la confirma sin volver a ejecutar nada.

Lo que no se pudo entregar vuelve a la lista de pendientes. El caso real es un
tipo de recompensa que todavía no está en `config.yml`: descartarla la perdería
para siempre, porque a la plataforma ya se le confirmó.

### Jugadores de Bedrock

A quien entra desde móvil o consola no se le enseña el botón para pulsar: en
Bedrock los enlaces del chat no funcionan, lo pulsarían, no pasaría nada y la
conclusión sería que el servidor está roto. A ellos se les manda el mensaje de
`voto-bedrock`, con dos datos y ninguno más: la página y el código.

La detección mira los primeros ocho bytes del UUID, que Geyser deja a cero. No
depende de Floodgate, así que el plugin sigue compilando y funcionando en un
servidor que no tenga Geyser instalado. Equivocarse ahí no rompe nada: el peor
caso es enseñar el mensaje que no tocaba, y el código aparece en los dos.

---

## La API

Cuatro llamadas, todas autenticadas con la cabecera `X-Api-Key`, contra
`https://api.latinosmc.com/api/v1`.

| Método | Ruta | Para qué |
|---|---|---|
| `POST` | `/plugin/heartbeat` | Confirmar que el servidor sigue vivo |
| `GET` | `/plugin/rewards/pending?limite=N` | Recoger las recompensas sin entregar |
| `POST` | `/plugin/rewards/ack` | Confirmar cuáles se entregaron |
| `POST` | `/plugin/vote-tokens` | Pedir el código y el enlace de un jugador |

`/plugin/vote-tokens` recibe `{tipoIdentidad, identidad}` y responde con
`{puedeVotar: true, token, url}` o, si el jugador ya votó, con
`{puedeVotar: false, motivo, segundosRestantes}`, donde `motivo` es
`YA_VOTO_HOY` o `YA_VOTO_AQUI`.

El heartbeat y el sondeo corren en hilos asíncronos. Lo único que vuelve al hilo
principal es la ejecución de los comandos de recompensa, y los de una misma
recompensa van juntos en la misma tarea: si son tres comandos que dependen del
anterior (crear la cuenta, ingresar, avisar), repartirlos en tareas distintas
los dejaría a merced de lo que se cuele en medio.

---

## Compilar desde el código

Solo hace falta si quieres modificarlo. Para instalarlo basta con el `.jar` de
la [última release](https://github.com/JhonTova/LatinosMC/releases/latest).

```bash
git clone https://github.com/JhonTova/LatinosMC.git
cd LatinosMC
mvn clean package
```

El `.jar` sale en `target/LatinosMC-<versión>.jar`.

Nada se empaqueta dentro: `paper-api` y Gson van con alcance `provided` porque
los aporta el servidor en tiempo de ejecución. Cada dependencia empaquetada en
un plugin es un posible choque de versiones con los otros veinte plugins del
servidor.

El `maven.compiler.release` está fijado a 21 a propósito. Compilar contra un
target superior haría que el plugin no cargase en el servidor del cliente, y eso
se descubre el día del lanzamiento.

### Estructura del código

| Clase | Responsabilidad |
|---|---|
| `LatinosMcPlugin` | Arranque, tareas programadas, entrega al conectarse |
| `ClienteApi` | Las cuatro llamadas HTTP y sus tipos de respuesta |
| `ComandoVotar` | `/votar`: pide el código y lo presenta según la plataforma del jugador |
| `EntregadorDeRecompensas` | Traduce un tipo de recompensa a comandos y los ejecuta |
| `AlmacenDeEntregas` | Persistencia de pendientes y de lo ya aplicado |
| `IdentidadDeJugador` | Las dos formas de identificar al votante |
| `Mensajes` | Lectura de `config.yml` y formato MiniMessage |
| `ComandoRecargar` | `/lmcreload`: relee las recompensas y avisa de lo que no se aplica |
| `AjustesDeArranque` | Los valores que solo se leen al arrancar, para poder comparar |

Los tests cubren las dos piezas donde un fallo silencioso cuesta dinero: la
traducción de recompensa a comando y la persistencia de las pendientes.

```bash
mvn test
```

---

## Resolución de problemas

**El plugin se desactiva al arrancar.** Falta la clave de API, o sigue con el
valor de ejemplo. El log lo dice explícitamente.

**Los votos no dan nada.** Mira la sección `recompensas` de `config.yml`. Si en
el arranque el log dice `Recompensas configuradas: 0`, es que no hay ninguna.

**"Recompensa de tipo X recibida pero no configurada".** Llegó un tipo que no
está en tu `config.yml`. La recompensa no se pierde: queda pendiente y se
entrega en cuanto añadas el tipo y reinicies.

**He cambiado la clave de API y sigue sin conectar.** `/lmcreload` no recarga la
clave. Reinicia el servidor. Si al recargar te salió un aviso en rojo, era
exactamente eso.

**Un jugador vota y no recibe nada, pero otros sí.** Suele ser el nombre: si
tiene caracteres fuera de `A-Z a-z 0-9 _`, el plugin cancela la entrega y lo
deja en el log con nivel `SEVERE`.

**"Usando una direccion NO OFICIAL" en el log.** Alguien ha puesto algo en
`avanzado.url-api`. Si no has sido tú, bórralo y cambia la clave de API desde el
panel.

**No llega nada y el log no dice nada.** Sube el nivel de log a `FINE` para ver
los heartbeats fallidos. Si el servidor no tiene salida a internet hacia
`api.latinosmc.com`, ese es el problema.

---

## Licencia

Pendiente de definir. Mientras tanto, todos los derechos reservados.
