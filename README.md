# Roblox3DME — controlador y escenario de prueba M3G

El punto de entrada es `main.java`, un MIDlet para **MIDP 2.0 + JSR-184 (M3G)**.

## Controles
- **D-pad:** gira la cámara en horizontal y permite mirar arriba/abajo.
- **2 / 8:** avanzar y retroceder en relación con la cámara.
- **4 / 6:** desplazamiento lateral.
- **5 / FIRE:** saltar.
- *** / #:** acercar / alejar la cámara.

## Recursos
El código carga `/noobidle.m3g`, `/noobrun.m3g` y `/noobjump.m3g` desde el classpath del MIDlet. Déjalos en la raíz de recursos del proyecto para que `getResourceAsStream` los encuentre. `face.png` todavía no se aplica por código; la cara puede estar incluida en los modelos M3G.

## Escenario
El escenario genera un suelo verde y varios bloques de colores mediante geometría M3G, sin necesitar modelos externos adicionales.

## Correcciones incluidas
- Vértices del escenario configurados con tres componentes (X/Y/Z).
- D-pad separado del movimiento lateral numérico.
- Cámara de tercera persona orientada hacia el avatar.
- Activación de pistas de animación también en nodos hijos.
- Salto con movimiento vertical básico, en vez de una pose fija mientras se mantiene pulsada la tecla.

## ¿Hace falta crear más scripts?
No para este prototipo: `main.java` contiene el MIDlet, el bucle de juego, el controlador, la cámara y la geometría del escenario. Sí necesitas un proyecto Java ME configurado con **MIDP 2.0 y una implementación JSR-184**, más los tres archivos `.m3g` como recursos. NetBeans/Java ME puede necesitar además su configuración de proyecto y descriptor JAD/JAR, según cómo hayas creado el proyecto.

**Estado:** los cambios se guardaron en GitHub, pero aún no se han compilado ni probado en el emulador o el teléfono. El salto todavía no incluye colisiones ni aterrizaje sobre plataformas; la activación real de animaciones depende de cómo estén exportados los archivos M3G.
