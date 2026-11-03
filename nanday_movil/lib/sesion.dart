import 'alertas.dart';
import 'api.dart';

/// Usuario que inició sesión, con su rol y sus permisos (los mismos de la web:
/// seguridad/Permisos.java, copiado en APInanday). Se cargan con APInanday/c/movil?tipo=sesion.
class Sesion {
  static int id = 0;
  static String nombre = '';
  static String apellido = '';
  static String email = '';
  static String rol = ''; // código: ADMINISTRADOR, GERENTE, AUXILIAR, ENCARGADO_COMPRAS, CONSULTOR
  static String rolNombre = '';
  static String centro = '';
  static String foto = ''; // ruta relativa, ej.: uploads/perfiles/perfil_1.jpg
  static bool todosLosCentros = false;
  static Map<String, bool> _permisos = <String, bool>{};
  static List<Map<String, dynamic>> centros = <Map<String, dynamic>>[];

  // Permisos (Permisos.java)
  static const String refAdmin = 'REF_ADMIN';
  static const String proveedores = 'PROVEEDORES';
  static const String insumosEditar = 'INSUMOS_EDITAR';
  static const String insumosEliminar = 'INSUMOS_ELIMINAR';
  static const String solicitudCompra = 'SOLICITUD_COMPRA';
  static const String ocRecepcion = 'OC_RECEPCION';
  static const String otGestion = 'OT_GESTION';
  static const String otSuministrar = 'OT_SUMINISTRAR';
  static const String otFinalizar = 'OT_FINALIZAR';
  static const String inventario = 'INVENTARIO';
  static const String activos = 'ACTIVOS';
  static const String activosAprobar = 'ACTIVOS_APROBAR';
  static const String activosRecepcion = 'ACTIVOS_RECEPCION';
  static const String informes = 'INFORMES';

  static bool get iniciada => Api.haySesion && id > 0;
  static String get nombreCompleto => '$nombre $apellido'.trim();
  static bool get esAdministrador => rol == 'ADMINISTRADOR';

  static bool puede(String permiso) => esAdministrador || _permisos[permiso] == true;
  static bool puedeAlguno(List<String> lista) => lista.any(puede);

  static bool get verOT => puedeAlguno(<String>[otGestion, otSuministrar, otFinalizar]);
  static bool get verActivos => puedeAlguno(<String>[activos, activosAprobar, activosRecepcion]);

  /// Alertas de stock mínimo: igual que la web (roles con inventario o con solicitud de compra).
  static bool get verAlertasStock => puedeAlguno(<String>[inventario, solicitudCompra]);

  /// Pide al servidor los datos del usuario de la sesión.
  static Future<void> cargar() async {
    final dynamic d = await Api.movil('sesion');
    if (d is! Map) {
      throw ApiError('Respuesta inesperada del servidor.');
    }
    id = (d['id'] as num?)?.toInt() ?? 0;
    nombre = (d['nombre'] ?? '').toString();
    apellido = (d['apellido'] ?? '').toString();
    email = (d['email'] ?? '').toString();
    rol = (d['rol'] ?? '').toString();
    rolNombre = (d['rol_nombre'] ?? '').toString();
    centro = (d['centro'] ?? '').toString();
    foto = (d['foto'] ?? '').toString();
    final dynamic p = d['permisos'];
    _permisos = <String, bool>{};
    todosLosCentros = false;
    if (p is Map) {
      p.forEach((dynamic k, dynamic v) {
        if (v is bool) _permisos[k.toString()] = v;
      });
      todosLosCentros = p['todosLosCentros'] == true;
    }
    centros = <Map<String, dynamic>>[
      if (d['centros'] is List)
        for (final dynamic c in d['centros'] as List)
          if (c is Map) Map<String, dynamic>.from(c),
    ];
  }

  static void cerrar() {
    id = 0;
    nombre = '';
    apellido = '';
    email = '';
    rol = '';
    rolNombre = '';
    centro = '';
    foto = '';
    todosLosCentros = false;
    _permisos = <String, bool>{};
    centros = <Map<String, dynamic>>[];
    AlertasStock.detener();
    Api.olvidarSesion();
  }
}
