import 'package:flutter/material.dart';

import '../../api.dart';
import '../../sesion.dart';
import '../../tema.dart';
import '../comunes.dart';
import 'detalle_ot.dart';
import 'formulario_ot.dart';

/// ÓRDENES DE TRABAJO: listado (otControlador.jsp?tipo=listar), con los mismos filtros de la web:
/// estado (abiertas, pendiente, en proceso, finalizada, todas) y "mías" (solicitadas / a mi cargo).
class ListaOTPantalla extends StatefulWidget {
  final String mias; // '' | SOLICITADAS | A_CARGO

  /// true = abre directamente el formulario de nueva OT (opción "Nueva OT" del menú).
  final bool abrirNueva;

  const ListaOTPantalla({super.key, this.mias = '', this.abrirNueva = false});

  @override
  State<ListaOTPantalla> createState() => _ListaOTPantallaState();
}

class _ListaOTPantallaState extends State<ListaOTPantalla> {
  String _estado = 'ABIERTAS';
  late String _mias = widget.mias;
  String _buscar = '';
  int _version = 0;

  @override
  void initState() {
    super.initState();
    if (widget.abrirNueva) WidgetsBinding.instance.addPostFrameCallback((_) => _nueva());
  }

  /// Crea la OT y abre su ficha (desde ahí se suministran los insumos).
  Future<void> _nueva() async {
    final int? id = await Navigator.of(context)
        .push<int>(MaterialPageRoute<int>(builder: (BuildContext c) => const FormularioOTPantalla()));
    if (id == null || !mounted) return;
    setState(() => _version++);
    await Navigator.of(context).push(MaterialPageRoute<void>(builder: (BuildContext c) => DetalleOTPantalla(idOT: id)));
    if (mounted) setState(() => _version++);
  }

  void _filtro(VoidCallback cambio) {
    setState(() {
      cambio();
      _version++;
    });
  }

  @override
  Widget build(BuildContext context) {
    return PaginaApp(
      titulo: 'Órdenes de Trabajo',
      migas: const <String>['Inicio', 'Órdenes de trabajo'],
      alRefrescar: () async => setState(() => _version++),
      hijos: <Widget>[
        if (Sesion.puede(Sesion.otGestion)) ...<Widget>[
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: BotonPrincipal(texto: 'Nueva orden de trabajo', icono: Icons.add_task, alTocar: _nueva),
          ),
          espacio,
        ],
        Tarjeta(
          child: CuerpoTarjeta(<Widget>[
            TextField(
              decoration: const InputDecoration(hintText: 'Buscar por número o título', prefixIcon: Icon(Icons.search, size: 20)),
              onChanged: (String v) => setState(() => _buscar = v.trim().toLowerCase()),
            ),
            const SizedBox(height: 10),
            Wrap(
              spacing: 8,
              runSpacing: 6,
              children: <Widget>[
                for (final List<String> e in const <List<String>>[
                  <String>['ABIERTAS', 'Abiertas'],
                  <String>['PENDIENTE', 'Pendientes'],
                  <String>['EN_PROCESO', 'En proceso'],
                  <String>['FINALIZADA', 'Finalizadas'],
                  <String>['', 'Todas'],
                ])
                  ChoiceChip(
                    label: Text(e[1]),
                    selected: _estado == e[0],
                    selectedColor: Nd.verdeSuave,
                    onSelected: (_) => _filtro(() => _estado = e[0]),
                  ),
              ],
            ),
            const SizedBox(height: 6),
            Wrap(
              spacing: 8,
              children: <Widget>[
                for (final List<String> m in const <List<String>>[
                  <String>['', 'Todas las que veo'],
                  <String>['A_CARGO', 'A mi cargo'],
                  <String>['SOLICITADAS', 'Pedidas por mí'],
                ])
                  ChoiceChip(
                    label: Text(m[1]),
                    selected: _mias == m[0],
                    selectedColor: const Color(0xFFDBEAFE),
                    onSelected: (_) => _filtro(() => _mias = m[0]),
                  ),
              ],
            ),
          ]),
        ),
        espacio,
      ],
      slivers: <Widget>[
        Cargable<List<Map<String, dynamic>>>.sliver(
          key: ValueKey<int>(_version),
          cargar: () async => mapas(await Api.ctrl('otControlador.jsp', 'listar', <String, String>{'estado': _estado, 'mias': _mias})),
          constructor: (BuildContext context, List<Map<String, dynamic>> datos, Future<void> Function() recargar) {
            final List<Map<String, dynamic>> lista = datos
                .where((Map<String, dynamic> o) =>
                    _buscar.isEmpty ||
                    tx(o['numero']).toLowerCase().contains(_buscar) ||
                    tx(o['titulo']).toLowerCase().contains(_buscar))
                .toList();
            if (lista.isEmpty) {
              return caja(const Aviso(icono: Icons.assignment, texto: 'No hay órdenes de trabajo con esos filtros.'));
            }
            return SliverTarjetaLista(
              cabecera: CabeceraTarjeta(titulo: '${lista.length} orden(es) de trabajo', icono: Icons.assignment),
              cantidad: lista.length,
              fila: (BuildContext c, int i) {
                final Map<String, dynamic> o = lista[i];
                return _FilaOT(o, alTocar: () async {
                  await Navigator.of(context).push(MaterialPageRoute<void>(
                    builder: (BuildContext c) => DetalleOTPantalla(idOT: ent(o['id'])),
                  ));
                  recargar();
                });
              },
            );
          },
        ),
      ],
    );
  }
}

class _FilaOT extends StatelessWidget {
  final Map<String, dynamic> o;
  final VoidCallback alTocar;

  const _FilaOT(this.o, {required this.alTocar});

  @override
  Widget build(BuildContext context) {
    final String responsable = tx(o['responsable']);
    final int sinSalida = ent(o['sin_salida']);
    return InkWell(
      onTap: alTocar,
      child: Container(
        padding: const EdgeInsets.fromLTRB(16, 12, 12, 12),
        decoration: BoxDecoration(
          border: Border(
            bottom: const BorderSide(color: Nd.borde),
            left: BorderSide(color: ChipEstado.colorDe(tx(o['estado'])), width: 3),
          ),
        ),
        child: Row(
          children: <Widget>[
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Row(children: <Widget>[
                    Text(tx(o['numero']), style: const TextStyle(color: Nd.apagado, fontSize: 12.5, fontWeight: FontWeight.w600)),
                    const SizedBox(width: 8),
                    ChipEstado(tx(o['estado'])),
                    if (o['a_mi_cargo'] == true) ...<Widget>[
                      const SizedBox(width: 6),
                      const Insignia(texto: 'A mi cargo', color: Nd.info),
                    ],
                  ]),
                  const SizedBox(height: 4),
                  Text(tx(o['titulo']), maxLines: 2, overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontWeight: FontWeight.w600, color: Nd.titulo, fontSize: 15)),
                  Text('${tx(o['centro'])}  ·  ${tx(o['centro_costo'])}', style: const TextStyle(color: Nd.apagado, fontSize: 12.5)),
                  Text(
                    '${fechaCorta(tx(o['fecha']))}  ·  ${ent(o['items'])} insumo(s)'
                    '${responsable.isEmpty ? '' : '  ·  Resp.: $responsable'}',
                    style: const TextStyle(color: Nd.apagado, fontSize: 12.5),
                  ),
                  if (sinSalida > 0)
                    Text('$sinSalida insumo(s) sin salida', style: const TextStyle(color: Nd.acentoProfundo, fontSize: 12.5, fontWeight: FontWeight.w600)),
                ],
              ),
            ),
            const Icon(Icons.chevron_right, color: Nd.apagado),
          ],
        ),
      ),
    );
  }
}
