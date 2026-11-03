import 'package:flutter/material.dart';

import '../../api.dart';
import '../comunes.dart';

/// ELIMINAR: pide confirmación (como el modal "Eliminar Insumo" de la web)
/// y envía DELETE ?id= -> la API hace el DELETE. Solo lo permite el rol Administrador.
Future<bool> eliminarInsumo(BuildContext context, int id, String descripcion) async {
  final bool confirmar = await confirmarEliminacion(
    context,
    titulo: 'Eliminar Insumo',
    mensaje: '¿Seguro que desea eliminar este insumo?\n\n$descripcion (N° $id)',
  );
  if (!confirmar) return false;
  try {
    await Api.movil('eliminarInsumo', <String, String>{'id': '$id'});
    if (context.mounted) {
      avisar(context, 'Insumo eliminado correctamente');
    }
    return true;
  } catch (e) {
    if (context.mounted) {
      manejarError(context, e);
    }
    return false;
  }
}
