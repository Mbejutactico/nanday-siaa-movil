import 'package:flutter/material.dart';

import '../../api.dart';
import '../../sesion.dart';
import '../../tema.dart';
import '../comunes.dart';
import '../listado.dart';

/// Formularios de los referenciales de administración, con los mismos campos y reglas que los
/// modales de la web (centrosvista.jsp, centro_costovista.jsp, almacenesvista.jsp,
/// unidad_medidavista.jsp, rolesvista.jsp, usuariosvista.jsp). La API valida otra vez con las
/// mismas reglas del Servicio de la web; aquí se validan los obligatorios para avisar antes.

// =====================================================================
//  Base común
// =====================================================================

/// Estado base: formulario, "guardando" y el envío a la API (tipo=guardar...).
abstract class _BaseFormularioState<T extends StatefulWidget> extends State<T> {
  final GlobalKey<FormState> formulario = GlobalKey<FormState>();
  bool guardando = false;

  /// Datos a enviar (sin el id: lo agrega [guardar]).
  Map<String, String> datos();

  /// Validaciones extra antes de enviar (combos). Devuelve el mensaje de error o null.
  String? validarExtra() => null;

  Future<void> guardar(String tipo, int id) async {
    if (guardando) return;
    if (!formulario.currentState!.validate()) return;
    final String? error = validarExtra();
    if (error != null) {
      avisar(context, error, error: true);
      return;
    }
    setState(() => guardando = true);
    try {
      final String mensaje = await Api.html('movil', tipo, <String, String>{
        if (id > 0) 'id': '$id',
        ...datos(),
      });
      if (!mounted) return;
      avisar(context, mensaje);
      Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) manejarError(context, e);
    } finally {
      if (mounted) setState(() => guardando = false);
    }
  }

  /// Pantalla con el aspecto del modal de la web: tarjeta, campos y pie Cerrar / Guardar.
  Widget pagina({
    required String titulo,
    required String seccion,
    required int id,
    required IconData icono,
    required String tipoGuardar,
    required List<Widget> campos,
  }) {
    return PaginaFormulario(
      titulo: titulo,
      migas: <String>['Inicio', 'Referenciales', seccion, if (id > 0) 'N° $id' else 'Nuevo'],
      icono: icono,
      cuerpo: Form(
        key: formulario,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 18, 16, 2),
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: campos),
            ),
            PieFormulario(
              guardando: guardando,
              textoGuardar: id > 0 ? 'Guardar cambios' : 'Guardar',
              alGuardar: () => guardar(tipoGuardar, id),
            ),
          ],
        ),
      ),
    );
  }
}

/// Campo de texto con etiqueta arriba (como los .form-group de la web).
Widget campoTexto(
  String etiqueta,
  TextEditingController controlador, {
  bool obligatorio = false,
  String? mensajeObligatorio,
  int maximo = 200,
  TextInputType? teclado,
  int lineas = 1,
  bool oculto = false,
  String? sugerencia,
  TextCapitalization mayusculas = TextCapitalization.sentences,
  String? Function(String?)? validador,
}) {
  return CampoFormulario(
    etiqueta: etiqueta,
    obligatorio: obligatorio,
    child: TextFormField(
      controller: controlador,
      maxLength: maximo,
      keyboardType: teclado,
      obscureText: oculto,
      minLines: lineas,
      maxLines: oculto ? 1 : lineas,
      textCapitalization: mayusculas,
      decoration: InputDecoration(counterText: '', hintText: sugerencia),
      validator: (String? v) {
        if (obligatorio && (v ?? '').trim().isEmpty) return mensajeObligatorio ?? 'Dato obligatorio';
        return validador?.call(v);
      },
    ),
  );
}

/// Elige un centro activo (todos los centros, como los combos de la web).
Future<Opcion?> _elegirCentro(BuildContext context, {String titulo = 'Centro', bool conNinguno = false}) async {
  try {
    final List<Opcion> lista = <Opcion>[
      if (conNinguno) const Opcion(0, '(Sin centro principal)'),
      ...Opcion.lista(await Api.movilCacheado('centros', <String, String>{'todos': 'true'})),
    ];
    if (!context.mounted) return null;
    return await elegirOpcion(context, titulo, lista);
  } catch (e) {
    if (context.mounted) manejarError(context, e);
    return null;
  }
}

/// Opción inicial de un combo a partir del registro (id + nombre que trae la lista).
Opcion? _opcionInicial(Map<String, dynamic>? r, String campoId, String campoNombre) {
  if (r == null) return null;
  final int id = ent(r[campoId]);
  return id > 0 ? Opcion(id, tx(r[campoNombre])) : null;
}

// =====================================================================
//  Centros (centrosvista.jsp)
// =====================================================================
class FormularioCentro extends StatefulWidget {
  final Map<String, dynamic>? registro;

  const FormularioCentro({super.key, this.registro});

  @override
  State<FormularioCentro> createState() => _FormularioCentroState();
}

class _FormularioCentroState extends _BaseFormularioState<FormularioCentro> {
  late final TextEditingController _codigo = TextEditingController(text: tx(widget.registro?['codigo']));
  late final TextEditingController _nombre = TextEditingController(text: tx(widget.registro?['nombre']));
  late final TextEditingController _direccion = TextEditingController(text: tx(widget.registro?['direccion']));
  late bool _activo = widget.registro?['activo'] != false;

  int get _id => ent(widget.registro?['id']);

  @override
  void dispose() {
    _codigo.dispose();
    _nombre.dispose();
    _direccion.dispose();
    super.dispose();
  }

  @override
  Map<String, String> datos() => <String, String>{
        'codigo': _codigo.text.trim(),
        'nombre': _nombre.text.trim(),
        'direccion': _direccion.text.trim(),
        'activo': '$_activo',
      };

  @override
  Widget build(BuildContext context) {
    return pagina(
      titulo: _id > 0 ? 'Editar Centro' : 'Nuevo Centro',
      seccion: 'Centros',
      id: _id,
      icono: Icons.apartment,
      tipoGuardar: 'guardarCentro',
      campos: <Widget>[
        campoTexto('Código', _codigo,
            obligatorio: true, mensajeObligatorio: 'Ingrese el código del centro.', maximo: 20, mayusculas: TextCapitalization.characters),
        campoTexto('Nombre', _nombre, obligatorio: true, mensajeObligatorio: 'Ingrese el nombre del centro.', maximo: 100),
        campoTexto('Dirección', _direccion, maximo: 200),
        CampoFormulario(etiqueta: 'Activo', child: SelectorActivo(valor: _activo, alCambiar: (bool v) => setState(() => _activo = v))),
      ],
    );
  }
}

// =====================================================================
//  Centros de costo (centro_costovista.jsp)
// =====================================================================
class FormularioCentroCosto extends StatefulWidget {
  final Map<String, dynamic>? registro;

  const FormularioCentroCosto({super.key, this.registro});

  @override
  State<FormularioCentroCosto> createState() => _FormularioCentroCostoState();
}

class _FormularioCentroCostoState extends _BaseFormularioState<FormularioCentroCosto> {
  late final TextEditingController _codigo = TextEditingController(text: tx(widget.registro?['codigo']));
  late final TextEditingController _descripcion = TextEditingController(text: tx(widget.registro?['descripcion']));
  late Opcion? _centro = _opcionInicial(widget.registro, 'id_centro', 'centro');
  late bool _activo = widget.registro?['activo'] != false;

  int get _id => ent(widget.registro?['id']);

  @override
  void dispose() {
    _codigo.dispose();
    _descripcion.dispose();
    super.dispose();
  }

  @override
  String? validarExtra() => _centro == null ? 'Seleccione el centro al que pertenece el centro de costo.' : null;

  @override
  Map<String, String> datos() => <String, String>{
        'codigo': _codigo.text.trim(),
        'descripcion': _descripcion.text.trim(),
        'id_centro': '${_centro?.id ?? 0}',
        'activo': '$_activo',
      };

  @override
  Widget build(BuildContext context) {
    return pagina(
      titulo: _id > 0 ? 'Editar Centro de Costo' : 'Nuevo Centro de Costo',
      seccion: 'Centros de costo',
      id: _id,
      icono: Icons.account_tree,
      tipoGuardar: 'guardarCentroCosto',
      campos: <Widget>[
        campoTexto('Código', _codigo,
            obligatorio: true, mensajeObligatorio: 'Ingrese el código del centro de costo.', maximo: 20, mayusculas: TextCapitalization.characters),
        campoTexto('Descripción', _descripcion, maximo: 200),
        CampoFormulario(
          etiqueta: 'Centro',
          obligatorio: true,
          child: CampoSeleccion(
            texto: _centro?.nombre ?? '',
            icono: Icons.apartment,
            alTocar: () async {
              final Opcion? o = await _elegirCentro(context);
              if (o != null && mounted) setState(() => _centro = o);
            },
          ),
        ),
        CampoFormulario(etiqueta: 'Activo', child: SelectorActivo(valor: _activo, alCambiar: (bool v) => setState(() => _activo = v))),
      ],
    );
  }
}

// =====================================================================
//  Almacenes (almacenesvista.jsp)
// =====================================================================
class FormularioAlmacen extends StatefulWidget {
  final Map<String, dynamic>? registro;

  const FormularioAlmacen({super.key, this.registro});

  @override
  State<FormularioAlmacen> createState() => _FormularioAlmacenState();
}

class _FormularioAlmacenState extends _BaseFormularioState<FormularioAlmacen> {
  late final TextEditingController _nombre = TextEditingController(text: tx(widget.registro?['nombre']));
  late final TextEditingController _descripcion = TextEditingController(text: tx(widget.registro?['descripcion']));
  late final TextEditingController _ubicacion = TextEditingController(text: tx(widget.registro?['ubicacion']));
  late Opcion? _centro = _opcionInicial(widget.registro, 'id_centro', 'centro');
  late bool _activo = widget.registro?['activo'] != false;

  int get _id => ent(widget.registro?['id']);

  @override
  void dispose() {
    _nombre.dispose();
    _descripcion.dispose();
    _ubicacion.dispose();
    super.dispose();
  }

  @override
  String? validarExtra() => _centro == null ? 'Seleccione el centro al que pertenece el almacén.' : null;

  @override
  Map<String, String> datos() => <String, String>{
        'nombre': _nombre.text.trim(),
        'descripcion': _descripcion.text.trim(),
        'ubicacion': _ubicacion.text.trim(),
        'id_centro': '${_centro?.id ?? 0}',
        'activo': '$_activo',
      };

  @override
  Widget build(BuildContext context) {
    return pagina(
      titulo: _id > 0 ? 'Editar Almacén' : 'Nuevo Almacén',
      seccion: 'Almacenes',
      id: _id,
      icono: Icons.warehouse,
      tipoGuardar: 'guardarAlmacen',
      campos: <Widget>[
        campoTexto('Nombre', _nombre, obligatorio: true, mensajeObligatorio: 'Ingrese el nombre del almacén.', maximo: 100),
        campoTexto('Descripción', _descripcion, maximo: 200),
        campoTexto('Ubicación', _ubicacion, maximo: 200),
        CampoFormulario(
          etiqueta: 'Centro',
          obligatorio: true,
          child: CampoSeleccion(
            texto: _centro?.nombre ?? '',
            icono: Icons.apartment,
            alTocar: () async {
              final Opcion? o = await _elegirCentro(context);
              if (o != null && mounted) setState(() => _centro = o);
            },
          ),
        ),
        CampoFormulario(etiqueta: 'Activo', child: SelectorActivo(valor: _activo, alCambiar: (bool v) => setState(() => _activo = v))),
      ],
    );
  }
}

// =====================================================================
//  Unidades de medida (unidad_medidavista.jsp)
// =====================================================================
class FormularioUnidad extends StatefulWidget {
  final Map<String, dynamic>? registro;

  const FormularioUnidad({super.key, this.registro});

  @override
  State<FormularioUnidad> createState() => _FormularioUnidadState();
}

class _FormularioUnidadState extends _BaseFormularioState<FormularioUnidad> {
  late final TextEditingController _nombre = TextEditingController(text: tx(widget.registro?['nombre']));
  late final TextEditingController _abreviatura = TextEditingController(text: tx(widget.registro?['abreviatura']));
  late bool _activo = widget.registro?['activo'] != false;

  int get _id => ent(widget.registro?['id']);

  @override
  void dispose() {
    _nombre.dispose();
    _abreviatura.dispose();
    super.dispose();
  }

  @override
  Map<String, String> datos() => <String, String>{
        'nombre': _nombre.text.trim(),
        'abreviatura': _abreviatura.text.trim(),
        'activo': '$_activo',
      };

  @override
  Widget build(BuildContext context) {
    return pagina(
      titulo: _id > 0 ? 'Editar Unidad de Medida' : 'Nueva Unidad de Medida',
      seccion: 'Unidades de medida',
      id: _id,
      icono: Icons.straighten,
      tipoGuardar: 'guardarUnidad',
      campos: <Widget>[
        campoTexto('Nombre', _nombre, obligatorio: true, mensajeObligatorio: 'Ingrese el nombre de la unidad de medida.', maximo: 50),
        campoTexto('Abreviatura', _abreviatura, maximo: 10, mayusculas: TextCapitalization.none, sugerencia: 'Ej.: kg, l, u'),
        CampoFormulario(etiqueta: 'Activo', child: SelectorActivo(valor: _activo, alCambiar: (bool v) => setState(() => _activo = v))),
      ],
    );
  }
}

// =====================================================================
//  Roles (rolesvista.jsp)
// =====================================================================
class FormularioRol extends StatefulWidget {
  final Map<String, dynamic>? registro;

  const FormularioRol({super.key, this.registro});

  @override
  State<FormularioRol> createState() => _FormularioRolState();
}

class _FormularioRolState extends _BaseFormularioState<FormularioRol> {
  late final TextEditingController _nombre = TextEditingController(text: tx(widget.registro?['nombre']));
  late final TextEditingController _descripcion = TextEditingController(text: tx(widget.registro?['descripcion']));
  late String _codigo = tx(widget.registro?['codigo']);
  late bool _activo = widget.registro?['activo'] != false;
  List<Map<String, dynamic>>? _tipos;
  String? _errorTipos;

  int get _id => ent(widget.registro?['id']);

  @override
  void initState() {
    super.initState();
    _cargarTipos();
  }

  Future<void> _cargarTipos() async {
    try {
      final List<Map<String, dynamic>> t = mapas(await Api.movilCacheado('tiposRol'));
      if (mounted) setState(() => _tipos = t);
    } catch (e) {
      if (mounted) setState(() => _errorTipos = e is ApiError ? e.mensaje : 'No se pudieron obtener los tipos de rol.');
    }
  }

  @override
  void dispose() {
    _nombre.dispose();
    _descripcion.dispose();
    super.dispose();
  }

  @override
  String? validarExtra() => _codigo.isEmpty ? 'Seleccione el tipo de permisos del rol.' : null;

  @override
  Map<String, String> datos() => <String, String>{
        'nombre': _nombre.text.trim(),
        'descripcion': _descripcion.text.trim(),
        'codigo': _codigo,
        'activo': '$_activo',
      };

  @override
  Widget build(BuildContext context) {
    final Map<String, dynamic>? tipo =
        (_tipos ?? <Map<String, dynamic>>[]).where((Map<String, dynamic> t) => tx(t['codigo']) == _codigo).firstOrNull;
    return pagina(
      titulo: _id > 0 ? 'Editar Rol' : 'Nuevo Rol',
      seccion: 'Roles',
      id: _id,
      icono: Icons.admin_panel_settings,
      tipoGuardar: 'guardarRol',
      campos: <Widget>[
        campoTexto('Nombre', _nombre, obligatorio: true, mensajeObligatorio: 'El nombre del rol es obligatorio.', maximo: 50),
        CampoFormulario(
          etiqueta: 'Tipo de permisos',
          obligatorio: true,
          child: _tipos == null
              ? (_errorTipos != null
                  ? Text(_errorTipos!, style: const TextStyle(color: Nd.peligro))
                  : const LinearProgressIndicator())
              : DropdownButtonFormField<String>(
                  initialValue: _codigo.isEmpty || tipo == null ? null : _codigo,
                  isExpanded: true,
                  hint: const Text('-- Seleccionar --'),
                  items: <DropdownMenuItem<String>>[
                    for (final Map<String, dynamic> t in _tipos!)
                      DropdownMenuItem<String>(value: tx(t['codigo']), child: Text(tx(t['nombre']))),
                  ],
                  onChanged: (String? v) => setState(() => _codigo = v ?? ''),
                ),
        ),
        if (tipo != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 16),
            child: Alerta(texto: tx(tipo['descripcion'])),
          ),
        campoTexto('Descripción', _descripcion, maximo: 300, lineas: 2),
        CampoFormulario(etiqueta: 'Activo', child: SelectorActivo(valor: _activo, alCambiar: (bool v) => setState(() => _activo = v))),
        if (_id > 0)
          const Padding(
            padding: EdgeInsets.only(bottom: 12),
            child: Text(
              'Los usuarios con este rol verán el cambio en menos de un minuto.',
              style: TextStyle(color: Nd.apagado, fontSize: 12.5),
            ),
          ),
      ],
    );
  }
}

// =====================================================================
//  Usuarios (usuariosvista.jsp)
// =====================================================================
class FormularioUsuario extends StatefulWidget {
  final Map<String, dynamic>? registro;

  const FormularioUsuario({super.key, this.registro});

  @override
  State<FormularioUsuario> createState() => _FormularioUsuarioState();
}

class _FormularioUsuarioState extends _BaseFormularioState<FormularioUsuario> {
  late final TextEditingController _nombre = TextEditingController(text: tx(widget.registro?['nombre']));
  late final TextEditingController _apellido = TextEditingController(text: tx(widget.registro?['apellido']));
  late final TextEditingController _email = TextEditingController(text: tx(widget.registro?['email']));
  final TextEditingController _password = TextEditingController();
  late Opcion? _rol = _opcionInicial(widget.registro, 'id_rol', 'rol');
  late String _codigoRol = tx(widget.registro?['rol_codigo']);
  late Opcion? _centro = _opcionInicial(widget.registro, 'centro_asignado', 'centro');
  late bool _activo = widget.registro?['activo'] != false;
  late int _centrosAdicionales = ent(widget.registro?['centros_adicionales']);
  bool _verClave = false;

  int get _id => ent(widget.registro?['id']);
  bool get _necesitaCentro => _codigoRol == 'GERENTE' || _codigoRol == 'AUXILIAR';

  @override
  void dispose() {
    _nombre.dispose();
    _apellido.dispose();
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _elegirRol() async {
    try {
      final List<Opcion> roles = mapas(await Api.movilCacheado('rolesActivos'))
          .map((Map<String, dynamic> r) => Opcion(ent(r['id']), tx(r['nombre']), datos: r))
          .toList();
      if (!mounted) return;
      final Opcion? o = await elegirOpcion(context, 'Rol', roles);
      if (o != null && mounted) {
        setState(() {
          _rol = o;
          _codigoRol = tx(o.datos['codigo']);
        });
      }
    } catch (e) {
      if (mounted) manejarError(context, e);
    }
  }

  @override
  String? validarExtra() {
    if (_rol == null) return 'Seleccione el rol del usuario.';
    if (_necesitaCentro && _centro == null) return 'El rol ${_rol!.nombre} trabaja sobre sus centros: asigne un centro principal';
    if (_id > 0 && _id == Sesion.id && !_activo) return 'No puede desactivar el usuario con el que inició sesión';
    return null;
  }

  @override
  Map<String, String> datos() => <String, String>{
        'nombre': _nombre.text.trim(),
        'apellido': _apellido.text.trim(),
        'email': _email.text.trim(),
        'id_rol': '${_rol?.id ?? 0}',
        'centro_asignado': _centro == null ? '' : '${_centro!.id}',
        'password': _password.text,
        'activo': '$_activo',
      };

  Future<void> _abrirCentrosAdicionales() async {
    final int? cantidad = await Navigator.of(context).push<int>(MaterialPageRoute<int>(
      builder: (BuildContext c) => CentrosUsuarioPantalla(idUsuario: _id, nombre: '${_nombre.text.trim()} ${_apellido.text.trim()}'.trim()),
    ));
    if (cantidad != null && mounted) setState(() => _centrosAdicionales = cantidad);
  }

  @override
  Widget build(BuildContext context) {
    return pagina(
      titulo: _id > 0 ? 'Editar Usuario' : 'Nuevo Usuario',
      seccion: 'Usuarios',
      id: _id,
      icono: Icons.manage_accounts,
      tipoGuardar: 'guardarUsuario',
      campos: <Widget>[
        campoTexto('Nombre', _nombre, obligatorio: true, mensajeObligatorio: 'Ingrese el nombre del usuario.', maximo: 100,
            mayusculas: TextCapitalization.words),
        campoTexto('Apellido', _apellido, obligatorio: true, mensajeObligatorio: 'Ingrese el apellido del usuario.', maximo: 100,
            mayusculas: TextCapitalization.words),
        campoTexto('Email', _email,
            obligatorio: true,
            mensajeObligatorio: 'Ingrese el email del usuario.',
            maximo: 150,
            teclado: TextInputType.emailAddress,
            mayusculas: TextCapitalization.none,
            validador: (String? v) => (v ?? '').contains('@') ? null : 'El email no es válido'),
        CampoFormulario(
          etiqueta: 'Rol',
          obligatorio: true,
          child: CampoSeleccion(texto: _rol?.nombre ?? '', icono: Icons.admin_panel_settings, alTocar: _elegirRol),
        ),
        CampoFormulario(
          etiqueta: 'Centro principal',
          obligatorio: _necesitaCentro,
          child: CampoSeleccion(
            texto: _centro?.nombre ?? '',
            sugerencia: _necesitaCentro ? '-- Seleccionar --' : '(Sin centro principal)',
            icono: Icons.apartment,
            alTocar: () async {
              final Opcion? o = await _elegirCentro(context, titulo: 'Centro principal', conNinguno: true);
              if (o != null && mounted) setState(() => _centro = o.id == 0 ? null : o);
            },
          ),
        ),
        if (_necesitaCentro)
          const Padding(
            padding: EdgeInsets.only(bottom: 12),
            child: Text('Gerente y Auxiliar trabajan sobre sus centros: el centro principal es obligatorio.',
                style: TextStyle(color: Nd.apagado, fontSize: 12.5)),
          ),
        CampoFormulario(
          etiqueta: _id > 0 ? 'Nueva contraseña' : 'Contraseña',
          obligatorio: _id == 0,
          child: TextFormField(
            controller: _password,
            obscureText: !_verClave,
            decoration: InputDecoration(
              hintText: _id > 0 ? 'Dejar vacío para no cambiarla' : 'Al menos 6 caracteres',
              suffixIcon: IconButton(
                icon: Icon(_verClave ? Icons.visibility_off : Icons.visibility, size: 20),
                onPressed: () => setState(() => _verClave = !_verClave),
              ),
            ),
            validator: (String? v) {
              final String t = v ?? '';
              if (_id == 0 && t.trim().isEmpty) return 'La contraseña es obligatoria';
              if (t.isNotEmpty && t.length < 6) return 'La contraseña debe tener al menos 6 caracteres';
              return null;
            },
          ),
        ),
        CampoFormulario(etiqueta: 'Activo', child: SelectorActivo(valor: _activo, alCambiar: (bool v) => setState(() => _activo = v))),
        if (_id > 0)
          Padding(
            padding: const EdgeInsets.only(bottom: 14),
            child: OutlinedButton.icon(
              onPressed: guardando ? null : _abrirCentrosAdicionales,
              icon: const Icon(Icons.add_business),
              label: Text('Centros adicionales ($_centrosAdicionales)'),
            ),
          ),
      ],
    );
  }
}

// =====================================================================
//  Centros adicionales de un usuario (modal "Centros" de usuariosvista.jsp)
// =====================================================================
class CentrosUsuarioPantalla extends StatefulWidget {
  final int idUsuario;
  final String nombre;

  const CentrosUsuarioPantalla({super.key, required this.idUsuario, required this.nombre});

  @override
  State<CentrosUsuarioPantalla> createState() => _CentrosUsuarioPantallaState();
}

class _CentrosUsuarioPantallaState extends State<CentrosUsuarioPantalla> {
  final GlobalKey<CargableEstado<List<Map<String, dynamic>>>> _carga = GlobalKey<CargableEstado<List<Map<String, dynamic>>>>();
  int _cantidad = 0;
  bool _ocupado = false;

  Future<List<Map<String, dynamic>>> _cargar() async {
    final List<Map<String, dynamic>> l = mapas(await Api.movil('centrosUsuario', <String, String>{'id': '${widget.idUsuario}'}));
    _cantidad = l.length;
    return l;
  }

  Future<void> _agregar() async {
    final Map<String, String>? datos = await showDialog<Map<String, String>>(
      context: context,
      builder: (BuildContext d) => const _DialogoCentroAdicional(),
    );
    if (datos == null || !mounted) return;
    await _operar('agregarCentroUsuario', datos);
  }

  Future<void> _quitar(Map<String, dynamic> c) async {
    if (!await confirmar(context, 'Quitar centro', '¿Quitar el centro ${tx(c['centro'])} de los centros adicionales?',
        aceptar: 'Quitar', color: Nd.peligro)) {
      return;
    }
    await _operar('quitarCentroUsuario', <String, String>{'id_centro': '${ent(c['id_centro'])}'});
  }

  Future<void> _operar(String tipo, Map<String, String> datos) async {
    if (_ocupado) return;
    setState(() => _ocupado = true);
    try {
      final String mensaje = await Api.html('movil', tipo, <String, String>{'id': '${widget.idUsuario}', ...datos});
      if (!mounted) return;
      avisar(context, mensaje);
      await _carga.currentState?.recargar();
    } catch (e) {
      if (mounted) manejarError(context, e);
    } finally {
      if (mounted) setState(() => _ocupado = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope<int>(
      canPop: false,
      onPopInvokedWithResult: (bool hecho, int? r) {
        if (!hecho) Navigator.of(context).pop(_cantidad);
      },
      child: PaginaApp(
        titulo: 'Centros adicionales',
        migas: <String>['Inicio', 'Referenciales', 'Usuarios', widget.nombre],
        alRefrescar: () async => _carga.currentState?.recargar(),
        hijos: <Widget>[
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 16),
            child: Alerta(
              texto: 'Además de su centro principal, el usuario puede trabajar sobre estos centros. '
                  'Sin fecha "hasta", el permiso no vence.',
            ),
          ),
          espacio,
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: BotonPrincipal(texto: 'Agregar centro', icono: Icons.add, ocupado: _ocupado, alTocar: _agregar),
          ),
          espacio,
          Cargable<List<Map<String, dynamic>>>(
            key: _carga,
            cargar: _cargar,
            constructor: (BuildContext context, List<Map<String, dynamic>> lista, Future<void> Function() recargar) {
              if (lista.isEmpty) {
                return const Aviso(icono: Icons.apartment, texto: 'El usuario no tiene centros adicionales.');
              }
              return Tarjeta(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: <Widget>[
                    CabeceraTarjeta(titulo: '${lista.length} centro(s)', icono: Icons.apartment),
                    for (final Map<String, dynamic> c in lista)
                      ListTile(
                        title: Row(children: <Widget>[
                          Flexible(child: Text(tx(c['centro']), style: const TextStyle(fontWeight: FontWeight.w600))),
                          const SizedBox(width: 8),
                          Insignia(texto: c['vigente'] == true ? 'Vigente' : 'No vigente', color: c['vigente'] == true ? Nd.primario : Nd.apagado),
                        ]),
                        subtitle: Text('Desde ${fechaCorta(tx(c['desde']))}'
                            '${tx(c['hasta']).isEmpty ? '  ·  sin vencimiento' : '  ·  hasta ${fechaCorta(tx(c['hasta']))}'}'
                            '${tx(c['observacion']).isEmpty ? '' : '\n${tx(c['observacion'])}'}'),
                        trailing: IconButton(
                          tooltip: 'Quitar',
                          icon: const Icon(Icons.remove_circle_outline, color: Nd.peligro),
                          onPressed: _ocupado ? null : () => _quitar(c),
                        ),
                      ),
                  ],
                ),
              );
            },
          ),
        ],
      ),
    );
  }
}

/// Centro + desde / hasta (opcionales) + observación.
class _DialogoCentroAdicional extends StatefulWidget {
  const _DialogoCentroAdicional();

  @override
  State<_DialogoCentroAdicional> createState() => _DialogoCentroAdicionalState();
}

class _DialogoCentroAdicionalState extends State<_DialogoCentroAdicional> {
  final TextEditingController _observacion = TextEditingController();
  Opcion? _centro;
  DateTime? _desde;
  DateTime? _hasta;

  @override
  void dispose() {
    _observacion.dispose();
    super.dispose();
  }

  String _iso(DateTime? d) => d == null
      ? ''
      : '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  Future<void> _fecha(bool desde) async {
    final DateTime ahora = DateTime.now();
    final DateTime? f = await showDatePicker(
      context: context,
      initialDate: (desde ? _desde : _hasta) ?? ahora,
      firstDate: DateTime(2020),
      lastDate: DateTime(ahora.year + 10),
      helpText: desde ? 'Desde (cancelar = hoy)' : 'Hasta (cancelar = sin vencimiento)',
    );
    if (!mounted) return;
    setState(() {
      if (desde) {
        _desde = f;
      } else {
        _hasta = f;
      }
    });
  }

  void _aceptar() {
    if (_centro == null) {
      avisar(context, 'Seleccione el centro', error: true);
      return;
    }
    if (_desde != null && _hasta != null && _hasta!.isBefore(_desde!)) {
      avisar(context, 'La fecha hasta no puede ser anterior a la fecha desde', error: true);
      return;
    }
    Navigator.of(context).pop(<String, String>{
      'id_centro': '${_centro!.id}',
      'desde': _iso(_desde),
      'hasta': _iso(_hasta),
      'observacion': _observacion.text.trim(),
    });
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Agregar centro adicional', style: TextStyle(fontWeight: FontWeight.w700, color: Nd.titulo, fontSize: 18)),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            CampoFormulario(
              etiqueta: 'Centro',
              obligatorio: true,
              child: CampoSeleccion(
                texto: _centro?.nombre ?? '',
                icono: Icons.apartment,
                alTocar: () async {
                  final Opcion? o = await _elegirCentro(context);
                  if (o != null && mounted) setState(() => _centro = o);
                },
              ),
            ),
            CampoFormulario(
              etiqueta: 'Desde',
              child: CampoSeleccion(
                  texto: _desde == null ? '' : fechaCorta(_iso(_desde)), sugerencia: 'Hoy', icono: Icons.event, alTocar: () => _fecha(true)),
            ),
            CampoFormulario(
              etiqueta: 'Hasta',
              child: CampoSeleccion(
                  texto: _hasta == null ? '' : fechaCorta(_iso(_hasta)),
                  sugerencia: 'Sin vencimiento',
                  icono: Icons.event,
                  alTocar: () => _fecha(false)),
            ),
            campoTexto('Observación', _observacion, maximo: 300),
          ],
        ),
      ),
      actions: <Widget>[
        OutlinedButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Cancelar')),
        FilledButton(onPressed: _aceptar, child: const Text('Guardar')),
      ],
    );
  }
}
