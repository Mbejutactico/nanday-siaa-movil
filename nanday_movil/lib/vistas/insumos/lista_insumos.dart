import 'package:flutter/material.dart';

import '../../api.dart';
import '../../sesion.dart';
import '../comunes.dart';
import '../listado.dart';
import 'eliminar_insumo.dart';
import 'modificar_insumo.dart';
import 'nuevo_insumo.dart';

/// LISTAR: al abrir hace GET -> la API hace el SELECT y devuelve la lista en JSON.
/// Desde cada fila se puede modificar o eliminar; "Nuevo Insumo" abre el alta.
class ListaInsumos extends StatefulWidget {
  const ListaInsumos({super.key});

  @override
  State<ListaInsumos> createState() => _ListaInsumosState();
}

class _ListaInsumosState extends State<ListaInsumos> {
  List<Map<String, dynamic>> _insumos = <Map<String, dynamic>>[];
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
      final dynamic datos = await Api.movil('insumosCatalogo');
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
        _insumos = lista;
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

  /// Insumos que coinciden con el texto buscado (código o descripción).
  List<Map<String, dynamic>> get _filtrados {
    final String t = _buscar.trim().toLowerCase();
    if (t.isEmpty) return _insumos;
    return _insumos.where((Map<String, dynamic> i) {
      return (i['codigo'] ?? '').toString().toLowerCase().contains(t) ||
          (i['descripcion'] ?? '').toString().toLowerCase().contains(t);
    }).toList();
  }

  Future<void> _nuevo() async {
    final bool? cambio = await Navigator.of(context).push<bool>(
      MaterialPageRoute<bool>(builder: (BuildContext context) => const NuevoInsumo()),
    );
    if (cambio == true) _cargar();
  }

  Future<void> _modificar(int id) async {
    final bool? cambio = await Navigator.of(context).push<bool>(
      MaterialPageRoute<bool>(builder: (BuildContext context) => ModificarInsumo(id: id)),
    );
    if (cambio == true) _cargar();
  }

  Future<void> _eliminar(int id, String descripcion) async {
    final bool eliminado = await eliminarInsumo(context, id, descripcion);
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
      titulo: 'Insumos',
      migas: const <String>['Inicio', 'Referenciales', 'Insumos'],
      textoNuevo: 'Nuevo Insumo',
      alNuevo: _nuevo,
      alActualizar: _cargar,
      alRefrescar: _cargar,
      ayudaBuscar: 'Buscar por código o descripción',
      alBuscar: (String v) => setState(() => _buscar = v),
      buscador: txtBuscar,
      encabezadoTabla: 'Código / Descripción',
      cargando: _cargando,
      error: _error,
      total: _insumos.length,
      textoVacio: 'Todavía no hay insumos registrados.',
      cantidad: lista.length,
      fila: (BuildContext context, int indice) {
        final Map<String, dynamic> i = lista[indice];
        return FilaTabla(
            id: (i['id'] as num?)?.toInt() ?? 0,
            titulo: (i['descripcion'] ?? '').toString(),
            detalles: <String>[
              <String>[
                (i['codigo'] ?? '').toString(),
                (i['unidad'] ?? '').toString(),
              ].where((String s) => s.isNotEmpty).join('  ·  '),
              'Precio de compra: Gs. ${guaranies(i['precio_compra'])}',
            ],
            activo: i['activo'] != false,
            alModificar: () => _modificar((i['id'] as num?)?.toInt() ?? 0),
            // Eliminar insumos solo lo permite el rol Administrador
            alEliminar: Sesion.puede(Sesion.insumosEliminar)
                ? () => _eliminar((i['id'] as num?)?.toInt() ?? 0, (i['descripcion'] ?? '').toString())
                : null,
        );
      },
    );
  }
}
