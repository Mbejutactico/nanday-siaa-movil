import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api.dart';
import '../../tema.dart';
import '../comunes.dart';

/// INVENTARIOS > Inventario físico: se recorre el almacén cargando lo contado.
/// Al registrar, la web (inventarioControlador.jsp?tipo=registrarConteo) crea un ajuste
/// "corrección por conteo" solo por las diferencias, y queda en el Kardex.
/// Las filas vacías no se ajustan.
///
/// Rendimiento: la lista se dibuja a medida que se desplaza, y al escribir una cantidad
/// solo se actualiza esa fila y el contador de abajo (no toda la pantalla).
class InventarioFisicoPantalla extends StatefulWidget {
  const InventarioFisicoPantalla({super.key});

  @override
  State<InventarioFisicoPantalla> createState() => _InventarioFisicoPantallaState();
}

class _InventarioFisicoPantallaState extends State<InventarioFisicoPantalla> {
  Opcion? _almacen;
  List<Map<String, dynamic>>? _items;
  Map<int, TextEditingController> _contado = <int, TextEditingController>{};
  Listenable _cambios = Listenable.merge(const <Listenable>[]);
  final TextEditingController _observacion = TextEditingController();
  String _buscar = '';
  bool _cargando = false;
  bool _guardando = false;

  @override
  void dispose() {
    for (final TextEditingController c in _contado.values) {
      c.dispose();
    }
    _observacion.dispose();
    super.dispose();
  }

  Future<void> _elegirAlmacen() async {
    try {
      final List<Opcion> lista = mapas(await Api.movilCacheado('almacenes'))
          .map((Map<String, dynamic> a) => Opcion(ent(a['id']), tx(a['nombre']), detalle: tx(a['centro'])))
          .toList();
      if (!mounted) return;
      final Opcion? o = await elegirOpcion(context, 'Almacén a contar', lista);
      if (o == null || !mounted) return;
      if (_hayCargados() &&
          !await confirmar(context, 'Cambiar de almacén', 'Se perderán las cantidades cargadas. ¿Continuar?')) {
        return;
      }
      setState(() => _almacen = o);
      await _cargar();
    } catch (e) {
      if (mounted) manejarError(context, e);
    }
  }

  Future<void> _cargar() async {
    if (_almacen == null) return;
    setState(() => _cargando = true);
    try {
      final List<Map<String, dynamic>> items =
          mapas(await Api.ctrl('inventarioControlador.jsp', 'stockAlmacen', <String, String>{'id_almacen': '${_almacen!.id}'}));
      if (!mounted) return;
      final Map<int, TextEditingController> anteriores = _contado;
      final Map<int, TextEditingController> nuevos = <int, TextEditingController>{
        for (final Map<String, dynamic> i in items) ent(i['id_insumo']): TextEditingController(),
      };
      setState(() {
        _items = items;
        _contado = nuevos;
        _cambios = Listenable.merge(nuevos.values.toList());
        _cargando = false;
      });
      // Los controladores viejos todavía están en pantalla hasta que se dibuje la lista nueva:
      // se liberan después de ese cuadro (antes daba error "used after being disposed").
      WidgetsBinding.instance.addPostFrameCallback((_) {
        for (final TextEditingController c in anteriores.values) {
          c.dispose();
        }
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _cargando = false);
      manejarError(context, e);
    }
  }

  bool _hayCargados() => _contado.values.any((TextEditingController c) => c.text.trim().isNotEmpty);

  /// (contados, con diferencia)
  (int, int) _resumen() {
    int cargados = 0, diferencias = 0;
    for (final Map<String, dynamic> i in _items ?? <Map<String, dynamic>>[]) {
      final int? n = int.tryParse(_contado[ent(i['id_insumo'])]?.text.trim() ?? '');
      if (n != null) {
        cargados++;
        if (n != ent(i['stock'])) diferencias++;
      }
    }
    return (cargados, diferencias);
  }

  Future<void> _registrar() async {
    if (_guardando) return;
    final List<Map<String, int>> items = <Map<String, int>>[];
    int conDiferencia = 0;
    for (final Map<String, dynamic> i in _items ?? <Map<String, dynamic>>[]) {
      final int id = ent(i['id_insumo']);
      final String v = _contado[id]?.text.trim() ?? '';
      if (v.isEmpty) continue;
      final int? n = int.tryParse(v);
      if (n == null || n < 0) {
        avisar(context, 'Las cantidades contadas deben ser números enteros mayores o iguales a 0', error: true);
        return;
      }
      if (n != ent(i['stock'])) conDiferencia++;
      items.add(<String, int>{'id_insumo': id, 'contado': n});
    }
    if (items.isEmpty) {
      avisar(context, 'Cargue al menos una cantidad contada', error: true);
      return;
    }
    if (!await confirmar(context, 'Registrar conteo',
        '${items.length} insumo(s) contados, $conDiferencia con diferencia.\nSe ajustará el stock por las diferencias.',
        aceptar: 'Registrar')) {
      return;
    }
    if (!mounted) return;
    setState(() => _guardando = true);
    try {
      final Map<String, dynamic> r = mapa(await Api.ctrl('inventarioControlador.jsp', 'registrarConteo', <String, String>{
        'id_almacen': '${_almacen!.id}',
        'observacion': _observacion.text.trim(),
        'items': jsonEncode(items),
      }));
      if (!mounted) return;
      await mostrarResultado(
        context,
        r['sin_diferencias'] == true ? 'Conteo sin diferencias' : 'Conteo registrado',
        r['sin_diferencias'] == true
            ? 'El stock coincide con lo contado: no se generó ningún ajuste.'
            : 'Se generó el ajuste ${tx(r['numero'])} por ${ent(r['diferencias'])} diferencia(s) el ${tx(r['fecha'])}.',
      );
      _observacion.clear();
      await _cargar();
    } catch (e) {
      if (mounted) manejarError(context, e);
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final String t = _buscar.toLowerCase();
    final List<Map<String, dynamic>> lista = (_items ?? <Map<String, dynamic>>[]).where((Map<String, dynamic> i) {
      return t.isEmpty || tx(i['codigo']).toLowerCase().contains(t) || tx(i['nombre']).toLowerCase().contains(t);
    }).toList();
    final bool hayItems = _items != null && _items!.isNotEmpty;

    return PaginaApp(
      titulo: 'Inventario Físico',
      migas: const <String>['Inicio', 'Inventarios', 'Inventario físico'],
      barraInferior: !hayItems
          ? null
          : SafeArea(
              child: Container(
                padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
                decoration: const BoxDecoration(color: Colors.white, border: Border(top: BorderSide(color: Nd.borde))),
                child: Row(
                  children: <Widget>[
                    Expanded(
                      // solo este texto se actualiza al escribir una cantidad
                      child: ListenableBuilder(
                        listenable: _cambios,
                        builder: (BuildContext context, Widget? hijo) {
                          final (int cargados, int diferencias) = _resumen();
                          return Text('$cargados contados · $diferencias con diferencia',
                              style: const TextStyle(color: Nd.pizarra, fontWeight: FontWeight.w600));
                        },
                      ),
                    ),
                    FilledButton.icon(
                      onPressed: _guardando ? null : _registrar,
                      icon: const Icon(Icons.check),
                      label: Text(_guardando ? 'Registrando...' : 'Registrar'),
                    ),
                  ],
                ),
              ),
            ),
      hijos: <Widget>[
        Tarjeta(
          child: CuerpoTarjeta(<Widget>[
            CampoFormulario(
              etiqueta: 'Almacén a contar',
              obligatorio: true,
              child: CampoSeleccion(texto: _almacen?.nombre ?? '', alTocar: _guardando ? null : _elegirAlmacen, icono: Icons.warehouse),
            ),
            CampoFormulario(
              etiqueta: 'Observación',
              child: TextField(
                controller: _observacion,
                maxLength: 500,
                decoration: const InputDecoration(hintText: 'Ej.: conteo mensual', counterText: ''),
              ),
            ),
            const Text(
              'Cargue la cantidad CONTADA de cada insumo. Las filas vacías no se ajustan. '
              'Al registrar se crea un ajuste solo por las diferencias.',
              style: TextStyle(color: Nd.apagado, fontSize: 13),
            ),
          ]),
        ),
        espacio,
      ],
      slivers: <Widget>[
        if (_cargando)
          caja(const Padding(padding: EdgeInsets.all(30), child: Cargando()))
        else if (_almacen == null)
          caja(const Aviso(icono: Icons.fact_check, texto: 'Seleccione el almacén que va a contar.'))
        else if (!hayItems)
          caja(const Aviso(icono: Icons.inbox, texto: 'Este almacén no tiene insumos registrados.'))
        else
          SliverTarjetaLista(
            cabecera: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                Padding(
                  padding: const EdgeInsets.all(12),
                  child: TextField(
                    decoration: const InputDecoration(hintText: 'Buscar insumo', prefixIcon: Icon(Icons.search, size: 20)),
                    onChanged: (String v) => setState(() => _buscar = v.trim()),
                  ),
                ),
                const Divider(),
                if (lista.isEmpty)
                  const Padding(
                    padding: EdgeInsets.all(16),
                    child: Text('Ningún insumo coincide con la búsqueda.', style: TextStyle(color: Nd.apagado)),
                  ),
              ],
            ),
            cantidad: lista.length,
            fila: (BuildContext c, int indice) {
              final Map<String, dynamic> i = lista[indice];
              final int id = ent(i['id_insumo']);
              return _FilaConteo(key: ValueKey<int>(id), item: i, controlador: _contado[id]!);
            },
          ),
      ],
    );
  }
}

/// Fila de un insumo: datos del sistema y el campo "Contado". La diferencia se recalcula
/// escuchando solo su propio campo.
class _FilaConteo extends StatelessWidget {
  final Map<String, dynamic> item;
  final TextEditingController controlador;

  const _FilaConteo({super.key, required this.item, required this.controlador});

  @override
  Widget build(BuildContext context) {
    final int sistema = ent(item['stock']);
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 10, 12, 10),
      decoration: const BoxDecoration(border: Border(bottom: BorderSide(color: Nd.borde))),
      child: Row(
        children: <Widget>[
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(tx(item['nombre']), style: const TextStyle(fontWeight: FontWeight.w600, color: Nd.titulo)),
                Text('${tx(item['codigo'])}  ·  Sistema: $sistema ${tx(item['unidad'])}',
                    style: const TextStyle(color: Nd.apagado, fontSize: 12.5)),
                ValueListenableBuilder<TextEditingValue>(
                  valueListenable: controlador,
                  builder: (BuildContext context, TextEditingValue valor, Widget? hijo) {
                    final int? contado = int.tryParse(valor.text.trim());
                    if (contado == null) return const SizedBox.shrink();
                    final int dif = contado - sistema;
                    return Text(
                      dif == 0 ? 'Sin diferencia' : 'Diferencia: ${dif > 0 ? '+' : ''}$dif',
                      style: TextStyle(
                          fontSize: 12.5,
                          fontWeight: FontWeight.w700,
                          color: dif == 0 ? Nd.apagado : (dif > 0 ? Nd.primario : Nd.peligro)),
                    );
                  },
                ),
              ],
            ),
          ),
          const SizedBox(width: 10),
          SizedBox(
            width: 96,
            child: TextField(
              controller: controlador,
              textAlign: TextAlign.center,
              keyboardType: TextInputType.number,
              inputFormatters: <TextInputFormatter>[FilteringTextInputFormatter.digitsOnly],
              decoration: const InputDecoration(hintText: 'Contado'),
            ),
          ),
        ],
      ),
    );
  }
}
