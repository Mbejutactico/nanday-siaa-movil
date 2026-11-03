import 'package:flutter/material.dart';

import '../../sesion.dart';
import '../comunes.dart';
import 'formularios.dart';
import 'lista_referencial.dart';

/// Referenciales de administración (permiso REF_ADMIN, solo Administrador, igual que la web).
/// Cada pantalla configura el listado genérico [ListaReferencial] con sus columnas y su formulario.

String _unir(List<String> partes) => partes.where((String s) => s.trim().isNotEmpty).join('  ·  ');

/// CENTROS (centrosvista.jsp)
class ListaCentros extends StatelessWidget {
  const ListaCentros({super.key});

  @override
  Widget build(BuildContext context) {
    return ListaReferencial(
      titulo: 'Centros',
      textoNuevo: 'Nuevo Centro',
      ayudaBuscar: 'Buscar por código o nombre',
      encabezadoTabla: 'Código / Nombre',
      textoVacio: 'Todavía no hay centros registrados.',
      tipoListar: 'refCentros',
      tipoEliminar: 'eliminarCentro',
      nombreSingular: 'Centro',
      tituloFila: (Map<String, dynamic> r) => tx(r['nombre']),
      detallesFila: (Map<String, dynamic> r) => <String>[_unir(<String>[tx(r['codigo']), tx(r['direccion'])])],
      camposBusqueda: (Map<String, dynamic> r) => <String>[tx(r['codigo']), tx(r['nombre']), tx(r['direccion'])],
      formulario: (Map<String, dynamic>? r) => FormularioCentro(registro: r),
    );
  }
}

/// CENTROS DE COSTO (centro_costovista.jsp)
class ListaCentrosCosto extends StatelessWidget {
  const ListaCentrosCosto({super.key});

  @override
  Widget build(BuildContext context) {
    return ListaReferencial(
      titulo: 'Centros de Costo',
      textoNuevo: 'Nuevo Centro de Costo',
      ayudaBuscar: 'Buscar por código, descripción o centro',
      encabezadoTabla: 'Código / Descripción',
      textoVacio: 'Todavía no hay centros de costo registrados.',
      tipoListar: 'refCentrosCosto',
      tipoEliminar: 'eliminarCentroCosto',
      nombreSingular: 'Centro de Costo',
      tituloFila: (Map<String, dynamic> r) => _unir(<String>[tx(r['codigo']), tx(r['descripcion'])]).replaceAll('  ·  ', ' - '),
      detallesFila: (Map<String, dynamic> r) => <String>[tx(r['centro']).isEmpty ? 'Sin centro' : 'Centro: ${tx(r['centro'])}'],
      camposBusqueda: (Map<String, dynamic> r) => <String>[tx(r['codigo']), tx(r['descripcion']), tx(r['centro'])],
      formulario: (Map<String, dynamic>? r) => FormularioCentroCosto(registro: r),
    );
  }
}

/// ALMACENES (almacenesvista.jsp)
class ListaAlmacenes extends StatelessWidget {
  const ListaAlmacenes({super.key});

  @override
  Widget build(BuildContext context) {
    return ListaReferencial(
      titulo: 'Almacenes',
      textoNuevo: 'Nuevo Almacén',
      ayudaBuscar: 'Buscar por nombre, ubicación o centro',
      encabezadoTabla: 'Nombre / Centro',
      textoVacio: 'Todavía no hay almacenes registrados.',
      tipoListar: 'refAlmacenes',
      tipoEliminar: 'eliminarAlmacen',
      nombreSingular: 'Almacén',
      tituloFila: (Map<String, dynamic> r) => tx(r['nombre']),
      detallesFila: (Map<String, dynamic> r) => <String>[
        tx(r['centro']).isEmpty ? 'Sin centro' : 'Centro: ${tx(r['centro'])}',
        _unir(<String>[tx(r['descripcion']), tx(r['ubicacion'])]),
      ],
      camposBusqueda: (Map<String, dynamic> r) =>
          <String>[tx(r['nombre']), tx(r['descripcion']), tx(r['ubicacion']), tx(r['centro'])],
      formulario: (Map<String, dynamic>? r) => FormularioAlmacen(registro: r),
    );
  }
}

/// UNIDADES DE MEDIDA (unidad_medidavista.jsp)
class ListaUnidades extends StatelessWidget {
  const ListaUnidades({super.key});

  @override
  Widget build(BuildContext context) {
    return ListaReferencial(
      titulo: 'Unidades de Medida',
      textoNuevo: 'Nueva Unidad',
      ayudaBuscar: 'Buscar por nombre o abreviatura',
      encabezadoTabla: 'Nombre / Abreviatura',
      textoVacio: 'Todavía no hay unidades de medida registradas.',
      tipoListar: 'refUnidades',
      tipoEliminar: 'eliminarUnidad',
      nombreSingular: 'Unidad de Medida',
      tituloFila: (Map<String, dynamic> r) =>
          tx(r['abreviatura']).isEmpty ? tx(r['nombre']) : '${tx(r['nombre'])} (${tx(r['abreviatura'])})',
      detallesFila: (Map<String, dynamic> r) =>
          <String>[ent(r['insumos']) == 0 ? 'Sin insumos' : '${ent(r['insumos'])} insumo(s) la usan'],
      camposBusqueda: (Map<String, dynamic> r) => <String>[tx(r['nombre']), tx(r['abreviatura'])],
      formulario: (Map<String, dynamic>? r) => FormularioUnidad(registro: r),
      // la API igual lo controla: una unidad usada por insumos no se elimina
      sePuedeEliminar: (Map<String, dynamic> r) => ent(r['insumos']) == 0,
    );
  }
}

/// ROLES (rolesvista.jsp)
class ListaRoles extends StatelessWidget {
  const ListaRoles({super.key});

  @override
  Widget build(BuildContext context) {
    return ListaReferencial(
      titulo: 'Roles',
      textoNuevo: 'Nuevo Rol',
      ayudaBuscar: 'Buscar por nombre o tipo',
      encabezadoTabla: 'Nombre / Tipo de permisos',
      textoVacio: 'Todavía no hay roles registrados.',
      tipoListar: 'refRoles',
      tipoEliminar: 'eliminarRol',
      nombreSingular: 'Rol',
      tituloFila: (Map<String, dynamic> r) => tx(r['nombre']),
      detallesFila: (Map<String, dynamic> r) => <String>[
        'Tipo: ${tx(r['tipo'])}  ·  ${ent(r['usuarios'])} usuario(s)',
        tx(r['descripcion']),
      ],
      camposBusqueda: (Map<String, dynamic> r) => <String>[tx(r['nombre']), tx(r['tipo']), tx(r['descripcion'])],
      formulario: (Map<String, dynamic>? r) => FormularioRol(registro: r),
    );
  }
}

/// USUARIOS (usuariosvista.jsp)
class ListaUsuarios extends StatelessWidget {
  const ListaUsuarios({super.key});

  @override
  Widget build(BuildContext context) {
    return ListaReferencial(
      titulo: 'Usuarios',
      textoNuevo: 'Nuevo Usuario',
      ayudaBuscar: 'Buscar por nombre, email, rol o centro',
      encabezadoTabla: 'Nombre / Email',
      textoVacio: 'Todavía no hay usuarios registrados.',
      tipoListar: 'refUsuarios',
      tipoEliminar: 'eliminarUsuario',
      nombreSingular: 'Usuario',
      tituloFila: (Map<String, dynamic> r) => '${tx(r['nombre'])} ${tx(r['apellido'])}'.trim(),
      detallesFila: (Map<String, dynamic> r) => <String>[
        tx(r['email']),
        _unir(<String>[
          tx(r['rol']),
          tx(r['centro']),
          if (ent(r['centros_adicionales']) > 0) '+${ent(r['centros_adicionales'])} centro(s)',
        ]),
      ],
      camposBusqueda: (Map<String, dynamic> r) =>
          <String>[tx(r['nombre']), tx(r['apellido']), tx(r['email']), tx(r['rol']), tx(r['centro'])],
      formulario: (Map<String, dynamic>? r) => FormularioUsuario(registro: r),
      // no se puede eliminar el usuario con el que se inició sesión (la API también lo controla)
      sePuedeEliminar: (Map<String, dynamic> r) => ent(r['id']) != Sesion.id,
    );
  }
}
