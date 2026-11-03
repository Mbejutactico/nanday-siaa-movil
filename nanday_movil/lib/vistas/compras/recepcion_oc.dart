import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api.dart';
import '../../tema.dart';
import '../comunes.dart';

/// COMPRAS > Recepción de OC: órdenes de compra SOLICITADAS o PARCIALES de sus centros.
/// Insumos: cantidad + almacén (entran al stock y al Kardex).
/// Activos (equipos): centro de costo + número de serie opcional (el sistema genera el número de activo).
/// Usa ocControlador.jsp (obtenerDetalleRecepcion / procesarRecepcion), igual que la web.
class RecepcionOCPantalla extends StatefulWidget {
  const RecepcionOCPantalla({super.key});

  @override
  State<RecepcionOCPantalla> createState() => _RecepcionOCPantallaState();
}

class _RecepcionOCPantallaState extends State<RecepcionOCPantalla> {
  final GlobalKey<CargableEstado<List<Map<String, dynamic>>>> _carga = GlobalKey<CargableEstado<List<Map<String, dynamic>>>>();

  @override
  Widget build(BuildContext context) {
    return PaginaApp(
      titulo: 'Recepción de OC',
      migas: const <String>['Inicio', 'Compras', 'Recepción OC'],
      alRefrescar: () async => _carga.currentState?.recargar(),
      hijos: <Widget>[
        Cargable<List<Map<String, dynamic>>>(
          key: _carga,
          cargar: () async => mapas(await Api.movil('ocPendientes')),
          constructor: (BuildContext context, List<Map<String, dynamic>> ocs, Future<void> Function() recargar) {
            if (ocs.isEmpty) {
              return const Aviso(icono: Icons.inventory, texto: 'No hay órdenes de compra pendientes de recepción en sus centros.');
            }
            return Tarjeta(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: <Widget>[
                  CabeceraTarjeta(titulo: 'Pendientes de recepción (${ocs.length})', icono: Icons.local_shipping),
                  for (final Map<String, dynamic> oc in ocs)
                    ListTile(
                      title: Row(children: <Widget>[
                        Flexible(child: Text(tx(oc['numero']), style: const TextStyle(fontWeight: FontWeight.w700, color: Nd.titulo))),
                        const SizedBox(width: 8),
                        ChipEstado(tx(oc['estado'])),
                      ]),
                      subtitle: Text('${tx(oc['proveedor'])}\n${tx(oc['centro'])}  ·  ${fechaCorta(tx(oc['fecha']))}  ·  ${ent(oc['lineas'])} línea(s) pendientes'),
                      isThreeLine: true,
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () async {
                        final bool? hecho = await Navigator.of(context).push<bool>(MaterialPageRoute<bool>(
                          builder: (BuildContext c) => RecibirOCPantalla(oc: oc),
                        ));
                        if (hecho == true) recargar();
                      },
                    ),
                ],
              ),
            );
          },
        ),
      ],
    );
  }
}

class _LineaRecepcion {
  final Map<String, dynamic> d;
  final TextEditingController cantidad = TextEditingController();
  final TextEditingController serie = TextEditingController();
  Opcion? almacen;
  Opcion? centroCosto;

  _LineaRecepcion(this.d);

  bool get esEquipo => tx(d['tipo_item']) == 'EQUIPO';
  int get pendiente => ent(d['pendiente']);
  int get aRecibir => int.tryParse(cantidad.text.trim()) ?? 0;
}

/// Recepción de una OC: por cada línea pendiente, cuánto se recibe y dónde queda.
class RecibirOCPantalla extends StatefulWidget {
  final Map<String, dynamic> oc;

  const RecibirOCPantalla({super.key, required this.oc});

  @override
  State<RecibirOCPantalla> createState() => _RecibirOCPantallaState();
}

class _RecibirOCPantallaState extends State<RecibirOCPantalla> {
  List<_LineaRecepcion>? _lineas;
  List<Opcion> _almacenes = <Opcion>[];
  List<Opcion> _centrosCosto = <Opcion>[];
  String? _error;
  bool _guardando = false;

  int get _idCentro => ent(widget.oc['id_centro']);

  @override
  void initState() {
    super.initState();
    _cargar();
  }

  @override
  void dispose() {
    for (final _LineaRecepcion l in _lineas ?? <_LineaRecepcion>[]) {
      l.cantidad.dispose();
      l.serie.dispose();
    }
    super.dispose();
  }

  Future<void> _cargar() async {
    setState(() => _error = null);
    try {
      // los tres pedidos van juntos (en paralelo)
      final List<dynamic> r = await Future.wait(<Future<dynamic>>[
        Api.ctrl('ocControlador.jsp', 'obtenerDetalleRecepcion', <String, String>{'id': '${ent(widget.oc['id'])}'}),
        Api.movilCacheado('almacenes', <String, String>{'id_centro': '$_idCentro'}),
        Api.movilCacheado('centrosCosto', <String, String>{'id_centro': '$_idCentro'}),
      ]);
      final Map<String, dynamic> det = mapa(r[0]);
      final List<Opcion> almacenes = mapas(r[1]).map((Map<String, dynamic> a) => Opcion(ent(a['id']), tx(a['nombre']))).toList();
      final List<Opcion> ccs = Opcion.lista(r[2]);
      if (!mounted) return;
      setState(() {
        _lineas = mapas(det['detalle']).map((Map<String, dynamic> d) => _LineaRecepcion(d)).toList();
        _almacenes = almacenes;
        _centrosCosto = ccs;
        // Si hay un solo almacén o un solo centro de costo, queda elegido
        for (final _LineaRecepcion l in _lineas!) {
          if (!l.esEquipo && almacenes.length == 1) l.almacen = almacenes.first;
          if (l.esEquipo && ccs.length == 1) l.centroCosto = ccs.first;
        }
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = e is ApiError ? e.mensaje : 'No se pudo obtener la OC.');
      if (e is ApiError && e.sesionVencida) manejarError(context, e);
    }
  }

  void _recibirTodo() {
    setState(() {
      for (final _LineaRecepcion l in _lineas ?? <_LineaRecepcion>[]) {
        l.cantidad.text = '${l.pendiente}';
      }
    });
  }

  Future<void> _elegirParaTodos(bool equipos) async {
    final Opcion? o = await elegirOpcion(context, equipos ? 'Centro de costo para todos los activos' : 'Almacén para todos los insumos',
        equipos ? _centrosCosto : _almacenes);
    if (o == null) return;
    setState(() {
      for (final _LineaRecepcion l in _lineas ?? <_LineaRecepcion>[]) {
        if (equipos && l.esEquipo) l.centroCosto = o;
        if (!equipos && !l.esEquipo) l.almacen = o;
      }
    });
  }

  Future<void> _procesar() async {
    if (_guardando) return;
    final List<Map<String, dynamic>> datos = <Map<String, dynamic>>[];
    for (final _LineaRecepcion l in _lineas ?? <_LineaRecepcion>[]) {
      final int c = l.aRecibir;
      if (c == 0) continue;
      if (c < 0 || c > l.pendiente) {
        avisar(context, '${tx(l.d['descripcion'])}: la cantidad debe estar entre 0 y ${l.pendiente}', error: true);
        return;
      }
      if (l.esEquipo) {
        if (l.centroCosto == null) {
          avisar(context, 'Elija el centro de costo de ${tx(l.d['descripcion'])}', error: true);
          return;
        }
        datos.add(<String, dynamic>{'id_detalle': ent(l.d['id_detalle']), 'cantidad': c, 'id_centro_costo': l.centroCosto!.id, 'serie': l.serie.text.trim()});
      } else {
        if (l.almacen == null) {
          avisar(context, 'Elija el almacén de ${tx(l.d['descripcion'])}', error: true);
          return;
        }
        datos.add(<String, dynamic>{'id_detalle': ent(l.d['id_detalle']), 'cantidad': c, 'id_almacen': l.almacen!.id});
      }
    }
    if (datos.isEmpty) {
      avisar(context, 'Indique al menos un artículo con cantidad a recibir mayor a 0', error: true);
      return;
    }
    if (!await confirmar(context, 'Procesar recepción', 'Se recibirán ${datos.length} línea(s) de ${tx(widget.oc['numero'])}.', aceptar: 'Recibir')) {
      return;
    }
    if (!mounted) return;
    setState(() => _guardando = true);
    try {
      final String mensaje = await Api.html('ocControlador.jsp', 'procesarRecepcion', <String, String>{
        'id_compra': '${ent(widget.oc['id'])}',
        'datos': jsonEncode(datos),
      });
      if (!mounted) return;
      await mostrarResultado(context, 'Recepción procesada', mensaje);
      if (mounted) Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) manejarError(context, e);
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final List<_LineaRecepcion> lineas = _lineas ?? <_LineaRecepcion>[];
    final bool hayInsumos = lineas.any((_LineaRecepcion l) => !l.esEquipo);
    final bool hayEquipos = lineas.any((_LineaRecepcion l) => l.esEquipo);
    return PaginaApp(
      titulo: 'Recibir ${tx(widget.oc['numero'])}',
      migas: const <String>['Inicio', 'Compras', 'Recepción OC'],
      hijos: <Widget>[
        Tarjeta(
          acento: Nd.primario,
          child: CuerpoTarjeta(<Widget>[
            DatoFila('Proveedor', tx(widget.oc['proveedor'])),
            DatoFila('Centro destino', tx(widget.oc['centro'])),
            DatoFila('Fecha', fechaCorta(tx(widget.oc['fecha']))),
            DatoFila('Estado', legible(tx(widget.oc['estado']))),
          ]),
        ),
        espacio,
        if (_error != null)
          Aviso(icono: Icons.error_outline, texto: _error!, alReintentar: _cargar)
        else if (_lineas == null)
          const Padding(padding: EdgeInsets.all(30), child: Cargando())
        else if (lineas.isEmpty)
          const Aviso(icono: Icons.check_circle, texto: 'La OC no tiene líneas pendientes de recepción.')
        else ...<Widget>[
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Wrap(
              spacing: 8,
              runSpacing: 6,
              children: <Widget>[
                OutlinedButton.icon(onPressed: _recibirTodo, icon: const Icon(Icons.done_all), label: const Text('Recibir todo lo pendiente')),
                if (hayInsumos)
                  OutlinedButton.icon(onPressed: () => _elegirParaTodos(false), icon: const Icon(Icons.warehouse), label: const Text('Almacén para todos')),
                if (hayEquipos)
                  OutlinedButton.icon(onPressed: () => _elegirParaTodos(true), icon: const Icon(Icons.apartment), label: const Text('C. de costo para todos')),
              ],
            ),
          ),
          espacio,
          for (final _LineaRecepcion l in lineas) ...<Widget>[_tarjetaLinea(l), espacio],
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: BotonPrincipal(texto: 'Procesar recepción', icono: Icons.inventory, ocupado: _guardando, alTocar: _procesar),
          ),
        ],
      ],
    );
  }

  Widget _tarjetaLinea(_LineaRecepcion l) {
    return Tarjeta(
      acento: l.esEquipo ? Nd.info : null,
      child: CuerpoTarjeta(<Widget>[
        Row(children: <Widget>[
          Insignia(texto: l.esEquipo ? 'Activo fijo' : 'Insumo', color: l.esEquipo ? Nd.info : Nd.primario),
          const SizedBox(width: 8),
          Text(tx(l.d['codigo']), style: const TextStyle(color: Nd.apagado, fontWeight: FontWeight.w600)),
        ]),
        const SizedBox(height: 6),
        Text(tx(l.d['descripcion']), style: const TextStyle(fontWeight: FontWeight.w700, color: Nd.titulo, fontSize: 15)),
        Text('Pedido ${ent(l.d['cantidad'])}  ·  Recibido ${ent(l.d['recibido'])}  ·  Pendiente ${l.pendiente}',
            style: const TextStyle(color: Nd.apagado, fontSize: 13)),
        const SizedBox(height: 10),
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            SizedBox(
              width: 110,
              child: CampoFormulario(
                etiqueta: 'Recibir',
                child: TextField(
                  controller: l.cantidad,
                  textAlign: TextAlign.center,
                  keyboardType: TextInputType.number,
                  inputFormatters: <TextInputFormatter>[FilteringTextInputFormatter.digitsOnly],
                  decoration: InputDecoration(hintText: '0 a ${l.pendiente}'),
                ),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: CampoFormulario(
                etiqueta: l.esEquipo ? 'Centro de costo' : 'Almacén',
                child: CampoSeleccion(
                  texto: (l.esEquipo ? l.centroCosto : l.almacen)?.nombre ?? '',
                  alTocar: () async {
                    final Opcion? o = await elegirOpcion(context, l.esEquipo ? 'Centro de costo' : 'Almacén', l.esEquipo ? _centrosCosto : _almacenes);
                    if (o == null || !mounted) return;
                    setState(() {
                      if (l.esEquipo) {
                        l.centroCosto = o;
                      } else {
                        l.almacen = o;
                      }
                    });
                  },
                ),
              ),
            ),
          ],
        ),
        if (l.esEquipo)
          CampoFormulario(
            etiqueta: 'Número de serie del fabricante (opcional)',
            child: TextField(controller: l.serie, maxLength: 100, decoration: const InputDecoration(counterText: '')),
          ),
      ]),
    );
  }
}
