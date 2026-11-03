import 'package:flutter/material.dart';

import '../../api.dart';
import '../comunes.dart';
import '../listado.dart';
import 'formulario_insumo.dart';

/// MODIFICAR en dos pasos:
///  1) GET ?id=  -> trae los datos actuales (y las unidades) y completa el formulario.
///  2) PUT       -> envía los cambios y la API hace el UPDATE.
class ModificarInsumo extends StatefulWidget {
  final int id;

  const ModificarInsumo({super.key, required this.id});

  @override
  State<ModificarInsumo> createState() => _ModificarInsumoState();
}

class _ModificarInsumoState extends State<ModificarInsumo> {
  Map<String, dynamic>? _insumo;
  List<Map<String, dynamic>> _unidades = <Map<String, dynamic>>[];
  String? _error;

  @override
  void initState() {
    super.initState();
    _buscar();
  }

  Future<void> _buscar() async {
    setState(() {
      _insumo = null;
      _error = null;
    });
    try {
      // los dos pedidos van juntos (en paralelo)
      final List<dynamic> r = await Future.wait(<Future<dynamic>>[
        Api.movilCacheado('unidades'),
        Api.movil('insumo', <String, String>{'id': '${widget.id}'}),
      ]);
      final dynamic unidades = r[0];
      final dynamic datos = r[1];
      if (!mounted) return;
      if (datos is! Map) {
        throw ApiError('Respuesta inesperada del servidor.');
      }
      setState(() {
        _unidades = unidadesDesde(unidades);
        _insumo = Map<String, dynamic>.from(datos);
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = e is ApiError ? e.mensaje : 'No se pudo obtener el insumo.');
      if (e is ApiError && e.estado == 401) {
        manejarError(context, e);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    Widget cuerpo;
    if (_error != null) {
      cuerpo = Aviso(icono: Icons.error_outline, texto: _error!, alReintentar: _buscar);
    } else if (_insumo == null) {
      cuerpo = const Padding(padding: EdgeInsets.all(40), child: Cargando());
    } else {
      cuerpo = FormularioInsumo(
        inicial: _insumo,
        unidades: _unidades,
        textoBoton: 'Guardar cambios',
        alGuardar: (Map<String, String> datos) async {
          datos['id'] = '${widget.id}';
          await Api.movil('guardarInsumo', datos);
          if (!context.mounted) return;
          avisar(context, 'Insumo modificado correctamente');
          Navigator.of(context).pop(true);
        },
      );
    }
    return PaginaFormulario(
      titulo: 'Editar Insumo',
      migas: <String>['Inicio', 'Referenciales', 'Insumos', 'N° ${widget.id}'],
      icono: Icons.edit,
      cuerpo: cuerpo,
    );
  }
}
