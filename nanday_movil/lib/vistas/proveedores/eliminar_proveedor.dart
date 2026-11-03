import 'package:flutter/material.dart';

import '../../api.dart';
import '../comunes.dart';

/// ELIMINAR: pide confirmación (como el modal "Eliminar Proveedor" de la web)
/// y envía DELETE ?id= -> la API hace el DELETE. Devuelve true si se eliminó.
Future<bool> eliminarProveedor(BuildContext context, int id, String razonSocial) async {
  final bool confirmar = await confirmarEliminacion(
    context,
    titulo: 'Eliminar Proveedor',
    mensaje: '¿Seguro que desea eliminar este proveedor?\n\n$razonSocial (N° $id)',
  );
  if (!confirmar) return false;
  try {
    await Api.movil('eliminarProveedor', <String, String>{'id': '$id'});
    if (context.mounted) {
      avisar(context, 'Proveedor eliminado correctamente');
    }
    return true;
  } catch (e) {
    if (context.mounted) {
      manejarError(context, e);
    }
    return false;
  }
}
