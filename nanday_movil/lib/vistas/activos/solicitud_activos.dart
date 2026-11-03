import 'dart:convert';

import 'package:flutter/material.dart';

import '../../api.dart';
import '../../tema.dart';
import '../comunes.dart';

/// ACTIVOS FIJOS > Solicitud de salida o baja (activosMovControlador.jsp?tipo=crearSolicitud).
///   REPARACION    : al proveedor que repara
///   TRANSFERENCIA : a otro centro + centro de costo
///   ENTREGA_OT    : al centro / centro de costo de una OT abierta
///   BAJA          : motivo y descripción obligatorios (sin transportista)
/// La solicitud queda PENDIENTE hasta que se apruebe en Aprobaciones.
class SolicitudActivosPantalla extends StatefulWidget {
  final Set<int> preseleccion;

  const SolicitudActivosPantalla({super.key, this.preseleccion = const <int>{}});

  @override
  State<SolicitudActivosPantalla> createState() => _SolicitudActivosPantallaState();
}

class _SolicitudActivosPantallaState extends State<SolicitudActivosPantalla> {
  static const Map<String, String> _tipos = <String, String>{
    'REPARACION': 'Reparación (al proveedor)',
    'TRANSFERENCIA': 'Transferencia a otro centro',
    'ENTREGA_OT': 'Entrega para una OT',
    'BAJA': 'Baja definitiva',
  };
  static const Map<String, String> _motivosBaja = <String, String>{
    'OBSOLESCENCIA': 'Obsolescencia',
    'ROTURA_IRREPARABLE': 'Rotura irreparable',
    'ROBO_PERDIDA': 'Robo o pérdida',
    'VENTA': 'Venta',
    'DONACION': 'Donación',
    'OTRO': 'Otro',
  };

  final GlobalKey<FormState> _formulario = GlobalKey<FormState>();
  final DatosTransportista _tr = DatosTransportista();
  final TextEditingController _observacion = TextEditingController();
  String _tipo = 'REPARACION';
  String? _motivoBaja;
  Opcion? _proveedor;
  Opcion? _centro;
  Opcion? _centroCosto;
  Opcion? _ot;
  List<Map<String, dynamic>>? _activos;
  late final Set<int> _elegidos = <int>{...widget.preseleccion};
  String _buscar = '';
  bool _guardando = false;

  @override
  void initState() {
    super.initState();
    _cargarActivos();
  }

  @override
  void dispose() {
    _tr.dispose();
    _observacion.dispose();
    super.dispose();
  }

  Future<void> _cargarActivos() async {
    try {
      final List<Map<String, dynamic>> lista = mapas(await Api.ctrl('activosMovControlador.jsp', 'activos'));
      if (!mounted) return;
      setState(() {
        _activos = lista;
        _elegidos.removeWhere((int id) => !lista.any((Map<String, dynamic> a) => ent(a['id']) == id && a['seleccionable'] == true));
      });
    } catch (e) {
      if (mounted) manejarError(context, e);
    }
  }

  Future<void> _elegir(String titulo, Future<List<Opcion>> Function() cargar, void Function(Opcion) asignar) async {
    try {
      final List<Opcion> lista = await cargar();
      if (!mounted) return;
      final Opcion? o = await elegirOpcion(context, titulo, lista);
      if (o != null && mounted) setState(() => asignar(o));
    } catch (e) {
      if (mounted) manejarError(context, e);
    }
  }

  Future<void> _enviar() async {
    if (_guardando) return;
    if (_elegidos.isEmpty) {
      avisar(context, 'Seleccione al menos un activo', error: true);
      return;
    }
    final Map<String, String> datos = <String, String>{
      'tipo_solicitud': _tipo,
      'activos': jsonEncode(_elegidos.toList()),
      'observacion': _observacion.text.trim(),
    };
    if (_tipo == 'BAJA') {
      if (_motivoBaja == null) {
        avisar(context, 'Seleccione el motivo de la baja', error: true);
        return;
      }
      if (_observacion.text.trim().isEmpty) {
        avisar(context, 'Describa el motivo de la baja en la observación', error: true);
        return;
      }
      datos['motivo_baja'] = _motivoBaja!;
    } else {
      if (!_formulario.currentState!.validate()) return;
      datos.addAll(_tr.aMapa());
      if (_tipo == 'REPARACION') {
        if (_proveedor == null) {
          avisar(context, 'Seleccione el proveedor que realizará la reparación', error: true);
          return;
        }
        datos['id_proveedor'] = '${_proveedor!.id}';
      } else if (_tipo == 'TRANSFERENCIA') {
        if (_centro == null || _centroCosto == null) {
          avisar(context, 'Seleccione el centro y el centro de costo de destino', error: true);
          return;
        }
        datos['id_centro_destino'] = '${_centro!.id}';
        datos['id_centro_costo_destino'] = '${_centroCosto!.id}';
      } else {
        if (_ot == null) {
          avisar(context, 'Seleccione la orden de trabajo', error: true);
          return;
        }
        datos['id_ot'] = '${_ot!.id}';
      }
    }
    if (!await confirmar(context, 'Enviar solicitud', '${_tipos[_tipo]} de ${_elegidos.length} activo(s). Queda pendiente de aprobación.',
        aceptar: 'Enviar')) {
      return;
    }
    if (!mounted) return;
    setState(() => _guardando = true);
    try {
      final Map<String, dynamic> r = mapa(await Api.ctrl('activosMovControlador.jsp', 'crearSolicitud', datos));
      if (!mounted) return;
      await mostrarResultado(context, 'Solicitud ${tx(r['numero'])}', 'La solicitud quedó pendiente de aprobación (${tx(r['fecha'])}).');
      if (mounted) Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) manejarError(context, e);
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final bool baja = _tipo == 'BAJA';
    final List<Map<String, dynamic>> visibles = (_activos ?? <Map<String, dynamic>>[])
        .where((Map<String, dynamic> a) =>
            _buscar.isEmpty ||
            tx(a['numero_activo']).toLowerCase().contains(_buscar) ||
            tx(a['descripcion']).toLowerCase().contains(_buscar) ||
            tx(a['numero_serie']).toLowerCase().contains(_buscar))
        .toList();
    return PaginaApp(
      titulo: 'Salida o Baja de Activos',
      migas: const <String>['Inicio', 'Activos fijos', 'Solicitud'],
      hijos: <Widget>[
        Tarjeta(
          acento: baja ? Nd.peligro : Nd.acento,
          child: CuerpoTarjeta(<Widget>[
            CampoFormulario(
              etiqueta: 'Tipo de salida',
              obligatorio: true,
              child: DropdownButtonFormField<String>(
                initialValue: _tipo,
                isExpanded: true,
                items: <DropdownMenuItem<String>>[
                  for (final MapEntry<String, String> t in _tipos.entries) DropdownMenuItem<String>(value: t.key, child: Text(t.value)),
                ],
                onChanged: (String? v) => setState(() => _tipo = v ?? 'REPARACION'),
              ),
            ),
            if (_tipo == 'REPARACION')
              CampoFormulario(
                etiqueta: 'Proveedor que repara',
                obligatorio: true,
                child: CampoSeleccion(
                  texto: _proveedor?.nombre ?? '',
                  alTocar: () => _elegir('Proveedor', () async => Opcion.lista(await Api.movilCacheado('proveedoresActivos')), (Opcion o) => _proveedor = o),
                ),
              ),
            if (_tipo == 'TRANSFERENCIA') ...<Widget>[
              CampoFormulario(
                etiqueta: 'Centro de destino',
                obligatorio: true,
                child: CampoSeleccion(
                  texto: _centro?.nombre ?? '',
                  alTocar: () => _elegir('Centro de destino', () async => Opcion.lista(await Api.movilCacheado('centros', <String, String>{'todos': 'true'})),
                      (Opcion o) {
                    _centro = o;
                    _centroCosto = null;
                  }),
                ),
              ),
              CampoFormulario(
                etiqueta: 'Centro de costo de destino',
                obligatorio: true,
                child: CampoSeleccion(
                  texto: _centroCosto?.nombre ?? '',
                  alTocar: _centro == null
                      ? null
                      : () => _elegir('Centro de costo', () async => Opcion.lista(await Api.movilCacheado('centrosCosto', <String, String>{'id_centro': '${_centro!.id}'})),
                          (Opcion o) => _centroCosto = o),
                ),
              ),
            ],
            if (_tipo == 'ENTREGA_OT')
              CampoFormulario(
                etiqueta: 'Orden de trabajo',
                obligatorio: true,
                child: CampoSeleccion(
                  texto: _ot?.nombre ?? '',
                  alTocar: () => _elegir('Orden de trabajo', () async => Opcion.lista(await Api.movil('otAbiertas')), (Opcion o) => _ot = o),
                ),
              ),
            if (baja)
              CampoFormulario(
                etiqueta: 'Motivo de la baja',
                obligatorio: true,
                child: DropdownButtonFormField<String>(
                  initialValue: _motivoBaja,
                  isExpanded: true,
                  hint: const Text('-- Seleccionar --'),
                  items: <DropdownMenuItem<String>>[
                    for (final MapEntry<String, String> m in _motivosBaja.entries) DropdownMenuItem<String>(value: m.key, child: Text(m.value)),
                  ],
                  onChanged: (String? v) => setState(() => _motivoBaja = v),
                ),
              ),
            CampoFormulario(
              etiqueta: baja ? 'Descripción del motivo' : 'Observación',
              obligatorio: baja,
              child: TextField(controller: _observacion, maxLength: 500, minLines: 1, maxLines: 3, decoration: const InputDecoration(counterText: '')),
            ),
          ]),
        ),
        espacio,
      ],
      slivers: <Widget>[
        SliverTarjetaLista(
          cabecera: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              CabeceraTarjeta(titulo: 'Activos (${_elegidos.length} elegidos)', icono: Icons.checklist),
              Padding(
                padding: const EdgeInsets.all(12),
                child: TextField(
                  decoration: const InputDecoration(hintText: 'Buscar activo', prefixIcon: Icon(Icons.search, size: 20)),
                  onChanged: (String v) => setState(() => _buscar = v.trim().toLowerCase()),
                ),
              ),
              if (_activos == null)
                const Padding(padding: EdgeInsets.all(20), child: Cargando())
              else if (_activos!.isEmpty)
                const Padding(padding: EdgeInsets.all(16), child: Text('No hay activos en sus centros.', style: TextStyle(color: Nd.apagado)))
              else if (visibles.isEmpty)
                const Padding(padding: EdgeInsets.all(16), child: Text('Ningún activo coincide con la búsqueda.', style: TextStyle(color: Nd.apagado))),
            ],
          ),
          cantidad: visibles.length,
          fila: (BuildContext c, int i) {
            final Map<String, dynamic> a = visibles[i];
            return CheckboxListTile(
              value: _elegidos.contains(ent(a['id'])),
              onChanged: a['seleccionable'] == true
                  ? (bool? v) => setState(() => v == true ? _elegidos.add(ent(a['id'])) : _elegidos.remove(ent(a['id'])))
                  : null,
              title: Text('${tx(a['numero_activo'])} - ${tx(a['descripcion'])}', style: const TextStyle(fontWeight: FontWeight.w600)),
              subtitle: Text('${tx(a['centro'])} / ${tx(a['centro_costo'])}  ·  ${legible(tx(a['estado']))}'
                  '${tx(a['motivo']).isEmpty ? '' : '\n${tx(a['motivo'])}'}'),
              controlAffinity: ListTileControlAffinity.leading,
            );
          },
        ),
        if (!baja) ...<Widget>[
          caja(espacio),
          caja(Tarjeta(
            child: Form(
              key: _formulario,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: <Widget>[
                  const CabeceraTarjeta(titulo: 'Quién transporta', icono: Icons.local_shipping),
                  CuerpoTarjeta(<Widget>[_tr.campos(obligatorio: true)]),
                ],
              ),
            ),
          )),
        ],
        caja(espacio),
        caja(Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: BotonPrincipal(
            texto: baja ? 'Solicitar baja' : 'Solicitar salida',
            icono: Icons.send,
            color: baja ? Nd.peligro : null,
            ocupado: _guardando,
            alTocar: _enviar,
          ),
        )),
      ],
    );
  }
}
