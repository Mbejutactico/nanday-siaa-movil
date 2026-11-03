import 'package:flutter/material.dart';

import '../../api.dart';
import '../../tema.dart';
import '../comunes.dart';
import 'hoja_vida.dart';

/// ACTIVOS FIJOS > Listado (activosControlador.jsp?tipo=listar) con resumen por estado,
/// filtro de estado y búsqueda por número de activo, serie o descripción.
class ListaActivosPantalla extends StatefulWidget {
  const ListaActivosPantalla({super.key});

  @override
  State<ListaActivosPantalla> createState() => _ListaActivosPantallaState();
}

class _ListaActivosPantallaState extends State<ListaActivosPantalla> {
  String _estado = '';
  String _buscar = '';
  int _version = 0; // recarga la lista
  int _versionResumen = 0; // recarga las cifras (no hace falta al cambiar el filtro)

  void _recargarTodo() => setState(() {
        _version++;
        _versionResumen++;
      });

  @override
  Widget build(BuildContext context) {
    return PaginaApp(
      titulo: 'Activos Fijos',
      migas: const <String>['Inicio', 'Activos fijos'],
      alRefrescar: () async => _recargarTodo(),
      hijos: <Widget>[
        Cargable<Map<String, dynamic>>(
          key: ValueKey<String>('resumen$_versionResumen'),
          cargar: () async => mapa(await Api.ctrl('activosControlador.jsp', 'resumen')),
          constructor: (BuildContext context, Map<String, dynamic> r, Future<void> Function() recargar) => SizedBox(
            height: 86,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16),
              children: <Widget>[
                for (final List<String> e in const <List<String>>[
                  <String>['TOTAL', 'En inventario'],
                  <String>['DISPONIBLE', 'Disponibles'],
                  <String>['ASIGNADO', 'Asignados'],
                  <String>['EN_TRANSITO', 'En tránsito'],
                  <String>['EN_REPARACION', 'En reparación'],
                  <String>['BAJA', 'De baja'],
                ])
                  Container(
                    width: 120,
                    margin: const EdgeInsets.only(right: 10),
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(10),
                      border: Border(
                        left: BorderSide(color: e[0] == 'TOTAL' ? Nd.oscuro : ChipEstado.colorDe(e[0]), width: 3),
                        top: const BorderSide(color: Nd.borde),
                        right: const BorderSide(color: Nd.borde),
                        bottom: const BorderSide(color: Nd.borde),
                      ),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: <Widget>[
                        Text(e[1].toUpperCase(),
                            style: const TextStyle(fontSize: 10.5, fontWeight: FontWeight.w600, color: Nd.apagado, letterSpacing: 0.4)),
                        const SizedBox(height: 4),
                        Text('${ent(r[e[0]])}', style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w700, color: Nd.titulo)),
                      ],
                    ),
                  ),
              ],
            ),
          ),
        ),
        espacio,
        Tarjeta(
          child: CuerpoTarjeta(<Widget>[
            TextField(
              decoration: const InputDecoration(hintText: 'Buscar por N° de activo, serie o descripción', prefixIcon: Icon(Icons.search, size: 20)),
              onChanged: (String v) => setState(() => _buscar = v.trim().toLowerCase()),
            ),
            const SizedBox(height: 10),
            Wrap(
              spacing: 8,
              runSpacing: 6,
              children: <Widget>[
                for (final List<String> e in const <List<String>>[
                  <String>['', 'Todos'],
                  <String>['DISPONIBLE', 'Disponibles'],
                  <String>['ASIGNADO', 'Asignados'],
                  <String>['EN_TRANSITO', 'En tránsito'],
                  <String>['EN_REPARACION', 'En reparación'],
                  <String>['BAJA', 'De baja'],
                ])
                  ChoiceChip(
                    label: Text(e[1]),
                    selected: _estado == e[0],
                    selectedColor: Nd.verdeSuave,
                    onSelected: (_) => setState(() {
                      _estado = e[0];
                      _version++;
                    }),
                  ),
              ],
            ),
          ]),
        ),
        espacio,
      ],
      slivers: <Widget>[
        Cargable<List<Map<String, dynamic>>>.sliver(
          key: ValueKey<String>('lista$_version'),
          cargar: () async => mapas(await Api.ctrl('activosControlador.jsp', 'listar', <String, String>{'estado': _estado})),
          constructor: (BuildContext context, List<Map<String, dynamic>> datos, Future<void> Function() recargar) {
            final List<Map<String, dynamic>> lista = datos.where((Map<String, dynamic> a) {
              return _buscar.isEmpty ||
                  tx(a['numero_activo']).toLowerCase().contains(_buscar) ||
                  tx(a['numero_serie']).toLowerCase().contains(_buscar) ||
                  tx(a['descripcion']).toLowerCase().contains(_buscar) ||
                  tx(a['codigo']).toLowerCase().contains(_buscar);
            }).toList();
            if (lista.isEmpty) {
              return caja(const Aviso(icono: Icons.devices_other, texto: 'No hay activos con esos filtros.'));
            }
            return SliverTarjetaLista(
              cabecera: CabeceraTarjeta(titulo: '${lista.length} activo(s)', icono: Icons.devices_other),
              cantidad: lista.length,
              fila: (BuildContext c, int i) {
                final Map<String, dynamic> a = lista[i];
                return InkWell(
                      onTap: () async {
                        await Navigator.of(context).push(MaterialPageRoute<void>(builder: (BuildContext c) => HojaVidaPantalla(idEquipo: ent(a['id']))));
                        if (mounted) _recargarTodo();
                      },
                      child: Container(
                        padding: const EdgeInsets.fromLTRB(16, 12, 12, 12),
                        decoration: BoxDecoration(
                          border: Border(
                            bottom: const BorderSide(color: Nd.borde),
                            left: BorderSide(color: ChipEstado.colorDe(tx(a['estado'])), width: 3),
                          ),
                        ),
                        child: Row(
                          children: <Widget>[
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: <Widget>[
                                  Row(children: <Widget>[
                                    Text(tx(a['numero_activo']).isEmpty ? 'Sin número' : tx(a['numero_activo']),
                                        style: const TextStyle(color: Nd.apagado, fontWeight: FontWeight.w700, fontSize: 12.5)),
                                    const SizedBox(width: 8),
                                    ChipEstado(tx(a['estado'])),
                                  ]),
                                  const SizedBox(height: 3),
                                  Text(tx(a['descripcion']), style: const TextStyle(fontWeight: FontWeight.w600, color: Nd.titulo, fontSize: 15)),
                                  Text('${tx(a['centro'])}  ·  ${tx(a['centro_costo'])}', style: const TextStyle(color: Nd.apagado, fontSize: 12.5)),
                                  if (tx(a['responsable']).isNotEmpty)
                                    Text('Responsable: ${tx(a['responsable'])}', style: const TextStyle(color: Nd.pizarra, fontSize: 12.5)),
                                  if (tx(a['situacion']).isNotEmpty)
                                    Text(tx(a['situacion']),
                                        style: const TextStyle(color: Nd.acentoProfundo, fontSize: 12.5, fontWeight: FontWeight.w600)),
                                ],
                              ),
                            ),
                            const Icon(Icons.chevron_right, color: Nd.apagado),
                          ],
                        ),
                      ),
                    );
              },
            );
          },
        ),
      ],
    );
  }
}
