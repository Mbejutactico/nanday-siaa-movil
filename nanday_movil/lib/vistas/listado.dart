import 'package:flutter/material.dart';

import '../tema.dart';
import 'comunes.dart';

/// Pantalla de listado con el mismo armado que las vistas de la web (proveedorvista.jsp,
/// insumosvista.jsp): título + migas, y una tarjeta con el botón "Nuevo ...", el buscador,
/// la tabla (encabezado con línea verde, filas alternadas) y "Mostrando X de Y registros".
class PaginaListado extends StatelessWidget {
  final String titulo;
  final List<String> migas;
  final String textoNuevo;
  final VoidCallback alNuevo;
  final VoidCallback alActualizar;
  final Future<void> Function() alRefrescar;
  final String ayudaBuscar;
  final ValueChanged<String> alBuscar;
  final TextEditingController buscador;
  final String encabezadoTabla;
  final bool cargando;
  final String? error;
  final int total;

  /// Cantidad de filas que coinciden con la búsqueda.
  final int cantidad;

  /// Construye la fila [i] (solo se llama para las filas visibles en pantalla).
  final IndexedWidgetBuilder fila;
  final String textoVacio;

  const PaginaListado({
    super.key,
    required this.titulo,
    required this.migas,
    required this.textoNuevo,
    required this.alNuevo,
    required this.alActualizar,
    required this.alRefrescar,
    required this.ayudaBuscar,
    required this.alBuscar,
    required this.buscador,
    required this.encabezadoTabla,
    required this.cargando,
    required this.error,
    required this.total,
    required this.cantidad,
    required this.fila,
    required this.textoVacio,
  });

  @override
  Widget build(BuildContext context) {
    // .card-header: botón "Nuevo ..." y buscador
    final Widget cabeceraTarjeta = Container(
      padding: const EdgeInsets.all(14),
      decoration: const BoxDecoration(border: Border(bottom: BorderSide(color: Nd.borde))),
      child: Wrap(
        spacing: 10,
        runSpacing: 10,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: <Widget>[
          FilledButton.icon(
            onPressed: alNuevo,
            icon: const Icon(Icons.add, size: 20),
            label: Text(textoNuevo),
          ),
          ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 420),
            child: TextField(
              controller: buscador,
              onChanged: alBuscar,
              decoration: InputDecoration(
                hintText: ayudaBuscar,
                prefixIcon: const Icon(Icons.search, size: 20),
              ),
            ),
          ),
        ],
      ),
    );

    Widget? aviso;
    if (cargando) {
      aviso = const Padding(padding: EdgeInsets.all(40), child: Cargando());
    } else if (error != null) {
      aviso = Aviso(icono: Icons.cloud_off, texto: error!, alReintentar: alActualizar);
    } else if (cantidad == 0) {
      aviso = Aviso(
        icono: Icons.inbox,
        texto: total == 0 ? textoVacio : 'Ningún registro coincide con la búsqueda.',
      );
    }

    final Widget tabla = aviso != null
        ? SliverTarjetaLista(
            cabecera: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: <Widget>[cabeceraTarjeta, aviso]),
            cantidad: 0,
            fila: (BuildContext c, int i) => const SizedBox.shrink(),
          )
        : SliverTarjetaLista(
            cabecera: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                cabeceraTarjeta,
                // thead: texto pizarra con subrayado verde
                Container(
                  padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
                  decoration: const BoxDecoration(
                    border: Border(bottom: BorderSide(color: Nd.primario, width: 2)),
                  ),
                  child: Row(
                    children: <Widget>[
                      Expanded(
                        child: Text(
                          encabezadoTabla,
                          style: const TextStyle(fontWeight: FontWeight.w700, color: Nd.pizarra, fontSize: 13.5),
                        ),
                      ),
                      const Text(
                        'Acción',
                        style: TextStyle(fontWeight: FontWeight.w700, color: Nd.pizarra, fontSize: 13.5),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            cantidad: cantidad,
            // .table-striped
            fila: (BuildContext c, int i) => ColoredBox(
              color: i.isOdd ? const Color(0xFFF9FAFB) : Colors.white,
              child: fila(c, i),
            ),
            pie: Container(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 14),
              child: Text(
                cantidad == total
                    ? 'Mostrando $total registro${total == 1 ? '' : 's'}'
                    : 'Mostrando $cantidad de $total registros',
                style: const TextStyle(color: Nd.apagado, fontSize: 13),
              ),
            ),
          );

    return Scaffold(
      appBar: barraNanday(
        acciones: <Widget>[
          IconButton(tooltip: 'Actualizar', icon: const Icon(Icons.refresh), onPressed: alActualizar),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: alRefrescar,
        child: CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: <Widget>[
            caja(EncabezadoPagina(titulo: titulo, migas: migas)),
            tabla,
            caja(const PiePagina()),
          ],
        ),
      ),
    );
  }
}

/// Fila de la tabla: N° a la izquierda, datos en el centro y botones de acción a la derecha.
class FilaTabla extends StatelessWidget {
  final int id;
  final String titulo;
  final List<String> detalles;
  final bool activo;
  final VoidCallback alModificar;
  final VoidCallback? alEliminar;

  const FilaTabla({
    super.key,
    required this.id,
    required this.titulo,
    required this.detalles,
    required this.activo,
    required this.alModificar,
    this.alEliminar,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: alModificar,
      hoverColor: Nd.primario.withValues(alpha: 0.07),
      child: Container(
        padding: const EdgeInsets.fromLTRB(16, 12, 12, 12),
        decoration: const BoxDecoration(border: Border(bottom: BorderSide(color: Nd.borde))),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: <Widget>[
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Row(
                    children: <Widget>[
                      Text('#$id', style: const TextStyle(color: Nd.apagado, fontSize: 12, fontWeight: FontWeight.w600)),
                      const SizedBox(width: 8),
                      Insignia.activo(activo),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Text(
                    titulo,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontWeight: FontWeight.w600, color: Nd.titulo, fontSize: 15),
                  ),
                  for (final String d in detalles.where((String d) => d.isNotEmpty))
                    Padding(
                      padding: const EdgeInsets.only(top: 2),
                      child: Text(d, style: const TextStyle(color: Nd.apagado, fontSize: 13)),
                    ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            BotonAccion.modificar(alTocar: alModificar),
            if (alEliminar != null) ...<Widget>[
              const SizedBox(width: 6),
              BotonAccion.eliminar(alTocar: alEliminar),
            ],
          ],
        ),
      ),
    );
  }
}

/// Pantalla de formulario con el aspecto del modal de la web: tarjeta con cabecera,
/// campos con etiqueta arriba y pie con "Cerrar" y el botón de guardar.
class PaginaFormulario extends StatelessWidget {
  final String titulo;
  final List<String> migas;
  final IconData icono;
  final Widget cuerpo;

  const PaginaFormulario({
    super.key,
    required this.titulo,
    required this.migas,
    required this.icono,
    required this.cuerpo,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: barraNanday(),
      body: ListView(
        children: <Widget>[
          EncabezadoPagina(titulo: titulo, migas: migas),
          Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 640),
              child: Tarjeta(
                acento: Nd.acento,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: <Widget>[
                    CabeceraTarjeta(titulo: titulo, icono: icono),
                    cuerpo,
                  ],
                ),
              ),
            ),
          ),
          const PiePagina(),
        ],
      ),
    );
  }
}

/// Pie del formulario (.modal-footer): Cerrar (gris) y Guardar (verde).
class PieFormulario extends StatelessWidget {
  final bool guardando;
  final String textoGuardar;
  final VoidCallback alGuardar;

  const PieFormulario({super.key, required this.guardando, required this.textoGuardar, required this.alGuardar});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 14),
      decoration: const BoxDecoration(border: Border(top: BorderSide(color: Nd.borde))),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.end,
        children: <Widget>[
          OutlinedButton(
            onPressed: guardando ? null : () => Navigator.of(context).maybePop(),
            child: const Text('Cerrar'),
          ),
          const SizedBox(width: 10),
          FilledButton.icon(
            onPressed: guardando ? null : alGuardar,
            icon: guardando
                ? const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                  )
                : const Icon(Icons.save, size: 20),
            label: Text(guardando ? 'Guardando...' : textoGuardar),
          ),
        ],
      ),
    );
  }
}

/// Selector "Sí / No" del campo Activo (igual al select de la web).
class SelectorActivo extends StatelessWidget {
  final bool valor;
  final ValueChanged<bool> alCambiar;

  const SelectorActivo({super.key, required this.valor, required this.alCambiar});

  @override
  Widget build(BuildContext context) {
    return DropdownButtonFormField<bool>(
      initialValue: valor,
      isExpanded: true,
      items: const <DropdownMenuItem<bool>>[
        DropdownMenuItem<bool>(value: true, child: Text('Sí')),
        DropdownMenuItem<bool>(value: false, child: Text('No')),
      ],
      onChanged: (bool? v) => alCambiar(v ?? true),
    );
  }
}
