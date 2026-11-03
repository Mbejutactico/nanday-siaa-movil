import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart' show MediaType;

import 'config.dart';

/// Error devuelto por la API (o de conexión) con un mensaje listo para mostrar.
class ApiError implements Exception {
  final String mensaje;
  final int estado; // código HTTP; 0 = no hubo respuesta del servidor

  ApiError(this.mensaje, [this.estado = 0]);

  bool get sesionVencida => estado == 401;

  @override
  String toString() => mensaje;
}

/// Todas las llamadas a la API (proyecto APInanday en Tomcat) pasan por aquí.
///
/// Al iniciar sesión (LoginServlet) la API entrega un token, que va en cada pedido
/// (cabecera Authorization). Las operaciones de los módulos tienen los mismos nombres que
/// en el sistema web, porque APInanday tiene una copia de esa lógica:
///   Api.ctrl('otControlador.jsp', 'listar', {...})   -> POST APInanday/c/otControlador
///   Api.movil('stock', {...})                         -> POST APInanday/c/movil
///   Api.html('perfilcontrolador.jsp', 'ActualizarDatos', {...}) -> POST APInanday/PerfilServlet
class Api {
  static const Duration _espera = Duration(seconds: 25);
  static String _token = '';

  /// Combos que casi no cambian (almacenes, centros, unidades...): se guardan unos minutos
  /// para no pedirlos al servidor cada vez que se abre un selector.
  static const Duration _vidaCache = Duration(minutes: 5);
  static final Map<String, (DateTime, dynamic)> _cache = <String, (DateTime, dynamic)>{};

  static bool get haySesion => _token.isNotEmpty;

  /// Cabeceras para cargar imágenes protegidas (fotos de perfil).
  static Map<String, String> get cabecerasImagen => <String, String>{'Authorization': 'Bearer $_token'};

  /// Dirección de la foto de perfil de la app (nombre "perfil_N.jpg", carpeta propia de APInanday).
  static String urlFoto(String ruta) {
    final String archivo = ruta.split('/').last;
    return '${Config.urlBase}/FotoServlet?archivo=${Uri.encodeQueryComponent(archivo)}';
  }

  static Uri _url(String ruta) => Uri.parse('${Config.urlBase}/$ruta');

  static Map<String, String> get _cabeceras => <String, String>{
        if (_token.isNotEmpty) 'Authorization': 'Bearer $_token',
      };

  /// Servicio de la API que atiende cada controlador.
  static String _servicio(String controlador) {
    if (controlador == 'perfilcontrolador.jsp') return 'PerfilServlet';
    return 'c/${controlador.replaceAll('.jsp', '')}';
  }

  /// Inicia sesión con el email y la contraseña de la web. Lanza ApiError si no se pudo.
  static Future<void> login(String email, String password) async {
    _token = '';
    _cache.clear();
    final http.Response r = await _enviar(() => http.post(
          _url('LoginServlet'),
          body: <String, String>{'email': email, 'password': password},
        ));
    final dynamic datos = _json(r);
    if (r.statusCode != 200 || datos is! Map || (datos['token'] ?? '').toString().isEmpty) {
      throw ApiError(datos is Map && datos['error'] != null ? datos['error'].toString() : 'No se pudo iniciar sesión.', r.statusCode);
    }
    _token = datos['token'].toString();
  }

  /// Cierra la sesión en la API (DELETE LoginServlet).
  static Future<void> logout() async {
    if (_token.isEmpty) return;
    try {
      await http.delete(_url('LoginServlet'), headers: _cabeceras).timeout(const Duration(seconds: 4));
    } catch (_) {
      // sin conexión: igual se olvida la sesión en el teléfono
    }
    olvidarSesion();
  }

  static void olvidarSesion() {
    _token = '';
    _cache.clear();
  }

  /// Operación [tipo] de un módulo. Devuelve el JSON decodificado (Map o List) o el texto si no es JSON.
  static Future<dynamic> ctrl(String controlador, String tipo, [Map<String, String>? datos]) async {
    final http.Response r = await _enviar(() => http.post(
          _url(_servicio(controlador)),
          headers: _cabeceras,
          body: <String, String>{'tipo': tipo, ...?datos},
        ));
    return _interpretar(r);
  }

  /// Combos, stock, OC por recibir, proveedores e insumos (APInanday/c/movil).
  static Future<dynamic> movil(String tipo, [Map<String, String>? datos]) => ctrl('movil', tipo, datos);

  /// Igual que [movil], pero reutiliza la respuesta durante unos minutos.
  /// Solo para listas que casi no cambian (almacenes, centros, centros de costo, unidades...).
  static Future<dynamic> movilCacheado(String tipo, [Map<String, String>? datos]) async {
    final String clave = '$tipo|${jsonEncode(datos ?? const <String, String>{})}';
    final (DateTime, dynamic)? guardado = _cache[clave];
    if (guardado != null && DateTime.now().difference(guardado.$1) < _vidaCache) {
      return guardado.$2;
    }
    final dynamic r = await movil(tipo, datos);
    _cache[clave] = (DateTime.now(), r);
    return r;
  }

  /// Olvida los combos guardados (por ejemplo, al tocar "Actualizar").
  static void limpiarCache() => _cache.clear();

  /// Operaciones que responden con un mensaje (aviso). Devuelve el texto del mensaje.
  static Future<String> html(String controlador, String tipo, Map<String, String> datos) async {
    final dynamic r = await ctrl(controlador, tipo, datos);
    if (r is Map) return (r['mensaje'] ?? 'Operación realizada').toString();
    return textoPlano(r.toString());
  }

  /// Sube la foto de perfil (multipart, campo "foto"). Devuelve la ruta nueva.
  static Future<String> subirFoto(List<int> bytes, String nombreArchivo, String tipoContenido) async {
    final http.MultipartRequest req = http.MultipartRequest('POST', _url('PerfilServlet'));
    req.headers.addAll(_cabeceras);
    req.fields['tipo'] = 'ActualizarFoto';
    final List<String> partes = tipoContenido.split('/');
    req.files.add(http.MultipartFile.fromBytes(
      'foto',
      bytes,
      filename: nombreArchivo,
      contentType: MediaType(partes[0], partes.length > 1 ? partes[1] : 'jpeg'),
    ));
    final http.Response r = await _enviar(() async => http.Response.fromStream(await req.send()));
    final dynamic resultado = _interpretar(r);
    return resultado is Map ? (resultado['foto'] ?? '').toString() : resultado.toString().trim();
  }

  // ------------------------------------------------------------------ interno

  static Future<http.Response> _enviar(Future<http.Response> Function() pedido) async {
    try {
      return await pedido().timeout(_espera);
    } on TimeoutException {
      throw ApiError('El servidor tarda demasiado en responder. Intente de nuevo.');
    } catch (_) {
      throw ApiError('No se pudo conectar con el servidor (${Config.urlBase}). '
          'Verifique que Tomcat esté iniciado con APInanday y la dirección del servidor.');
    }
  }

  static String _texto(http.Response r) {
    try {
      return utf8.decode(r.bodyBytes); // respeta tildes y ñ
    } catch (_) {
      return latin1.decode(r.bodyBytes);
    }
  }

  static dynamic _json(http.Response r) {
    final String texto = _texto(r).trim();
    if (texto.startsWith('{') || texto.startsWith('[')) {
      try {
        return jsonDecode(texto);
      } catch (_) {
        return texto;
      }
    }
    return texto;
  }

  static dynamic _interpretar(http.Response r) {
    final dynamic datos = _json(r);
    final String error = datos is Map && datos['error'] != null ? datos['error'].toString() : '';
    if (r.statusCode == 401) {
      _token = '';
      throw ApiError(error.isNotEmpty ? error : 'La sesión venció. Vuelva a iniciar sesión.', 401);
    }
    if (r.statusCode == 404 && error.isEmpty) {
      throw ApiError('No se encontró el servicio en el servidor (¿está desplegada la versión nueva de APInanday?).', 404);
    }
    if (r.statusCode < 200 || r.statusCode >= 300) {
      throw ApiError(error.isNotEmpty ? error : 'El servidor respondió con el error ${r.statusCode}.', r.statusCode);
    }
    if (datos is Map) {
      if (datos['ok'] == false) {
        throw ApiError(error.isNotEmpty ? error : 'No se pudo completar la operación.');
      }
      if (!datos.containsKey('ok') && error.isNotEmpty) {
        throw ApiError(error);
      }
    } else if (datos is String) {
      if (datos.toLowerCase().startsWith('error')) {
        throw ApiError(datos.replaceFirst(RegExp(r'^error:?\s*', caseSensitive: false), ''));
      }
      if (datos.contains('alert-danger')) {
        throw ApiError(textoPlano(datos));
      }
    }
    return datos;
  }

  /// Entidades HTML con nombre que pueden venir en los avisos (tildes, ñ, signos).
  static const Map<String, String> _entidades = <String, String>{
    'nbsp': ' ',
    'lt': '<',
    'gt': '>',
    'quot': '"',
    'apos': "'",
    'aacute': 'á',
    'eacute': 'é',
    'iacute': 'í',
    'oacute': 'ó',
    'uacute': 'ú',
    'Aacute': 'Á',
    'Eacute': 'É',
    'Iacute': 'Í',
    'Oacute': 'Ó',
    'Uacute': 'Ú',
    'ntilde': 'ñ',
    'Ntilde': 'Ñ',
    'uuml': 'ü',
    'Uuml': 'Ü',
    'iquest': '¿',
    'iexcl': '¡',
    'deg': '°',
    'ordm': 'º',
    'ordf': 'ª',
    'middot': '·',
    'bull': '•',
    'hellip': '…',
    'ndash': '–',
    'mdash': '—',
  };

  /// Quita las etiquetas HTML de un aviso y deja el texto legible.
  static String textoPlano(String html) {
    final String sinEtiquetas = html
        .replaceAll(RegExp(r'<br\s*/?>|</li>|</p>|</div>', caseSensitive: false), '\n')
        .replaceAll(RegExp(r'<li[^>]*>', caseSensitive: false), '• ')
        .replaceAll(RegExp(r'<[^>]+>'), '');
    // &eacute; &#233; &#xE9; -> é   (&amp; se deja para el final para no decodificar dos veces)
    final String decodificado = sinEtiquetas.replaceAllMapped(RegExp(r'&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);'), (Match m) {
      final String e = m.group(1)!;
      if (e == 'amp') return '\u0000';
      if (e.startsWith('#')) {
        final int? c = e.startsWith('#x') ? int.tryParse(e.substring(2), radix: 16) : int.tryParse(e.substring(1));
        return c == null || c <= 0 || c > 0x10FFFF ? m.group(0)! : String.fromCharCode(c);
      }
      return _entidades[e] ?? m.group(0)!;
    });
    return decodificado.replaceAll('\u0000', '&').replaceAll(RegExp(r'\n\s*\n+'), '\n').trim();
  }
}
