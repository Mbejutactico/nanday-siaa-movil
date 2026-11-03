import 'package:flutter/material.dart';

import '../../api.dart';
import '../../tema.dart';
import '../comunes.dart';

/// NUEVA / MODIFICAR ORDEN DE TRABAJO, igual que otCrear.jsp de la web
/// (otControlador.jsp: contextoUsuario, listarResponsables, crear, modificar). Permiso OT_GESTION.
///
/// - Centro destino: el centro que va a resolver la OT (cualquier centro activo). Por defecto, el del usuario.
///   Al modificar solo se puede cambiar mientras la OT no tenga insumos entregados.
/// - Centro de costo: obligatorio, del centro destino.
/// - Responsable: opcional; un Administrador, Gerente o Auxiliar que trabaje en el centro destino.
/// La API valida todo otra vez con las mismas reglas del Servicio de la web.
class FormularioOTPantalla extends StatefulWidget {
  /// Ficha de la OT (respuesta de otControlador?tipo=detalle) al modificar; null = nueva.
  final Map<String, dynamic>? ot;

  const FormularioOTPantalla({super.key, this.ot});

  @override
  State<FormularioOTPantalla> createState() => _FormularioOTPantallaState();
}

class _FormularioOTPantallaState extends State<FormularioOTPantalla> {
  final GlobalKey<FormState> _formulario = GlobalKey<FormState>();
  late final TextEditingController _titulo = TextEditingController(text: tx(widget.ot?['titulo']));
  late final TextEditingController _descripcion = TextEditingController(text: tx(widget.ot?['descripcion']));
  Opcion? _centro;
  Opcion? _centroCosto;
  Opcion? _responsable;
  List<Opcion> _centros = <Opcion>[];
  String _solicitante = '';
  bool _cargando = true;
  String? _error;
  bool _guardando = false;

  int get _id => ent(widget.ot?['id']);
  bool get _nueva => _id == 0;

  /// Con insumos entregados ya no se puede cambiar el centro destino (regla de la web).
  bool get _centroBloqueado => !_nueva && mapas(widget.ot?['items']).isNotEmpty;

  @override
  void initState() {
    super.initState();
    final Map<String, dynamic>? ot = widget.ot;
    if (ot != null) {
      if (ent(ot['id_centro']) > 0) _centro = Opcion(ent(ot['id_centro']), tx(ot['centro']));
      if (ent(ot['id_centro_costo']) > 0) _centroCosto = Opcion(ent(ot['id_centro_costo']), tx(ot['centro_costo']));
      if (ent(ot['id_responsable']) > 0) _responsable = Opcion(ent(ot['id_responsable']), tx(ot['responsable']));
    }
    _cargarContexto();
  }

  @override
  void dispose() {
    _titulo.dispose();
    _descripcion.dispose();
    super.dispose();
  }

  /// Datos del usuario y centros destino posibles (igual que la pestaña "Datos del usuario" de la web).
  Future<void> _cargarContexto() async {
    setState(() {
      _cargando = true;
      _error = null;
    });
    try {
      final Map<String, dynamic> c = mapa(await Api.ctrl('otControlador.jsp', 'contextoUsuario'));
      if (!mounted) return;
      setState(() {
        _centros = Opcion.lista(c['centros']);
        _solicitante = tx(c['nombre']);
        // OT nueva: por defecto el centro del usuario
        if (_nueva && _centro == null && ent(c['id_centro']) > 0) {
          _centro = Opcion(ent(c['id_centro']), tx(c['centro']));
        }
        _cargando = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _cargando = false;
        _error = e is ApiError ? e.mensaje : 'No se pudieron obtener los datos.';
      });
      if (e is ApiError && e.sesionVencida) manejarError(context, e);
    }
  }

  Future<void> _elegirCentro() async {
    final Opcion? o = await elegirOpcion(context, 'Centro destino', _centros);
    if (o == null || !mounted || o.id == _centro?.id) return;
    setState(() {
      _centro = o;
      _centroCosto = null; // el centro de costo y el responsable dependen del centro destino
      _responsable = null;
    });
  }

  Future<void> _elegirCentroCosto() async {
    if (_centro == null) return;
    try {
      final List<Opcion> lista =
          Opcion.lista(await Api.movilCacheado('centrosCosto', <String, String>{'id_centro': '${_centro!.id}'}));
      if (!mounted) return;
      if (lista.isEmpty) {
        avisar(context, 'El centro ${_centro!.nombre} no tiene centros de costo activos', error: true);
        return;
      }
      final Opcion? o = await elegirOpcion(context, 'Centro de costo', lista);
      if (o != null && mounted) setState(() => _centroCosto = o);
    } catch (e) {
      if (mounted) manejarError(context, e);
    }
  }

  Future<void> _elegirResponsable() async {
    if (_centro == null) return;
    try {
      final List<Opcion> lista = <Opcion>[
        const Opcion(0, '(Sin responsable)'),
        ...mapas(await Api.ctrl('otControlador.jsp', 'listarResponsables', <String, String>{'id_centro': '${_centro!.id}'}))
            .map((Map<String, dynamic> u) => Opcion(ent(u['id']), tx(u['nombre']),
                detalle: '${tx(u['rol'])}${tx(u['centro']).isEmpty ? '' : '  ·  ${tx(u['centro'])}'}')),
      ];
      if (!mounted) return;
      final Opcion? o = await elegirOpcion(context, 'Responsable', lista);
      if (o != null && mounted) setState(() => _responsable = o.id == 0 ? null : o);
    } catch (e) {
      if (mounted) manejarError(context, e);
    }
  }

  Future<void> _guardar() async {
    if (_guardando) return;
    if (!_formulario.currentState!.validate()) return;
    if (_centro == null) {
      avisar(context, 'Seleccione el centro destino (el centro que va a resolver la orden de trabajo)', error: true);
      return;
    }
    if (_centroCosto == null) {
      avisar(context, 'Seleccione el centro de costo', error: true);
      return;
    }
    setState(() => _guardando = true);
    try {
      final Map<String, String> datos = <String, String>{
        if (!_nueva) 'id': '$_id',
        'titulo': _titulo.text.trim(),
        'descripcion': _descripcion.text.trim(),
        'id_centro': '${_centro!.id}',
        'id_centro_costo': '${_centroCosto!.id}',
        'id_responsable': _responsable == null ? '' : '${_responsable!.id}',
      };
      final Map<String, dynamic> r = mapa(await Api.ctrl('otControlador.jsp', _nueva ? 'crear' : 'modificar', datos));
      if (!mounted) return;
      if (_nueva) {
        await mostrarResultado(context, 'Orden de trabajo creada',
            'Se creó la OT ${tx(r['numero_ot'])} (${tx(r['fecha'])}) para el centro ${_centro!.nombre}.');
        if (mounted) Navigator.of(context).pop(ent(r['id_ot']));
      } else {
        avisar(context, 'Orden de trabajo modificada');
        Navigator.of(context).pop(_id);
      }
    } catch (e) {
      if (mounted) manejarError(context, e);
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final String numero = tx(widget.ot?['numero']);
    return PaginaApp(
      titulo: _nueva ? 'Nueva Orden de Trabajo' : 'Modificar $numero',
      migas: <String>['Inicio', 'Órdenes de trabajo', if (_nueva) 'Nueva' else 'Modificar'],
      hijos: <Widget>[
        if (_cargando)
          const Padding(padding: EdgeInsets.all(40), child: Cargando())
        else if (_error != null)
          Aviso(icono: Icons.cloud_off, texto: _error!, alReintentar: _cargarContexto)
        else ...<Widget>[
          Tarjeta(
            acento: Nd.acento,
            child: Form(
              key: _formulario,
              child: CuerpoTarjeta(<Widget>[
                if (_nueva && _solicitante.isNotEmpty) ...<Widget>[
                  DatoFila('Solicitante', _solicitante),
                  const SizedBox(height: 10),
                ],
                CampoFormulario(
                  etiqueta: 'Título',
                  obligatorio: true,
                  child: TextFormField(
                    controller: _titulo,
                    maxLength: 200,
                    textCapitalization: TextCapitalization.sentences,
                    decoration: const InputDecoration(hintText: 'Ej.: Reparación del aire acondicionado', counterText: ''),
                    validator: (String? v) => (v ?? '').trim().isEmpty ? 'Ingrese un título para la orden de trabajo' : null,
                  ),
                ),
                CampoFormulario(
                  etiqueta: 'Descripción',
                  child: TextFormField(
                    controller: _descripcion,
                    maxLength: 2000,
                    minLines: 3,
                    maxLines: 6,
                    textCapitalization: TextCapitalization.sentences,
                    decoration: const InputDecoration(hintText: 'Qué hay que hacer, dónde, observaciones...', counterText: ''),
                  ),
                ),
                CampoFormulario(
                  etiqueta: 'Centro destino',
                  obligatorio: true,
                  child: CampoSeleccion(
                    texto: _centro?.nombre ?? '',
                    icono: Icons.apartment,
                    alTocar: _guardando || _centroBloqueado ? null : _elegirCentro,
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.only(bottom: 14),
                  child: Text(
                    _centroBloqueado
                        ? 'La OT ya tiene insumos entregados: no se puede cambiar el centro destino.'
                        : 'El centro que va a resolver la orden de trabajo.',
                    style: const TextStyle(color: Nd.apagado, fontSize: 12.5),
                  ),
                ),
                CampoFormulario(
                  etiqueta: 'Centro de costo',
                  obligatorio: true,
                  child: CampoSeleccion(
                    texto: _centroCosto?.nombre ?? '',
                    sugerencia: _centro == null ? '-- Elija el centro destino --' : '-- Seleccionar --',
                    icono: Icons.account_tree,
                    alTocar: _centro == null || _guardando ? null : _elegirCentroCosto,
                  ),
                ),
                CampoFormulario(
                  etiqueta: 'Responsable',
                  child: CampoSeleccion(
                    texto: _responsable?.nombre ?? '',
                    sugerencia: _centro == null ? '-- Elija el centro destino --' : '(Sin responsable)',
                    icono: Icons.person_search,
                    alTocar: _centro == null || _guardando ? null : _elegirResponsable,
                  ),
                ),
                BotonPrincipal(
                  texto: _nueva ? 'Crear orden de trabajo' : 'Guardar cambios',
                  icono: _nueva ? Icons.add_task : Icons.save,
                  ocupado: _guardando,
                  alTocar: _guardar,
                ),
              ]),
            ),
          ),
        ],
      ],
    );
  }
}
