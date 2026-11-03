import 'package:flutter/material.dart';

import '../../api.dart';
import '../comunes.dart';
import '../listado.dart';
import 'formulario_proveedor.dart';

/// MODIFICAR en dos pasos:
///  1) GET ?id=  -> trae los datos actuales y completa el formulario.
///  2) PUT       -> envía los cambios y la API hace el UPDATE.
class ModificarProveedor extends StatefulWidget {
  final int id;

  const ModificarProveedor({super.key, required this.id});

  @override
  State<ModificarProveedor> createState() => _ModificarProveedorState();
}

class _ModificarProveedorState extends State<ModificarProveedor> {
  Map<String, dynamic>? _proveedor;
  String? _error;

  @override
  void initState() {
    super.initState();
    _buscar();
  }

  Future<void> _buscar() async {
    setState(() {
      _proveedor = null;
      _error = null;
    });
    try {
      final dynamic datos = await Api.movil('proveedor', <String, String>{'id': '${widget.id}'});
      if (!mounted) return;
      if (datos is! Map) {
        throw ApiError('Respuesta inesperada del servidor.');
      }
      setState(() => _proveedor = Map<String, dynamic>.from(datos));
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = e is ApiError ? e.mensaje : 'No se pudo obtener el proveedor.');
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
    } else if (_proveedor == null) {
      cuerpo = const Padding(padding: EdgeInsets.all(40), child: Cargando());
    } else {
      cuerpo = FormularioProveedor(
        inicial: _proveedor,
        textoBoton: 'Guardar cambios',
        alGuardar: (Map<String, String> datos) async {
          datos['id'] = '${widget.id}';
          await Api.movil('guardarProveedor', datos);
          if (!context.mounted) return;
          avisar(context, 'Proveedor modificado correctamente');
          Navigator.of(context).pop(true);
        },
      );
    }
    return PaginaFormulario(
      titulo: 'Editar Proveedor',
      migas: <String>['Inicio', 'Referenciales', 'Proveedores', 'N° ${widget.id}'],
      icono: Icons.edit,
      cuerpo: cuerpo,
    );
  }
}
