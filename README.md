# Roblox3DME — controlador y escenario de prueba M3G

El punto de entrada es `main.java`, dentro del paquete `meblox`. Es un MIDlet para **MIDP 2.0 + JSR-184 (M3G)**.

## Menú

El menú contiene **15 juegos**, repartidos en páginas de cinco opciones (cuatro en pantallas pequeñas). Usa arriba/abajo para elegir, **7/9** para cambiar de página y **5 / FIRE** para entrar. La opción **Exit game**, al final de la lista, cierra la aplicación. El menú reproduce siempre `R3.mid`; cada nueva partida elige la música al azar entre `R1.mid`, `R2.mid` y `R3.mid`, recorriendo las tres antes de repetir una. Incluye Obby, Baseplate, Horda zombi y Studio, además de Lava ascendente, Lluvia de meteoritos, Plataformas que caen, Carrera contrarreloj, Caza de monedas, Rey de la colina, Arena de espadas, Escondite, Simon dice, Mini tycoon y Defensa de base. El fondo `bgtitle.png` se desplaza como una panorámica detrás del logo. Pulsa **0** durante un juego para volver al menú.

## Controles en los juegos

- **Softkey derecho:** abre la pausa. El Canvas se ejecuta a pantalla completa y no instala comandos nativos, para ocultar la barra azul de acciones de J2ME.
- **Menú:** arriba/abajo recorre los juegos; **7/9** cambia la página; **5 / FIRE** inicia el seleccionado.
- **D-pad:** orbita la cámara y permite mirar arriba/abajo.
- **2 / 8:** avanzar y retroceder en relación con la cámara.
- **4 / 6:** desplazamiento lateral.
- **5 / FIRE:** saltar.
- *** / #:** acercar / alejar la cámara.
- El avatar gira al caminar, hacia el movimiento; la cámara puede orbitar mientras permanece quieto.

El menú de pausa tiene un fondo oscuro semitransparente y permite reanudar, configurar sensibilidad e inversión de cámara, ajustar los bots (aleatorio de 3–5, una cantidad exacta de 1–5 o desactivarlos), remapear botones, reiniciar la partida o volver al menú de juegos. El número elegido se aplica al iniciar la siguiente partida; en Tycoon se limita a sus tres bases. **Ajustes → Avatar** permite cambiar los colores de piel, camisa y pantalón con una vista 3D giratoria en tiempo real; también se guardan en RMS y se comparten por LAN. Al abrir la pausa, el mundo sigue avanzando detrás del menú, como en Roblox. Sensibilidad, inversión, zoom, cantidad de bots, colores del avatar y controles se guardan en RMS y persisten al cerrar la aplicación. J2ME no permite que el MIDlet modifique el JAR instalado; RMS es el almacenamiento persistente apropiado.

En la Horda zombi, **7/9** cambian entre **Espada**, **Bomba** y **Pala**, y **3** usa el objeto. En Horda zombi, Arena de espadas y Defensa de base, **7/9** seleccionan espada/bomba y **3** usa el objeto; Horda zombi conserva también la pala. La Arena y Defensa comparten la espada, su animación y la bomba de la Horda. Mini tycoon usa **3** para comprar desde la plataforma amarilla, situada delante del punto de aparición. Lava ascendente exige saltar por la ruta antes de que suba la lava; Lluvia de meteoritos dura 30 segundos; Plataformas que caen premia cruzar la ruta antes de perder los bloques; Carrera contrarreloj usa el obby y muestra el tiempo; Caza de monedas tiene 12 monedas; Rey de la colina exige controlar la plataforma central durante 20 segundos; Escondite es un mapa amplio con refugios y NPC ocultos que se revelan cuando te acercas; Simon dice pide llegar al color indicado. Cada partida genera entre 3 y 5 avatares bot: caminan, saltan ante obstáculos y, en los modos armados, atacan con espada o bomba. Al reaparecer reciben un aura verde temporal.

## Compatibilidad

Esta variante no requiere JSR-82 ni permisos de Bluetooth. Incluye multijugador TCP por LAN cuando el dispositivo lo soporta:
- En el menú, pulsa **#** para abrir LAN. El anfitrión inicia el juego seleccionado y muestra la dirección y el puerto **47653**.
- Los demás dispositivos deben estar en la misma Wi-Fi; eligen **Join by host IP**, escriben la IPv4 del anfitrión y pulsan Connect.
- Se admiten hasta cinco jugadores contando al anfitrión. Se sincronizan la posición, orientación, vida y estado vivo/reaparecido de los avatares.
- Para volver al menú, el anfitrión cierra la sesión LAN. Las partidas de los niveles y sus NPC continúan simulándose localmente en cada dispositivo.

Las conexiones de socket y de servidor son opcionales en MIDP 2.0 y el sistema de seguridad del teléfono puede bloquearlas. El JAD debe solicitar `javax.microedition.io.Connector.socket` y `javax.microedition.io.Connector.serversocket`; si el teléfono no ofrece esos protocolos o deniega el permiso, el menú muestra el error de conexión. Hace falta un firmware/emulador con soporte de sockets y conceder acceso de red de entrada y salida.

## Niveles

Cada juego tiene su propia clase Java (`ObbyLevel.java`, `HideSeekLevel.java`, etc.) para mantener separados el nombre y la geometría del mapa; `LevelCatalog.java` registra los 15 niveles. `main.java` conserva el motor común de renderizado, controles y físicas.

## Studio

Studio abre un Baseplate editable. Pulsa **5 / FIRE** para añadir el tipo de objeto actual; el catálogo rota entre Block, Platform, Hazard, Pillar y Spawn Pad. **7/9** seleccionan los objetos colocados y **2/4/6/8** mueven el seleccionado en el plano. El D-pad orbita la cámara. Pulsa ***** para elegir propiedad y **1/#** para reducir/aumentar posición, tamaño o color; **0** elimina el objeto seleccionado. Un Spawn Pad cambia el punto de aparición y Hazard elimina al jugador al tocarlo durante la prueba. Pulsa **3** para entrar o salir del modo de prueba. Los objetos se conservan mientras la aplicación siga abierta; aún no hay exportación o guardado permanente de escenas.

## Física y reaparición

La gravedad actúa al saltar y al caer fuera de las plataformas; el jugador aterriza en el suelo y en las plataformas de los modos que las incluyen, como Obby, Lava ascendente, Plataformas que caen y Rey de la colina. El jugador, los zombis y las piezas físicas chocan con las caras superiores y laterales de los bloques. Tocar el bloque rojo del Baseplate separa cabeza, torso, brazos y piernas. Las piezas salen despedidas, giran en varios ejes y chocan según su posición y rotación con el suelo, las plataformas y entre sí; caen y rebotan levemente. La cámara sigue al torso mientras esa pieza exista. Al entrar al juego y después de morir, el personaje aparece dentro de un aura azul semitransparente esférica; el aura pulsa con destellos y lo protege durante 4 segundos. Tras morir, reaparece después de 2,6 segundos.

## Recursos

Incluye `faceNEW.png` (cara del jugador), `faceZomb.png` (cara de los zombis), `logoR.png`, `bgtitle.png` y `skyboxR.png` como recursos del proyecto en `/meblox/` o en la raíz del JAR. Las dos caras usan RGBA para conservar la transparencia del PNG. El menú muestra `bgtitle.png` como panorama y reescala `logoR.png` conservando toda la imagen para que quepa completa en pantalla. En los juegos, `skyboxR.png` sigue la orientación de la cámara en sentido contrario y a la misma velocidad angular, desplazando también su recorte vertical cuando miras arriba o abajo.

El código adapta ambas caras a las dimensiones que requiere una textura JSR-184, conserva el canal alpha y aplica transparencia con descarte de píxeles transparentes para evitar que oculten la geometría. La textura se curva sobre la cara del cilindro. Si falta una textura, el juego muestra el detalle del error.

Los archivos `noobidle.m3g`, `noobrun.m3g` y `noobjump.m3g` no son necesarios: el personaje y los escenarios se construyen desde Java.

## Compilación

Compila todos los archivos `.java` del paquete `meblox` con un SDK MIDP 2.0 que incluya JSR-184, copia los recursos PNG del proyecto al JAR y vuelve a instalarlo.

**Estado:** la rama incluye una variante sin Bluetooth; los 15 juegos y los bots aún no se han compilado ni probado en un emulador o teléfono.
