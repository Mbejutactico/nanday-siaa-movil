import 'package:flutter/material.dart';

import '../../api.dart';
import '../comunes.dart';
import '../listado.dart';
import 'formulario_insumo.dart';

/// CREAR: primero trae las unidades de medida (GET) para el desplegable;
/// al guardar envía los datos con POST -> la API hace el INSERT.
class NuevoInsumo extends StatefulWidget {
  const NuevoInsumo({super.key});

  @override
  State<NuevoInsumo> createState() => _NuevoInsumoState();
}

class _NuevoInsumoState extends State<NuevoInsumo> {
  List<Map<String, dynamic>>? _unidades;
  String? _error;

  @override
  void initState() {
    super.initState();
    _cargarUnidades();
  }

  Future<void> _cargarUnidades() async {
    setState(() {
      _unidades = null;
      _error = null;
    });
    try {
      final dynamic datos = await Api.movilCacheado('unidades');
      if (!mounted) return;
      setState(() => _unidades = unidadesDesde(datos));
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = e is ApiError ? e.mensaje : 'No se pudieron obtener las unidades de medida.');
      if (e is ApiError && e.estado == 401) {
        manejarError(context, e);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    Widget cuerpo;
    if (_error != null) {
      cuerpo = Aviso(icono: Icons.error_outline, texto: _error!, alReintentar: _cargarUnidades);
    } else if (_unidades == null) {
      cuerpo = const Padding(padding: EdgeInsets.all(40), child: Cargando());
    } else {
      cuerpo = FormularioInsumo(
        unidades: _unidades!,
        textoBoton: 'Guardar',
        alGuardar: (Map<String, String> datos) async {
          await Api.movil('guardarInsumo', datos);
          if (!context.mounted) return;
          avisar(context, 'Insumo guardado correctamente');
          Navigator.of(context).pop(true); // true = hubo cambios, la lista se recarga
        },
      );
    }
    return PaginaFormulario(
      titulo: 'Nuevo Insumo',
      migas: const <String>['Inicio', 'Referenciales', 'Insumos', 'Nuevo'],
      icono: Icons.add_box,
      cuerpo: cuerpo,
    );
  }
}
