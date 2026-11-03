import 'dart:convert';

import 'package:flutter/material.dart';

import '../../api.dart';
import '../../tema.dart';
import '../comunes.dart';

/// SUMINISTRAR OT: se eligen insumos, el almacén de salida (con stock) y la cantidad.
/// Al confirmar, la web (otControlador.jsp?tipo=suministrar) descuenta el stock y lo registra
/// en el Kardex en una sola transacción; si no alcanza el stock, no se guarda nada.
class SuministrarOTPantalla extends StatefulWidget {
  final int idOT;
  final String numero;

  const SuministrarOTPantalla({super.key, required this.idOT, required this.numero});

  @override
  State<SuministrarOTPantalla> createState() => _SuministrarOTPantallaState();
}

class _Linea {
  final int idInsumo;
  final String insumo;
  final String unidad;
  final int idAlmacen;
  final String almacen;
  final int disponible;
  final int cantidad;

  _Linea(this.idInsumo, this.insumo, this.unidad, this.idAlmacen, this.almacen, this.disponible, this.cantidad);
}

class _SuministrarOTPantallaState extends State<SuministrarOTPantalla> {
  final List<_Linea> _lineas = <_Linea>[];
  bool _guardando = false;

  Future<void> _agregar() async {
    try {
      final List<Opcion> insumos = mapas(await Api.movil('insumos'))
          .where((Map<String, dynamic> i) => ent(i['stock']) > 0)
          .map((Map<String, dynamic> i) => Opcion(ent(i['id']), '${tx(i['codigo'])} - ${tx(i['nombre'])}',
              detalle: 'Disponible en sus almacenes: ${ent(i['stock'])} ${tx(i['unidad'])}', datos: i))
          .toList();
      if (!mounted) return;
      final Opcion? ins = await elegirOpcion(context, 'Insumo', insumos);
      if (ins == null || !mounted) return;
      final List<Opcion> almacenes =
          mapas(await Api.ctrl('otControlador.jsp', 'almacenesConStock', <String, String>{'id_insumo': '${ins.id}'}))
              .map((Map<String, dynamic> a) {
        // descuenta lo que ya está cargado en esta pantalla para el mismo insumo y almacén
        final int yaUsado = _lineas
            .where((_Linea l) => l.idInsumo == ins.id && l.idAlmacen == ent(a['id']))
            .fold(0, (int s, _Linea l) => s + l.cantidad);
        final int libre = ent(a['stock']) - yaUsado;
        return Opcion(ent(a['id']), tx(a['almacen']),
            detalle: '${tx(a['centro'])}  ·  disponible: $libre ${tx(ins.datos['unidad'])}', datos: <String, dynamic>{'libre': libre});
      }).where((Opcion o) => ent(o.datos['libre']) > 0).toList();
      if (!mounted) return;
      if (almacenes.isEmpty) {
        avisar(context, 'No hay stock disponible de ese insumo en sus almacenes', error: true);
        return;
      }
      final Opcion? alm = almacenes.length == 1 ? almacenes.first : await elegirOpcion(context, 'Almacén de salida', almacenes);
      if (alm == null || !mounted) return;
      final int maximo = ent(alm.datos['libre']);
      final int? cantidad = await pedirCantidad(context, '${ins.nombre}\n${alm.nombre}', maximo);
      if (cantidad == null || !mounted) return;
      setState(() => _lineas.add(_Linea(ins.id, ins.nombre, tx(ins.datos['unidad']), alm.id, alm.nombre, maximo, cantidad)));
    } catch (e) {
      if (mounted) manejarError(context, e);
    }
  }

  Future<void> _confirmar() async {
    if (_guardando) return;
    if (_lineas.isEmpty) {
      avisar(context, 'Agregue al menos un insumo', error: true);
      return;
    }
    if (!await confirmar(context, 'Suministrar a ${widget.numero}',
        'Se descontarán ${_lineas.length} línea(s) del stock. ¿Confirmar?', aceptar: 'Suministrar')) {
      return;
    }
    if (!mounted) return;
    setState(() => _guardando = true);
    try {
      await Api.ctrl('otControlador.jsp', 'suministrar', <String, String>{
        'id_ot': '${widget.idOT}',
        'items': jsonEncode(<Map<String, int>>[
          for (final _Linea l in _lineas) <String, int>{'id_insumo': l.idInsumo, 'id_almacen': l.idAlmacen, 'cantidad': l.cantidad},
        ]),
      });
      if (!mounted) return;
      avisar(context, 'Insumos entregados a ${widget.numero}');
      Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) manejarError(context, e);
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return PaginaApp(
      titulo: 'Suministrar ${widget.numero}',
      migas: const <String>['Inicio', 'Órdenes de trabajo', 'Suministrar'],
      hijos: <Widget>[
        Tarjeta(
          acento: Nd.acento,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              CabeceraTarjeta(
                titulo: 'Insumos a entregar (${_lineas.length})',
                icono: Icons.add_shopping_cart,
                derecha: TextButton.icon(onPressed: _guardando ? null : _agregar, icon: const Icon(Icons.add), label: const Text('Agregar')),
              ),
              if (_lineas.isEmpty)
                const Padding(
                  padding: EdgeInsets.all(16),
                  child: Text('Agregue los insumos: elija el insumo, el almacén de salida y la cantidad.', style: TextStyle(color: Nd.apagado)),
                ),
              for (final _Linea l in _lineas)
                ListTile(
                  title: Text(l.insumo, style: const TextStyle(fontWeight: FontWeight.w600)),
                  subtitle: Text('Sale de: ${l.almacen}'),
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: <Widget>[
                      Text('${l.cantidad} ${l.unidad}', style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15)),
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
          child: BotonPrincipal(texto: 'Confirmar suministro', icono: Icons.check, ocupado: _guardando, alTocar: _confirmar),
        ),
      ],
    );
  }
}
