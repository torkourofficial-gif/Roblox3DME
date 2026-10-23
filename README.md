# Roblox3DME — controlador y escenario de prueba M3G

El punto de entrada es `main.java`, dentro del paquete `meblox`. Es un MIDlet para **MIDP 2.0 + JSR-184 (M3G)**.

## Controles

- **D-pad:** gira la cámara en horizontal y permite mirar arriba/abajo.
- **2 / 8:** avanzar y retroceder en relación con la cámara.
- **4 / 6:** desplazamiento lateral.
- **5 / FIRE:** saltar.
- *** / #:** acercar / alejar la cámara.

El avatar gira con la orientación horizontal de la cámara y mantiene la cara hacia delante, en sentido opuesto a ella. El cuerpo, la cabeza cilíndrica con bordes redondeados y las animaciones se construyen en Java; no se cargan los modelos `.m3g` separados.

## Recursos

Incluye `face.png` como recurso del proyecto en `/meblox/face.png` o en la raíz del JAR. El código adapta sus dimensiones a las que requiere una textura JSR-184 y la aplica a la cara frontal de la cabeza. Si no se encuentra o falla la textura, el juego muestra el detalle del error en pantalla.

Los archivos `noobidle.m3g`, `noobrun.m3g` y `noobjump.m3g` ya no son necesarios para ejecutar el modelo generado.

## Escenario

El juego genera el suelo verde, los bloques de colores y el avatar con geometría M3G desde Java.

## Compilación

Compila `main.java` con un SDK MIDP 2.0 que incluya JSR-184, agrega `face.png` a los recursos del JAR y vuelve a instalar el JAR generado para probar los últimos cambios.

**Estado:** el código se actualizó en GitHub, pero no se ha compilado ni probado en un emulador o teléfono.
