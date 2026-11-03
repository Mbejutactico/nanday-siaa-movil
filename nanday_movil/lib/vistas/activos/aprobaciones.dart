import 'dart:convert';

import 'package:flutter/material.dart';

import '../../api.dart';
import '../../sesion.dart';
import '../../tema.dart';
import '../comunes.dart';

/// ACTIVOS FIJOS > Aprobaciones (activosMovControlador.jsp), en tres pestañas:
///   Por aprobar  (ACTIVOS_APROBAR)   : aprobar (genera la remisión) o rechazar con motivo
///   Por recibir  (ACTIVOS_RECEPCION) : activos en tránsito o en reparación -> recibir o rechazar
///   Historial                        : solicitudes resueltas; anular una salida aprobada sin recepciones
class AprobacionesPantalla extends StatefulWidget {
  /// true = abre directamente la pestaña "Por recibir" (si el usuario la tiene).
  final bool porRecibir;

  const AprobacionesPantalla({super.key, this.porRecibir = false});

  @override
  State<AprobacionesPantalla> createState() => _AprobacionesPantallaState();
}

class _AprobacionesPantallaState extends State<AprobacionesPantalla> {
  @override
  Widget build(BuildContext context) {
    final List<Tab> pestanas = <Tab>[
      if (Sesion.puede(Sesion.activosAprobar)) const Tab(text: 'Por aprobar'),
      if (Sesion.puede(Sesion.activosRecepcion)) const Tab(text: 'Por recibir'),
      const Tab(text: 'Historial'),
    ];
    final int inicial = widget.porRecibir && Sesion.puede(Sesion.activosRecepcion)
        ? (Sesion.puede(Sesion.activosAprobar) ? 1 : 0)
        : 0;
    return DefaultTabController(
      length: pestanas.length,
      initialIndex: inicial,
      child: Scaffold(
        appBar: barraNanday(),
        body: Column(
          children: <Widget>[
            const EncabezadoPagina(titulo: 'Aprobaciones', migas: <String>['Inicio', 'Activos fijos', 'Aprobaciones']),
            Container(
              color: Colors.white,
              child: TabBar(
                tabs: pestanas,
                labelColor: Nd.profundo,
                indicatorColor: Nd.acento,
                unselectedLabelColor: Nd.apagado,
                labelStyle: const TextStyle(fontFamily: 'Inter', fontWeight: FontWeight.w700),
              ),
            ),
            Expanded(
              child: TabBarView(
                children: <Widget>[
                  if (Sesion.puede(Sesion.activosAprobar)) const _Solicitudes(pendientes: true),
                  if (Sesion.puede(Sesion.activosRecepcion)) const _PorRecibir(),
                  const _Solicitudes(pendientes: false),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Solicitudes pendientes (para aprobar) o el historial.
class _Solicitudes extends StatefulWidget {
  final bool pendientes;

  const _Solicitudes({required this.pendientes});

  @override
  State<_Solicitudes> createState() => _SolicitudesState();
}

class _SolicitudesState extends State<_Solicitudes> with AutomaticKeepAliveClientMixin<_Solicitudes> {
  final GlobalKey<CargableEstado<List<Map<String, dynamic>>>> _carga = GlobalKey<CargableEstado<List<Map<String, dynamic>>>>();
  bool _ocupado = false;

  /// Detalle de cada solicitud ya consultada (no se vuelve a pedir al expandir otra vez).
  final Map<int, List<Map<String, dynamic>>> _detalles = <int, List<Map<String, dynamic>>>{};

  @override
  bool get wantKeepAlive => true; // al cambiar de pestaña no se vuelve a cargar

  Future<void> _recargar() async {
    _detalles.clear();
    await _carga.currentState?.recargar();
  }

  Future<void> _operar(String tipo, Map<String, String> datos, String ok) async {
    if (_ocupado) return;
    setState(() => _ocupado = true);
    try {
      final Map<String, dynamic> r = mapa(await Api.ctrl('activosMovControlador.jsp', tipo, datos));
      if (!mounted) return;
      avisar(context, tx(r['remision']).isEmpty ? ok : '$ok. Remisión ${tx(r['remision'])}');
      await _recargar();
    } catch (e) {
      if (mounted) manejarError(context, e);
    } finally {
      if (mounted) setState(() => _ocupado = false);
    }
  }

  Future<void> _aprobar(Map<String, dynamic> s) async {
    if (!await confirmar(context, 'Aprobar ${tx(s['numero'])}',
        s['tipo'] == 'BAJA' ? 'Los activos quedan dados de BAJA (definitivo).' : 'Se genera la remisión y los activos salen.',
        aceptar: 'Aprobar')) {
      return;
    }
    await _operar('aprobar', <String, String>{'id': '${ent(s['id'])}'}, 'Solicitud ${tx(s['numero'])} aprobada');
  }

  Future<void> _rechazar(Map<String, dynamic> s) async {
    final String? motivo = await pedirTexto(context, titulo: 'Rechazar ${tx(s['numero'])}', aceptar: 'Rechazar', color: Nd.peligro);
    if (motivo == null) return;
    await _operar('rechazar', <String, String>{'id': '${ent(s['id'])}', 'motivo': motivo}, 'Solicitud rechazada');
  }

  Future<void> _anular(Map<String, dynamic> s) async {
    final String? motivo = await pedirTexto(context,
        titulo: 'Anular salida ${tx(s['numero'])}',
        mensaje: 'La remisión queda anulada y los activos vuelven a su estado y ubicación de origen.',
        aceptar: 'Anular',
        color: Nd.peligro);
    if (motivo == null) return;
    await _operar('anularSalida', <String, String>{'id': '${ent(s['id'])}', 'motivo': motivo}, 'Salida anulada');
  }

  @override
  Widget build(BuildContext context) {
    super.build(context); // AutomaticKeepAliveClientMixin
    final bool aprobar = Sesion.puede(Sesion.activosAprobar);
    return RefreshIndicator(
      onRefresh: _recargar,
      child: CustomScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        slivers: <Widget>[
          const SliverToBoxAdapter(child: SizedBox(height: 14)),
          Cargable<List<Map<String, dynamic>>>.sliver(
            key: _carga,
            cargar: () async => mapas(await Api.ctrl('activosMovControlador.jsp', 'listarSolicitudes',
                <String, String>{'estado': widget.pendientes ? 'PENDIENTE' : ''})),
            constructor: (BuildContext context, List<Map<String, dynamic>> lista, Future<void> Function() recargar) {
              if (lista.isEmpty) {
                return caja(Aviso(
                    icono: Icons.inbox, texto: widget.pendientes ? 'No hay solicitudes pendientes de aprobación.' : 'Todavía no hay solicitudes.'));
              }
              // solo se construyen las solicitudes visibles en pantalla
              return SliverList.builder(
                itemCount: lista.length,
                itemBuilder: (BuildContext c, int indice) {
                  final Map<String, dynamic> s = lista[indice];
                  final int id = ent(s['id']);
                  return Column(
                    key: ValueKey<int>(id),
                    children: <Widget>[
                    Tarjeta(
                      acento: ChipEstado.colorDe(tx(s['estado'])),
                      child: ExpansionTile(
                        shape: const Border(),
                        title: Row(children: <Widget>[
                          Text(tx(s['numero']), style: const TextStyle(fontWeight: FontWeight.w700, color: Nd.titulo)),
                          const SizedBox(width: 8),
                          ChipEstado(tx(s['estado'])),
                        ]),
                        subtitle: Text('${legible(tx(s['tipo']))}  ·  ${ent(s['activos'])} activo(s)\n${tx(s['destino'])}\n${tx(s['fecha'])}  ·  ${tx(s['solicitante'])}'),
                        childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                        children: <Widget>[
                          DatoFila('Observación', tx(s['observacion'])),
                          DatoFila('Motivo de baja', legible(tx(s['motivo_baja']))),
                          DatoFila('Transportista', tx(s['transportista'])),
                          DatoFila('Remisión', tx(s['remision'])),
                          DatoFila('Resolvió', '${tx(s['resolvio'])} ${tx(s['fecha_resolucion'])}'.trim()),
                          DatoFila('Motivo del rechazo', tx(s['motivo_rechazo'])),
                          DatoFila('Anulación', '${tx(s['fecha_anulacion'])} ${tx(s['motivo_anulacion'])}'.trim()),
                          if (s['tipo'] != 'BAJA' && tx(s['estado']) == 'APROBADA')
                            DatoFila('Recibidos', '${ent(s['recibidos'])} de ${ent(s['activos'])}'),
                          Cargable<List<Map<String, dynamic>>>(
                            cargar: () async => _detalles[id] ??=
                                mapas(await Api.ctrl('activosMovControlador.jsp', 'detalleSolicitud', <String, String>{'id': '$id'})),
                            constructor: (BuildContext c, List<Map<String, dynamic>> det, Future<void> Function() r) => Column(
                              children: <Widget>[
                                for (final Map<String, dynamic> d in det)
                                  ListTile(
                                    dense: true,
                                    contentPadding: EdgeInsets.zero,
                                    title: Text('${tx(d['numero_activo'])} - ${tx(d['descripcion'])}', style: const TextStyle(fontWeight: FontWeight.w600)),
                                    subtitle: Text('${tx(d['origen'])}  ·  ${legible(tx(d['estado_linea']))}'),
                                  ),
                              ],
                            ),
                          ),
                          if (widget.pendientes && aprobar && tx(s['estado']) == 'PENDIENTE')
                            Row(
                              mainAxisAlignment: MainAxisAlignment.end,
                              children: <Widget>[
                                OutlinedButton.icon(
                                  style: OutlinedButton.styleFrom(foregroundColor: Nd.peligro),
                                  onPressed: _ocupado ? null : () => _rechazar(s),
                                  icon: const Icon(Icons.close),
                                  label: const Text('Rechazar'),
                                ),
                                const SizedBox(width: 8),
                                FilledButton.icon(onPressed: _ocupado ? null : () => _aprobar(s), icon: const Icon(Icons.check), label: const Text('Aprobar')),
                              ],
                            ),
                          if (!widget.pendientes && aprobar && tx(s['estado']) == 'APROBADA' && s['tipo'] != 'BAJA' && ent(s['recibidos']) == 0)
                            Align(
                              alignment: Alignment.centerRight,
                              child: TextButton.icon(
                                style: TextButton.styleFrom(foregroundColor: Nd.peligro),
                                onPressed: _ocupado ? null : () => _anular(s),
                                icon: const Icon(Icons.block),
                                label: const Text('Anular salida'),
                              ),
                            ),
                        ],
                      ),
                    ),
                    espacio,
                    ],
                  );
                },
              );
            },
          ),
        ],
      ),
    );
  }
}

/// Activos en tránsito o en reparación que llegan a sus centros.
class _PorRecibir extends StatefulWidget {
  const _PorRecibir();

  @override
  State<_PorRecibir> createState() => _PorRecibirState();
}

class _PorRecibirState extends State<_PorRecibir> with AutomaticKeepAliveClientMixin<_PorRecibir> {
  final GlobalKey<CargableEstado<List<Map<String, dynamic>>>> _carga = GlobalKey<CargableEstado<List<Map<String, dynamic>>>>();
  final Set<int> _elegidos = <int>{};
  bool _ocupado = false;

  @override
  bool get wantKeepAlive => true;

  /// Carga la lista y quita de la selección los activos que ya no están pendientes
  /// (antes quedaban elegidos después de actualizar y se enviaban igual).
  Future<List<Map<String, dynamic>>> _cargar() async {
    final List<Map<String, dynamic>> lista = mapas(await Api.ctrl('activosMovControlador.jsp', 'enRecepcion'));
    final Set<int> ids = <int>{for (final Map<String, dynamic> a in lista) ent(a['id'])};
    if (mounted) setState(() => _elegidos.removeWhere((int id) => !ids.contains(id)));
    return lista;
  }

  Future<void> _procesar(bool rechazo) async {
    if (_ocupado) return;
    if (_elegidos.isEmpty) {
      avisar(context, 'Seleccione al menos un activo', error: true);
      return;
    }
    final String? obs = await pedirTexto(context,
        titulo: rechazo ? 'Rechazar recepción' : 'Recibir ${_elegidos.length} activo(s)',
        mensaje: rechazo ? 'Los activos vuelven a su origen.' : 'Quedan DISPONIBLES en el destino.',
        etiqueta: rechazo ? 'Justificación' : 'Observación',
        obligatorio: rechazo,
        aceptar: rechazo ? 'Rechazar' : 'Recibir',
        color: rechazo ? Nd.peligro : Nd.primario);
    if (obs == null || !mounted) return;
    setState(() => _ocupado = true);
    try {
      await Api.ctrl('activosMovControlador.jsp', rechazo ? 'rechazarRecepcion' : 'recibir',
          <String, String>{'ids': jsonEncode(_elegidos.toList()), 'observacion': obs});
      if (!mounted) return;
      avisar(context, rechazo ? 'Recepción rechazada' : 'Activos recibidos');
      _elegidos.clear();
      await _carga.currentState?.recargar();
    } catch (e) {
      if (mounted) manejarError(context, e);
    } finally {
      if (mounted) setState(() => _ocupado = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    super.build(context); // AutomaticKeepAliveClientMixin
    return Column(
      children: <Widget>[
        Expanded(
          child: RefreshIndicator(
            onRefresh: () async => _carga.currentState?.recargar(),
            child: CustomScrollView(
              physics: const AlwaysScrollableScrollPhysics(),
              slivers: <Widget>[
                const SliverToBoxAdapter(child: SizedBox(height: 14)),
                Cargable<List<Map<String, dynamic>>>.sliver(
                  key: _carga,
                  cargar: _cargar,
                  constructor: (BuildContext context, List<Map<String, dynamic>> lista, Future<void> Function() recargar) {
                    if (lista.isEmpty) {
                      return caja(const Aviso(icono: Icons.inbox, texto: 'No hay activos pendientes de recepción.'));
                    }
                    return SliverTarjetaLista(
                      cantidad: lista.length,
                      fila: (BuildContext c, int i) {
                        final Map<String, dynamic> a = lista[i];
                        return CheckboxListTile(
                              value: _elegidos.contains(ent(a['id'])),
                              onChanged: (bool? v) => setState(() => v == true ? _elegidos.add(ent(a['id'])) : _elegidos.remove(ent(a['id']))),
                              controlAffinity: ListTileControlAffinity.leading,
                              title: Text('${tx(a['numero_activo'])} - ${tx(a['descripcion'])}', style: const TextStyle(fontWeight: FontWeight.w600)),
                              subtitle: Text('${legible(tx(a['tipo']))} ${tx(a['solicitud'])}  ·  ${tx(a['remision'])}\n'
                                  '${tx(a['origen'])} → ${tx(a['destino'])}'),
                              isThreeLine: true,
                            );
                      },
                    );
                  },
                ),
              ],
            ),
          ),
        ),
        SafeArea(
          child: Container(
            padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
            decoration: const BoxDecoration(color: Colors.white, border: Border(top: BorderSide(color: Nd.borde))),
            child: Row(
              children: <Widget>[
                Expanded(
                  child: OutlinedButton(
                    style: OutlinedButton.styleFrom(foregroundColor: Nd.peligro),
                    onPressed: _ocupado ? null : () => _procesar(true),
                    child: const Text('Rechazar'),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: FilledButton(
                    onPressed: _ocupado ? null : () => _procesar(false),
                    child: Text('Recibir (${_elegidos.length})'),
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
