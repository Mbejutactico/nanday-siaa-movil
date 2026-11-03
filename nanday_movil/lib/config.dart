import 'package:flutter/foundation.dart' show defaultTargetPlatform, kIsWeb, TargetPlatform;
import 'package:shared_preferences/shared_preferences.dart';

/// Dirección de la API móvil (proyecto APInanday en Tomcat), que se conecta a la misma
/// base de datos que el sistema web. La app no depende del proyecto web.
///
/// Orden en que se elige el servidor:
///  1. El que el usuario guardó en la pantalla de inicio de sesión (botón "Servidor").
///  2. El pasado al compilar:  flutter build apk --dart-define=SERVIDOR=http://192.168.1.50:8080
///  3. Por defecto: Linux/Windows -> http://localhost:8080 ; emulador Android -> http://10.0.2.2:8080
/// Si la API se despliega con otro nombre: --dart-define=APLICACION=/otroNombre
class Config {
  static const String _claveServidor = 'servidor';

  /// Dirección pasada con --dart-define=SERVIDOR=... (vacía si no se pasó).
  static const String servidorElegido = String.fromEnvironment('SERVIDOR');

  /// Tomcat en la misma PC (Linux, Windows, macOS).
  static const String servidorLocal = 'http://localhost:8080';

  /// Emulador Android: así ve el emulador a la PC.
  static const String servidorEmulador = 'http://10.0.2.2:8080';

  /// Nombre de la API en Tomcat (APInanday.war -> /APInanday).
  static const String aplicacion = String.fromEnvironment('APLICACION', defaultValue: '/APInanday');

  /// Dirección guardada en el teléfono desde la pantalla de login ('' = no se guardó ninguna).
  static String _servidorGuardado = '';

  static bool get _esCelular =>
      !kIsWeb && (defaultTargetPlatform == TargetPlatform.android || defaultTargetPlatform == TargetPlatform.iOS);

  /// Servidor que se usa si el usuario no guardó ninguno.
  static String get servidorPorDefecto {
    if (servidorElegido.isNotEmpty) return _limpiar(servidorElegido);
    return _esCelular ? servidorEmulador : servidorLocal;
  }

  static String get servidor => _servidorGuardado.isNotEmpty ? _servidorGuardado : servidorPorDefecto;

  static String get urlBase => servidor + aplicacion;

  /// Lee el servidor guardado (se llama una vez al abrir la app).
  static Future<void> cargar() async {
    try {
      final SharedPreferences p = await SharedPreferences.getInstance();
      _servidorGuardado = _limpiar(p.getString(_claveServidor) ?? '');
    } catch (_) {
      _servidorGuardado = ''; // sin almacenamiento: se usa el servidor por defecto
    }
  }

  /// Guarda el servidor elegido por el usuario. Vacío = volver al servidor por defecto.
  static Future<void> guardar(String direccion) async {
    _servidorGuardado = _limpiar(direccion);
    try {
      final SharedPreferences p = await SharedPreferences.getInstance();
      if (_servidorGuardado.isEmpty) {
        await p.remove(_claveServidor);
      } else {
        await p.setString(_claveServidor, _servidorGuardado);
      }
    } catch (_) {
      // si no se pudo guardar, igual queda en uso hasta cerrar la app
    }
  }

  /// "192.168.1.50:8080/" -> "http://192.168.1.50:8080"
  static String _limpiar(String direccion) {
    String d = direccion.trim();
    if (d.isEmpty) return '';
    if (!d.startsWith('http://') && !d.startsWith('https://')) d = 'http://$d';
    while (d.endsWith('/')) {
      d = d.substring(0, d.length - 1);
    }
    return d;
  }

  /// Valida lo que escribe el usuario. Devuelve el mensaje de error o null si está bien.
  static String? validar(String direccion) {
    final String d = _limpiar(direccion);
    if (d.isEmpty) return null; // vacío = servidor por defecto
    final Uri? u = Uri.tryParse(d);
    if (u == null || u.host.isEmpty) return 'Dirección no válida. Ej.: 192.168.1.50:8080';
    if (u.path.isNotEmpty && u.path != '/') return 'Escriba solo la IP y el puerto, sin /APInanday';
    return null;
  }
}
