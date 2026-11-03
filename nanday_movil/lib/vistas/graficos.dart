import 'dart:math' show pi;

import 'package:flutter/material.dart';

import '../api.dart';
import '../tema.dart';
import 'comunes.dart';

/// Gráficos del tablero, iguales a los de la web (dashboardcontrolador.jsp?tipo=graficos):
///   OT por estado (últimos 12 meses), órdenes de compra por mes (últimos 6 meses) y
///   consumo por centro de costo (últimos 90 días, en guaraníes).
/// El servidor solo envía los gráficos que el rol puede ver; si no envía ninguno, no se muestra nada.
class GraficosTablero extends StatelessWidget {
  const GraficosTablero({super.key});

  static const Map<String, Color> _coloresOT = <String, Color>{
    'PENDIENTE': Color(0xFF6C757D),
    'EN_PROCESO': Color(0xFFF39C12),
    'FINALIZADA': Nd.primario,
    'ANULADA': Nd.peligro,
  };

  @override
  Widget build(BuildContext context) {
    return Cargable<Map<String, dynamic>>(
      cargar: () async => mapa(await Api.ctrl('dashboardcontrolador.jsp', 'graficos')),
      constructor: (BuildContext context, Map<String, dynamic> d, Future<void> Function() recargar) {
        final List<Map<String, dynamic>> ot = mapas(d['ot_estado']);
        final List<Map<String, dynamic>> oc = mapas(d['oc_mes']);
        final List<Map<String, dynamic>> consumo = mapas(d['consumo_cc']);
        final List<Widget> tarjetas = <Widget>[
          if (d.containsKey('ot_estado'))
            _TarjetaGrafico(
              titulo: 'OT por estado',
              nota: 'últimos 12 meses',
              icono: Icons.donut_large,
              acento: Nd.primario,
              child: _Dona(
                etiquetas: <String>[for (final Map<String, dynamic> p in ot) legible(tx(p['etiqueta']))],
                valores: <double>[for (final Map<String, dynamic> p in ot) ent(p['cantidad']).toDouble()],
                colores: <Color>[for (final Map<String, dynamic> p in ot) _coloresOT[tx(p['etiqueta'])] ?? Nd.apagado],
              ),
            ),
          if (d.containsKey('oc_mes'))
            _TarjetaGrafico(
              titulo: 'Órdenes de compra por mes',
              nota: 'últimos 6 meses',
              icono: Icons.bar_chart,
              acento: const Color(0xFF1D5E8C),
              child: _BarrasVerticales(
                etiquetas: <String>[for (final Map<String, dynamic> p in oc) tx(p['etiqueta'])],
                valores: <int>[for (final Map<String, dynamic> p in oc) ent(p['cantidad'])],
                color: const Color(0xFF1D5E8C),
              ),
            ),
          if (d.containsKey('consumo_cc'))
            _TarjetaGrafico(
              titulo: 'Consumo por centro de costo',
              nota: '90 días, en guaraníes',
              icono: Icons.stacked_bar_chart,
              acento: Nd.oscuro,
              child: _BarrasHorizontales(
                etiquetas: <String>[for (final Map<String, dynamic> p in consumo) tx(p['etiqueta'])],
                valores: <double>[for (final Map<String, dynamic> p in consumo) double.tryParse(tx(p['valor'])) ?? 0],
              ),
            ),
        ];
        if (tarjetas.isEmpty) return const SizedBox.shrink();
        return Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: LayoutBuilder(
            builder: (BuildContext context, BoxConstraints c) {
              final int columnas = c.maxWidth >= 900 ? 3 : (c.maxWidth >= 600 ? 2 : 1);
              final double ancho = (c.maxWidth - (columnas - 1) * 12) / columnas;
              return Wrap(
                spacing: 12,
                runSpacing: 12,
                children: <Widget>[for (final Widget t in tarjetas) SizedBox(width: ancho, child: t)],
              );
            },
          ),
        );
      },
    );
  }
}

/// Tarjeta de un gráfico: título, nota en gris y el gráfico.
class _TarjetaGrafico extends StatelessWidget {
  final String titulo;
  final String nota;
  final IconData icono;
  final Color acento;
  final Widget child;

  const _TarjetaGrafico({required this.titulo, required this.nota, required this.icono, required this.acento, required this.child});

  @override
  Widget build(BuildContext context) {
    return Tarjeta(
      margen: EdgeInsets.zero,
      acento: acento,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          CabeceraTarjeta(
            titulo: titulo,
            icono: icono,
            derecha: Text(nota, style: const TextStyle(color: Nd.apagado, fontSize: 12)),
          ),
          Padding(padding: const EdgeInsets.fromLTRB(14, 14, 14, 12), child: child),
        ],
      ),
    );
  }
}

const Widget _sinDatos = SizedBox(
  height: 120,
  child: Center(child: Text('Sin datos en el período', style: TextStyle(color: Nd.apagado))),
);

/// Gráfico de dona con su leyenda (etiqueta y cantidad).
class _Dona extends StatelessWidget {
  final List<String> etiquetas;
  final List<double> valores;
  final List<Color> colores;

  const _Dona({required this.etiquetas, required this.valores, required this.colores});

  @override
  Widget build(BuildContext context) {
    final double total = valores.fold<double>(0, (double a, double b) => a + b);
    if (total <= 0) return _sinDatos;
    return Column(
      children: <Widget>[
        SizedBox(
          height: 160,
          child: Stack(
            alignment: Alignment.center,
            children: <Widget>[
              CustomPaint(size: const Size(160, 160), painter: _PintorDona(valores, colores)),
              Column(
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  Text(guaranies(total), style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w700, color: Nd.titulo)),
                  const Text('OT', style: TextStyle(fontSize: 12, color: Nd.apagado)),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        Wrap(
          alignment: WrapAlignment.center,
          spacing: 14,
          runSpacing: 6,
          children: <Widget>[
            for (int i = 0; i < etiquetas.length; i++)
              Row(
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  Container(width: 12, height: 12, decoration: BoxDecoration(color: colores[i], borderRadius: BorderRadius.circular(3))),
                  const SizedBox(width: 6),
                  Text('${etiquetas[i]}: ${guaranies(valores[i])}', style: const TextStyle(fontSize: 12.5, color: Nd.pizarra)),
                ],
              ),
          ],
        ),
      ],
    );
  }
}

class _PintorDona extends CustomPainter {
  final List<double> valores;
  final List<Color> colores;

  _PintorDona(this.valores, this.colores);

  @override
  void paint(Canvas canvas, Size size) {
    final double total = valores.fold<double>(0, (double a, double b) => a + b);
    final double grosor = size.shortestSide * 0.2;
    final Rect rect = Rect.fromCircle(center: size.center(Offset.zero), radius: (size.shortestSide - grosor) / 2);
    final Paint pincel = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = grosor;
    if (total <= 0) {
      pincel.color = Nd.borde;
      canvas.drawArc(rect, 0, 2 * pi, false, pincel);
      return;
    }
    // Separación blanca de 2 px entre sectores (como el gráfico de la web)
    final double separacion = valores.where((double v) => v > 0).length > 1 ? 2 / (rect.width / 2) : 0;
    double inicio = -pi / 2;
    for (int i = 0; i < valores.length; i++) {
      if (valores[i] <= 0) continue;
      final double barrido = 2 * pi * valores[i] / total;
      pincel.color = colores[i];
      canvas.drawArc(rect, inicio + separacion / 2, barrido - separacion, false, pincel);
      inicio += barrido;
    }
  }

  @override
  bool shouldRepaint(_PintorDona anterior) => anterior.valores != valores || anterior.colores != colores;
}

/// Barras verticales con la cantidad arriba y la etiqueta abajo.
class _BarrasVerticales extends StatelessWidget {
  final List<String> etiquetas;
  final List<int> valores;
  final Color color;

  const _BarrasVerticales({required this.etiquetas, required this.valores, required this.color});

  @override
  Widget build(BuildContext context) {
    final int maximo = valores.fold<int>(0, (int a, int b) => b > a ? b : a);
    if (maximo <= 0) return _sinDatos;
    const double alto = 140;
    return SizedBox(
      height: alto + 44,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: <Widget>[
          for (int i = 0; i < valores.length; i++)
            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 4),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: <Widget>[
                    Text(guaranies(valores[i]), style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: Nd.pizarra)),
                    const SizedBox(height: 4),
                    Container(
                      height: valores[i] <= 0 ? 2 : alto * valores[i] / maximo,
                      decoration: BoxDecoration(
                        color: valores[i] <= 0 ? Nd.borde : color,
                        borderRadius: const BorderRadius.vertical(top: Radius.circular(4)),
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(_mesCorto(etiquetas[i]), style: const TextStyle(fontSize: 11, color: Nd.apagado)),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }

  /// MM/AAAA -> MM/AA (para que entre en pantallas angostas).
  static String _mesCorto(String e) {
    final RegExpMatch? m = RegExp(r'^(\d{2})/\d{2}(\d{2})$').firstMatch(e);
    return m == null ? e : '${m.group(1)}/${m.group(2)}';
  }
}

/// Barras horizontales: etiqueta a la izquierda, barra y monto en guaraníes.
class _BarrasHorizontales extends StatelessWidget {
  final List<String> etiquetas;
  final List<double> valores;

  const _BarrasHorizontales({required this.etiquetas, required this.valores});

  @override
  Widget build(BuildContext context) {
    final double maximo = valores.fold<double>(0, (double a, double b) => b > a ? b : a);
    if (maximo <= 0) return _sinDatos;
    return Column(
      children: <Widget>[
        for (int i = 0; i < valores.length; i++)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 5),
            child: Row(
              children: <Widget>[
                SizedBox(
                  width: 78,
                  child: Text(etiquetas[i],
                      maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12.5, color: Nd.pizarra)),
                ),
                Expanded(
                  child: Align(
                    alignment: Alignment.centerLeft,
                    child: FractionallySizedBox(
                      widthFactor: (valores[i] / maximo).clamp(0.02, 1.0).toDouble(),
                      child: Container(
                        height: 18,
                        decoration: BoxDecoration(color: Nd.primario, borderRadius: BorderRadius.circular(4)),
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                Text(guaranies(valores[i]), style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: Nd.titulo)),
              ],
            ),
          ),
      ],
    );
  }
}
