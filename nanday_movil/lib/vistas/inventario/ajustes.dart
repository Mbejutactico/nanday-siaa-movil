import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api.dart';
import '../../tema.dart';
import '../comunes.dart';

/// INVENTARIOS > Ajustes de inventario (AJ-000001), igual que ajustesInventario.jsp de la web.
/// Usa inventarioControlador.jsp: listarAjustes, detalleAjuste, crearAjuste y anularAjuste.
///
/// Tipos: Consumo directo (sin OT), Rotura / pérdida, Vencimiento (salen del stock) y
/// Corrección por conteo (+/-, el centro de costo es opcional). Anular revierte el stock y queda en el Kardex.
const Map<String, String> tiposAjuste = <String, String>{
  'CONSUMO': 'Consumo directo (sin OT)',
  'ROTURA_PERDIDA': 'Rotura / pérdida',
  'VENCIMIENTO': 'Vencimiento',
  'CORRECCION_CONTEO': 'Corrección por conteo (+/-)',
};

const Map<String, String> _tiposCortos = <String, String>{
  'CONSUMO': 'Consumo directo',
  'ROTURA_PERDIDA': 'Rotura / pérdida',
  'VENCIMIENTO': 'Vencimiento',
  'CORRECCION_CONTEO': 'Corrección por conteo',
};

class AjustesPantalla extends StatefulWidget {
  const AjustesPantalla({super.key});

  @override
  State<AjustesPantalla> createState() => _AjustesPantallaState();
}

class _AjustesPantallaState extends State<AjustesPantalla> {
  final GlobalKey<CargableEstado<List<Map<String, dynamic>>>> _carga = GlobalKey<CargableEstado<List<Map<String, dynamic>>>>();

  /// Detalle de cada ajuste ya consultado (no se vuelve a pedir al expandir otra vez).
  final Map<int, List<Map<String, dynamic>>> _detalles = <int, List<Map<String, dynamic>>>{};
  String _buscar = '';
  bool _anulando = false;

  Future<void> _recargar() async {
    _detalles.clear();
    await _carga.currentState?.recargar();
  }

  Future<void> _nuevo() async {
    final bool? hecho = await Navigator.of(context)
        .push<bool>(MaterialPageRoute<bool>(builder: (BuildContext c) => const NuevoAjustePantalla()));
    if (hecho == true) _recargar();
  }

  Future<void> _anular(Map<String, dynamic> a) async {
    if (_anulando) return;
    final String? motivo = await pedirTexto(context,
        titulo: 'Anular ajuste ${tx(a['numero'])}',
        mensaje: 'El stock se revierte y queda registrado en el Kardex.',
        aceptar: 'Anular',
        color: Nd.peligro);
    if (motivo == null || !mounted) return;
    _anulando = true;
    try {
      await Api.ctrl('inventarioControlador.jsp', 'anularAjuste', <String, String>{'id': '${ent(a['id'])}', 'motivo': motivo});
      if (!mounted) return;
      avisar(context, 'Ajuste ${tx(a['numero'])} anulado');
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
      titulo: 'Ajustes de Inventario',
      migas: const <String>['Inicio', 'Inventarios', 'Ajustes'],
      alRefrescar: _recargar,
      hijos: <Widget>[
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: BotonPrincipal(texto: 'Nuevo ajuste', icono: Icons.add, alTocar: _nuevo),
        ),
        espacio,
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: TextField(
            decoration: const InputDecoration(
                hintText: 'Buscar por número, tipo, almacén o motivo', prefixIcon: Icon(Icons.search, size: 20), fillColor: Colors.white),
            onChanged: (String v) => setState(() => _buscar = v.trim().toLowerCase()),
          ),
        ),
        espacio,
      ],
      slivers: <Widget>[
        Cargable<List<Map<String, dynamic>>>.sliver(
          key: _carga,
          cargar: () async => mapas(await Api.ctrl('inventarioControlador.jsp', 'listarAjustes')),
          constructor: (BuildContext context, List<Map<String, dynamic>> todos, Future<void> Function() recargar) {
            final List<Map<String, dynamic>> lista = todos.where((Map<String, dynamic> a) {
              if (_buscar.isEmpty) return true;
              return <String>[
                tx(a['numero']),
                _tiposCortos[tx(a['tipo'])] ?? tx(a['tipo']),
                tx(a['almacen']),
                tx(a['centro']),
                tx(a['motivo']),
              ].any((String c) => c.toLowerCase().contains(_buscar));
            }).toList();
            if (lista.isEmpty) {
              return caja(Aviso(
                  icono: Icons.tune, texto: todos.isEmpty ? 'Todavía no hay ajustes registrados.' : 'Ningún ajuste coincide con la búsqueda.'));
            }
            return SliverTarjetaLista(
              cabecera: CabeceraTarjeta(titulo: '${lista.length} ajuste(s)', icono: Icons.tune),
              cantidad: lista.length,
              fila: (BuildContext c, int i) {
                final Map<String, dynamic> a = lista[i];
                final int id = ent(a['id']);
                return _FilaAjuste(
                  a,
                  key: ValueKey<int>(id),
                  alAnular: () => _anular(a),
                  cargarDetalle: () async => _detalles[id] ??=
                      mapas(await Api.ctrl('inventarioControlador.jsp', 'detalleAjuste', <String, String>{'id': '$id'})),
                );
              },
            );
          },
        ),
      ],
    );
  }
}

class _FilaAjuste extends StatelessWidget {
  final Map<String, dynamic> a;
  final VoidCallback alAnular;
  final Future<List<Map<String, dynamic>>> Function() cargarDetalle;

  const _FilaAjuste(this.a, {super.key, required this.alAnular, required this.cargarDetalle});

  @override
  Widget build(BuildContext context) {
    final bool anulado = a['anulado'] == true;
    final bool delConteo = tx(a['origen']) == 'INVENTARIO_FISICO';
    return ExpansionTile(
      shape: const Border(),
      title: Row(
        children: <Widget>[
          Text(tx(a['numero']), style: const TextStyle(fontWeight: FontWeight.w700, color: Nd.titulo)),
          const SizedBox(width: 8),
          ChipEstado(anulado ? 'ANULADA' : 'VIGENTE', texto: anulado ? 'Anulado' : 'Vigente'),
        ],
      ),
      subtitle: Text('${_tiposCortos[tx(a['tipo'])] ?? legible(tx(a['tipo']))}  ·  ${tx(a['almacen'])}\n'
          '${tx(a['fecha'])}  ·  ${ent(a['items'])} insumo(s)  ·  Gs. ${guaranies(a['valor'])}'),
      childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
      children: <Widget>[
        DatoFila('Centro', tx(a['centro'])),
        DatoFila('Centro de costo', tx(a['centro_costo'])),
        DatoFila('Origen', delConteo ? 'Inventario físico' : 'Manual'),
        DatoFila('Motivo', tx(a['motivo'])),
        DatoFila('Registró', tx(a['usuario'])),
        if (anulado) DatoFila('Anulado', '${tx(a['fecha_anulacion'])}: ${tx(a['motivo_anulacion'])}'),
        Cargable<List<Map<String, dynamic>>>(
          cargar: cargarDetalle,
          constructor: (BuildContext c, List<Map<String, dynamic>> det, Future<void> Function() r) => Column(
            children: <Widget>[
              for (final Map<String, dynamic> d in det)
                ListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  title: Text('${tx(d['codigo'])} - ${tx(d['nombre'])}'),
                  subtitle: Text('Valor: Gs. ${guaranies(ent(d['cantidad']) * (num.tryParse(tx(d['costo'])) ?? 0))}'),
                  trailing: Text(
                    '${ent(d['cantidad']) > 0 ? '+' : ''}${ent(d['cantidad'])} ${tx(d['unidad'])}',
                    style: TextStyle(fontWeight: FontWeight.w700, color: ent(d['cantidad']) > 0 ? Nd.primario : Nd.peligro),
                  ),
                ),
            ],
          ),
        ),
        // Los ajustes que genera el inventario físico también se pueden anular (igual que la web)
        if (!anulado)
          Align(
            alignment: Alignment.centerRight,
            child: TextButton.icon(
              style: TextButton.styleFrom(foregroundColor: Nd.peligro),
              onPressed: alAnular,
              icon: const Icon(Icons.block),
              label: Text(delConteo ? 'Anular ajuste del conteo' : 'Anular ajuste'),
            ),
          ),
      ],
    );
  }
}

// =====================================================================
//  Nuevo ajuste
// =====================================================================

class _LineaAjuste {
  final int idInsumo;
  final String nombre;
  final String unidad;
  final int cantidad; // con signo solo en corrección por conteo

  const _LineaAjuste(this.idInsumo, this.nombre, this.unidad, this.cantidad);
}

class NuevoAjustePantalla extends StatefulWidget {
  const NuevoAjustePantalla({super.key});

  @override
  State<NuevoAjustePantalla> createState() => _NuevoAjustePantallaState();
}

class _NuevoAjustePantallaState extends State<NuevoAjustePantalla> {
  final TextEditingController _motivo = TextEditingController();
  String _tipo = 'CONSUMO';
  Opcion? _centro;
  Opcion? _centroCosto;
  Opcion? _almacen;
  Map<int, int> _stock = <int, int>{}; // id_insumo -> stock en el almacén elegido
  final List<_LineaAjuste> _lineas = <_LineaAjuste>[];
  bool _guardando = false;

  bool get _conteo => _tipo == 'CORRECCION_CONTEO';

  @override
  void dispose() {
    _motivo.dispose();
    super.dispose();
  }

  Future<void> _elegirCentro() async {
    try {
      final List<Opcion> lista = Opcion.lista(await Api.movilCacheado('centros'));
      if (!mounted) return;
      final Opcion? o = await elegirOpcion(context, 'Centro', lista);
      if (o == null || !mounted || o.id == _centro?.id) return;
      setState(() {
        _centro = o;
        _centroCosto = null;
        _almacen = null;
        _stock = <int, int>{};
        _lineas.clear();
      });
    } catch (e) {
      if (mounted) manejarError(context, e);
    }
  }

  Future<void> _elegirCentroCosto() async {
    if (_centro == null) return;
    try {
      final List<Opcion> lista = <Opcion>[
        if (_conteo) const Opcion(0, '(Sin centro de costo)'),
        ...Opcion.lista(await Api.movilCacheado('centrosCosto', <String, String>{'id_centro': '${_centro!.id}'})),
      ];
      if (!mounted) return;
      final Opcion? o = await elegirOpcion(context, 'Centro de costo', lista);
      if (o != null && mounted) setState(() => _centroCosto = o.id == 0 ? null : o);
    } catch (e) {
      if (mounted) manejarError(context, e);
    }
  }

  Future<void> _elegirAlmacen() async {
    if (_centro == null) return;
    try {
      final List<Opcion> lista = mapas(await Api.movilCacheado('almacenes', <String, String>{'id_centro': '${_centro!.id}'}))
          .map((Map<String, dynamic> a) => Opcion(ent(a['id']), tx(a['nombre'])))
          .toList();
      if (!mounted) return;
      final Opcion? o = await elegirOpcion(context, 'Almacén', lista);
      if (o == null || !mounted || o.id == _almacen?.id) return;
      final List<Map<String, dynamic>> stock =
          mapas(await Api.ctrl('inventarioControlador.jsp', 'stockAlmacen', <String, String>{'id_almacen': '${o.id}'}));
      if (!mounted) return;
      setState(() {
        _almacen = o;
        _stock = <int, int>{for (final Map<String, dynamic> s in stock) ent(s['id_insumo']): ent(s['stock'])};
        _lineas.clear();
      });
    } catch (e) {
      if (mounted) manejarError(context, e);
    }
  }

  void _cambiarTipo(String? t) {
    if (t == null || t == _tipo) return;
    setState(() {
      _tipo = t;
      _lineas.clear(); // igual que la web: al cambiar el tipo se vacían las líneas
    });
  }

  /// Lo que ya está cargado de ese insumo (en unidades que SALEN del stock).
  int _salidaCargada(int idInsumo) => _lineas
      .where((_LineaAjuste l) => l.idInsumo == idInsumo)
      .fold(0, (int s, _LineaAjuste l) => s + (_conteo ? -l.cantidad : l.cantidad));

  Future<void> _agregar() async {
    if (_almacen == null) {
      avisar(context, 'Primero elija el almacén', error: true);
      return;
    }
    try {
      // Con corrección por conteo se puede sumar cualquier insumo; en los demás, solo los que tienen stock
      final List<Opcion> opciones = mapas(await Api.movil('insumos'))
          .where((Map<String, dynamic> i) => _conteo || (_stock[ent(i['id'])] ?? 0) > 0)
          .map((Map<String, dynamic> i) => Opcion(ent(i['id']), '${tx(i['codigo'])} - ${tx(i['nombre'])}',
              detalle: 'Disponible en el almacén: ${_stock[ent(i['id'])] ?? 0} ${tx(i['unidad'])}', datos: i))
          .toList();
      if (!mounted) return;
      if (opciones.isEmpty) {
        avisar(context, 'El almacén no tiene insumos con stock', error: true);
        return;
      }
      final Opcion? o = await elegirOpcion(context, 'Insumo', opciones);
      if (o == null || !mounted) return;
      final int disponible = (_stock[o.id] ?? 0) - _salidaCargada(o.id);
      final int? cantidad = await showDialog<int>(
        context: context,
        builder: (BuildContext d) => _DialogoCantidadAjuste(titulo: o.nombre, disponible: disponible, conSigno: _conteo),
      );
      if (cantidad == null || !mounted) return;
      setState(() => _lineas.add(_LineaAjuste(o.id, o.nombre, tx(o.datos['unidad']), cantidad)));
    } catch (e) {
      if (mounted) manejarError(context, e);
    }
  }

  Future<void> _guardar() async {
    if (_guardando) return;
    if (_centro == null || _almacen == null || _motivo.text.trim().isEmpty || (_centroCosto == null && !_conteo)) {
      avisar(context, 'Complete centro, centro de costo, almacén y motivo.', error: true);
      return;
    }
    if (_lineas.isEmpty) {
      avisar(context, 'Agregue al menos un insumo.', error: true);
      return;
    }
    if (!await confirmar(context, 'Registrar ajuste',
        '${_tiposCortos[_tipo]} de ${_lineas.length} línea(s) en ${_almacen!.nombre}.\nEl stock se mueve al confirmar.',
        aceptar: 'Registrar')) {
      return;
    }
    if (!mounted) return;
    setState(() => _guardando = true);
    try {
      final Map<String, dynamic> r = mapa(await Api.ctrl('inventarioControlador.jsp', 'crearAjuste', <String, String>{
        'tipo_ajuste': _tipo,
        'id_centro': '${_centro!.id}',
        'id_centro_costo': _centroCosto == null ? '' : '${_centroCosto!.id}',
        'id_almacen': '${_almacen!.id}',
        'motivo': _motivo.text.trim(),
        'items': jsonEncode(<Map<String, int>>[
          for (final _LineaAjuste l in _lineas) <String, int>{'id_insumo': l.idInsumo, 'cantidad': l.cantidad},
        ]),
      }));
      if (!mounted) return;
      await mostrarResultado(context, 'Ajuste registrado', 'Ajuste ${tx(r['numero'])} registrado el ${tx(r['fecha'])}.');
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
      titulo: 'Nuevo Ajuste',
      migas: const <String>['Inicio', 'Inventarios', 'Ajustes', 'Nuevo'],
      hijos: <Widget>[
        Tarjeta(
          acento: Nd.acento,
          child: CuerpoTarjeta(<Widget>[
            CampoFormulario(
              etiqueta: 'Tipo de ajuste',
              obligatorio: true,
              child: DropdownButtonFormField<String>(
                initialValue: _tipo,
                isExpanded: true,
                items: <DropdownMenuItem<String>>[
                  for (final MapEntry<String, String> t in tiposAjuste.entries)
                    DropdownMenuItem<String>(value: t.key, child: Text(t.value)),
                ],
                onChanged: _guardando ? null : _cambiarTipo,
              ),
            ),
            CampoFormulario(
              etiqueta: 'Centro',
              obligatorio: true,
              child: CampoSeleccion(texto: _centro?.nombre ?? '', icono: Icons.apartment, alTocar: _guardando ? null : _elegirCentro),
            ),
            CampoFormulario(
              etiqueta: _conteo ? 'Centro de costo (opcional)' : 'Centro de costo',
              obligatorio: !_conteo,
              child: CampoSeleccion(
                texto: _centroCosto?.nombre ?? '',
                sugerencia: _centro == null ? '-- Elija el centro --' : '-- Seleccionar --',
                alTocar: _centro == null || _guardando ? null : _elegirCentroCosto,
              ),
            ),
            CampoFormulario(
              etiqueta: 'Almacén',
              obligatorio: true,
              child: CampoSeleccion(
                texto: _almacen?.nombre ?? '',
                sugerencia: _centro == null ? '-- Elija el centro --' : '-- Seleccionar --',
                icono: Icons.warehouse,
                alTocar: _centro == null || _guardando ? null : _elegirAlmacen,
              ),
            ),
            CampoFormulario(
              etiqueta: 'Motivo',
              obligatorio: true,
              child: TextField(controller: _motivo, maxLength: 500, decoration: const InputDecoration(counterText: '')),
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
                  onPressed: _almacen == null || _guardando ? null : _agregar,
                  icon: const Icon(Icons.add),
                  label: const Text('Agregar'),
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 10, 16, 4),
                child: Text(
                  _conteo ? 'Corrección por conteo: cantidad positiva suma al stock, negativa resta.' : 'Las cantidades salen del stock.',
                  style: const TextStyle(color: Nd.apagado, fontSize: 13),
                ),
              ),
              if (_lineas.isEmpty)
                Padding(
                  padding: const EdgeInsets.all(16),
                  child: Text(_almacen == null ? 'Primero elija el centro y el almacén.' : 'Agregue los insumos del ajuste.',
                      style: const TextStyle(color: Nd.apagado)),
                ),
              for (final _LineaAjuste l in _lineas)
                ListTile(
                  title: Text(l.nombre, style: const TextStyle(fontWeight: FontWeight.w600)),
                  subtitle: Text('Stock en el almacén: ${_stock[l.idInsumo] ?? 0} ${l.unidad}'),
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: <Widget>[
                      Text(
                        '${_conteo && l.cantidad > 0 ? '+' : (_conteo ? '' : '-')}${l.cantidad} ${l.unidad}',
                        style: TextStyle(
                            fontWeight: FontWeight.w700, fontSize: 15, color: _conteo && l.cantidad > 0 ? Nd.primario : Nd.peligro),
                      ),
                      IconButton(
                        tooltip: 'Quitar',
                        icon: const Icon(Icons.close, color: Nd.peligro),
                        onPressed: _guardando ? null : () => setState(() => _lineas.remove(l)),
                      ),
                    ],
                  ),
                ),
            ],
          ),
        ),
        espacio,
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: BotonPrincipal(texto: 'Registrar ajuste', icono: Icons.check, ocupado: _guardando, alTocar: _guardar),
        ),
      ],
    );
  }
}

/// Cantidad de una línea. Sin signo: 1..disponible (sale del stock).
/// Con signo (corrección por conteo): sumar o restar; al restar no puede superar lo disponible.
class _DialogoCantidadAjuste extends StatefulWidget {
  final String titulo;
  final int disponible;
  final bool conSigno;

  const _DialogoCantidadAjuste({required this.titulo, required this.disponible, required this.conSigno});

  @override
  State<_DialogoCantidadAjuste> createState() => _DialogoCantidadAjusteState();
}

class _DialogoCantidadAjusteState extends State<_DialogoCantidadAjuste> {
  final TextEditingController _txt = TextEditingController(text: '1');
  final GlobalKey<FormState> _formulario = GlobalKey<FormState>();
  bool _sumar = true;

  @override
  void dispose() {
    _txt.dispose();
    super.dispose();
  }

  bool get _sale => !widget.conSigno || !_sumar;

  void _aceptar() {
    if (!_formulario.currentState!.validate()) return;
    final int n = int.parse(_txt.text.trim());
    Navigator.of(context).pop(widget.conSigno && !_sumar ? -n : n);
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.titulo, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
      content: Form(
        key: _formulario,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            if (widget.conSigno) ...<Widget>[
              SegmentedButton<bool>(
                segments: const <ButtonSegment<bool>>[
                  ButtonSegment<bool>(value: true, label: Text('Sumar'), icon: Icon(Icons.add)),
                  ButtonSegment<bool>(value: false, label: Text('Restar'), icon: Icon(Icons.remove)),
                ],
                selected: <bool>{_sumar},
                onSelectionChanged: (Set<bool> s) => setState(() => _sumar = s.first),
              ),
              const SizedBox(height: 14),
            ],
            TextFormField(
              controller: _txt,
              autofocus: true,
              keyboardType: TextInputType.number,
              inputFormatters: <TextInputFormatter>[FilteringTextInputFormatter.digitsOnly],
              decoration: InputDecoration(
                hintText: 'Cantidad',
                helperText: _sale ? 'Disponible: ${widget.disponible}' : 'Se suma al stock',
              ),
              onFieldSubmitted: (_) => _aceptar(),
              validator: (String? s) {
                final int n = int.tryParse((s ?? '').trim()) ?? 0;
                if (n <= 0) return widget.conSigno ? 'La cantidad debe ser distinta de 0' : 'La cantidad debe ser mayor a 0';
                if (_sale && n > widget.disponible) return 'La cantidad supera el stock disponible (${widget.disponible})';
                return null;
              },
            ),
          ],
        ),
      ),
      actions: <Widget>[
        OutlinedButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Cancelar')),
        FilledButton(onPressed: _aceptar, child: const Text('Agregar')),
      ],
    );
  }
}
