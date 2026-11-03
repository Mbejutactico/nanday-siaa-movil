import 'package:flutter/material.dart';

import '../../api.dart';
import '../comunes.dart';
import '../listado.dart';

/// Listado genérico de un referencial (centros, centros de costo, almacenes, unidades, roles, usuarios).
/// Mismo diseño que Proveedores e Insumos: botón "Nuevo", buscador, tabla y "Mostrando X de Y".
///
/// LISTAR: POST APInanday/c/movil?tipo=[tipoListar]  (ReferencialesControlador en la API)
/// ELIMINAR: tipo=[tipoEliminar]&id=N  (mismas reglas que la web: registros en uso, último administrador...)
class ListaReferencial extends StatefulWidget {
  final String titulo;
  final String textoNuevo;
  final String ayudaBuscar;
  final String encabezadoTabla;
  final String textoVacio;
  final String tipoListar;
  final String tipoEliminar;

  /// Nombre para el diálogo de eliminar: "Eliminar Centro".
  final String nombreSingular;

  final String Function(Map<String, dynamic> r) tituloFila;
  final List<String> Function(Map<String, dynamic> r) detallesFila;

  /// Textos en los que busca el buscador.
  final List<String> Function(Map<String, dynamic> r) camposBusqueda;

  /// Pantalla de alta (registro = null) o modificación. Devuelve true si guardó.
  final Widget Function(Map<String, dynamic>? registro) formulario;

  /// Si devuelve false, la fila no muestra el botón eliminar (por ejemplo, el propio usuario).
  final bool Function(Map<String, dynamic> r)? sePuedeEliminar;

  const ListaReferencial({
    super.key,
    required this.titulo,
    required this.textoNuevo,
    required this.ayudaBuscar,
    required this.encabezadoTabla,
    required this.textoVacio,
    required this.tipoListar,
    required this.tipoEliminar,
    required this.nombreSingular,
    required this.tituloFila,
    required this.detallesFila,
    required this.camposBusqueda,
    required this.formulario,
    this.sePuedeEliminar,
  });

  @override
  State<ListaReferencial> createState() => _ListaReferencialState();
}

class _ListaReferencialState extends State<ListaReferencial> {
  List<Map<String, dynamic>> _registros = <Map<String, dynamic>>[];
  bool _cargando = true;
  String? _error;
  String _buscar = '';
  final TextEditingController _txtBuscar = TextEditingController();

  @override
  void initState() {
    super.initState();
    _cargar();
  }

  @override
  void dispose() {
    _txtBuscar.dispose();
    super.dispose();
  }

  Future<void> _cargar() async {
    setState(() {
      _cargando = true;
      _error = null;
    });
    try {
      final List<Map<String, dynamic>> lista = mapas(await Api.movil(widget.tipoListar));
      if (!mounted) return;
      setState(() {
        _registros = lista;
        _cargando = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _cargando = false;
        _error = e is ApiError ? e.mensaje : 'No se pudo obtener la lista.';
      });
      if (e is ApiError && e.sesionVencida) manejarError(context, e);
    }
  }

  List<Map<String, dynamic>> get _filtrados {
    final String t = _buscar.trim().toLowerCase();
    if (t.isEmpty) return _registros;
    return _registros
        .where((Map<String, dynamic> r) => widget.camposBusqueda(r).any((String c) => c.toLowerCase().contains(t)))
        .toList();
  }

  Future<void> _abrirFormulario(Map<String, dynamic>? registro) async {
    final bool? cambio = await Navigator.of(context).push<bool>(
      MaterialPageRoute<bool>(builder: (BuildContext c) => widget.formulario(registro)),
    );
    if (cambio == true) {
      Api.limpiarCache(); // los combos (centros, almacenes, unidades...) se vuelven a pedir
      _cargar();
    }
  }

  Future<void> _eliminar(Map<String, dynamic> r) async {
    final bool ok = await confirmarEliminacion(
      context,
      titulo: 'Eliminar ${widget.nombreSingular}',
      mensaje: '¿Seguro que desea eliminar este registro?\n\n${widget.tituloFila(r)} (N° ${ent(r['id'])})',
    );
    if (!ok || !mounted) return;
    try {
      final String mensaje = await Api.html('movil', widget.tipoEliminar, <String, String>{'id': '${ent(r['id'])}'});
      if (!mounted) return;
      avisar(context, mensaje);
      Api.limpiarCache();
      _cargar();
    } catch (e) {
      if (mounted) manejarError(context, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    final List<Map<String, dynamic>> lista = _filtrados;
    return PaginaListado(
      titulo: widget.titulo,
      migas: <String>['Inicio', 'Referenciales', widget.titulo],
      textoNuevo: widget.textoNuevo,
      alNuevo: () => _abrirFormulario(null),
      alActualizar: _cargar,
      alRefrescar: _cargar,
      ayudaBuscar: widget.ayudaBuscar,
      alBuscar: (String v) => setState(() => _buscar = v),
      buscador: _txtBuscar,
      encabezadoTabla: widget.encabezadoTabla,
      cargando: _cargando,
      error: _error,
      total: _registros.length,
      textoVacio: widget.textoVacio,
      cantidad: lista.length,
      fila: (BuildContext context, int i) {
        final Map<String, dynamic> r = lista[i];
        final bool eliminable = widget.sePuedeEliminar?.call(r) ?? true;
        return FilaTabla(
          id: ent(r['id']),
          titulo: widget.tituloFila(r),
          detalles: widget.detallesFila(r),
          activo: r['activo'] != false,
          alModificar: () => _abrirFormulario(r),
          alEliminar: eliminable ? () => _eliminar(r) : null,
        );
      },
    );
  }
}
