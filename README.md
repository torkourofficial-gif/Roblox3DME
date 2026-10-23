# Roblox3DME — controlador y escenario de prueba M3G

El punto de entrada es `main.java`, dentro del paquete `meblox`. Es un MIDlet para **MIDP 2.0 + JSR-184 (M3G)**.

## Menú

Al iniciar, elige **Obby** o **Baseplate** con arriba/abajo y confirma con **5 / FIRE**. Obby incluye los bloques de salto; Baseplate deja el suelo verde y un bloque rojo de prueba que elimina al jugador al tocarlo. Pulsa **0** durante el juego para volver al menú. El fondo `bgtitle.png` se dibuja directamente en el tamaño de pantalla y se desplaza en bucle como una panorámica.

## Controles en los juegos

- **D-pad:** orbita la cámara y permite mirar arriba/abajo.
- **2 / 8:** avanzar y retroceder en relación con la cámara.
- **4 / 6:** desplazamiento lateral.
- **5 / FIRE:** saltar.
- *** / #:** acercar / alejar la cámara.
- El avatar gira al caminar, hacia el movimiento; la cámara puede orbitar mientras permanece quieto.

## Física y reaparición

La gravedad actúa al saltar y al caer fuera de las plataformas; el jugador aterriza en el suelo y en las plataformas del Obby. Tocar el bloque rojo del Baseplate separa cabeza, torso, brazos y piernas. Las piezas salen despedidas, giran en varios ejes y chocan según su posición y rotación con el suelo, las plataformas y entre sí; caen y rebotan levemente. La cámara sigue al torso mientras esa pieza exista. Tras 2,6 segundos reaparece el personaje dentro de un aura azul semitransparente esférica; el aura pulsa con destellos y lo protege durante 4 segundos.

## Recursos

Incluye `face.png`, `logoR.png`, `bgtitle.png` y `skyboxR.png` como recursos del proyecto en `/meblox/` o en la raíz del JAR. El menú muestra `bgtitle.png` como panorama y reescala `logoR.png` conservando toda la imagen para que quepa completa en pantalla. En los juegos, `skyboxR.png` sigue la orientación de la cámara en sentido contrario y a la misma velocidad angular, desplazando también su recorte vertical cuando miras arriba o abajo.

El código adapta `face.png` a las dimensiones que requiere una textura JSR-184 y la curva sobre la cara del cilindro. Si falta una textura, el juego muestra el detalle del error.

Los archivos `noobidle.m3g`, `noobrun.m3g` y `noobjump.m3g` no son necesarios: el personaje y los escenarios se construyen desde Java.

## Compilación

Compila `main.java` con un SDK MIDP 2.0 que incluya JSR-184, copia los cuatro PNG al directorio de recursos del proyecto y vuelve a instalar el JAR generado.

**Estado:** los cambios se guardaron en GitHub, pero no se han compilado ni probado en un emulador o teléfono.
