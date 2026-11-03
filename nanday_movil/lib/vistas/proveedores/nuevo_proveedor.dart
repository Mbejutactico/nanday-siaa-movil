import 'package:flutter/material.dart';

import '../../api.dart';
import '../comunes.dart';
import '../listado.dart';
import 'formulario_proveedor.dart';

/// CREAR: envía los datos con POST -> la API hace el INSERT.
class NuevoProveedor extends StatelessWidget {
  const NuevoProveedor({super.key});

  @override
  Widget build(BuildContext context) {
    return PaginaFormulario(
      titulo: 'Nuevo Proveedor',
      migas: const <String>['Inicio', 'Referenciales', 'Proveedores', 'Nuevo'],
      icono: Icons.add_business,
      cuerpo: FormularioProveedor(
        textoBoton: 'Guardar',
        alGuardar: (Map<String, String> datos) async {
          await Api.movil('guardarProveedor', datos);
          if (!context.mounted) return;
          avisar(context, 'Proveedor guardado correctamente');
          Navigator.of(context).pop(true); // true = hubo cambios, la lista se recarga
        },
      ),
    );
  }
}
