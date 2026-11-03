import 'package:flutter/material.dart';

import '../../api.dart';
import '../../sesion.dart';
import '../../tema.dart';
import '../comunes.dart';
import '../inventario/salida_insumos.dart';
import 'formulario_ot.dart';
import 'suministrar_ot.dart';

/// Ficha de una OT (otControlador.jsp?tipo=detalle): datos, insumos entregados, remisiones
/// e historial. Acciones según el rol y el estado, con las mismas reglas que la web:
///   Suministrar insumos  -> OT_SUMINISTRAR, centro destino, OT abierta
///   Quitar un insumo     -> OT_SUMINISTRAR, la línea todavía no salió (sin remisión)
///   Registrar la salida  -> INVENTARIO (Salida de insumos)
///   Finalizar            -> OT_FINALIZAR, sin insumos pendientes de salida y con al menos una salida
///   Anular finalización  -> OT_FINALIZAR, con motivo (solo quien la finalizó o el Administrador)
class DetalleOTPantalla extends StatefulWidget {
  final int idOT;

  const DetalleOTPantalla({super.key, required this.idOT});

  @override
  State<DetalleOTPantalla> createState() => _DetalleOTPantallaState();
}

class _DetalleOTPantallaState extends State<DetalleOTPantalla> {
  final GlobalKey<CargableEstado<Map<String, dynamic>>> _carga = GlobalKey<CargableEstado<Map<String, dynamic>>>();
  bool _ocupado = false;

  Future<void> _recargar() async => _carga.currentState?.recargar();

  Future<void> _accion(Future<String> Function() operacion) async {
    if (_ocupado) return;
    setState(() => _ocupado = true);
    try {
      final String mensaje = await operacion();
      if (!mounted) return;
      if (mensaje.isNotEmpty) avisar(context, mensaje);
      await _recargar();
    } catch (e) {
      if (mounted) manejarError(context, e);
    } finally {
      if (mounted) setState(() => _ocupado = false);
    }
  }

  Future<void> _suministrar(Map<String, dynamic> f) async {
    final bool? hecho = await Navigator.of(context).push<bool>(MaterialPageRoute<bool>(
      builder: (BuildContext c) => SuministrarOTPantalla(idOT: widget.idOT, numero: tx(f['numero'])),
    ));
    if (hecho == true) _recargar();
  }

  Future<void> _salida(Map<String, dynamic> f) async {
    final bool? hecho = await Navigator.of(context).push<bool>(MaterialPageRoute<bool>(
      builder: (BuildContext c) => EmitirSalidaPantalla(idOT: widget.idOT, numero: tx(f['numero'])),
    ));
    if (hecho == true) _recargar();
  }

  Future<void> _modificar(Map<String, dynamic> f) async {
    final int? id = await Navigator.of(context)
        .push<int>(MaterialPageRoute<int>(builder: (BuildContext c) => FormularioOTPantalla(ot: f)));
    if (id != null) _recargar();
  }

  /// Igual que la web: no se elimina si está finalizada, si tiene remisiones o insumos suministrados.
  Future<void> _eliminar(Map<String, dynamic> f) async {
    if (!await confirmarEliminacion(context,
        titulo: 'Eliminar orden de trabajo', mensaje: '¿Seguro que desea eliminar la OT ${tx(f['numero'])}?\n\n${tx(f['titulo'])}')) {
      return;
    }
    if (!mounted || _ocupado) return;
    setState(() => _ocupado = true);
    try {
      await Api.ctrl('otControlador.jsp', 'eliminar', <String, String>{'id': '${widget.idOT}'});
      if (!mounted) return;
      avisar(context, 'OT ${tx(f['numero'])} eliminada');
      Navigator.of(context).pop();
    } catch (e) {
      if (mounted) {
        manejarError(context, e);
        setState(() => _ocupado = false);
      }
    }
  }

  Future<void> _quitar(Map<String, dynamic> it) async {
    if (!await confirmar(context, 'Quitar insumo',
        '¿Quitar ${ent(it['cantidad'])} x ${tx(it['descripcion'])}? El stock vuelve al almacén ${tx(it['almacen'])}.',
        aceptar: 'Quitar', color: Nd.peligro)) {
      return;
    }
    await _accion(() async {
      await Api.ctrl('otControlador.jsp', 'quitarSuministro', <String, String>{'id_detalle': '${ent(it['id'])}'});
      return 'Insumo quitado; el stock volvió al almacén';
    });
  }

  Future<void> _finalizar(Map<String, dynamic> f) async {
    final String? obs = await pedirTexto(context,
        titulo: 'Finalizar ${tx(f['numero'])}',
        mensaje: 'La OT queda cerrada y no se podrá usar (se puede anular la finalización con un motivo).',
        etiqueta: 'Observación',
        obligatorio: false,
        aceptar: 'Finalizar');
    if (obs == null) return;
    await _accion(() async {
      final Map<String, dynamic> r = mapa(await Api.ctrl('otControlador.jsp', 'finalizar', <String, String>{'id': '${widget.idOT}', 'observacion': obs}));
      return 'OT finalizada el ${tx(r['fecha'])}';
    });
  }

  Future<void> _reabrir(Map<String, dynamic> f) async {
    final String? motivo = await pedirTexto(context,
        titulo: 'Anular finalización',
        mensaje: 'La OT ${tx(f['numero'])} vuelve a quedar abierta.',
        aceptar: 'Anular finalización',
        color: Nd.acentoProfundo);
    if (motivo == null) return;
    await _accion(() async {
      await Api.ctrl('otControlador.jsp', 'reabrir', <String, String>{'id': '${widget.idOT}', 'motivo': motivo});
      return 'Finalización anulada: la OT está abierta otra vez';
    });
  }

  @override
  Widget build(BuildContext context) {
    return PaginaApp(
      titulo: 'Orden de Trabajo',
      migas: const <String>['Inicio', 'Órdenes de trabajo', 'Detalle'],
      alRefrescar: _recargar,
      hijos: <Widget>[
        Cargable<Map<String, dynamic>>(
          key: _carga,
          cargar: () async => mapa(await Api.ctrl('otControlador.jsp', 'detalle', <String, String>{'id': '${widget.idOT}'})),
          constructor: (BuildContext context, Map<String, dynamic> f, Future<void> Function() recargar) => _contenido(f),
        ),
      ],
    );
  }

  Widget _contenido(Map<String, dynamic> f) {
    final String estado = tx(f['estado']);
    final bool abierta = estado == 'PENDIENTE' || estado == 'EN_PROCESO';
    final bool operar = f['puede_operar'] == true;
    final List<Map<String, dynamic>> items = mapas(f['items']);
    final List<Map<String, dynamic>> remisiones = mapas(f['remisiones']);
    final List<Map<String, dynamic>> eventos = mapas(f['eventos']);
    final bool puedeSuministrar = abierta && operar && Sesion.puede(Sesion.otSuministrar);
    final bool puedeSalida = abierta && f['pendientes_salida'] == true && operar && Sesion.puede(Sesion.inventario);
    final bool puedeFinalizar = operar && Sesion.puede(Sesion.otFinalizar);
    // Modificar: OT_GESTION y ser del centro destino o quien la pidió (puede_gestionar)
    final bool gestionar = f['puede_gestionar'] == true && Sesion.puede(Sesion.otGestion);
    final bool puedeModificar = gestionar && abierta;
    // Eliminar: solo quien creó la OT o el administrador (puede_eliminar)
    final bool puedeEliminar = f['puede_eliminar'] == true && Sesion.puede(Sesion.otGestion) && estado != 'FINALIZADA';
    // Anular la finalización: solo quien la finalizó o el administrador (puede_reabrir)
    final bool puedeReabrir = puedeFinalizar && estado == 'FINALIZADA' && f['puede_reabrir'] == true;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        Tarjeta(
          acento: ChipEstado.colorDe(estado),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              CabeceraTarjeta(titulo: tx(f['numero']), icono: Icons.assignment, derecha: ChipEstado(estado)),
              CuerpoTarjeta(<Widget>[
                Text(tx(f['titulo']), style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700, color: Nd.titulo)),
                if (tx(f['descripcion']).isNotEmpty) ...<Widget>[
                  const SizedBox(height: 4),
                  Text(tx(f['descripcion']), style: const TextStyle(color: Nd.pizarra, height: 1.35)),
                ],
                const SizedBox(height: 10),
                DatoFila('Centro destino', tx(f['centro'])),
                DatoFila('Centro de costo', tx(f['centro_costo'])),
                DatoFila('Solicitante', '${tx(f['solicitante'])}${tx(f['centro_solicitante']).isEmpty ? '' : ' (${tx(f['centro_solicitante'])})'}'),
                DatoFila('Responsable', tx(f['responsable']).isEmpty ? 'Sin responsable' : tx(f['responsable'])),
                DatoFila('Creada', tx(f['fecha_creacion'])),
                if (estado == 'FINALIZADA') ...<Widget>[
                  DatoFila('Finalizada', '${tx(f['fecha_finalizacion'])} por ${tx(f['finalizado_por'])}'),
                  DatoFila('Observación', tx(f['observacion_finalizacion'])),
                ],
                if (tx(f['fecha_anulacion_fin']).isNotEmpty)
                  DatoFila('Finalización anulada', '${tx(f['fecha_anulacion_fin'])}: ${tx(f['motivo_anulacion_fin'])}'),
              ]),
            ],
          ),
        ),
        if (puedeModificar || puedeEliminar) ...<Widget>[
          const SizedBox(height: 10),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              children: <Widget>[
                if (puedeModificar)
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: _ocupado ? null : () => _modificar(f),
                      icon: const Icon(Icons.edit, size: 18),
                      label: const Text('Modificar'),
                    ),
                  ),
                if (puedeModificar && puedeEliminar) const SizedBox(width: 10),
                if (puedeEliminar)
                  Expanded(
                    child: OutlinedButton.icon(
                      style: OutlinedButton.styleFrom(foregroundColor: Nd.peligro),
                      onPressed: _ocupado ? null : () => _eliminar(f),
                      icon: const Icon(Icons.delete_outline, size: 18),
                      label: const Text('Eliminar'),
                    ),
                  ),
              ],
            ),
          ),
        ],
        espacio,
        // ---- Acciones ----
        if (puedeSuministrar || puedeSalida || puedeFinalizar)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                if (puedeSuministrar) ...<Widget>[
                  BotonPrincipal(texto: 'Suministrar insumos', icono: Icons.add_shopping_cart, ocupado: _ocupado, alTocar: () => _suministrar(f)),
                  const SizedBox(height: 10),
                ],
                if (puedeSalida) ...<Widget>[
                  BotonPrincipal(
                      texto: 'Registrar salida (remisión)', icono: Icons.local_shipping, color: Nd.info, ocupado: _ocupado, alTocar: () => _salida(f)),
                  const SizedBox(height: 10),
                ],
                if (puedeFinalizar && abierta) ...<Widget>[
                  BotonPrincipal(
                    texto: 'Finalizar OT',
                    icono: Icons.task_alt,
                    color: Nd.profundo,
                    ocupado: _ocupado,
                    alTocar: f['finalizable'] == true ? () => _finalizar(f) : null,
                  ),
                  if (f['finalizable'] != true)
                    const Padding(
                      padding: EdgeInsets.only(top: 6),
                      child: Text(
                        'Para finalizar, todos los insumos entregados deben tener su salida (remisión) y debe haber al menos una salida registrada.',
                        style: TextStyle(color: Nd.apagado, fontSize: 12.5),
                      ),
                    ),
                  const SizedBox(height: 10),
                ],
                if (puedeReabrir) ...<Widget>[
                  BotonPrincipal(
                      texto: 'Anular finalización', icono: Icons.lock_open, color: Nd.acentoProfundo, ocupado: _ocupado, alTocar: () => _reabrir(f)),
                  const SizedBox(height: 10),
                ],
              ],
            ),
          ),
        // ---- Insumos ----
        Tarjeta(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              CabeceraTarjeta(titulo: 'Insumos entregados (${items.length})', icono: Icons.inventory_2),
              if (items.isEmpty)
                const Padding(padding: EdgeInsets.all(16), child: Text('Todavía no se entregaron insumos.', style: TextStyle(color: Nd.apagado))),
              for (final Map<String, dynamic> it in items)
                Container(
                  padding: const EdgeInsets.fromLTRB(16, 10, 8, 10),
                  decoration: const BoxDecoration(border: Border(bottom: BorderSide(color: Nd.borde))),
                  child: Row(
                    children: <Widget>[
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: <Widget>[
                            Text('${tx(it['codigo'])} - ${tx(it['descripcion'])}', style: const TextStyle(fontWeight: FontWeight.w600, color: Nd.titulo)),
                            Text('${tx(it['almacen'])}  ·  ${tx(it['fecha'])}  ·  ${tx(it['suministrado_por'])}',
                                style: const TextStyle(color: Nd.apagado, fontSize: 12.5)),
                            Text(
                              tx(it['remision']).isEmpty ? 'Pendiente de salida' : 'Salió con ${tx(it['remision'])}',
                              style: TextStyle(
                                  fontSize: 12.5,
                                  fontWeight: FontWeight.w600,
                                  color: tx(it['remision']).isEmpty ? Nd.acentoProfundo : Nd.primario),
                            ),
                          ],
                        ),
                      ),
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: <Widget>[
                          Text('${ent(it['cantidad'])} ${tx(it['unidad'])}', style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15)),
                          Text('Gs. ${guaranies(it['subtotal'])}', style: const TextStyle(color: Nd.apagado, fontSize: 12)),
                        ],
                      ),
                      if (puedeSuministrar && tx(it['remision']).isEmpty)
                        IconButton(
                          tooltip: 'Quitar (el stock vuelve al almacén)',
                          icon: const Icon(Icons.remove_circle_outline, color: Nd.peligro),
                          onPressed: _ocupado ? null : () => _quitar(it),
                        ),
                    ],
                  ),
                ),
            ],
          ),
        ),
        // ---- Remisiones ----
        if (remisiones.isNotEmpty) ...<Widget>[
          espacio,
          Tarjeta(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                CabeceraTarjeta(titulo: 'Remisiones (${remisiones.length})', icono: Icons.receipt_long),
                for (final Map<String, dynamic> r in remisiones)
                  ListTile(
                    dense: true,
                    title: Text('${tx(r['numero'])}  ·  ${legible(tx(r['tipo']))}', style: const TextStyle(fontWeight: FontWeight.w600)),
                    subtitle: Text('${tx(r['fecha'])}  ·  ${tx(r['transportista'])}'
                        '${r['anulada'] == true ? '\nAnulada: ${tx(r['motivo_anulacion'])}' : ''}'),
                    trailing: ChipEstado(r['anulada'] == true ? 'ANULADA' : 'VIGENTE'),
                  ),
              ],
            ),
          ),
        ],
        // ---- Historial ----
        espacio,
        Tarjeta(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              CabeceraTarjeta(titulo: 'Historial (${eventos.length})', icono: Icons.history),
              for (final Map<String, dynamic> e in eventos)
                Container(
                  padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
                  decoration: const BoxDecoration(
                    border: Border(left: BorderSide(color: Nd.acento, width: 3), bottom: BorderSide(color: Nd.borde)),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Text('${legible(tx(e['tipo']))}  ·  ${tx(e['fecha'])}',
                          style: const TextStyle(fontWeight: FontWeight.w700, color: Nd.titulo, fontSize: 13.5)),
                      Text(tx(e['detalle']), style: const TextStyle(color: Nd.pizarra, fontSize: 13.5)),
                      Text(tx(e['usuario']), style: const TextStyle(color: Nd.apagado, fontSize: 12)),
                    ],
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }
}
