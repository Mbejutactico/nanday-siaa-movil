import 'dart:async';

import 'package:flutter/material.dart';

import '../api.dart';
import '../sesion.dart';
import '../tema.dart';
import 'activos/aprobaciones.dart';
import 'activos/lista_activos.dart';
import 'bitacora.dart';
import 'comunes.dart';
import 'inventario/stock.dart';
import 'ot/detalle_ot.dart';

/// Campana de notificaciones de la barra superior, igual que la de la web
/// (notificacionControlador.jsp?tipo=listar):
///   Pendientes: tareas que siguen abiertas hasta que se resuelven (stock bajo el mínimo,
///               solicitudes por aprobar, activos por recibir, inicios de sesión fallidos).
///   Novedades:  OT nuevas o asignadas y solicitudes de activos aprobadas o rechazadas.
/// Se actualiza cada minuto mientras la pantalla está abierta. Al abrir la lista, las novedades
/// quedan vistas (también en la web, porque se guarda en el servidor).
class CampanaNotificaciones extends StatefulWidget {
  final Future<void> Function(Widget pantalla) alAbrir;

  const CampanaNotificaciones({super.key, required this.alAbrir});

  @override
  State<CampanaNotificaciones> createState() => CampanaNotificacionesState();
}

class CampanaNotificacionesState extends State<CampanaNotificaciones> {
  static const Duration _intervalo = Duration(minutes: 1);

  List<Map<String, dynamic>> _pendientes = <Map<String, dynamic>>[];
  List<Map<String, dynamic>> _novedades = <Map<String, dynamic>>[];
  int _nuevas = 0;
  Timer? _timer;
  bool _consultando = false;

  @override
  void initState() {
    super.initState();
    actualizar();
    _timer = Timer.periodic(_intervalo, (_) => actualizar());
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  /// Pide las notificaciones al servidor (sin avisar errores: se reintenta en la próxima vuelta).
  Future<void> actualizar() async {
    if (_consultando || !Sesion.iniciada) return;
    _consultando = true;
    try {
      final Map<String, dynamic> d = mapa(await Api.ctrl('notificacionControlador.jsp', 'listar'));
      if (!mounted) return;
      setState(() {
        _pendientes = mapas(d['pendientes']);
        _novedades = mapas(d['novedades']);
        _nuevas = ent(d['nuevas']);
      });
    } on ApiError catch (e) {
      if (e.sesionVencida && mounted) manejarError(context, e);
    } catch (_) {
      // sin conexión: se vuelve a intentar en la próxima vuelta
    } finally {
      _consultando = false;
    }
  }

  int get _cantidad => _pendientes.length + _nuevas;

  Future<void> _abrirLista() async {
    await actualizar();
    if (!mounted) return;
    final Widget? destino = await showModalBottomSheet<Widget>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (BuildContext hoja) => _ListaNotificaciones(pendientes: _pendientes, novedades: _novedades),
    );
    // Al abrir la lista, las novedades quedan vistas (igual que en la web)
    if (_nuevas > 0) {
      try {
        await Api.ctrl('notificacionControlador.jsp', 'marcarVistas');
      } catch (_) {
        // si falla, se marcarán la próxima vez
      }
      await actualizar();
    }
    if (destino != null) {
      await widget.alAbrir(destino);
      await actualizar();
    }
  }

  @override
  Widget build(BuildContext context) {
    final int n = _cantidad;
    return IconButton(
      tooltip: n == 0 ? 'Notificaciones' : '$n notificación${n == 1 ? '' : 'es'}',
      onPressed: _abrirLista,
      icon: Badge(
        isLabelVisible: n > 0,
        backgroundColor: Nd.peligro,
        label: Text(n > 99 ? '99+' : '$n'),
        child: Icon(n > 0 ? Icons.notifications_active : Icons.notifications_none, color: n > 0 ? Nd.primario : null),
      ),
    );
  }
}

/// Lista de la campana. Al tocar una notificación devuelve la pantalla que hay que abrir.
class _ListaNotificaciones extends StatelessWidget {
  final List<Map<String, dynamic>> pendientes;
  final List<Map<String, dynamic>> novedades;

  const _ListaNotificaciones({required this.pendientes, required this.novedades});

  static IconData _icono(String tipo) {
    switch (tipo) {
      case 'STOCK':
        return Icons.warning_amber;
      case 'APROBAR':
        return Icons.done_all;
      case 'RECIBIR':
        return Icons.move_to_inbox;
      case 'SEGURIDAD':
        return Icons.lock_person;
      case 'OT':
        return Icons.assignment;
      default:
        return Icons.devices_other;
    }
  }

  static Color _color(String tipo) {
    switch (tipo) {
      case 'STOCK':
        return Nd.acento;
      case 'APROBAR':
        return Nd.info;
      case 'RECIBIR':
        return Nd.teal;
      case 'SEGURIDAD':
        return Nd.peligro;
      case 'OT':
        return Nd.primario;
      default:
        return Nd.apagado;
    }
  }

  /// Pantalla donde se resuelve cada notificación (null = solo informa).
  static Widget? _destino(Map<String, dynamic> n) {
    switch (tx(n['tipo'])) {
      case 'STOCK':
        return Sesion.puede(Sesion.inventario) ? const StockPantalla() : null;
      case 'APROBAR':
        return const AprobacionesPantalla();
      case 'RECIBIR':
        return const AprobacionesPantalla(porRecibir: true);
      case 'SEGURIDAD':
        return const BitacoraPantalla(accion: 'LOGIN_FALLIDO', soloHoy: true);
      case 'OT':
        return ent(n['id']) > 0 ? DetalleOTPantalla(idOT: ent(n['id'])) : null;
      case 'SOLICITUD':
        return Sesion.puede(Sesion.activos) ? const ListaActivosPantalla() : null;
      default:
        return null;
    }
  }

  /// "hace 5 min", "hace 2 h", "ayer"... a partir de la fecha ISO del servidor.
  static String _hace(String iso) {
    final DateTime? f = DateTime.tryParse(iso);
    if (f == null) return '';
    final Duration d = DateTime.now().difference(f);
    if (d.inMinutes < 1) return 'recién';
    if (d.inMinutes < 60) return 'hace ${d.inMinutes} min';
    if (d.inHours < 24) return 'hace ${d.inHours} h';
    if (d.inDays == 1) return 'ayer';
    return 'hace ${d.inDays} días';
  }

  @override
  Widget build(BuildContext context) {
    final int total = pendientes.length + novedades.length;
    Widget seccion(String titulo) => Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
          child: Text(titulo, style: const TextStyle(fontSize: 12, letterSpacing: 1.1, fontWeight: FontWeight.w600, color: Nd.apagado)),
        );

    Widget fila(Map<String, dynamic> n, {required bool novedad}) {
      final String tipo = tx(n['tipo']);
      final Widget? destino = _destino(n);
      final bool nueva = novedad && n['nueva'] == true;
      final String detalle = tx(n['detalle']);
      final String cuando = novedad ? _hace(tx(n['fecha'])) : '';
      return Material(
        color: nueva ? Nd.verdeSuave : Colors.white,
        child: InkWell(
          onTap: destino == null ? null : () => Navigator.of(context).pop(destino),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 10, 12, 10),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Icon(_icono(tipo), color: _color(tipo), size: 22),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Row(
                        children: <Widget>[
                          if (nueva) ...<Widget>[
                            Container(width: 8, height: 8, decoration: const BoxDecoration(color: Nd.peligro, shape: BoxShape.circle)),
                            const SizedBox(width: 6),
                          ],
                          Expanded(
                            child: Text(tx(n['texto']),
                                style: TextStyle(fontWeight: nueva ? FontWeight.w700 : FontWeight.w600, color: Nd.titulo)),
                          ),
                        ],
                      ),
                      if (detalle.isNotEmpty) Text(detalle, style: const TextStyle(color: Nd.apagado, fontSize: 12.5)),
                      if (cuando.isNotEmpty) Text(cuando, style: const TextStyle(color: Nd.apagado, fontSize: 12)),
                    ],
                  ),
                ),
                if (destino != null) const Icon(Icons.chevron_right, color: Nd.apagado),
              ],
            ),
          ),
        ),
      );
    }

    return SafeArea(
      child: ConstrainedBox(
        constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.8),
        child: ListView(
          shrinkWrap: true,
          children: <Widget>[
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 4),
              child: Text(
                total == 0 ? 'Sin notificaciones' : '$total notificación${total == 1 ? '' : 'es'}',
                textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700, color: Nd.titulo),
              ),
            ),
            if (total == 0)
              const Padding(
                padding: EdgeInsets.all(24),
                child: Text('No tiene tareas pendientes ni novedades de los últimos 7 días.',
                    textAlign: TextAlign.center, style: TextStyle(color: Nd.apagado)),
              ),
            if (pendientes.isNotEmpty) ...<Widget>[
              seccion('PENDIENTES'),
              for (final Map<String, dynamic> n in pendientes) fila(n, novedad: false),
            ],
            if (novedades.isNotEmpty) ...<Widget>[
              seccion('NOVEDADES'),
              for (final Map<String, dynamic> n in novedades) fila(n, novedad: true),
            ],
            const SizedBox(height: 12),
          ],
        ),
      ),
    );
  }
}
