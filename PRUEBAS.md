# Pruebas automáticas de Huella Segura

Hay **tres capas** de pruebas. Cada una prueba una cosa distinta y se puede correr por separado.
Todas son reales: se ejecutan contra el código de la app y pasan o fallan según lo que el código haga.

| Capa | Qué prueba | Dónde corre | Pruebas |
|---|---|---|---|
| **Reglas de seguridad** | Quién puede leer y escribir cada colección de Firestore (ciudadano, veterinario, funcionario, sin sesión) | Emulador de Firestore (Node) | **120** |
| **Unitarias** | La lógica de la app, sin pantalla: distancias, salud de animales comunitarios, reconocimiento de la categoría, escritor de Excel, textos | El computador (JVM) | **39** |
| **Con la app abierta** | Pantallas y modos de ayuda funcionando de verdad: se toca, se escribe y se comprueba lo que sale | Emulador o celular Android | **26** |
| | | **Total** | **185** |

## Qué se prueba en cada capa

**Reglas de seguridad (`tests/rules/test.mjs`)**
- Sin sesión no se lee nada; un ciudadano solo ve lo suyo; solo el funcionario atiende reportes y modera perdidos; solo el veterinario crea mascotas; nadie puede asignarse un rol.
- Animales comunitarios: cualquiera con sesión lee los activos, los inactivos solo el personal, y solo el personal escribe.

**Unitarias (`app/src/test`)**
- `ComunitariosTest`: la distancia entre dos puntos (¿Zipaquirá queda a ~35 km de Bogotá? ¿1° de latitud son ~111 km?), el radio de 1 km y el color de salud (completo / parcial / pendiente).
- `AnimalRecognitionTest`: cómo se traduce lo que ve el modelo (perro, loro, vaca…) a las 12 categorías de la app, y que lo que no encaja vaya a «Otro».
- `XlsxWriterTest`: el Excel que genera la app se abre como zip, su XML es válido, escapa caracteres raros, pone encabezados y filtros, y las columnas pasan de la Z a la AA.
- `TextAndGeoUtilsTest`: normalizar tildes, edad de la mascota y enlaces de mapa.

**Con la app abierta (`app/src/androidTest`)**
- `AccessibilityEngineTest`: tema claro/oscuro/automático, alto contraste (negro y blanco reales), tamaño de texto y modo para daltonismo (cada estado con su icono y su color).
- `ScreensTest`: el login y su enlace «Crear cuenta», el selector de las 12 especies, los errores al registrar una mascota con un correo inválido, y los ajustes de accesibilidad guardando lo que se elige.
- `SpeciesAndDistanceTest`: las categorías y sus iconos con los recursos reales, y el texto «a 450 m» / «a 1,5 km».

## Cómo correrlas

```bash
# 1) Unitarias (no necesitan emulador)
gradlew.bat testDebugUnitTest

# 2) Con la app abierta (hay que tener un emulador o un celular conectado)
gradlew.bat connectedDebugAndroidTest

# 3) Reglas de seguridad (hace falta Java y Node)
cd tests/rules
npm install
npm test
```

Informes para mostrar (HTML, se abren con el navegador):
- `app/build/reports/tests/testDebugUnitTest/index.html` — unitarias
- `app/build/reports/androidTests/connected/debug/index.html` — con la app abierta

Resumen de todo junto:

```bash
python scripts/resumen_pruebas.py --reglas "RESULTADO: 120 correctas, 0 con fallo"
```

## Cómo sabemos que las pruebas sirven
Para comprobar que no son «pruebas que siempre pasan» se hizo un experimento: se metieron a propósito tres errores en el
código (volver al error de los 40 decimales en el Excel, subir el radio de 1 km a 5 km, y marcar «completo» como «parcial»).
**Cuatro pruebas fallaron**, justo las que debían, y al quitar los errores volvieron a pasar.
Las pruebas de la app abierta también encontraron un error real de la propia prueba (tocaba el centro del texto «¿No tienes
cuenta? Crear cuenta», y solo «Crear cuenta» es el enlace), que se corrigió.

## Lo que todavía no se prueba automáticamente
- Los flujos completos con internet (reportar de punta a punta, mapa, chat): se probaron a mano en emulador, no automáticos.
- La cámara, el GPS y la voz en un celular real.
- El modelo de reconocimiento de animales en sí (solo se prueba la lógica que usa su resultado).
