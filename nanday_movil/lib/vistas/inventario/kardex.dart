import 'package:flutter/material.dart';

import '../../api.dart';
import '../../tema.dart';
import '../comunes.dart';

/// INVENTARIOS > Kardex: movimientos de un insumo (todos los almacenes o uno), con saldo.
/// Usa inventarioControlador.jsp?tipo=kardex (el mismo de la web).
class KardexPantalla extends StatefulWidget {
  final int idInsumo;
  final String nombreInsumo;
  final int idAlmacen;
  final String nombreAlmacen;

  const KardexPantalla({super.key, this.idInsumo = 0, this.nombreInsumo = '', this.idAlmacen = 0, this.nombreAlmacen = ''});

  @override
  State<KardexPantalla> createState() => _KardexPantallaState();
}

class _KardexPantallaState extends State<KardexPantalla> {
  Opcion? _insumo;
  Opcion? _almacen;
  DateTime? _desde;
  DateTime? _hasta;
  int _version = 0; // fuerza la recarga al cambiar filtros

  @override
  void initState() {
    super.initState();
    if (widget.idInsumo > 0) _insumo = Opcion(widget.idInsumo, widget.nombreInsumo);
    if (widget.idAlmacen > 0) _almacen = Opcion(widget.idAlmacen, widget.nombreAlmacen);
  }

  String _iso(DateTime? d) => d == null
      ? ''
      : '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  String _dmy(DateTime? d) => d == null ? '' : '${d.day.toString().padLeft(2, '0')}/${d.month.toString().padLeft(2, '0')}/${d.year}';

  Future<void> _elegirInsumo() async {
    try {
      final List<Opcion> lista = mapas(await Api.movil('insumos'))
          .map((Map<String, dynamic> i) => Opcion(ent(i['id']), '${tx(i['codigo'])} - ${tx(i['nombre'])}',
              detalle: 'Stock en sus almacenes: ${ent(i['stock'])} ${tx(i['unidad'])}'))
          .toList();
      if (!mounted) return;
      final Opcion? o = await elegirOpcion(context, 'Insumo', lista);
      if (o != null) {
        setState(() {
        _insumo = o;
        _version++;
      });
      }
    } catch (e) {
      if (mounted) manejarError(context, e);
    }
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
      if (o != null) {
        setState(() {
        _almacen = o.id == 0 ? null : o;
        _version++;
      });
      }
    } catch (e) {
      if (mounted) manejarError(context, e);
    }
  }

  Future<void> _elegirFecha(bool desde) async {
    final DateTime ahora = DateTime.now();
    final DateTime? f = await showDatePicker(
      context: context,
      initialDate: (desde ? _desde : _hasta) ?? ahora,
      firstDate: DateTime(2020),
      lastDate: DateTime(ahora.year + 1),
      helpText: desde ? 'Desde (cancelar = sin límite)' : 'Hasta (cancelar = sin límite)',
    );
    if (!mounted) return;
    setState(() {
      if (desde) {
        _desde = f;
      } else {
        _hasta = f;
      }
      _version++;
    });
  }

  @override
  Widget build(BuildContext context) {
    return PaginaApp(
      titulo: 'Kardex',
      migas: const <String>['Inicio', 'Inventarios', 'Kardex'],
      hijos: <Widget>[
        Tarjeta(
          child: CuerpoTarjeta(<Widget>[
            CampoFormulario(
              etiqueta: 'Insumo',
              obligatorio: true,
              child: CampoSeleccion(texto: _insumo?.nombre ?? '', sugerencia: '-- Seleccionar insumo --', alTocar: _elegirInsumo),
            ),
            CampoFormulario(
              etiqueta: 'Almacén',
              child: CampoSeleccion(texto: _almacen?.nombre ?? 'Todos los almacenes', alTocar: _elegirAlmacen),
            ),
            Row(
              children: <Widget>[
                Expanded(
                  child: CampoFormulario(
                    etiqueta: 'Desde',
                    child: CampoSeleccion(texto: _dmy(_desde), sugerencia: 'Sin límite', icono: Icons.event, alTocar: () => _elegirFecha(true)),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: CampoFormulario(
                    etiqueta: 'Hasta',
                    child: CampoSeleccion(texto: _dmy(_hasta), sugerencia: 'Sin límite', icono: Icons.event, alTocar: () => _elegirFecha(false)),
                  ),
                ),
              ],
            ),
          ]),
        ),
        espacio,
      ],
      slivers: <Widget>[
        if (_insumo == null)
          caja(const Aviso(icono: Icons.timeline, texto: 'Elija un insumo para ver sus movimientos.'))
        else
          Cargable<List<Map<String, dynamic>>>.sliver(
            key: ValueKey<int>(_version),
            cargar: () async => mapas(await Api.ctrl('inventarioControlador.jsp', 'kardex', <String, String>{
              'id_insumo': '${_insumo!.id}',
              'id_almacen': '${_almacen?.id ?? 0}',
              'desde': _iso(_desde),
              'hasta': _iso(_hasta),
            })),
            constructor: (BuildContext context, List<Map<String, dynamic>> movs, Future<void> Function() recargar) {
              if (movs.isEmpty) {
                return caja(const Aviso(icono: Icons.inbox, texto: 'El insumo no tiene movimientos con esos filtros.'));
              }
              int entradas = 0, salidas = 0;
              for (final Map<String, dynamic> m in movs) {
                final int c = ent(m['cantidad']);
                if (c > 0) {
                  entradas += c;
                } else {
                  salidas -= c;
                }
              }
              final List<Map<String, dynamic>> recientes = movs.reversed.toList();
              return SliverMainAxisGroup(
                slivers: <Widget>[
                  caja(Tarjeta(
                    child: Padding(
                      padding: const EdgeInsets.all(14),
                      child: Row(
                        children: <Widget>[
                          _Cifra('Entradas', '+${guaranies(entradas)}', Nd.primario),
                          _Cifra('Salidas', '-${guaranies(salidas)}', Nd.peligro),
                          if (_almacen != null) _Cifra('Saldo', guaranies(recientes.first['saldo']), Nd.titulo),
                        ],
                      ),
                    ),
                  )),
                  caja(espacio),
                  SliverTarjetaLista(
                    cabecera: CabeceraTarjeta(
                        titulo: '${movs.length} movimiento${movs.length == 1 ? '' : 's'} (más recientes primero)', icono: Icons.timeline),
                    cantidad: recientes.length,
                    fila: (BuildContext c, int i) => _FilaKardex(recientes[i]),
                  ),
                ],
              );
            },
          ),
      ],
    );
  }
}

class _Cifra extends StatelessWidget {
  final String etiqueta;
  final String valor;
  final Color color;

  const _Cifra(this.etiqueta, this.valor, this.color);

  @override
  Widget build(BuildContext context) => Expanded(
        child: Column(
          children: <Widget>[
            Text(etiqueta.toUpperCase(),
                style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: Nd.apagado, letterSpacing: 0.5)),
            const SizedBox(height: 2),
            Text(valor, style: TextStyle(fontSize: 22, fontWeight: FontWeight.w700, color: color)),
          ],
        ),
      );
}

class _FilaKardex extends StatelessWidget {
  final Map<String, dynamic> m;

  const _FilaKardex(this.m);

  @override
  Widget build(BuildContext context) {
    final int cantidad = ent(m['cantidad']);
    final String obs = tx(m['observacion']);
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
      decoration: const BoxDecoration(border: Border(bottom: BorderSide(color: Nd.borde))),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(legible(tx(m['tipo'])), style: const TextStyle(fontWeight: FontWeight.w600, color: Nd.titulo)),
                Text('${tx(m['fecha'])}  ·  ${tx(m['documento'])}', style: const TextStyle(color: Nd.apagado, fontSize: 12.5)),
                Text('${tx(m['almacen'])}  ·  ${tx(m['usuario'])}', style: const TextStyle(color: Nd.apagado, fontSize: 12.5)),
                if (obs.isNotEmpty) Text(obs, style: const TextStyle(color: Nd.pizarra, fontSize: 12.5)),
              ],
            ),
          ),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: <Widget>[
              Text(cantidad > 0 ? '+$cantidad' : '$cantidad',
                  style: TextStyle(fontWeight: FontWeight.w700, fontSize: 17, color: cantidad > 0 ? Nd.primario : Nd.peligro)),
              Text('Saldo ${tx(m['saldo'])}', style: const TextStyle(color: Nd.apagado, fontSize: 12)),
            ],
          ),
        ],
      ),
    );
  }
}
