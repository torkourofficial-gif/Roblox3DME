# Roblox3DME — primer controlador y escenario M3G

Se añadió `Roblox3DME.java` como punto de partida para un MIDlet MIDP 2.0 + JSR-184 (M3G).

## Controles
- **Arriba / 2 / W según el teclado del dispositivo:** avanzar
- **Abajo / 8:** retroceder
- **Izquierda / 4 y derecha / 6:** girar
- **1 / 3:** desplazamiento lateral
- **5 / FIRE:** saltar y seleccionar la animación de salto
- *** / #:** acercar / alejar cámara
- **0:** reiniciar posición y cámara

## Recursos
El código carga `/noobidle.m3g`, `/noobrun.m3g` y `/noobjump.m3g` desde el classpath del MIDlet. Déjalos en la raíz de recursos del proyecto (junto al descriptor del MIDlet) para que `getResourceAsStream` los encuentre. `face.png` todavía no se aplica por código; la cara puede estar ya incluida en los modelos M3G.

## Escenario de prueba
Se generan un suelo verde grande y varios bloques de colores usando geometría M3G básica. Sirven para verificar cámara, profundidad, movimiento y escala sin añadir recursos externos.

## Importante / estado
Este es un **prototipo inicial**, no una compilación verificada: hay que abrirlo en el proyecto Java ME con una implementación JSR-184 instalada y compilarlo en el SDK objetivo. La carga de animaciones depende de cómo estén empaquetadas las pistas y controladores dentro de cada archivo M3G; el código intenta activar los controladores que encuentre, pero no puede garantizar que todos los modelos exportados tengan sus pistas configuradas igual. El controlador usa teclas de pulsación mantenida y el salto es actualmente una pose/animación temporal, no física con colisiones.

No se modificaron ni reemplazaron los modelos M3G existentes.
