# Cambios

Las versiones siguen [SemVer](https://semver.org/lang/es/): el primer número
cambia cuando algo deja de funcionar como antes, el segundo cuando se añade algo
nuevo, el tercero cuando solo se arregla.

Mientras el primer número sea `0`, la plataforma y el plugin todavía se están
asentando: lee siempre el apartado de cada versión antes de actualizar.

---

## 0.2.0 — 27 de agosto de 2026

### Nuevo

- **Comando `/lmcreload`** (alias `/latinosmcreload`, `/lmcrecargar`; permiso
  `latinosmc.reload`, de operador). Recarga las recompensas y los mensajes sin
  reiniciar el servidor. Cambiar un premio ya no obliga a echar a todo el mundo.
- La recarga **avisa de lo que no ha aplicado**. La clave de API, la dirección
  de la API y los intervalos se leen una sola vez al arrancar; si los editas y
  recargas, el comando lo detecta y te dice en rojo que hace falta reiniciar.

### Arreglado

- Los mensajes de `config.yml` no se releían aunque se recargara la
  configuración: el plugin se quedaba con el objeto de configuración de
  arranque. Ahora lo lee en cada uso.

### Notas para actualizar

- Sustituir el `.jar` y reiniciar. No hay que tocar `config.yml`: el que ya
  tienes sigue valiendo tal cual.
- Si arrancas el servidor **sin** clave de API, el plugin se desactiva solo y
  `/lmcreload` no está disponible —un plugin desactivado no atiende comandos—.
  Ese caso sigue necesitando un reinicio.

---

## 0.1.0 — 26 de agosto de 2026

Primera versión publicada.

- `/votar` y `/vote`: enlace y código de voto, con mensaje aparte para los
  jugadores de Bedrock, que no pueden pulsar enlaces en el chat.
- Entrega de recompensas por tipo, mapeado en `config.yml`. La plataforma nunca
  manda texto de comando.
- Recompensas pendientes para quien votó y ya se había desconectado, con
  protección contra entregas repetidas.
- Identidad del votante por UUID de Mojang o por nombre, según el modo del
  servidor.
