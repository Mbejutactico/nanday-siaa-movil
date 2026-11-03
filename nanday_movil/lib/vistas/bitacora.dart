import 'package:flutter/material.dart';

import '../api.dart';
import '../tema.dart';
import 'comunes.dart';

/// SEGURIDAD > Bitácora del sistema (solo el Administrador), igual que la pantalla de la web:
/// resumen del día, filtros, lista de acciones y el detalle con los campos modificados.
/// Usa bitacoraControlador.jsp (tipo=resumen, usuarios, listar, detalle). Es solo de consulta.
///
/// [tabla] e [idRegistro]: muestra el historial de un registro (botón "Ver todo el historial").
/// [accion] y [soloHoy]: filtros iniciales (por ejemplo, los inicios de sesión fallidos de hoy).
class BitacoraPantalla extends StatefulWidget {
  final String accion;
  final bool soloHoy;
  final String tabla;
  final int idRegistro;
  final String titulo;

  const BitacoraPantalla({
    super.key,
    this.accion = '',
    this.soloHoy = false,
    this.tabla = '',
    this.idRegistro = 0,
    this.titulo = '',
  });

  @override
  State<BitacoraPantalla> createState() => _BitacoraPantallaState();
}

/// Módulos y acciones de la bitácora con su nombre para mostrar.
const Map<String, String> modulosBitacora = <String, String>{
  'SEGURIDAD': 'Seguridad',
  'REFERENCIALES': 'Referenciales',
  'COMPRAS': 'Compras',
  'OT': 'Órdenes de trabajo',
  'INVENTARIO': 'Inventario',
  'ACTIVOS': 'Activos fijos',
};

const Map<String, String> accionesBitacora = <String, String>{
  'LOGIN': 'Inicio de sesión',
  'LOGIN_FALLIDO': 'Inicio fallido',
  'LOGOUT': 'Cierre de sesión',
  'ALTA': 'Alta',
  'MODIFICACION': 'Modificación',
  'BAJA': 'Baja',
  'ANULACION': 'Anulación',
  'APROBACION': 'Aprobación',
  'RECHAZO': 'Rechazo',
  'FINALIZACION': 'Finalización',
  'REAPERTURA': 'Reapertura',
  'RECEPCION': 'Recepción',
};

Color colorAccion(String accion) {
  switch (accion) {
    case 'ALTA':
    case 'APROBACION':
    case 'FINALIZACION':
    case 'RECEPCION':
      return Nd.primario;
    case 'MODIFICACION':
    case 'REAPERTURA':
      return Nd.info;
    case 'BAJA':
    case 'ANULACION':
    case 'RECHAZO':
    case 'LOGIN_FALLIDO':
      return Nd.peligro;
    default:
      return Nd.apagado;
  }
}

class _BitacoraPantallaState extends State<BitacoraPantalla> {
  DateTime? _desde;
  DateTime? _hasta;
  Opcion? _usuario;
  String _modulo = '';
  String _accion = '';
  final TextEditingController _texto = TextEditingController();
  int _version = 0; // fuerza la recarga al cambiar filtros

  bool get _historial => widget.tabla.isNotEmpty && widget.idRegistro > 0;

  @override
  void initState() {
    super.initState();
    _accion = widget.accion;
    _valoresIniciales();
  }

  @override
  void dispose() {
    _texto.dispose();
    super.dispose();
  }

  /// Igual que la web: los últimos 7 días (o solo hoy); el historial de un registro, sin límite de fechas.
  void _valoresIniciales() {
    final DateTime hoy = DateTime.now();
    _desde = _historial ? null : (widget.soloHoy ? hoy : hoy.subtract(const Duration(days: 7)));
    _hasta = null;
  }

  String _iso(DateTime? d) => d == null
      ? ''
      : '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  String _dmy(DateTime? d) => d == null ? '' : '${d.day.toString().padLeft(2, '0')}/${d.month.toString().padLeft(2, '0')}/${d.year}';

  void _buscar() => setState(() => _version++);

  void _limpiar() {
    setState(() {
      _valoresIniciales();
      _usuario = null;
      _modulo = '';
      _accion = '';
      _texto.clear();
      _version++;
    });
  }

  Future<void> _elegirFecha(bool desde) async {
    final DateTime ahora = DateTime.now();
    final DateTime? f = await showDatePicker(
      context: context,
      initialDate: (desde ? _desde : _hasta) ?? ahora,
      firstDate: DateTime(2020),
      lastDate: DateTime(ahora.year + 1),
      helpText: desde ? 'Desde (cancelar = sin límite)' : 'Hasta (cancelar = sin límite)',
    );
    if (!mounted) return;
    setState(() {
      if (desde) {
        _desde = f;
      } else {
        _hasta = f;
      }
      _version++;
    });
  }

  Future<void> _elegirUsuario() async {
    try {
      final List<Opcion> lista = <Opcion>[
        const Opcion(0, 'Todos'),
        ...Opcion.lista(await Api.ctrl('bitacoraControlador.jsp', 'usuarios')),
      ];
      if (!mounted) return;
      final Opcion? o = await elegirOpcion(context, 'Usuario', lista);
      if (o != null) {
        setState(() {
          _usuario = o.id == 0 ? null : o;
          _version++;
        });
      }
    } catch (e) {
      if (mounted) manejarError(context, e);
    }
  }

  Future<void> _elegirDeLista(String titulo, Map<String, String> valores, void Function(String) asignar) async {
    final List<String> claves = valores.keys.toList();
    final List<Opcion> lista = <Opcion>[
      Opcion(0, titulo == 'Módulo' ? 'Todos' : 'Todas'),
      for (int i = 0; i < claves.length; i++) Opcion(i + 1, valores[claves[i]]!),
    ];
    final Opcion? o = await elegirOpcion(context, titulo, lista);
    if (o == null || !mounted) return;
    setState(() {
      asignar(o.id == 0 ? '' : claves[o.id - 1]);
      _version++;
    });
  }

  Map<String, String> get _filtros => <String, String>{
        'desde': _iso(_desde),
        'hasta': _iso(_hasta),
        'id_usuario': '${_usuario?.id ?? 0}',
        'modulo': _modulo,
        'accion': _accion,
        'texto': _texto.text.trim(),
        if (_historial) 'tabla': widget.tabla,
        if (_historial) 'id_registro': '${widget.idRegistro}',
      };

  Future<void> _verDetalle(int id) async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (BuildContext hoja) => _DetalleRegistro(
        id: id,
        alVerHistorial: (String tabla, int idRegistro, String titulo) {
          Navigator.of(hoja).pop();
          Navigator.of(context).push(MaterialPageRoute<void>(
            builder: (BuildContext c) => BitacoraPantalla(tabla: tabla, idRegistro: idRegistro, titulo: titulo),
          ));
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return PaginaApp(
      titulo: _historial ? 'Historial del registro' : 'Bitácora del sistema',
      migas: const <String>['Inicio', 'Seguridad', 'Bitácora'],
      alRefrescar: () async => _buscar(),
      hijos: <Widget>[
        if (_historial)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 14),
            child: Alerta(
              titulo: widget.titulo.isEmpty ? 'Historial' : widget.titulo,
              texto: 'Todas las acciones registradas sobre este registro (${widget.tabla} #${widget.idRegistro}).',
            ),
          )
        else ...<Widget>[
          Cargable<Map<String, dynamic>>(
            key: ValueKey<String>('resumen$_version'),
            cargar: () async => mapa(await Api.ctrl('bitacoraControlador.jsp', 'resumen')),
            constructor: (BuildContext c, Map<String, dynamic> r, Future<void> Function() recargar) => _Resumen(r),
          ),
          espacio,
        ],
        Tarjeta(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              const CabeceraTarjeta(titulo: 'Filtros', icono: Icons.filter_alt),
              CuerpoTarjeta(<Widget>[
                Row(
                  children: <Widget>[
                    Expanded(
                      child: CampoFormulario(
                        etiqueta: 'Desde',
                        child: CampoSeleccion(texto: _dmy(_desde), sugerencia: 'Sin límite', icono: Icons.event, alTocar: () => _elegirFecha(true)),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: CampoFormulario(
                        etiqueta: 'Hasta',
                        child: CampoSeleccion(texto: _dmy(_hasta), sugerencia: 'Sin límite', icono: Icons.event, alTocar: () => _elegirFecha(false)),
                      ),
                    ),
                  ],
                ),
                CampoFormulario(
                  etiqueta: 'Usuario',
                  child: CampoSeleccion(texto: _usuario?.nombre ?? 'Todos', alTocar: _elegirUsuario),
                ),
                Row(
                  children: <Widget>[
                    Expanded(
                      child: CampoFormulario(
                        etiqueta: 'Módulo',
                        child: CampoSeleccion(
                          texto: modulosBitacora[_modulo] ?? 'Todos',
                          alTocar: () => _elegirDeLista('Módulo', modulosBitacora, (String v) => _modulo = v),
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: CampoFormulario(
                        etiqueta: 'Acción',
                        child: CampoSeleccion(
                          texto: accionesBitacora[_accion] ?? 'Todas',
                          alTocar: () => _elegirDeLista('Acción', accionesBitacora, (String v) => _accion = v),
                        ),
                      ),
                    ),
                  ],
                ),
                CampoFormulario(
                  etiqueta: 'Buscar',
                  child: TextField(
                    controller: _texto,
                    textInputAction: TextInputAction.search,
                    onSubmitted: (_) => _buscar(),
                    decoration: const InputDecoration(hintText: 'Descripción, email, IP o valor modificado', prefixIcon: Icon(Icons.search)),
                  ),
                ),
                Row(
                  children: <Widget>[
                    Expanded(
                      child: FilledButton.icon(onPressed: _buscar, icon: const Icon(Icons.search), label: const Text('Buscar')),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: OutlinedButton.icon(onPressed: _limpiar, icon: const Icon(Icons.cleaning_services), label: const Text('Limpiar')),
                    ),
                  ],
                ),
              ]),
            ],
          ),
        ),
        espacio,
      ],
      slivers: <Widget>[
        Cargable<Map<String, dynamic>>.sliver(
          key: ValueKey<int>(_version),
          cargar: () async => mapa(await Api.ctrl('bitacoraControlador.jsp', 'listar', _filtros)),
          constructor: (BuildContext context, Map<String, dynamic> d, Future<void> Function() recargar) {
            final List<Map<String, dynamic>> registros = mapas(d['registros']);
            if (registros.isEmpty) {
              return caja(const Aviso(icono: Icons.history, texto: 'No hay registros con esos filtros.'));
            }
            final int limite = ent(d['limite']);
            return SliverTarjetaLista(
              cabecera: CabeceraTarjeta(
                titulo: '${guaranies(registros.length)} registro${registros.length == 1 ? '' : 's'}'
                    '${limite > 0 && registros.length >= limite ? ' (los más recientes; acote con los filtros)' : ''}',
                icono: Icons.history,
              ),
              cantidad: registros.length,
              fila: (BuildContext c, int i) => _FilaBitacora(registros[i], alTocar: () => _verDetalle(ent(registros[i]['id']))),
              pie: const Padding(
                padding: EdgeInsets.all(12),
                child: Text('Toque un registro para ver el detalle. Solo consulta: la bitácora no se puede modificar ni borrar.',
                    style: TextStyle(color: Nd.apagado, fontSize: 12)),
              ),
            );
          },
        ),
      ],
    );
  }
}

/// Resumen de hoy: acciones, inicios de sesión, fallidos, altas y modificaciones, bajas y anulaciones.
class _Resumen extends StatelessWidget {
  final Map<String, dynamic> r;

  const _Resumen(this.r);

  @override
  Widget build(BuildContext context) {
    // Tarjeta pequeña con una franja de color a la izquierda (como las de la web)
    Widget cifra(String etiqueta, dynamic valor, Color color, IconData icono) => Container(
          width: 150,
          clipBehavior: Clip.antiAlias,
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: Nd.borde),
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              Container(width: 4, color: color),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(10, 8, 10, 8),
                  child: Row(
                    children: <Widget>[
                      Expanded(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: <Widget>[
                            Text(etiqueta.toUpperCase(),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(fontSize: 10.5, fontWeight: FontWeight.w600, color: Nd.apagado)),
                            Text(guaranies(valor), style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w700, color: Nd.titulo)),
                          ],
                        ),
                      ),
                      Icon(icono, color: color, size: 20),
                    ],
                  ),
                ),
              ),
            ],
          ),
        );
    return SizedBox(
      height: 72,
      child: ListView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        children: <Widget>[
          cifra('Acciones de hoy', r['TOTAL'], Nd.info, Icons.list),
          const SizedBox(width: 10),
          cifra('Inicios de sesión', r['LOGIN'], Nd.primario, Icons.login),
          const SizedBox(width: 10),
          cifra('Inicios fallidos', r['LOGIN_FALLIDO'], Nd.peligro, Icons.lock_person),
          const SizedBox(width: 10),
          cifra('Altas y modif.', r['CAMBIOS'], Nd.profundo, Icons.edit_note),
          const SizedBox(width: 10),
          cifra('Bajas y anulac.', r['BAJAS'], Nd.acento, Icons.block),
        ],
      ),
    );
  }
}

class _FilaBitacora extends StatelessWidget {
  final Map<String, dynamic> b;
  final VoidCallback alTocar;

  const _FilaBitacora(this.b, {required this.alTocar});

  @override
  Widget build(BuildContext context) {
    final String accion = tx(b['accion']);
    final int cambios = ent(b['cambios']);
    return InkWell(
      onTap: alTocar,
      child: Container(
        padding: const EdgeInsets.fromLTRB(16, 10, 12, 10),
        decoration: const BoxDecoration(border: Border(bottom: BorderSide(color: Nd.borde))),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Row(
                    children: <Widget>[
                      Insignia(texto: accionesBitacora[accion] ?? legible(accion), color: colorAccion(accion)),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(modulosBitacora[tx(b['modulo'])] ?? legible(tx(b['modulo'])),
                            style: const TextStyle(color: Nd.apagado, fontSize: 12.5)),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Text(tx(b['descripcion']), style: const TextStyle(fontWeight: FontWeight.w600, color: Nd.titulo)),
                  Text('${tx(b['fecha'])}  ·  ${tx(b['usuario'])}  ·  ${tx(b['ip'])}',
                      style: const TextStyle(color: Nd.apagado, fontSize: 12.5)),
                  if (cambios > 0)
                    Text('$cambios campo${cambios == 1 ? '' : 's'} modificado${cambios == 1 ? '' : 's'}',
                        style: const TextStyle(color: Nd.info, fontSize: 12.5)),
                ],
              ),
            ),
            const Icon(Icons.chevron_right, color: Nd.apagado),
          ],
        ),
      ),
    );
  }
}

/// Detalle de un registro: datos, campos modificados (valor anterior y nuevo) y acceso al historial.
class _DetalleRegistro extends StatelessWidget {
  final int id;
  final void Function(String tabla, int idRegistro, String titulo) alVerHistorial;

  const _DetalleRegistro({required this.id, required this.alVerHistorial});

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: ConstrainedBox(
        constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.85),
        child: Cargable<Map<String, dynamic>>(
          cargar: () async => mapa(await Api.ctrl('bitacoraControlador.jsp', 'detalle', <String, String>{'id': '$id'})),
          constructor: (BuildContext c, Map<String, dynamic> d, Future<void> Function() recargar) {
            final String accion = tx(d['accion']);
            final List<Map<String, dynamic>> cambios = mapas(d['cambios']);
            final String tabla = tx(d['tabla']);
            final int idRegistro = ent(d['id_registro']);
            return ListView(
              shrinkWrap: true,
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
              children: <Widget>[
                Row(
                  children: <Widget>[
                    Insignia(texto: accionesBitacora[accion] ?? legible(accion), color: colorAccion(accion)),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(tx(d['descripcion']), style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700, color: Nd.titulo)),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                DatoFila('Usuario', '${tx(d['usuario'])}${tx(d['email']).isEmpty ? '' : ' (${tx(d['email'])})'}'),
                DatoFila('Fecha', tx(d['fecha'])),
                DatoFila('IP', tx(d['ip'])),
                DatoFila('Módulo', modulosBitacora[tx(d['modulo'])] ?? tx(d['modulo'])),
                DatoFila('Registro', tabla.isEmpty ? '' : '$tabla #$idRegistro'),
                if (cambios.isNotEmpty) ...<Widget>[
                  const SizedBox(height: 12),
                  const Text('Campos modificados', style: TextStyle(fontWeight: FontWeight.w700, color: Nd.titulo)),
                  const SizedBox(height: 6),
                  for (final Map<String, dynamic> x in cambios)
                    Container(
                      margin: const EdgeInsets.only(bottom: 6),
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(border: Border.all(color: Nd.borde), borderRadius: BorderRadius.circular(8)),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: <Widget>[
                          Text(tx(x['campo']), style: const TextStyle(fontWeight: FontWeight.w600, color: Nd.pizarra)),
                          const SizedBox(height: 2),
                          Text('Antes: ${tx(x['anterior']).isEmpty ? '(vacío)' : tx(x['anterior'])}',
                              style: const TextStyle(color: Color(0xFF9B1C1C))),
                          Text('Ahora: ${tx(x['nuevo']).isEmpty ? '(vacío)' : tx(x['nuevo'])}',
                              style: const TextStyle(color: Nd.profundo)),
                        ],
                      ),
                    ),
                ],
                if (tabla.isNotEmpty && idRegistro > 0) ...<Widget>[
                  const SizedBox(height: 12),
                  OutlinedButton.icon(
                    onPressed: () => alVerHistorial(tabla, idRegistro, tx(d['descripcion'])),
                    icon: const Icon(Icons.history),
                    label: const Text('Ver todo el historial de este registro'),
                  ),
                ],
              ],
            );
          },
        ),
      ),
    );
  }
}
