# Roblox3DME — controlador y escenario de prueba M3G

El punto de entrada es `main.java`, dentro del paquete `meblox`. Es un MIDlet para **MIDP 2.0 + JSR-184 (M3G)**.

## Menú

Al iniciar, elige **Obby**, **Baseplate** o **Horda zombi** con arriba/abajo y confirma con **5 / FIRE**. Obby tiene una ruta larga de 81 plataformas en zigzag y una plataforma verde de meta; Baseplate deja el suelo verde y un bloque rojo de prueba que elimina al jugador al tocarlo. En Horda zombi, los enemigos usan el modelo del avatar con piel verde y ropa marrón, persiguen al jugador en oleadas y lo eliminan al alcanzarlo. Pulsa **0** durante el juego para volver al menú. El fondo `bgtitle.png` se dibuja directamente en el tamaño de pantalla y se desplaza en bucle como una panorámica.

## Controles en los juegos

- **Softkey derecho:** abre la pausa.
- **D-pad:** orbita la cámara y permite mirar arriba/abajo.
- **2 / 8:** avanzar y retroceder en relación con la cámara.
- **4 / 6:** desplazamiento lateral.
- **5 / FIRE:** saltar.
- *** / #:** acercar / alejar la cámara.
- El avatar gira al caminar, hacia el movimiento; la cámara puede orbitar mientras permanece quieto.

El menú de pausa tiene un fondo oscuro semitransparente y permite reanudar, configurar sensibilidad e inversión de cámara, reiniciar la partida o volver al menú de juegos. Al abrirlo, el mundo sigue avanzando detrás del menú, como en Roblox.

En la Horda zombi, **7/9** cambian entre **Espada**, **Bomba** y **Pala**, y **3** usa el objeto. La espada anima el brazo y la pierna y golpea a un zombi cercano que esté delante. La bomba cae con gravedad, parpadea y explota tras 2,6 segundos; elimina zombis dentro de 4,8 unidades y también puede alcanzar al jugador. La pala suelta seis bloques que caen, chocan entre sí y forman una pared temporal.

## Física y reaparición

La gravedad actúa al saltar y al caer fuera de las plataformas; el jugador aterriza en el suelo y en las plataformas del Obby. Tocar el bloque rojo del Baseplate separa cabeza, torso, brazos y piernas. Las piezas salen despedidas, giran en varios ejes y chocan según su posición y rotación con el suelo, las plataformas y entre sí; caen y rebotan levemente. La cámara sigue al torso mientras esa pieza exista. Al entrar al juego y después de morir, el personaje aparece dentro de un aura azul semitransparente esférica; el aura pulsa con destellos y lo protege durante 4 segundos. Tras morir, reaparece después de 2,6 segundos.

## Recursos

Incluye `face.png`, `logoR.png`, `bgtitle.png` y `skyboxR.png` como recursos del proyecto en `/meblox/` o en la raíz del JAR. El menú muestra `bgtitle.png` como panorama y reescala `logoR.png` conservando toda la imagen para que quepa completa en pantalla. En los juegos, `skyboxR.png` sigue la orientación de la cámara en sentido contrario y a la misma velocidad angular, desplazando también su recorte vertical cuando miras arriba o abajo.

El código adapta `face.png` a las dimensiones que requiere una textura JSR-184 y la curva sobre la cara del cilindro. Si falta una textura, el juego muestra el detalle del error.

Los archivos `noobidle.m3g`, `noobrun.m3g` y `noobjump.m3g` no son necesarios: el personaje y los escenarios se construyen desde Java.

## Compilación

Compila `main.java` con un SDK MIDP 2.0 que incluya JSR-184, copia los cuatro PNG al directorio de recursos del proyecto y vuelve a instalar el JAR generado.

**Estado:** los cambios se guardaron en GitHub, pero no se han compilado ni probado en un emulador o teléfono.
