# Ñanday SIAA – Aplicación móvil y API

Aplicación móvil para Android del sistema Ñanday SIAA. Permite consultar el stock, gestionar órdenes de trabajo, registrar salidas, transferencias y ajustes, recibir órdenes de compra, trabajar con activos fijos y consultar notificaciones, gráficos y la bitácora.
Trabaja sobre la misma base de datos que el sistema web.

El repositorio tiene dos partes:

| Carpeta | Contenido |
|---|---|
| `APImovil` | API en Java EE (Servlets) que se despliega en Apache Tomcat y conecta la app con la base de datos |
| `nanday_movil` | Aplicación móvil desarrollada en Flutter |

## Requisitos

| Componente | Versión |
|---|---|
| JDK | OpenJDK 17 |
| PostgreSQL | 16, con la base `proyecto_final` del sistema web ya restaurada |
| Apache Tomcat | 9 (ver nota para Tomcat 10 u 11) |
| Driver JDBC | postgresql-42.7.10.jar, copiado en la carpeta `lib` de Tomcat |
| Apache NetBeans con Maven | para compilar la API |
| Flutter SDK | 3.x, con Android SDK (para compilar la app) |
| Teléfono | Android, conectado a la misma red que el servidor |

> **Tomcat 10 u 11:** la API usa Java EE (`javax`). En esas versiones, el `.war` debe copiarse en la carpeta `webapps-javaee` (no en `webapps`), y Tomcat lo convierte automáticamente al iniciar.

## 1. API (APImovil)

1. Abrir la carpeta `APImovil` en Apache NetBeans y hacer **Clean and Build**.
2. Copiar el `.war` generado en `target` (APInanday.war) en Tomcat e iniciarlo.
3. Los datos de conexión a la base están en `APImovil/src/main/webapp/META-INF/context.xml`. Si la base está en otra PC o el usuario tiene otra contraseña, se cambian ahí.

## 2. Aplicación móvil (nanday_movil)
cd /home/giovanni/Escritorio/nanday_app_movil/
cat > README.md <<'EOF'
# Ñanday SIAA – Aplicación móvil y API

Aplicación móvil para Android del sistema Ñanday SIAA. Permite consultar el stock, gestionar órdenes de trabajo, registrar salidas, transferencias y ajustes, recibir órdenes de compra, trabajar con activos fijos y consultar notificaciones, gráficos y la bitácora.
Trabaja sobre la misma base de datos que el sistema web.

El repositorio tiene dos partes:

| Carpeta | Contenido |
|---|---|
| `APImovil` | API en Java EE (Servlets) que se despliega en Apache Tomcat y conecta la app con la base de datos |
| `nanday_movil` | Aplicación móvil desarrollada en Flutter |

## Requisitos

| Componente | Versión |
|---|---|
| JDK | OpenJDK 17 |
| PostgreSQL | 16, con la base `proyecto_final` del sistema web ya restaurada |
| Apache Tomcat | 9 (ver nota para Tomcat 10 u 11) |
| Driver JDBC | postgresql-42.7.10.jar, copiado en la carpeta `lib` de Tomcat |
| Apache NetBeans con Maven | para compilar la API |
| Flutter SDK | 3.x, con Android SDK (para compilar la app) |
| Teléfono | Android, conectado a la misma red que el servidor |

> **Tomcat 10 u 11:** la API usa Java EE (`javax`). En esas versiones, el `.war` debe copiarse en la carpeta `webapps-javaee` (no en `webapps`), y Tomcat lo convierte automáticamente al iniciar.

## 1. API (APImovil)

1. Abrir la carpeta `APImovil` en Apache NetBeans y hacer **Clean and Build**.
2. Copiar el `.war` generado en `target` (APInanday.war) en Tomcat e iniciarlo.
3. Los datos de conexión a la base están en `APImovil/src/main/webapp/META-INF/context.xml`. Si la base está en otra PC o el usuario tiene otra contraseña, se cambian ahí.

## 2. Aplicación móvil (nanday_movil)

Desde la carpeta `nanday_movil`:

    flutter pub get
    flutter build apk --release --split-per-abi

El APK queda en `nanday_movil/build/app/outputs/flutter-apk/`. Para la mayoría de los celulares se usa `app-arm64-v8a-release.apk`.

## 3. Primer uso

1. Instalar el APK en el teléfono.
2. En la pantalla de acceso, tocar la dirección del **Servidor** y escribir la IP y el puerto de la PC donde corre Tomcat (por ejemplo `192.168.1.50:8080`).
3. Ingresar con el mismo correo y contraseña que se usan en el sistema web.
