import 'package:flutter/material.dart';

import '../../api.dart';
import '../../tema.dart';
import '../comunes.dart';
import 'kardex.dart';

/// INVENTARIOS > Stock: existencias por almacén de los centros del usuario.
/// Filtros: almacén, situación (sin stock / bajo mínimo / normal) y búsqueda por código o nombre.
/// Al tocar un insumo se abre su Kardex en ese almacén.
class StockPantalla extends StatefulWidget {
  const StockPantalla({super.key});

  @override
  State<StockPantalla> createState() => _StockPantallaState();
}

class _StockPantallaState extends State<StockPantalla> {
  final GlobalKey<CargableEstado<List<Map<String, dynamic>>>> _carga = GlobalKey<CargableEstado<List<Map<String, dynamic>>>>();
  Opcion? _almacen;
  String _situacion = '';
  String _buscar = '';

  Future<List<Map<String, dynamic>>> _cargar() async {
    return mapas(await Api.movil('stock', <String, String>{
      'id_almacen': '${_almacen?.id ?? 0}',
      'situacion': _situacion,
    }));
  }

  Future<void> _elegirAlmacen() async {
    try {
      final List<Opcion> lista = <Opcion>[
        const Opcion(0, 'Todos los almacenes'),
        ...mapas(await Api.movilCacheado('almacenes'))
            .map((Map<String, dynamic> a) => Opcion(ent(a['id']), tx(a['nombre']), detalle: tx(a['centro']))),
      ];
      if (!mounted) return;
      final Opcion? o = await elegirOpcion(context, 'Almacén', lista);
      if (o == null) return;
      setState(() => _almacen = o.id == 0 ? null : o);
      _carga.currentState?.recargar();
    } catch (e) {
      if (mounted) manejarError(context, e);
    }
  }

  void _cambiarSituacion(String s) {
    setState(() => _situacion = s);
    _carga.currentState?.recargar();
  }

  @override
  Widget build(BuildContext context) {
    return PaginaApp(
      titulo: 'Stock',
      migas: const <String>['Inicio', 'Inventarios', 'Stock'],
      alRefrescar: () async => _carga.currentState?.recargar(),
      hijos: <Widget>[
        Tarjeta(
          child: CuerpoTarjeta(<Widget>[
            CampoSeleccion(texto: _almacen?.nombre ?? 'Todos los almacenes', alTocar: _elegirAlmacen, icono: Icons.warehouse),
            const SizedBox(height: 10),
            TextField(
              decoration: const InputDecoration(hintText: 'Buscar por código o nombre', prefixIcon: Icon(Icons.search, size: 20)),
              onChanged: (String v) => setState(() => _buscar = v.trim().toLowerCase()),
            ),
            const SizedBox(height: 10),
            Wrap(
              spacing: 8,
              runSpacing: 6,
              children: <Widget>[
                for (final List<String> s in const <List<String>>[
                  <String>['', 'Todos'],
                  <String>['SIN_STOCK', 'Sin stock'],
                  <String>['BAJO_MINIMO', 'Bajo mínimo'],
                  <String>['NORMAL', 'Normal'],
                ])
                  ChoiceChip(
                    label: Text(s[1]),
                    selected: _situacion == s[0],
                    selectedColor: Nd.verdeSuave,
                    onSelected: (_) => _cambiarSituacion(s[0]),
                  ),
              ],
            ),
          ]),
        ),
        espacio,
      ],
      slivers: <Widget>[
        Cargable<List<Map<String, dynamic>>>.sliver(
          key: _carga,
          cargar: _cargar,
          constructor: (BuildContext context, List<Map<String, dynamic>> datos, Future<void> Function() recargar) {
            final List<Map<String, dynamic>> lista = datos.where((Map<String, dynamic> s) {
              return _buscar.isEmpty ||
                  tx(s['codigo']).toLowerCase().contains(_buscar) ||
                  tx(s['descripcion']).toLowerCase().contains(_buscar);
            }).toList();
            if (lista.isEmpty) {
              return caja(const Aviso(icono: Icons.inventory_2, texto: 'No hay registros de stock con esos filtros.'));
            }
            return SliverTarjetaLista(
              cabecera: CabeceraTarjeta(titulo: '${lista.length} registro${lista.length == 1 ? '' : 's'}', icono: Icons.inventory),
              cantidad: lista.length,
              fila: (BuildContext c, int i) => _FilaStock(lista[i]),
            );
          },
        ),
      ],
    );
  }
}

class _FilaStock extends StatelessWidget {
  final Map<String, dynamic> s;

  const _FilaStock(this.s);

  @override
  Widget build(BuildContext context) {
    final String unidad = tx(s['unidad']);
    return InkWell(
      onTap: () => Navigator.of(context).push(MaterialPageRoute<void>(
        builder: (BuildContext c) => KardexPantalla(
          idInsumo: ent(s['id_insumo']),
          nombreInsumo: '${tx(s['codigo'])} - ${tx(s['descripcion'])}',
          idAlmacen: ent(s['id_almacen']),
          nombreAlmacen: tx(s['almacen']),
        ),
      )),
      child: Container(
        padding: const EdgeInsets.fromLTRB(16, 12, 12, 12),
        decoration: const BoxDecoration(border: Border(bottom: BorderSide(color: Nd.borde))),
        child: Row(
          children: <Widget>[
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Text(tx(s['descripcion']), style: const TextStyle(fontWeight: FontWeight.w600, color: Nd.titulo, fontSize: 15)),
                  const SizedBox(height: 2),
                  Text('${tx(s['codigo'])}  ·  ${tx(s['almacen'])} (${tx(s['centro'])})',
                      style: const TextStyle(color: Nd.apagado, fontSize: 12.5)),
                  const SizedBox(height: 4),
                  Row(children: <Widget>[
                    ChipEstado(tx(s['situacion'])),
                    const SizedBox(width: 8),
                    Text('Mínimo ${ent(s['minimo'])}  ·  Gs. ${guaranies(s['valor'])}',
                        style: const TextStyle(color: Nd.apagado, fontSize: 12.5)),
                  ]),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: <Widget>[
                Text(guaranies(s['actual']),
                    style: TextStyle(
                        fontSize: 22,
                        fontWeight: FontWeight.w700,
                        color: ent(s['actual']) <= 0 ? Nd.peligro : Nd.titulo)),
                Text(unidad, style: const TextStyle(color: Nd.apagado, fontSize: 12)),
              ],
            ),
            const Icon(Icons.chevron_right, color: Nd.apagado),
          ],
        ),
      ),
    );
  }
}
