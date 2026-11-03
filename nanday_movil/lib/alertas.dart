import 'dart:async';

import 'package:flutter/foundation.dart' show defaultTargetPlatform, kIsWeb, TargetPlatform;
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

import 'api.dart';
import 'sesion.dart';

/// Alertas de stock mínimo como NOTIFICACIÓN del celular (notificación local, sin servicios externos).
///
/// - Se revisa al entrar al tablero y luego cada 15 minutos mientras la app está abierta.
/// - Usa la misma consulta que el tablero (inventarioControlador / alertasMinimo), así que respeta
///   el rol y los centros del usuario; solo corre si tiene el permiso INVENTARIO.
/// - Avisa solo cuando aparece un insumo NUEVO bajo el mínimo (no repite la misma alerta cada vez).
/// - Funciona en Android, iOS, macOS y Linux. En Windows y Chrome no hay notificación: las alertas
///   se siguen viendo en el tablero.
class AlertasStock {
  static const Duration intervalo = Duration(minutes: 15);
  static const int _idNotificacion = 1001;

  static final FlutterLocalNotificationsPlugin _plugin = FlutterLocalNotificationsPlugin();
  static bool _iniciado = false;
  static bool _disponible = false;
  static Timer? _timer;
  static final Set<String> _avisadas = <String>{};

  static bool get _soportado {
    if (kIsWeb) return false;
    switch (defaultTargetPlatform) {
      case TargetPlatform.android:
      case TargetPlatform.iOS:
      case TargetPlatform.macOS:
      case TargetPlatform.linux:
        return true;
      default:
        return false;
    }
  }

  static Future<void> _iniciarPlugin() async {
    if (_iniciado) return;
    _iniciado = true;
    if (!_soportado) return;
    try {
      const InitializationSettings ajustes = InitializationSettings(
        android: AndroidInitializationSettings('@mipmap/ic_launcher'),
        iOS: DarwinInitializationSettings(),
        macOS: DarwinInitializationSettings(),
        linux: LinuxInitializationSettings(defaultActionName: 'Abrir'),
      );
      await _plugin.initialize(ajustes);
      // Android 13 o superior: pide permiso para mostrar notificaciones (una sola vez)
      await _plugin
          .resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>()
          ?.requestNotificationsPermission();
      _disponible = true;
    } catch (_) {
      _disponible = false; // sin notificaciones: el tablero sigue mostrando las alertas
    }
  }

  /// Arranca la revisión periódica (se llama desde el tablero, que ya trae la primera lista
  /// de alertas y la pasa a [procesar]; por eso aquí no se consulta de nuevo al servidor).
  static Future<void> iniciar() async {
    if (!Sesion.verAlertasStock) return;
    await _iniciarPlugin();
    if (!_disponible) return;
    _timer ??= Timer.periodic(intervalo, (_) => revisar());
  }

  /// Detiene la revisión y olvida lo avisado (al cerrar sesión).
  static void detener() {
    _timer?.cancel();
    _timer = null;
    _avisadas.clear();
  }

  /// Consulta las alertas al servidor y notifica las nuevas.
  static Future<void> revisar() async {
    if (!_disponible || !Sesion.iniciada || !Sesion.verAlertasStock) return;
    try {
      final dynamic d = await Api.ctrl('inventarioControlador.jsp', 'alertasMinimo');
      if (d is List) {
        await procesar(<Map<String, dynamic>>[
          for (final dynamic e in d)
            if (e is Map) Map<String, dynamic>.from(e),
        ]);
      }
    } catch (_) {
      // sin conexión o sesión vencida: se reintenta en la próxima vuelta
    }
  }

  /// Recibe la lista de alertas (la del tablero o la de [revisar]) y notifica si hay nuevas.
  static Future<void> procesar(List<Map<String, dynamic>>? alertas) async {
    if (!_disponible || alertas == null) return;
    final Set<String> actuales = <String>{for (final Map<String, dynamic> a in alertas) '${a['codigo']}|${a['almacen']}'};
    // Las que se repusieron se olvidan: si vuelven a bajar, se avisa otra vez
    _avisadas.removeWhere((String k) => !actuales.contains(k));
    final List<Map<String, dynamic>> nuevas = <Map<String, dynamic>>[
      for (final Map<String, dynamic> a in alertas)
        if (!_avisadas.contains('${a['codigo']}|${a['almacen']}')) a,
    ];
    if (nuevas.isEmpty) return;
    _avisadas.addAll(actuales);

    final Map<String, dynamic> primera = nuevas.first;
    final String titulo = nuevas.length == 1 ? 'Insumo bajo el stock mínimo' : '${nuevas.length} insumos bajo el stock mínimo';
    final String cuerpo = nuevas.length == 1
        ? '${primera['nombre']} (${primera['almacen']}): ${primera['stock']} de mínimo ${primera['minimo']}'
        : nuevas.take(3).map((Map<String, dynamic> a) => '${a['nombre']} (${a['stock']}/${a['minimo']})').join(', ') +
            (nuevas.length > 3 ? ' y ${nuevas.length - 3} más' : '');

    const NotificationDetails detalles = NotificationDetails(
      android: AndroidNotificationDetails(
        'stock_minimo',
        'Stock mínimo',
        channelDescription: 'Avisa cuando un insumo queda en o por debajo del stock mínimo',
        importance: Importance.high,
        priority: Priority.high,
      ),
      iOS: DarwinNotificationDetails(),
      macOS: DarwinNotificationDetails(),
      linux: LinuxNotificationDetails(),
    );
    try {
      await _plugin.show(_idNotificacion, titulo, cuerpo, detalles);
    } catch (_) {
      // si el sistema no deja mostrarla, las alertas siguen en el tablero
    }
  }
}
