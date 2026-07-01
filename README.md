# Bitecma App

Proyecto con dos frentes principales:

- `Bitecma_Android`: aplicación Android usada para operaciones en terreno (offline-first).
- `src`: aplicación web construida con React y Vite.

## Requisitos

- Android Studio (recomendado para compilar/ejecutar Android).
- Node.js + npm (solo para la web).

## Versiones

### Android

- App: `1.1.0` (`versionCode` 1)
- minSdk: 21
- targetSdk: 35
- compileSdk: 36
- Android Gradle Plugin: 8.13.2
- Kotlin: 1.9.23
- Compose Compiler Extension: 1.5.11
- Compose BOM: 2024.04.01

### Web

- Vite: 8.x
- React: 19.x

## Backend / API

Por defecto, la app Android apunta a:

- `https://bitecma.cl/api/`

Si necesitas usar otro entorno, puedes sobrescribirlo sin tocar el código:

- Opción A (recomendada): crear/editar `Bitecma_Android/gradle.properties` (archivo local) y agregar:
  - `BITECMA_API_BASE_URL=https://tu-dominio/api`
- Opción B: por línea de comandos:
  - `./gradlew.bat -PBITECMA_API_BASE_URL=https://tu-dominio/api :app:assembleDebug`

No subas credenciales ni URLs sensibles al repositorio.

## Web

- Instalar dependencias: `npm install`
- Ejecutar en desarrollo: `npm run dev`
- Generar build: `npm run build`

## Android

- Abrir `Bitecma_Android` en Android Studio.
- Compilar desde Android Studio o con:
  - `./gradlew.bat :app:assembleDebug`
- Para compilar rápido solo Kotlin:
  - `./gradlew.bat :app:compileDebugKotlin`
- Para generar un bundle (Play Store):
  - `./gradlew.bat :app:bundleRelease`

### Modo offline / sincronización

- Requiere iniciar sesión con internet al menos una vez.
- Luego permite uso offline en terreno.
- La sincronización se ejecuta automáticamente cuando hay conectividad (sin switch manual).
- El catálogo de especies se sincroniza desde backend.

## Nota

- Este repositorio fue limpiado para entrega y no incluye reportes/archivos de debug ni plantillas genéricas no relacionadas al proyecto.

## Problemas comunes

- Gradle falla borrando `R.jar` (lock del sistema/IDE):
  - Cierra Android Studio si está en medio de una build.
  - Ejecuta `./gradlew.bat --stop` y vuelve a compilar.

## Publicación en Play Store (Android)

- Requiere un `AAB` (`bundleRelease`) y firma con upload key.
- En este repo la firma de `release` se toma desde `Bitecma_Android/keystore.properties` (archivo local, no se sube al repo).
- Ejemplo de formato: [keystore.properties.example](file:///c:/Users/braulio/Desktop/Muestra%20de%20amor/Bitecma_App/Bitecma_Android/keystore.properties.example)

## Estructura del repositorio

- `Bitecma_Android/`: app Android (Jetpack Compose).
- `src/`: app web (React + Vite).

## Arquitectura (Android)

- UI: Jetpack Compose.
- Estado global/sesión: `AppState`.
- Datos/sincronización/cache: `DataManager` + `SyncWorker`/`SyncScheduler`.
- Fuente remota: `DataRemoteSource` (Retrofit).
- Cache local: `LocalCacheStore` / `LocalCacheDatabase`.

## Contribución (opcional)

- Mantener los cambios enfocados en `Bitecma_Android` cuando el objetivo sea la app móvil.
- Evitar hardcodear credenciales/URLs sensibles; usar `BITECMA_API_BASE_URL` en `gradle.properties`.
