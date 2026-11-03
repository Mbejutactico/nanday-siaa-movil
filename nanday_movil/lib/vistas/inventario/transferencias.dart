import 'dart:convert';

import 'package:flutter/material.dart';

import '../../api.dart';
import '../../tema.dart';
import '../comunes.dart';

/// INVENTARIOS > Transferencias entre almacenes (TR-000001) con su remisión.
/// Usa inventarioControlador.jsp: listarTransferencias, detalleTransferencia,
/// crearTransferencia y anularTransferencia (las mismas operaciones de la web).
class TransferenciasPantalla extends StatefulWidget {
  const TransferenciasPantalla({super.key});

  @override
  State<TransferenciasPantalla> createState() => _TransferenciasPantallaState();
}

class _TransferenciasPantallaState extends State<TransferenciasPantalla> {
  final GlobalKey<CargableEstado<List<Map<String, dynamic>>>> _carga = GlobalKey<CargableEstado<List<Map<String, dynamic>>>>();

  /// Detalle de cada transferencia ya consultada: no se vuelve a pedir cada vez que se expande.
  final Map<int, List<Map<String, dynamic>>> _detalles = <int, List<Map<String, dynamic>>>{};
  bool _anulando = false;

  Future<void> _recargar() async {
    _detalles.clear();
    await _carga.currentState?.recargar();
  }

  Future<void> _nueva() async {
    final bool? hecho = await Navigator.of(context)
        .push<bool>(MaterialPageRoute<bool>(builder: (BuildContext c) => const NuevaTransferenciaPantalla()));
    if (hecho == true) _recargar();
  }

  Future<void> _anular(Map<String, dynamic> t) async {
    final String? motivo = await pedirTexto(context,
        titulo: 'Anular ${tx(t['numero'])}',
        mensaje: 'El stock vuelve al almacén de origen y la remisión queda anulada.',
        aceptar: 'Anular',
        color: Nd.peligro);
    if (motivo == null || !mounted || _anulando) return;
    _anulando = true;
    try {
      await Api.ctrl('inventarioControlador.jsp', 'anularTransferencia', <String, String>{'id': '${ent(t['id'])}', 'motivo': motivo});
      if (!mounted) return;
      avisar(context, 'Transferencia ${tx(t['numero'])} anulada');
      await _recargar();
    } catch (e) {
      if (mounted) manejarError(context, e);
    } finally {
      _anulando = false;
    }
  }

  @override
  Widget build(BuildContext context) {
    return PaginaApp(
      titulo: 'Transferencias',
      migas: const <String>['Inicio', 'Inventarios', 'Transferencias'],
      alRefrescar: _recargar,
      hijos: <Widget>[
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: BotonPrincipal(texto: 'Nueva transferencia', icono: Icons.add, alTocar: _nueva),
        ),
        espacio,
      ],
      slivers: <Widget>[
        Cargable<List<Map<String, dynamic>>>.sliver(
          key: _carga,
          cargar: () async => mapas(await Api.ctrl('inventarioControlador.jsp', 'listarTransferencias')),
          constructor: (BuildContext context, List<Map<String, dynamic>> lista, Future<void> Function() recargar) {
            if (lista.isEmpty) {
              return caja(const Aviso(icono: Icons.swap_horiz, texto: 'Todavía no hay transferencias.'));
            }
            return SliverTarjetaLista(
              cabecera: CabeceraTarjeta(titulo: '${lista.length} transferencia(s)', icono: Icons.swap_horiz),
              cantidad: lista.length,
              fila: (BuildContext c, int i) {
                final Map<String, dynamic> t = lista[i];
                final int id = ent(t['id']);
                return _FilaTransferencia(
                  t,
                  key: ValueKey<int>(id),
                  alAnular: () => _anular(t),
                  cargarDetalle: () async => _detalles[id] ??=
                      mapas(await Api.ctrl('inventarioControlador.jsp', 'detalleTransferencia', <String, String>{'id': '$id'})),
                );
              },
            );
          },
        ),
      ],
    );
  }
}

class _FilaTransferencia extends StatelessWidget {
  final Map<String, dynamic> t;
  final VoidCallback alAnular;
  final Future<List<Map<String, dynamic>>> Function() cargarDetalle;

  const _FilaTransferencia(this.t, {super.key, required this.alAnular, required this.cargarDetalle});

  @override
  Widget build(BuildContext context) {
    final bool anulada = t['anulada'] == true;
    return ExpansionTile(
      shape: const Border(),
      title: Row(
        children: <Widget>[
          Text(tx(t['numero']), style: const TextStyle(fontWeight: FontWeight.w700, color: Nd.titulo)),
          const SizedBox(width: 8),
          ChipEstado(anulada ? 'ANULADA' : 'VIGENTE'),
        ],
      ),
      subtitle: Text('${tx(t['origen'])}  →  ${tx(t['destino'])}\n${tx(t['fecha'])}  ·  ${ent(t['items'])} insumo(s)'),
      childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
      children: <Widget>[
        DatoFila('Remisión', tx(t['remision'])),
        DatoFila('Registró', tx(t['usuario'])),
        DatoFila('Observación', tx(t['observacion'])),
        if (anulada) DatoFila('Anulada', '${tx(t['fecha_anulacion'])}: ${tx(t['motivo_anulacion'])}'),
        Cargable<List<Map<String, dynamic>>>(
          cargar: cargarDetalle,
          constructor: (BuildContext c, List<Map<String, dynamic>> det, Future<void> Function() r) => Column(
            children: <Widget>[
              for (final Map<String, dynamic> d in det)
                ListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  title: Text('${tx(d['codigo'])} - ${tx(d['nombre'])}'),
                  trailing: Text('${ent(d['cantidad'])} ${tx(d['unidad'])}', style: const TextStyle(fontWeight: FontWeight.w700)),
                ),
            ],
          ),
        ),
        if (!anulada)
          Align(
            alignment: Alignment.centerRight,
            child: TextButton.icon(
              style: TextButton.styleFrom(foregroundColor: Nd.peligro),
              onPressed: alAnular,
              icon: const Icon(Icons.block),
              label: const Text('Anular transferencia'),
            ),
          ),
      ],
    );
  }
}

/// Nueva transferencia: almacén de origen (de sus centros), destino (cualquiera),
/// insumos con stock en el origen, transportista (opcional) y observación.
class NuevaTransferenciaPantalla extends StatefulWidget {
  const NuevaTransferenciaPantalla({super.key});

  @override
  State<NuevaTransferenciaPantalla> createState() => _NuevaTransferenciaPantallaState();
}

class _Linea {
  final int idInsumo;
  final String nombre;
  final String unidad;
  final int disponible;
  int cantidad;

  _Linea(this.idInsumo, this.nombre, this.unidad, this.disponible, this.cantidad);
}

class _NuevaTransferenciaPantallaState extends State<NuevaTransferenciaPantalla> {
  final GlobalKey<FormState> _formulario = GlobalKey<FormState>();
  final DatosTransportista _tr = DatosTransportista();
  final TextEditingController _observacion = TextEditingController();
  Opcion? _origen;
  Opcion? _destino;
  List<Map<String, dynamic>> _stockOrigen = <Map<String, dynamic>>[];
  final List<_Linea> _lineas = <_Linea>[];
  bool _guardando = false;

  @override
  void dispose() {
    _tr.dispose();
    _observacion.dispose();
    super.dispose();
  }

  Future<List<Opcion>> _almacenes({required bool destino}) async {
    return mapas(await Api.movilCacheado('almacenes', <String, String>{if (destino) 'destino': 'true'}))
        .map((Map<String, dynamic> a) => Opcion(ent(a['id']), tx(a['nombre']), detalle: tx(a['centro'])))
        .toList();
  }

  Future<void> _elegirOrigen() async {
    try {
      final List<Opcion> lista = await _almacenes(destino: false);
      if (!mounted) return;
      final Opcion? o = await elegirOpcion(context, 'Almacén de origen', lista);
      if (o == null) return;
      final List<Map<String, dynamic>> stock =
          mapas(await Api.ctrl('inventarioControlador.jsp', 'stockAlmacen', <String, String>{'id_almacen': '${o.id}'}));
      if (!mounted) return;
      setState(() {
        _origen = o;
        _stockOrigen = stock;
        _lineas.clear();
        if (_destino?.id == o.id) _destino = null;
      });
    } catch (e) {
      if (mounted) manejarError(context, e);
    }
  }

  Future<void> _elegirDestino() async {
    try {
      final List<Opcion> lista = (await _almacenes(destino: true)).where((Opcion a) => a.id != _origen?.id).toList();
      if (!mounted) return;
      final Opcion? o = await elegirOpcion(context, 'Almacén de destino', lista);
      if (o != null) setState(() => _destino = o);
    } catch (e) {
      if (mounted) manejarError(context, e);
    }
  }

  Future<void> _agregarInsumo() async {
    final List<Opcion> opciones = _stockOrigen
        .where((Map<String, dynamic> s) => ent(s['stock']) > 0 && !_lineas.any((_Linea l) => l.idInsumo == ent(s['id_insumo'])))
        .map((Map<String, dynamic> s) => Opcion(ent(s['id_insumo']), '${tx(s['codigo'])} - ${tx(s['nombre'])}',
            detalle: 'Disponible: ${ent(s['stock'])} ${tx(s['unidad'])}', datos: s))
        .toList();
    final Opcion? o = await elegirOpcion(context, 'Insumo a transferir', opciones);
    if (o == null || !mounted) return;
    final int disponible = ent(o.datos['stock']);
    final int? cant = await pedirCantidad(context, o.nombre, disponible);
    if (cant == null || !mounted) return;
    setState(() => _lineas.add(_Linea(o.id, o.nombre, tx(o.datos['unidad']), disponible, cant)));
  }

  Future<void> _guardar() async {
    if (_origen == null || _destino == null) {
      avisar(context, 'Seleccione el almacén de origen y el de destino', error: true);
      return;
    }
    if (_lineas.isEmpty) {
      avisar(context, 'Agregue al menos un insumo', error: true);
      return;
    }
    if (!_formulario.currentState!.validate()) return;
    if (!await confirmar(context, 'Registrar transferencia',
        '${_lineas.length} insumo(s) de ${_origen!.nombre} a ${_destino!.nombre}.\nEl stock se mueve al confirmar.',
        aceptar: 'Transferir')) {
      return;
    }
    if (!mounted) return;
    setState(() => _guardando = true);
    try {
      final Map<String, dynamic> r = mapa(await Api.ctrl('inventarioControlador.jsp', 'crearTransferencia', <String, String>{
        'id_almacen_origen': '${_origen!.id}',
        'id_almacen_destino': '${_destino!.id}',
        'items': jsonEncode(<Map<String, int>>[
          for (final _Linea l in _lineas) <String, int>{'id_insumo': l.idInsumo, 'cantidad': l.cantidad},
        ]),
        'observacion': _observacion.text.trim(),
        ..._tr.aMapa(),
      }));
      if (!mounted) return;
      await mostrarResultado(context, 'Transferencia registrada',
          'Transferencia ${tx(r['numero'])} con la remisión ${tx(r['remision'])} (${tx(r['fecha'])}).');
      if (mounted) Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) manejarError(context, e);
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return PaginaApp(
      titulo: 'Nueva Transferencia',
      migas: const <String>['Inicio', 'Inventarios', 'Transferencias', 'Nueva'],
      hijos: <Widget>[
        Tarjeta(
          acento: Nd.acento,
          child: CuerpoTarjeta(<Widget>[
            CampoFormulario(
              etiqueta: 'Almacén de origen',
              obligatorio: true,
              child: CampoSeleccion(texto: _origen == null ? '' : '${_origen!.nombre} (${_origen!.detalle})', alTocar: _elegirOrigen),
            ),
            CampoFormulario(
              etiqueta: 'Almacén de destino',
              obligatorio: true,
              child: CampoSeleccion(
                  texto: _destino == null ? '' : '${_destino!.nombre} (${_destino!.detalle})',
                  alTocar: _origen == null ? null : _elegirDestino),
            ),
          ]),
        ),
        espacio,
        Tarjeta(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              CabeceraTarjeta(
                titulo: 'Insumos (${_lineas.length})',
                icono: Icons.inventory_2,
                derecha: TextButton.icon(
                  onPressed: _origen == null ? null : _agregarInsumo,
                  icon: const Icon(Icons.add),
                  label: const Text('Agregar'),
                ),
              ),
              if (_lineas.isEmpty)
                Padding(
                  padding: const EdgeInsets.all(16),
                  child: Text(_origen == null ? 'Primero elija el almacén de origen.' : 'Agregue los insumos a transferir.',
                      style: const TextStyle(color: Nd.apagado)),
                ),
              for (final _Linea l in _lineas)
                ListTile(
                  title: Text(l.nombre, style: const TextStyle(fontWeight: FontWeight.w600)),
                  subtitle: Text('Disponible en origen: ${l.disponible} ${l.unidad}'),
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: <Widget>[
                      Text('${l.cantidad} ${l.unidad}', style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15)),
                      IconButton(
                        tooltip: 'Quitar',
                        icon: const Icon(Icons.close, color: Nd.peligro),
                        onPressed: () => setState(() => _lineas.remove(l)),
                      ),
                    ],
                  ),
                ),
            ],
          ),
        ),
        espacio,
        Tarjeta(
          child: Form(
            key: _formulario,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                const CabeceraTarjeta(titulo: 'Transporte (opcional)', icono: Icons.local_shipping),
                CuerpoTarjeta(<Widget>[
                  _tr.campos(obligatorio: false),
                  CampoFormulario(
                    etiqueta: 'Observación',
                    child: TextField(controller: _observacion, maxLength: 500, decoration: const InputDecoration(counterText: '')),
                  ),
                  BotonPrincipal(texto: 'Registrar transferencia', icono: Icons.swap_horiz, ocupado: _guardando, alTocar: _guardar),
                ]),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
