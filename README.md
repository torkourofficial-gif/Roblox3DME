# Roblox3DME — controlador y escenario de prueba M3G

El punto de entrada es `main.java`, dentro del paquete `meblox`. Es un MIDlet para **MIDP 2.0 + JSR-184 (M3G)**.

## Menú

Al iniciar, elige **Obby** o **Baseplate** con arriba/abajo y confirma con **5 / FIRE**. Obby incluye los bloques de salto; Baseplate deja solo el suelo verde. Pulsa **0** durante el juego para volver al menú.

## Controles en los juegos

- **D-pad:** orbita la cámara y permite mirar arriba/abajo.
- **2 / 8:** avanzar y retroceder en relación con la cámara.
- **4 / 6:** desplazamiento lateral.
- **5 / FIRE:** saltar.
- *** / #:** acercar / alejar la cámara.
- El avatar gira al caminar, hacia el movimiento; la cámara puede orbitar mientras permanece quieto.

## Recursos

Incluye `face.png`, `logoR.png`, `bgtitle.png` y `skyboxR.png` como recursos del proyecto en `/meblox/` o en la raíz del JAR. El menú ajusta `logoR.png` para que quepa en pantalla y recorta `bgtitle.png` como fondo panorámico. Los dos juegos usan `skyboxR.png` como imagen de fondo del mundo.

El código adapta `face.png` a las dimensiones que requiere una textura JSR-184 y la curva sobre la cara del cilindro. Si falta una textura, el juego muestra el detalle del error.

Los archivos `noobidle.m3g`, `noobrun.m3g` y `noobjump.m3g` no son necesarios: el personaje y los escenarios se construyen desde Java.

## Compilación

Compila `main.java` con un SDK MIDP 2.0 que incluya JSR-184, copia los cuatro PNG al directorio de recursos del proyecto y vuelve a instalar el JAR generado.

**Estado:** los cambios se guardaron en GitHub, pero no se han compilado ni probado en un emulador o teléfono.
