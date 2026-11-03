import 'package:flutter/material.dart';

import '../../api.dart';
import '../comunes.dart';
import '../listado.dart';
import 'eliminar_proveedor.dart';
import 'modificar_proveedor.dart';
import 'nuevo_proveedor.dart';

/// LISTAR: al abrir hace GET -> la API hace el SELECT y devuelve la lista en JSON.
/// Desde cada fila se puede modificar o eliminar; "Nuevo Proveedor" abre el alta.
class ListaProveedores extends StatefulWidget {
  const ListaProveedores({super.key});

  @override
  State<ListaProveedores> createState() => _ListaProveedoresState();
}

class _ListaProveedoresState extends State<ListaProveedores> {
  List<Map<String, dynamic>> _proveedores = <Map<String, dynamic>>[];
  bool _cargando = true;
  String? _error;
  String _buscar = '';
  final TextEditingController txtBuscar = TextEditingController();

  @override
  void initState() {
    super.initState();
    _cargar();
  }

  Future<void> _cargar() async {
    setState(() {
      _cargando = true;
      _error = null;
    });
    try {
      final dynamic datos = await Api.movil('proveedores');
      if (!mounted) return;
      final List<Map<String, dynamic>> lista = <Map<String, dynamic>>[];
      if (datos is List) {
        for (final dynamic fila in datos) {
          if (fila is Map) {
            lista.add(Map<String, dynamic>.from(fila));
          }
        }
      }
      setState(() {
        _proveedores = lista;
        _cargando = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _cargando = false;
        _error = e is ApiError ? e.mensaje : 'No se pudo obtener la lista.';
      });
      if (e is ApiError && e.estado == 401) {
        manejarError(context, e);
      }
    }
  }

  /// Proveedores que coinciden con el texto buscado (razón social, RUC o email).
  List<Map<String, dynamic>> get _filtrados {
    final String t = _buscar.trim().toLowerCase();
    if (t.isEmpty) return _proveedores;
    return _proveedores.where((Map<String, dynamic> p) {
      return (p['razon_social'] ?? '').toString().toLowerCase().contains(t) ||
          (p['ruc'] ?? '').toString().toLowerCase().contains(t) ||
          (p['email'] ?? '').toString().toLowerCase().contains(t);
    }).toList();
  }

  Future<void> _nuevo() async {
    final bool? cambio = await Navigator.of(context).push<bool>(
      MaterialPageRoute<bool>(builder: (BuildContext context) => const NuevoProveedor()),
    );
    if (cambio == true) _cargar();
  }

  Future<void> _modificar(int id) async {
    final bool? cambio = await Navigator.of(context).push<bool>(
      MaterialPageRoute<bool>(builder: (BuildContext context) => ModificarProveedor(id: id)),
    );
    if (cambio == true) _cargar();
  }

  Future<void> _eliminar(int id, String razonSocial) async {
    final bool eliminado = await eliminarProveedor(context, id, razonSocial);
    if (eliminado && mounted) _cargar();
  }

  @override
  void dispose() {
    txtBuscar.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final List<Map<String, dynamic>> lista = _filtrados;
    return PaginaListado(
      titulo: 'Proveedores',
      migas: const <String>['Inicio', 'Referenciales', 'Proveedores'],
      textoNuevo: 'Nuevo Proveedor',
      alNuevo: _nuevo,
      alActualizar: _cargar,
      alRefrescar: _cargar,
      ayudaBuscar: 'Buscar por razón social, RUC o email',
      alBuscar: (String v) => setState(() => _buscar = v),
      buscador: txtBuscar,
      encabezadoTabla: 'Razón Social / RUC',
      cargando: _cargando,
      error: _error,
      total: _proveedores.length,
      textoVacio: 'Todavía no hay proveedores registrados.',
      cantidad: lista.length,
      fila: (BuildContext context, int indice) {
        final Map<String, dynamic> p = lista[indice];
        return FilaTabla(
            id: (p['id'] as num?)?.toInt() ?? 0,
            titulo: (p['razon_social'] ?? '').toString(),
            detalles: <String>[
              'RUC: ${p['ruc'] ?? ''}',
              <String>[
                (p['telefono'] ?? '').toString(),
                (p['email'] ?? '').toString(),
              ].where((String s) => s.isNotEmpty).join('  ·  '),
            ],
            activo: p['activo'] != false,
            alModificar: () => _modificar((p['id'] as num?)?.toInt() ?? 0),
            alEliminar: () => _eliminar((p['id'] as num?)?.toInt() ?? 0, (p['razon_social'] ?? '').toString()),
        );
      },
    );
  }
}
