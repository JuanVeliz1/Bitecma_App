
# 🐟 Bitecma App
Sistema completo (Android + Web) para la gestión de **muestreos intermareales y submareales** de la organización Bitecma, diseñado para optimizar la captura, sincronización y visualización de datos bentónicos y de peso-longitud.


###  Aplicación Android
- **Lenguaje**: Kotlin
- **UI Framework**: Jetpack Compose
- **Navegación**: Compose Navigation
- **Red**: Retrofit + OkHttp (interceptor de autenticación Bearer)
- **Persistencia Local**: SharedPreferences (caché offline-first)
- **MinSdk**: 21 (compatible con dispositivos antiguos)


## 📋 Funcionalidades Principales
### Android
- ✅ Login online/offline (con fallback local sin internet)
- ✅ Gestión de **operaciones agrupadas por región** con menús abatibles
- ✅ Selección explícita de **Intermareal (a pie)** vs **Submareal (en bote)**
- ✅ Ingreso y edición de datos de **densidad** y **peso-longitud**
- ✅ Caché local + sincronización offline-first
- ✅ Botón para subir operaciones locales a la nube
- ✅ Botón de **sincronización completa** (catálogos + operaciones)
- ✅ Switch exclusivo admin (`bitecma@bitecma.cl`) para forzar modo offline/online
- ✅ Iconografía reactiva según estado (modo offline/online/Intermareal/Submareal)
- ✅ Exportación de reportes a `.csv`
- ✅ Mejora de rendimiento: caché persistido en background (evita ANR)

### Web
- ✅ Visualización de operaciones y datos
- ✅ Gestión de botes, caletas, sectores, especies y perfiles
- ✅ Generación de reportes y backup


## 🚀 Instalación y Ejecución
### Android
1. Abre la carpeta `Bitecma_Android/` en Android Studio.
2. Sincroniza Gradle.
3. Conecta un dispositivo físico/emulador y ejecuta la app.

### API
1. Sube la carpeta `Bitecma_Android/bitecma-api/` a tu servidor (cPanel, VPS, etc.) en la ruta deseada (ej: `public_html/api/`).
2. Edita `Bitecma_Android/bitecma-api/config/database.php` con tus credenciales de MySQL/MariaDB.
3. **Migración a InnoDB**: Ejecuta en el servidor (SSH/terminal, ruta donde está `index.php` de la API):
   ```bash
   # Si tu servidor usa php81 (ajusta si es otra versión: php80, php74, ea-php81, etc.)
   php cron/migrate_innodb.php
   ```

## 📝 Uso de la App Android
1. **Login**: Usa tu usuario de Bitecma (para modo admin: `bitecma@bitecma.cl`).
2. **Crear Operación**: Añade datos básicos (región, caleta, fecha, etc.).
3. **Gestión de Botes**:
   - Toca **"+ AGREGAR FILA"**.
   - Usa el toggle para elegir entre **Intermareal (a pie)** (sin bote maestro, solo buzo) o **Submareal (en bote)** (requiere bote maestro).
4. **Ingreso de Datos**:
   - Toca un bote para abrir el diálogo de datos.
   - Usa las pestañas **"Densidad"** y **"Peso-Longitud"**.
5. **Sincronizar**:
   - Usa el botón **"SINCRONIZAR TODO"** para bajar datos del servidor.
   - Toca el ícono de **"Cargar a nube"** en una operación local para subirla.


## 📞 Soporte
Para reportes de bugs o consultas, escribe al equipo de Bitecma.


---

**Repositorio**: [https://github.com/JuanVeliz1/Bitecma_App](https://github.com/JuanVeliz1/Bitecma_App)
