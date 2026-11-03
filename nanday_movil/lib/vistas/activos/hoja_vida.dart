import 'package:flutter/material.dart';

import '../../api.dart';
import '../../sesion.dart';
import '../../tema.dart';
import '../comunes.dart';
import 'solicitud_activos.dart';

/// HOJA DE VIDA de un activo (activosControlador.jsp?tipo=detalle): ficha, compra e historial
/// completo de movimientos. Acciones (activosControlador.jsp?tipo=registrarMovimiento):
///   Asignar   DISPONIBLE -> ASIGNADO (a un usuario o a una persona externa)
///   Devolver  ASIGNADO   -> DISPONIBLE
///   Reubicar  otro centro de costo del mismo centro
/// y "Solicitar salida o baja" (pasa por Aprobaciones).
class HojaVidaPantalla extends StatefulWidget {
  final int idEquipo;

  const HojaVidaPantalla({super.key, required this.idEquipo});

  @override
  State<HojaVidaPantalla> createState() => _HojaVidaPantallaState();
}

class _HojaVidaPantallaState extends State<HojaVidaPantalla> {
  final GlobalKey<CargableEstado<Map<String, dynamic>>> _carga = GlobalKey<CargableEstado<Map<String, dynamic>>>();
  bool _ocupado = false;

  Future<void> _movimiento(String accion, Map<String, String> datos, String ok) async {
    setState(() => _ocupado = true);
    try {
      await Api.ctrl('activosControlador.jsp', 'registrarMovimiento', <String, String>{
        'id_equipo': '${widget.idEquipo}',
        'accion': accion,
        ...datos,
      });
      if (!mounted) return;
      avisar(context, ok);
      await _carga.currentState?.recargar();
    } catch (e) {
      if (mounted) manejarError(context, e);
    } finally {
      if (mounted) setState(() => _ocupado = false);
    }
  }

  Future<void> _asignar() async {
    final Map<String, String>? datos = await showDialog<Map<String, String>>(
      context: context,
      builder: (BuildContext d) => const _DialogoAsignar(),
    );
    if (datos == null) return;
    await _movimiento('ASIGNACION', datos, 'Activo asignado');
  }

  Future<void> _devolver() async {
    final String? obs = await pedirTexto(context,
        titulo: 'Registrar devolución', mensaje: 'El activo vuelve a quedar DISPONIBLE.', etiqueta: 'Observaciones', obligatorio: false, aceptar: 'Registrar');
    if (obs == null) return;
    await _movimiento('DEVOLUCION', <String, String>{'observaciones': obs}, 'Devolución registrada');
  }

  Future<void> _reubicar(Map<String, dynamic> f) async {
    try {
      final List<Opcion> ccs = Opcion.lista(await Api.movilCacheado('centrosCosto', <String, String>{'id_centro': '${ent(f['id_centro'])}'}))
          .where((Opcion o) => o.id != ent(f['id_centro_costo']))
          .toList();
      if (!mounted) return;
      final Opcion? cc = await elegirOpcion(context, 'Nuevo centro de costo', ccs);
      if (cc == null || !mounted) return;
      final String? obs = await pedirTexto(context,
          titulo: 'Cambiar centro de costo', mensaje: 'Pasa a: ${cc.nombre}', etiqueta: 'Observaciones', obligatorio: false, aceptar: 'Cambiar');
      if (obs == null) return;
      await _movimiento('REUBICACION', <String, String>{'id_centro_costo': '${cc.id}', 'observaciones': obs}, 'Centro de costo actualizado');
    } catch (e) {
      if (mounted) manejarError(context, e);
    }
  }

  Future<void> _solicitar() async {
    final bool? hecho = await Navigator.of(context).push<bool>(MaterialPageRoute<bool>(
      builder: (BuildContext c) => SolicitudActivosPantalla(preseleccion: <int>{widget.idEquipo}),
    ));
    if (hecho == true) _carga.currentState?.recargar();
  }

  @override
  Widget build(BuildContext context) {
    return PaginaApp(
      titulo: 'Hoja de Vida',
      migas: const <String>['Inicio', 'Activos fijos', 'Hoja de vida'],
      alRefrescar: () async => _carga.currentState?.recargar(),
      hijos: <Widget>[
        Cargable<Map<String, dynamic>>(
          key: _carga,
          cargar: () async => mapa(await Api.ctrl('activosControlador.jsp', 'detalle', <String, String>{'id': '${widget.idEquipo}'})),
          constructor: (BuildContext context, Map<String, dynamic> f, Future<void> Function() recargar) => _contenido(f),
        ),
      ],
    );
  }

  Widget _contenido(Map<String, dynamic> f) {
    final String estado = tx(f['estado']);
    final bool libre = tx(f['situacion']).isEmpty;
    final bool puede = Sesion.puede(Sesion.activos);
    final List<Map<String, dynamic>> movs = mapas(f['movimientos']);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        Tarjeta(
          acento: ChipEstado.colorDe(estado),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              CabeceraTarjeta(
                titulo: tx(f['numero_activo']).isEmpty ? 'Activo sin número' : tx(f['numero_activo']),
                icono: Icons.qr_code_2,
                derecha: ChipEstado(estado),
              ),
              CuerpoTarjeta(<Widget>[
                Text(tx(f['descripcion']), style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700, color: Nd.titulo)),
                const SizedBox(height: 8),
                DatoFila('Código', tx(f['codigo'])),
                DatoFila('Serie fabricante', tx(f['numero_serie'])),
                DatoFila('Centro', tx(f['centro'])),
                DatoFila('Centro de costo', tx(f['centro_costo'])),
                DatoFila('Responsable', tx(f['responsable'])),
                DatoFila('Ingreso', fechaCorta(tx(f['fecha_ingreso']))),
                if (!libre)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Alerta(texto: tx(f['situacion'])),
                  ),
              ]),
            ],
          ),
        ),
        espacio,
        Tarjeta(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              const CabeceraTarjeta(titulo: 'Compra', icono: Icons.receipt),
              CuerpoTarjeta(<Widget>[
                DatoFila('Orden de compra', tx(f['numero_oc'])),
                DatoFila('Fecha de compra', fechaCorta(tx(f['fecha_compra']))),
                DatoFila('Proveedor', '${tx(f['proveedor'])}${tx(f['ruc']).isEmpty ? '' : ' (RUC ${tx(f['ruc'])})'}'),
                DatoFila('Valor', tx(f['valor']).isEmpty ? '' : 'Gs. ${guaranies(f['valor'])}'),
                DatoFila('Solicitado por', tx(f['solicitante'])),
              ]),
            ],
          ),
        ),
        if (puede && libre && (estado == 'DISPONIBLE' || estado == 'ASIGNADO')) ...<Widget>[
          espacio,
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Wrap(
              spacing: 8,
              runSpacing: 8,
              children: <Widget>[
                if (estado == 'DISPONIBLE')
                  FilledButton.icon(onPressed: _ocupado ? null : _asignar, icon: const Icon(Icons.person_add), label: const Text('Asignar')),
                if (estado == 'ASIGNADO')
                  FilledButton.icon(
                    style: FilledButton.styleFrom(backgroundColor: Nd.info),
                    onPressed: _ocupado ? null : _devolver,
                    icon: const Icon(Icons.assignment_return),
                    label: const Text('Registrar devolución'),
                  ),
                OutlinedButton.icon(
                    onPressed: _ocupado ? null : () => _reubicar(f), icon: const Icon(Icons.swap_horiz), label: const Text('Cambiar centro de costo')),
                OutlinedButton.icon(
                  style: OutlinedButton.styleFrom(foregroundColor: Nd.acentoProfundo),
                  onPressed: _ocupado ? null : _solicitar,
                  icon: const Icon(Icons.outbox),
                  label: const Text('Solicitar salida o baja'),
                ),
              ],
            ),
          ),
        ],
        espacio,
        Tarjeta(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              CabeceraTarjeta(titulo: 'Historial (${movs.length})', icono: Icons.history),
              for (final Map<String, dynamic> m in movs) _Movimiento(m),
            ],
          ),
        ),
      ],
    );
  }
}

class _Movimiento extends StatelessWidget {
  final Map<String, dynamic> m;

  const _Movimiento(this.m);

  @override
  Widget build(BuildContext context) {
    final String persona = '${tx(m['persona_nombre'])} ${tx(m['persona_apellido'])}'.trim();
    final String origen = <String>[tx(m['centro_origen']), tx(m['sub_origen'])].where((String s) => s.isNotEmpty).join(' / ');
    final String destino = <String>[tx(m['centro_destino']), tx(m['sub_destino'])].where((String s) => s.isNotEmpty).join(' / ');
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
      decoration: const BoxDecoration(border: Border(left: BorderSide(color: Nd.acento, width: 3), bottom: BorderSide(color: Nd.borde))),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text('${legible(tx(m['tipo']))}  ·  ${tx(m['fecha'])}', style: const TextStyle(fontWeight: FontWeight.w700, color: Nd.titulo, fontSize: 13.5)),
          if (tx(m['estado_anterior']).isNotEmpty || tx(m['estado_nuevo']).isNotEmpty)
            Text('${legible(tx(m['estado_anterior']))} → ${legible(tx(m['estado_nuevo']))}', style: const TextStyle(color: Nd.pizarra, fontSize: 13)),
          if (origen.isNotEmpty || destino.isNotEmpty)
            Text(origen == destino ? origen : '$origen → $destino', style: const TextStyle(color: Nd.pizarra, fontSize: 13)),
          if (tx(m['receptor']).isNotEmpty) Text('Responsable: ${tx(m['receptor'])}', style: const TextStyle(color: Nd.pizarra, fontSize: 13)),
          if (persona.isNotEmpty)
            Text('Persona: $persona${tx(m['persona_ci']).isEmpty ? '' : ' (CI ${tx(m['persona_ci'])})'}', style: const TextStyle(color: Nd.pizarra, fontSize: 13)),
          if (tx(m['remision']).isNotEmpty) Text('Remisión ${tx(m['remision'])}', style: const TextStyle(color: Nd.pizarra, fontSize: 13)),
          if (tx(m['observaciones']).isNotEmpty) Text(tx(m['observaciones']), style: const TextStyle(color: Nd.pizarra, fontSize: 13)),
          if (tx(m['justificacion']).isNotEmpty) Text('Justificación: ${tx(m['justificacion'])}', style: const TextStyle(color: Nd.peligro, fontSize: 13)),
          Text(tx(m['registrado_por']), style: const TextStyle(color: Nd.apagado, fontSize: 12)),
        ],
      ),
    );
  }
}

/// Asignación: a un usuario del sistema o a una persona externa (nombre, apellido y CI).
class _DialogoAsignar extends StatefulWidget {
  const _DialogoAsignar();

  @override
  State<_DialogoAsignar> createState() => _DialogoAsignarState();
}

class _DialogoAsignarState extends State<_DialogoAsignar> {
  final GlobalKey<FormState> _f = GlobalKey<FormState>();
  bool _externa = false;
  Opcion? _usuario;
  final TextEditingController _nombre = TextEditingController();
  final TextEditingController _apellido = TextEditingController();
  final TextEditingController _ci = TextEditingController();
  final TextEditingController _obs = TextEditingController();

  @override
  void dispose() {
    _nombre.dispose();
    _apellido.dispose();
    _ci.dispose();
    _obs.dispose();
    super.dispose();
  }

  Future<void> _elegirUsuario() async {
    try {
      final List<Opcion> lista = Opcion.lista(await Api.movilCacheado('usuarios'));
      if (!mounted) return;
      final Opcion? o = await elegirOpcion(context, 'Responsable', lista);
      if (o != null && mounted) setState(() => _usuario = o);
    } catch (e) {
      if (mounted) manejarError(context, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    String? req(String? v) => (v ?? '').trim().isEmpty ? 'Obligatorio' : null;
    return AlertDialog(
      title: const Text('Asignar activo', style: TextStyle(fontWeight: FontWeight.w700, color: Nd.titulo, fontSize: 18)),
      content: SingleChildScrollView(
        child: Form(
          key: _f,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              SegmentedButton<bool>(
                segments: const <ButtonSegment<bool>>[
                  ButtonSegment<bool>(value: false, label: Text('Usuario')),
                  ButtonSegment<bool>(value: true, label: Text('Persona externa')),
                ],
                selected: <bool>{_externa},
                onSelectionChanged: (Set<bool> s) => setState(() => _externa = s.first),
              ),
              const SizedBox(height: 14),
              if (!_externa)
                CampoSeleccion(texto: _usuario?.nombre ?? '', sugerencia: '-- Elegir usuario --', alTocar: _elegirUsuario)
              else ...<Widget>[
                TextFormField(controller: _nombre, decoration: const InputDecoration(hintText: 'Nombre'), validator: req),
                const SizedBox(height: 10),
                TextFormField(controller: _apellido, decoration: const InputDecoration(hintText: 'Apellido'), validator: req),
                const SizedBox(height: 10),
                TextFormField(controller: _ci, keyboardType: TextInputType.number, decoration: const InputDecoration(hintText: 'Cédula de identidad'), validator: req),
              ],
              const SizedBox(height: 10),
              TextField(controller: _obs, maxLines: 2, decoration: const InputDecoration(hintText: 'Observaciones (opcional)')),
            ],
          ),
        ),
      ),
      actions: <Widget>[
        OutlinedButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Cancelar')),
        FilledButton(
          onPressed: () {
            if (!_externa && _usuario == null) {
              avisar(context, 'Elija el usuario responsable', error: true);
              return;
            }
            if (!_f.currentState!.validate()) return;
            Navigator.of(context).pop(<String, String>{
              if (!_externa) 'id_usuario_receptor': '${_usuario!.id}',
              if (_externa) 'persona_nombre': _nombre.text.trim(),
              if (_externa) 'persona_apellido': _apellido.text.trim(),
              if (_externa) 'persona_ci': _ci.text.trim(),
              'observaciones': _obs.text.trim(),
            });
          },
          child: const Text('Asignar'),
        ),
      ],
    );
  }
}
