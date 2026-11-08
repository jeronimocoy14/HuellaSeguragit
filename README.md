# Huella Segura

App Android (Kotlin, vistas XML) de la Alcaldía de Zipaquirá para el bienestar animal: los ciudadanos reportan animales en peligro, publican mascotas perdidas o encontradas y llevan el historial médico de sus mascotas; los funcionarios y veterinarios atienden los casos.

- **Paquete:** `com.coy.huellasegura` · **minSdk** 24 (Android 7) · **targetSdk** 36
- **Backend:** Firebase (proyecto `huella-segura-396cb`): Authentication (correo y contraseña) y Firestore.

## Funciones

- **Reportar un animal en peligro** (flujo normal y flujo simplificado) con foto de la cámara o de la galería, ubicación y chat con el funcionario.
- **Perdidos y encontrados** con moderación del funcionario.
- **Mascotas** que registra el veterinario y acepta el dueño, con historial clínico.
- **Categoría del animal por foto**: un modelo que corre dentro del teléfono (ML Kit + EfficientNet, sin internet) sugiere la categoría (perro, gato, ave, caballo, vaca...) y la persona puede corregirla. Hay filtros por categoría en varias pantallas.
- **Animales comunitarios**: el personal registra los animales que cuida todo el barrio; cada ciudadano ve los que están a menos de 1 km (con la distancia y una referencia, sin el punto exacto).
- **Exportar a Excel** (última semana, último mes o todo): resumen y reportes para el funcionario, pacientes para el veterinario.
- **Modos de ayuda**: texto grande, tema claro/oscuro/automático, alto contraste, modo para daltonismo, modo con dibujos, lectura en voz alta y un modo simplificado con listas grandes y botón «Escuchar».

## Roles

| Rol | Cómo se asigna | Qué hace |
|---|---|---|
| Ciudadano | Cualquier cuenta nueva | Reporta peligros, chatea con un funcionario, publica perdidos/encontrados, acepta las mascotas que registra un veterinario y su historial. |
| Funcionario | Documento en `roles_profesionales/{correo}` con `rol: funcionario` | Bandeja de reportes, chats con ciudadanos, moderación de perdidos/encontrados. |
| Veterinario | Documento en `roles_profesionales/{correo}` con `rol: veterinario` | Registra mascotas y su historial clínico (observaciones, próxima visita, editar/anular). |

Los roles solo se asignan desde la consola de Firebase; las reglas impiden escribirlos desde la app.

## Pantallas principales

- `SessionRouter` decide a qué pantalla va cada sesión según el rol.
- Ciudadano: `MainDashboardActivity` (o `HomeSeniorActivity` en modo simplificado), `ReportarPeligro*`, `ChatActivity`, `MisReportesActivity`, `MisMascotasActivity`, `PerdidosActivity`.
- Profesional: `BandejaProfesionalActivity`, `ChatsFuncionarioActivity`, `PerdidosModeracionActivity`, `VeterinarioActivity`, `PerfilMascotaActivity`.
- Accesibilidad: texto grande, alto contraste, modo daltonismo y lectura en voz alta (`AccessibilityConfigActivity`, `BaseAccessibleActivity`).

## Compilar y ejecutar (en cualquier computador)

Requisitos: **Android Studio** reciente (trae el JDK) o un JDK 21, y el SDK de Android con `compileSdk 37` (Android Studio lo ofrece instalar solo).

1. Clona el repositorio y ábrelo con Android Studio (o usa la terminal).
2. Android Studio crea `local.properties` con la ruta de tu SDK. Desde la terminal, créalo tú con una línea: `sdk.dir=C\:\\ruta\\al\\Android\\Sdk` (en Linux/Mac: `sdk.dir=/ruta/al/Android/Sdk`).
3. Compila:

```bash
./gradlew :app:assembleDebug        # en Windows: gradlew.bat :app:assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

Todo lo demás viene en el repositorio: el wrapper de Gradle, `app/google-services.json` (configuración de Firebase del proyecto `huella-segura-396cb`) y el modelo de reconocimiento de animales (`app/src/main/assets/animales_imagenet.tflite`, 4.7 MB). La primera compilación descarga las dependencias.

El APK de depuración pesa unos 65 MB por los modelos de ML Kit (el de release baja bastante si se divide por arquitectura).

## Reglas de seguridad de Firestore

Están en [`firestore.rules`](firestore.rules). Se publican desde la consola de Firebase (Firestore → Reglas). Resumen: cada ciudadano solo lee y escribe lo suyo; los veterinarios ven las mascotas; los funcionarios ven reportes y chats; los roles no se pueden escribir desde la app.

Si cambias `firestore.rules`, hay que volver a publicarlas desde la consola (la app nueva de «animales comunitarios» necesita la regla de `comunitarios`).

Pruebas de las reglas (120 pruebas; se ejecutan contra el emulador de Firestore, hace falta Java y Node):

```
cd tests/rules
npm install
npm test
```

## Cuentas de prueba

No hay cuentas de prueba en el repositorio. Para probar los roles profesionales, crea la cuenta desde la app y agrega su correo (en minúsculas) en `roles_profesionales` con el campo `rol`.
