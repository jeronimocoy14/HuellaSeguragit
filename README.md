# Huella Segura

App Android (Kotlin, vistas XML) de la Alcaldía de Zipaquirá para el bienestar animal: los ciudadanos reportan animales en peligro, publican mascotas perdidas o encontradas y llevan el historial médico de sus mascotas; los funcionarios y veterinarios atienden los casos.

- **Paquete:** `com.coy.huellasegura` · **minSdk** 30 · **targetSdk** 36
- **Backend:** Firebase (proyecto `huella-segura-396cb`): Authentication (correo y contraseña) y Firestore.

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

## Compilar y ejecutar

Requisitos: Android Studio reciente o JDK 17+ con el SDK de Android (compileSdk 37).

```bash
./gradlew :app:assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. El archivo `app/google-services.json` es el de Firebase del proyecto.

## Reglas de seguridad de Firestore

Están en [`firestore.rules`](firestore.rules). Se publican desde la consola de Firebase (Firestore → Reglas). Resumen: cada ciudadano solo lee y escribe lo suyo; los veterinarios ven las mascotas; los funcionarios ven reportes y chats; los roles no se pueden escribir desde la app.

## Cuentas de prueba

No hay cuentas de prueba en el repositorio. Para probar los roles profesionales, crea la cuenta desde la app y agrega su correo (en minúsculas) en `roles_profesionales` con el campo `rol`.
