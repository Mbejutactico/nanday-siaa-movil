import 'package:flutter/material.dart';

import '../../api.dart';
import '../../tema.dart';
import '../comunes.dart';

/// INVENTARIOS > Salida de insumos: despacho de los insumos ya suministrados a una OT,
/// con los datos de quien transporta. Genera la REMISIÓN (REM-AAAA-000001).
/// Usa inventarioControlador.jsp (pendientesSalida / emitirSalida), igual que la web.
class SalidaInsumosPantalla extends StatefulWidget {
  const SalidaInsumosPantalla({super.key});

  @override
  State<SalidaInsumosPantalla> createState() => _SalidaInsumosPantallaState();
}

class _SalidaInsumosPantallaState extends State<SalidaInsumosPantalla> {
  final GlobalKey<CargableEstado<List<Map<String, dynamic>>>> carga = GlobalKey<CargableEstado<List<Map<String, dynamic>>>>();

  @override
  Widget build(BuildContext context) {
    return PaginaApp(
      titulo: 'Salida de Insumos',
      migas: const <String>['Inicio', 'Inventarios', 'Salida de insumos'],
      alRefrescar: () async => carga.currentState?.recargar(),
      hijos: <Widget>[
        const Padding(
          padding: EdgeInsets.symmetric(horizontal: 16),
          child: Alerta(
            texto: 'Órdenes de trabajo con insumos suministrados que todavía no salieron del almacén. '
                'Elija una para registrar la salida y generar la remisión.',
          ),
        ),
        espacio,
        Cargable<List<Map<String, dynamic>>>(
          key: carga,
          cargar: () async => mapas(await Api.movil('otsConPendientes')),
          constructor: (BuildContext context, List<Map<String, dynamic>> ots, Future<void> Function() recargar) {
            if (ots.isEmpty) {
              return const Aviso(icono: Icons.local_shipping, texto: 'No hay órdenes de trabajo con insumos pendientes de salida.');
            }
            return Tarjeta(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: <Widget>[
                  CabeceraTarjeta(titulo: 'Pendientes de salida (${ots.length})', icono: Icons.pending_actions),
                  for (final Map<String, dynamic> o in ots)
                    ListTile(
                      title: Text('${tx(o['numero'])} - ${tx(o['titulo'])}',
                          style: const TextStyle(fontWeight: FontWeight.w600, color: Nd.titulo)),
                      subtitle: Text('${tx(o['centro'])}  ·  ${ent(o['lineas'])} insumo(s) por despachar'),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () async {
                        final bool? hecho = await Navigator.of(context).push<bool>(MaterialPageRoute<bool>(
                          builder: (BuildContext c) => EmitirSalidaPantalla(idOT: ent(o['id']), numero: tx(o['numero'])),
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

/// Insumos pendientes de una OT + datos del transportista -> emitirSalida.
class EmitirSalidaPantalla extends StatefulWidget {
  final int idOT;
  final String numero;

  const EmitirSalidaPantalla({super.key, required this.idOT, required this.numero});

  @override
  State<EmitirSalidaPantalla> createState() => _EmitirSalidaPantallaState();
}

class _EmitirSalidaPantallaState extends State<EmitirSalidaPantalla> {
  final GlobalKey<FormState> _formulario = GlobalKey<FormState>();
  final DatosTransportista _tr = DatosTransportista();
  final TextEditingController _observacion = TextEditingController();
  bool _guardando = false;

  @override
  void dispose() {
    _tr.dispose();
    _observacion.dispose();
    super.dispose();
  }

  Future<void> _emitir() async {
    if (_guardando) return;
    if (!_formulario.currentState!.validate()) return;
    if (!await confirmar(context, 'Registrar salida', '¿Registrar la salida de los insumos de ${widget.numero} y emitir la remisión?',
        aceptar: 'Emitir remisión')) {
      return;
    }
    if (!mounted) return;
    setState(() => _guardando = true);
    try {
      final Map<String, dynamic> r = mapa(await Api.ctrl('inventarioControlador.jsp', 'emitirSalida', <String, String>{
        'id_ot': '${widget.idOT}',
        'observacion': _observacion.text.trim(),
        ..._tr.aMapa(),
      }));
      if (!mounted) return;
      await mostrarResultado(context, 'Remisión emitida', 'Se registró la salida con la remisión ${tx(r['numero'])} (${tx(r['fecha'])}).');
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
      titulo: 'Salida ${widget.numero}',
      migas: const <String>['Inicio', 'Inventarios', 'Salida de insumos'],
      hijos: <Widget>[
        Cargable<Map<String, dynamic>>(
          cargar: () async => mapa(await Api.ctrl('inventarioControlador.jsp', 'pendientesSalida', <String, String>{'id_ot': '${widget.idOT}'})),
          constructor: (BuildContext context, Map<String, dynamic> f, Future<void> Function() recargar) {
            final List<Map<String, dynamic>> items = mapas(f['items']);
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                Tarjeta(
                  acento: Nd.primario,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: <Widget>[
                      CabeceraTarjeta(titulo: '${tx(f['numero'])} - ${tx(f['titulo'])}', icono: Icons.assignment, derecha: ChipEstado(tx(f['estado']))),
                      CuerpoTarjeta(<Widget>[
                        DatoFila('Centro', tx(f['centro'])),
                        DatoFila('Centro de costo', tx(f['centro_costo'])),
                        DatoFila('Solicitante', tx(f['solicitante'])),
                      ]),
                    ],
                  ),
                ),
                espacio,
                Tarjeta(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: <Widget>[
                      CabeceraTarjeta(titulo: 'Insumos a despachar (${items.length})', icono: Icons.inventory_2),
                      for (final Map<String, dynamic> it in items)
                        ListTile(
                          dense: true,
                          title: Text('${tx(it['codigo'])} - ${tx(it['nombre'])}', style: const TextStyle(fontWeight: FontWeight.w600)),
                          subtitle: Text('Almacén: ${tx(it['almacen'])}'),
                          trailing: Text('${ent(it['cantidad'])} ${tx(it['unidad'])}',
                              style: const TextStyle(fontWeight: FontWeight.w700, color: Nd.titulo, fontSize: 15)),
                        ),
                    ],
                  ),
                ),
              ],
            );
          },
        ),
        espacio,
        Tarjeta(
          child: Form(
            key: _formulario,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                const CabeceraTarjeta(titulo: 'Quién transporta', icono: Icons.local_shipping),
                CuerpoTarjeta(<Widget>[
                  _tr.campos(obligatorio: true),
                  CampoFormulario(
                    etiqueta: 'Observación',
                    child: TextField(controller: _observacion, maxLength: 500, decoration: const InputDecoration(counterText: '')),
                  ),
                  BotonPrincipal(texto: 'Emitir remisión', icono: Icons.receipt_long, ocupado: _guardando, alTocar: _emitir),
                ]),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
