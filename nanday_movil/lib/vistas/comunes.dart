import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api.dart';
import '../sesion.dart';
import '../tema.dart';
import 'login.dart';

// =====================================================================
//  Mensajes
// =====================================================================

/// Mensaje breve en la parte inferior (verde = correcto, rojo = error).
void avisar(BuildContext context, String texto, {bool error = false}) {
  final ScaffoldMessengerState mensajero = ScaffoldMessenger.of(context);
  mensajero.hideCurrentSnackBar();
  mensajero.showSnackBar(
    SnackBar(
      content: Row(
        children: <Widget>[
          Icon(error ? Icons.error_outline : Icons.check_circle_outline, color: Colors.white),
          const SizedBox(width: 10),
          Expanded(child: Text(texto, style: const TextStyle(fontWeight: FontWeight.w600))),
        ],
      ),
      backgroundColor: error ? Nd.peligro : Nd.primario,
    ),
  );
}

/// Muestra el error de una llamada a la API. Si la sesión venció (401), vuelve al login.
void manejarError(BuildContext context, Object e) {
  final String texto = e is ApiError ? e.mensaje : 'Ocurrió un error inesperado.';
  avisar(context, texto, error: true);
  if (e is ApiError && e.sesionVencida) {
    Sesion.cerrar();
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute<void>(builder: (BuildContext context) => const Login()),
      (Route<dynamic> ruta) => false,
    );
  }
}

/// Número con separador de miles: 1500000 -> 1.500.000
String guaranies(dynamic valor) {
  final num n = valor is num ? valor : (num.tryParse('${valor ?? ''}') ?? 0);
  final int redondo = n.round();
  final String entero = redondo.abs().toString();
  final StringBuffer salida = StringBuffer(redondo < 0 ? '-' : '');
  for (int i = 0; i < entero.length; i++) {
    if (i > 0 && (entero.length - i) % 3 == 0) {
      salida.write('.');
    }
    salida.write(entero[i]);
  }
  return salida.toString();
}

// =====================================================================
//  Estructura de página (igual que la web: topbar blanco, fondo gris,
//  encabezado con título y migas, contenido en tarjetas y pie de página)
// =====================================================================

/// Barra superior blanca con el loro y "Ñanday SIAA" (como el topbar de la web).
PreferredSizeWidget barraNanday({List<Widget>? acciones}) {
  return AppBar(
    titleSpacing: 0,
    title: Row(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        Image.asset('assets/imagenes/logo_ave.png', width: 30, height: 30),
        const SizedBox(width: 8),
        const Text.rich(
          TextSpan(
            children: <InlineSpan>[
              TextSpan(text: 'Ñanday', style: TextStyle(fontWeight: FontWeight.w700, color: Nd.profundo)),
              TextSpan(text: ' SIAA'),
            ],
          ),
        ),
      ],
    ),
    actions: acciones,
  );
}

/// Título grande de la sección con las "migas" (Inicio / Referenciales / Proveedores).
class EncabezadoPagina extends StatelessWidget {
  final String titulo;
  final List<String> migas;

  const EncabezadoPagina({super.key, required this.titulo, this.migas = const <String>[]});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 18, 16, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text(
            titulo,
            style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w700, color: Nd.titulo, height: 1.15),
          ),
          if (migas.isNotEmpty) ...<Widget>[
            const SizedBox(height: 4),
            Text(
              migas.join('  /  '),
              style: const TextStyle(fontSize: 12.5, color: Nd.apagado),
            ),
          ],
        ],
      ),
    );
  }
}

/// Tarjeta blanca de la web (.card): radio 14, borde gris, sombra suave.
/// [acento]: franja superior de color (como .card-outline o los héroes).
class Tarjeta extends StatelessWidget {
  final Widget child;
  final Color? acento;
  final EdgeInsetsGeometry margen;

  const Tarjeta({super.key, required this.child, this.acento, this.margen = const EdgeInsets.symmetric(horizontal: 16)});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: margen,
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Nd.borde),
        boxShadow: Nd.sombra,
      ),
      clipBehavior: Clip.antiAlias,
      child: acento == null
          ? child
          : Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                Container(height: 4, color: acento),
                child,
              ],
            ),
    );
  }
}

/// Cabecera de tarjeta (.card-header): título en negrita y una línea gris debajo.
class CabeceraTarjeta extends StatelessWidget {
  final String titulo;
  final IconData? icono;
  final Widget? derecha;

  const CabeceraTarjeta({super.key, required this.titulo, this.icono, this.derecha});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 14, 12, 14),
      decoration: const BoxDecoration(border: Border(bottom: BorderSide(color: Nd.borde))),
      child: Row(
        children: <Widget>[
          if (icono != null) ...<Widget>[
            Icon(icono, size: 19, color: Nd.primario),
            const SizedBox(width: 8),
          ],
          Expanded(
            child: Text(titulo, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700, color: Nd.titulo)),
          ),
          ?derecha,
        ],
      ),
    );
  }
}

/// Botón cuadrado de acción de las tablas de la web:
/// naranja (.btn-warning, modificar), rojo (.btn-danger, eliminar), azul (.btn-info).
class BotonAccion extends StatelessWidget {
  final IconData icono;
  final Color color;
  final String ayuda;
  final VoidCallback? alTocar;

  const BotonAccion({super.key, required this.icono, required this.color, required this.ayuda, this.alTocar});

  const BotonAccion.modificar({super.key, this.alTocar})
      : icono = Icons.edit,
        color = Nd.acento,
        ayuda = 'Modificar';

  const BotonAccion.eliminar({super.key, this.alTocar})
      : icono = Icons.delete,
        color = Nd.peligro,
        ayuda = 'Eliminar';

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: ayuda,
      child: Material(
        color: color,
        borderRadius: BorderRadius.circular(10),
        child: InkWell(
          borderRadius: BorderRadius.circular(10),
          onTap: alTocar,
          child: SizedBox(width: 38, height: 38, child: Icon(icono, size: 19, color: Colors.white)),
        ),
      ),
    );
  }
}

/// Insignia "Activo / Inactivo" (.badge-success / .badge-secondary).
class Insignia extends StatelessWidget {
  final String texto;
  final Color color;

  const Insignia({super.key, required this.texto, required this.color});

  factory Insignia.activo(bool activo) =>
      activo ? const Insignia(texto: 'Activo', color: Nd.primario) : const Insignia(texto: 'Inactivo', color: Nd.apagado);

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(6)),
      child: Text(
        texto,
        style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.w700),
      ),
    );
  }
}

/// Aviso de la web (.alert-info del tablero y .alert-danger del login).
class Alerta extends StatelessWidget {
  final String texto;
  final String? titulo;
  final bool error;

  const Alerta({super.key, required this.texto, this.titulo, this.error = false});

  @override
  Widget build(BuildContext context) {
    final Color fondo = error ? const Color(0xFFFDECEA) : Nd.verdeSuave;
    final Color borde = error ? const Color(0xFFF5C2C0) : Nd.verdeBorde;
    final Color letra = error ? const Color(0xFF7F1D1D) : Nd.loginDesde;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: fondo,
        border: Border.all(color: borde),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Icon(error ? Icons.error_outline : Icons.info_outline, color: error ? Nd.peligro : Nd.primario, size: 22),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                if (titulo != null) ...<Widget>[
                  Text(titulo!, style: TextStyle(fontWeight: FontWeight.w700, fontSize: 15.5, color: letra)),
                  const SizedBox(height: 4),
                ],
                Text(texto, style: TextStyle(color: letra, height: 1.35)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// Campo de formulario con la etiqueta arriba, como los .form-group de los modales de la web.
class CampoFormulario extends StatelessWidget {
  final String etiqueta;
  final Widget child;
  final bool obligatorio;

  const CampoFormulario({super.key, required this.etiqueta, required this.child, this.obligatorio = false});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          Text.rich(
            TextSpan(
              text: etiqueta,
              children: <InlineSpan>[
                if (obligatorio) const TextSpan(text: ' *', style: TextStyle(color: Nd.peligro)),
              ],
            ),
            style: const TextStyle(fontWeight: FontWeight.w700, color: Nd.pizarra, fontSize: 14),
          ),
          const SizedBox(height: 6),
          child,
        ],
      ),
    );
  }
}

/// Pie de página de la web (.main-footer).
class PiePagina extends StatelessWidget {
  const PiePagina({super.key});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 24, 16, 20),
      child: Text.rich(
        TextSpan(
          children: <InlineSpan>[
            TextSpan(
              text: '© ${DateTime.now().year} Ñanday SIAA',
              style: const TextStyle(color: Nd.profundo, fontWeight: FontWeight.w700),
            ),
            const TextSpan(text: ' - Sistema de Gestión de Activos e Insumos.'),
          ],
        ),
        textAlign: TextAlign.center,
        style: const TextStyle(fontSize: 12, color: Nd.apagado),
      ),
    );
  }
}

/// Pantalla centrada para "sin datos" o "error con reintento".
class Aviso extends StatelessWidget {
  final IconData icono;
  final String texto;
  final VoidCallback? alReintentar;

  const Aviso({super.key, required this.icono, required this.texto, this.alReintentar});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Container(
              width: 64,
              height: 64,
              decoration: BoxDecoration(color: Nd.verdeSuave, borderRadius: BorderRadius.circular(16)),
              child: Icon(icono, size: 32, color: Nd.primario),
            ),
            const SizedBox(height: 14),
            Text(texto, textAlign: TextAlign.center, style: const TextStyle(color: Nd.pizarra)),
            if (alReintentar != null) ...<Widget>[
              const SizedBox(height: 16),
              FilledButton.icon(
                onPressed: alReintentar,
                icon: const Icon(Icons.refresh),
                label: const Text('Reintentar'),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

/// Indicador de carga con el verde de la marca.
class Cargando extends StatelessWidget {
  const Cargando({super.key});

  @override
  Widget build(BuildContext context) => const Center(child: CircularProgressIndicator());
}

/// Diálogo de confirmación para eliminar, como el modal "Eliminar ..." de la web.
Future<bool> confirmarEliminacion(BuildContext context, {required String titulo, required String mensaje}) async {
  final bool? ok = await showDialog<bool>(
    context: context,
    builder: (BuildContext dialogo) => AlertDialog(
      titlePadding: EdgeInsets.zero,
      contentPadding: const EdgeInsets.fromLTRB(20, 18, 20, 8),
      actionsPadding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
      title: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          Container(
            height: 4,
            decoration: const BoxDecoration(
              color: Nd.peligro,
              borderRadius: BorderRadius.vertical(top: Radius.circular(14)),
            ),
          ),
          Container(
            padding: const EdgeInsets.fromLTRB(20, 16, 20, 14),
            decoration: const BoxDecoration(border: Border(bottom: BorderSide(color: Nd.borde))),
            child: Text(titulo, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700, color: Nd.titulo)),
          ),
        ],
      ),
      content: Text(mensaje, style: const TextStyle(fontSize: 15, color: Nd.pizarra, height: 1.4)),
      actions: <Widget>[
        OutlinedButton(onPressed: () => Navigator.of(dialogo).pop(false), child: const Text('Cerrar')),
        FilledButton(
          style: FilledButton.styleFrom(backgroundColor: Nd.peligro),
          onPressed: () => Navigator.of(dialogo).pop(true),
          child: const Text('Eliminar'),
        ),
      ],
    ),
  );
  return ok == true;
}

// =====================================================================
//  Lectura segura de los datos JSON del servidor
// =====================================================================

/// Lista de objetos JSON -> List<Map>.
List<Map<String, dynamic>> mapas(dynamic datos) => <Map<String, dynamic>>[
      if (datos is List)
        for (final dynamic x in datos)
          if (x is Map) Map<String, dynamic>.from(x),
    ];

/// Objeto JSON -> Map (vacío si no lo es).
Map<String, dynamic> mapa(dynamic datos) => datos is Map ? Map<String, dynamic>.from(datos) : <String, dynamic>{};

/// Número entero de un valor JSON (número o texto).
int ent(dynamic v) => v is num ? v.toInt() : (int.tryParse('${v ?? ''}') ?? 0);

/// Texto de un valor JSON ('' si es null).
String tx(dynamic v) => v == null ? '' : v.toString();

/// Nombre legible de un código: EN_PROCESO -> En proceso
String legible(String codigo) {
  if (codigo.isEmpty) return '';
  final String t = codigo.replaceAll('_', ' ').toLowerCase();
  return t[0].toUpperCase() + t.substring(1);
}

/// Fecha ISO yyyy-MM-dd -> dd/MM/yyyy (deja igual cualquier otro formato).
String fechaCorta(String iso) {
  final RegExpMatch? m = RegExp(r'^(\d{4})-(\d{2})-(\d{2})').firstMatch(iso);
  return m == null ? iso : '${m.group(3)}/${m.group(2)}/${m.group(1)}';
}

// =====================================================================
//  Estructura de pantalla con carga, error y reintento
// =====================================================================

/// Pantalla estándar: barra superior, título con migas, contenido y pie de página.
///
/// [hijos]: widgets comunes (tarjetas, formularios).
/// [slivers]: se agregan después de [hijos]. Para listas largas se usan slivers
/// (ver [SliverTarjetaLista] y [Cargable.sliver]): solo se dibujan las filas que se ven en
/// pantalla, así la app no se pone lenta con cientos de registros.
class PaginaApp extends StatelessWidget {
  final String titulo;
  final List<String> migas;
  final List<Widget> hijos;
  final List<Widget> slivers;
  final Future<void> Function()? alRefrescar;
  final List<Widget>? acciones;
  final Widget? barraInferior;

  const PaginaApp({
    super.key,
    required this.titulo,
    this.migas = const <String>[],
    this.hijos = const <Widget>[],
    this.slivers = const <Widget>[],
    this.alRefrescar,
    this.acciones,
    this.barraInferior,
  });

  @override
  Widget build(BuildContext context) {
    final Widget lista = CustomScrollView(
      physics: const AlwaysScrollableScrollPhysics(),
      slivers: <Widget>[
        SliverToBoxAdapter(child: EncabezadoPagina(titulo: titulo, migas: migas)),
        if (hijos.isNotEmpty) SliverList(delegate: SliverChildListDelegate(hijos)),
        ...slivers,
        const SliverToBoxAdapter(child: PiePagina()),
        const SliverToBoxAdapter(child: SizedBox(height: 8)),
      ],
    );
    return Scaffold(
      appBar: barraNanday(acciones: acciones),
      body: alRefrescar == null ? lista : RefreshIndicator(onRefresh: alRefrescar!, child: lista),
      bottomNavigationBar: barraInferior,
    );
  }
}

/// Carga datos del servidor y los muestra; mientras carga muestra el indicador y, si falla,
/// el error con "Reintentar". Si la sesión venció, vuelve al login.
///
/// Con [Cargable.sliver] el [constructor] debe devolver un sliver (por ejemplo [SliverTarjetaLista]);
/// se usa dentro de [PaginaApp.slivers] para listas largas.
class Cargable<T> extends StatefulWidget {
  final Future<T> Function() cargar;
  final Widget Function(BuildContext context, T datos, Future<void> Function() recargar) constructor;
  final bool sliver;

  const Cargable({super.key, required this.cargar, required this.constructor}) : sliver = false;

  const Cargable.sliver({super.key, required this.cargar, required this.constructor}) : sliver = true;

  @override
  State<Cargable<T>> createState() => CargableEstado<T>();
}

class CargableEstado<T> extends State<Cargable<T>> {
  T? _datos;
  String? _error;
  bool _cargando = true;

  @override
  void initState() {
    super.initState();
    recargar();
  }

  Future<void> recargar() async {
    setState(() {
      _cargando = true;
      _error = null;
    });
    try {
      final T d = await widget.cargar();
      if (!mounted) return;
      setState(() {
        _datos = d;
        _cargando = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _cargando = false;
        _error = e is ApiError ? e.mensaje : 'No se pudieron obtener los datos.';
      });
      if (e is ApiError && e.sesionVencida) manejarError(context, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    Widget? aviso;
    if (_cargando && _datos == null) {
      aviso = const Padding(padding: EdgeInsets.all(40), child: Cargando());
    } else if (_error != null && _datos == null) {
      aviso = Aviso(icono: Icons.cloud_off, texto: _error!, alReintentar: recargar);
    }
    if (aviso != null) {
      return widget.sliver ? SliverToBoxAdapter(child: aviso) : aviso;
    }
    return widget.constructor(context, _datos as T, recargar);
  }
}

// =====================================================================
//  Piezas de pantalla
// =====================================================================

/// Insignia de estado con el color que usa la web para cada estado.
class ChipEstado extends StatelessWidget {
  final String estado;
  final String? texto;

  const ChipEstado(this.estado, {super.key, this.texto});

  static Color colorDe(String estado) {
    switch (estado) {
      case 'PENDIENTE':
      case 'PARCIAL':
      case 'EN_TRANSITO':
      case 'BAJO_MINIMO':
        return Nd.acentoProfundo;
      case 'EN_PROCESO':
      case 'ASIGNADO':
      case 'SOLICITADO':
      case 'EN_CURSO':
        return Nd.info;
      case 'FINALIZADA':
      case 'DISPONIBLE':
      case 'RECIBIDO':
      case 'APROBADA':
      case 'NORMAL':
      case 'VIGENTE':
        return Nd.primario;
      case 'EN_REPARACION':
        return Nd.teal;
      case 'BAJA':
      case 'RECHAZADA':
      case 'RECHAZADO_DESTINO':
      case 'SIN_STOCK':
        return Nd.peligro;
      default:
        return Nd.apagado;
    }
  }

  @override
  Widget build(BuildContext context) => Insignia(texto: texto ?? legible(estado), color: colorDe(estado));
}

/// Fila "Etiqueta: valor" de una ficha.
class DatoFila extends StatelessWidget {
  final String etiqueta;
  final String valor;

  const DatoFila(this.etiqueta, this.valor, {super.key});

  @override
  Widget build(BuildContext context) {
    if (valor.trim().isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          SizedBox(
            width: 130,
            child: Text(etiqueta, style: const TextStyle(color: Nd.apagado, fontSize: 13.5)),
          ),
          Expanded(
            child: Text(valor, style: const TextStyle(color: Nd.titulo, fontWeight: FontWeight.w600, fontSize: 14)),
          ),
        ],
      ),
    );
  }
}

/// Bloque con padding para el cuerpo de una tarjeta.
class CuerpoTarjeta extends StatelessWidget {
  final List<Widget> hijos;

  const CuerpoTarjeta(this.hijos, {super.key});

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 14),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: hijos),
      );
}

/// Separación vertical entre tarjetas.
const Widget espacio = SizedBox(height: 14);

/// Botón de ancho completo para la acción principal de una pantalla.
class BotonPrincipal extends StatelessWidget {
  final String texto;
  final IconData icono;
  final VoidCallback? alTocar;
  final bool ocupado;
  final Color? color;

  const BotonPrincipal({
    super.key,
    required this.texto,
    required this.icono,
    required this.alTocar,
    this.ocupado = false,
    this.color,
  });

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 48,
      child: FilledButton.icon(
        style: color == null ? null : FilledButton.styleFrom(backgroundColor: color),
        onPressed: ocupado ? null : alTocar,
        icon: ocupado
            ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
            : Icon(icono),
        label: Text(ocupado ? 'Procesando...' : texto),
      ),
    );
  }
}

// =====================================================================
//  Selección de opciones (combos con buscador)
// =====================================================================

/// Opción de un combo: id, texto principal y un detalle opcional.
class Opcion {
  final int id;
  final String nombre;
  final String detalle;
  final Map<String, dynamic> datos;

  const Opcion(this.id, this.nombre, {this.detalle = '', this.datos = const <String, dynamic>{}});

  /// Desde la respuesta [{id, nombre}] de APInanday/c/movil.
  static List<Opcion> lista(dynamic json) =>
      mapas(json).map((Map<String, dynamic> m) => Opcion(ent(m['id']), tx(m['nombre']), datos: m)).toList();
}

/// Abre una hoja inferior con buscador para elegir una opción.
Future<Opcion?> elegirOpcion(BuildContext context, String titulo, List<Opcion> opciones) {
  return showModalBottomSheet<Opcion>(
    context: context,
    isScrollControlled: true,
    backgroundColor: Colors.white,
    shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
    builder: (BuildContext hoja) => _HojaOpciones(titulo: titulo, opciones: opciones),
  );
}

class _HojaOpciones extends StatefulWidget {
  final String titulo;
  final List<Opcion> opciones;

  const _HojaOpciones({required this.titulo, required this.opciones});

  @override
  State<_HojaOpciones> createState() => _HojaOpcionesState();
}

class _HojaOpcionesState extends State<_HojaOpciones> {
  String _buscar = '';

  @override
  Widget build(BuildContext context) {
    final String t = _buscar.toLowerCase();
    final List<Opcion> lista = widget.opciones
        .where((Opcion o) => t.isEmpty || o.nombre.toLowerCase().contains(t) || o.detalle.toLowerCase().contains(t))
        .toList();
    return DraggableScrollableSheet(
      expand: false,
      initialChildSize: 0.75,
      maxChildSize: 0.95,
      builder: (BuildContext context, ScrollController controlador) => Column(
        children: <Widget>[
          Container(
            margin: const EdgeInsets.only(top: 10),
            width: 40,
            height: 4,
            decoration: BoxDecoration(color: Nd.borde, borderRadius: BorderRadius.circular(4)),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 8),
            child: Text(widget.titulo, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700, color: Nd.titulo)),
          ),
          if (widget.opciones.length > 6)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
              child: TextField(
                autofocus: false,
                decoration: const InputDecoration(hintText: 'Buscar...', prefixIcon: Icon(Icons.search, size: 20)),
                onChanged: (String v) => setState(() => _buscar = v.trim()),
              ),
            ),
          const Divider(),
          Expanded(
            child: lista.isEmpty
                ? const Aviso(icono: Icons.inbox, texto: 'No hay opciones para elegir.')
                : ListView.separated(
                    controller: controlador,
                    itemCount: lista.length,
                    separatorBuilder: (BuildContext c, int i) => const Divider(indent: 16, endIndent: 16),
                    itemBuilder: (BuildContext c, int i) {
                      final Opcion o = lista[i];
                      return ListTile(
                        title: Text(o.nombre, style: const TextStyle(fontWeight: FontWeight.w600, color: Nd.titulo)),
                        subtitle: o.detalle.isEmpty ? null : Text(o.detalle),
                        trailing: const Icon(Icons.chevron_right, color: Nd.apagado),
                        onTap: () => Navigator.of(context).pop(o),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}

/// Campo que se ve como un combo de la web y abre el selector con buscador.
class CampoSeleccion extends StatelessWidget {
  final String texto; // texto de la opción elegida ('' = nada elegido)
  final String sugerencia;
  final VoidCallback? alTocar;
  final IconData icono;

  const CampoSeleccion({
    super.key,
    required this.texto,
    this.sugerencia = '-- Seleccionar --',
    required this.alTocar,
    this.icono = Icons.arrow_drop_down,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(10),
      onTap: alTocar,
      child: InputDecorator(
        decoration: InputDecoration(
          enabled: alTocar != null,
          suffixIcon: Icon(icono),
        ),
        child: Text(
          texto.isEmpty ? sugerencia : texto,
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
          style: TextStyle(color: texto.isEmpty ? const Color(0xFF9CA3AF) : Nd.titulo, fontSize: 15),
        ),
      ),
    );
  }
}

// =====================================================================
//  Diálogos
// =====================================================================

/// Pide confirmación. Devuelve true si el usuario aceptó.
Future<bool> confirmar(BuildContext context, String titulo, String mensaje,
    {String aceptar = 'Confirmar', Color color = Nd.primario}) async {
  final bool? ok = await showDialog<bool>(
    context: context,
    builder: (BuildContext d) => AlertDialog(
      title: Text(titulo, style: const TextStyle(fontWeight: FontWeight.w700, color: Nd.titulo, fontSize: 18)),
      content: Text(mensaje, style: const TextStyle(height: 1.4)),
      actions: <Widget>[
        OutlinedButton(onPressed: () => Navigator.of(d).pop(false), child: const Text('Cancelar')),
        FilledButton(
          style: FilledButton.styleFrom(backgroundColor: color),
          onPressed: () => Navigator.of(d).pop(true),
          child: Text(aceptar),
        ),
      ],
    ),
  );
  return ok == true;
}

/// Pide un texto (motivo, observación). Devuelve null si se canceló.
Future<String?> pedirTexto(BuildContext context,
    {required String titulo,
    String mensaje = '',
    String etiqueta = 'Motivo',
    bool obligatorio = true,
    String aceptar = 'Confirmar',
    Color color = Nd.primario}) {
  return showDialog<String>(
    context: context,
    builder: (BuildContext d) => _DialogoTexto(
      titulo: titulo,
      mensaje: mensaje,
      etiqueta: etiqueta,
      obligatorio: obligatorio,
      aceptar: aceptar,
      color: color,
    ),
  );
}

/// El diálogo tiene su propio estado para liberar el controlador del texto al cerrarse
/// (recién cuando termina la animación de salida).
class _DialogoTexto extends StatefulWidget {
  final String titulo;
  final String mensaje;
  final String etiqueta;
  final bool obligatorio;
  final String aceptar;
  final Color color;

  const _DialogoTexto({
    required this.titulo,
    required this.mensaje,
    required this.etiqueta,
    required this.obligatorio,
    required this.aceptar,
    required this.color,
  });

  @override
  State<_DialogoTexto> createState() => _DialogoTextoState();
}

class _DialogoTextoState extends State<_DialogoTexto> {
  final TextEditingController _txt = TextEditingController();
  final GlobalKey<FormState> _formulario = GlobalKey<FormState>();

  @override
  void dispose() {
    _txt.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.titulo, style: const TextStyle(fontWeight: FontWeight.w700, color: Nd.titulo, fontSize: 18)),
      content: Form(
        key: _formulario,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            if (widget.mensaje.isNotEmpty) ...<Widget>[
              Text(widget.mensaje, style: const TextStyle(height: 1.4)),
              const SizedBox(height: 14),
            ],
            TextFormField(
              controller: _txt,
              autofocus: true,
              minLines: 2,
              maxLines: 4,
              maxLength: 500,
              decoration: InputDecoration(hintText: widget.etiqueta + (widget.obligatorio ? ' (obligatorio)' : ' (opcional)')),
              validator: (String? v) => widget.obligatorio && (v ?? '').trim().isEmpty ? 'Complete este dato' : null,
            ),
          ],
        ),
      ),
      actions: <Widget>[
        OutlinedButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Cancelar')),
        FilledButton(
          style: FilledButton.styleFrom(backgroundColor: widget.color),
          onPressed: () {
            if (_formulario.currentState!.validate()) Navigator.of(context).pop(_txt.text.trim());
          },
          child: Text(widget.aceptar),
        ),
      ],
    );
  }
}

/// Pide una cantidad entera entre 1 y [maximo]. Devuelve null si se canceló.
Future<int?> pedirCantidad(BuildContext context, String titulo, int maximo) {
  return showDialog<int>(
    context: context,
    builder: (BuildContext d) => _DialogoCantidad(titulo: titulo, maximo: maximo),
  );
}

class _DialogoCantidad extends StatefulWidget {
  final String titulo;
  final int maximo;

  const _DialogoCantidad({required this.titulo, required this.maximo});

  @override
  State<_DialogoCantidad> createState() => _DialogoCantidadState();
}

class _DialogoCantidadState extends State<_DialogoCantidad> {
  final TextEditingController _txt = TextEditingController();
  final GlobalKey<FormState> _formulario = GlobalKey<FormState>();

  @override
  void dispose() {
    _txt.dispose();
    super.dispose();
  }

  void _aceptar() {
    if (_formulario.currentState!.validate()) Navigator.of(context).pop(int.tryParse(_txt.text.trim()));
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.titulo, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
      content: Form(
        key: _formulario,
        child: TextFormField(
          controller: _txt,
          autofocus: true,
          keyboardType: TextInputType.number,
          inputFormatters: <TextInputFormatter>[FilteringTextInputFormatter.digitsOnly],
          decoration: InputDecoration(hintText: 'Cantidad (máximo ${widget.maximo})'),
          onFieldSubmitted: (_) => _aceptar(),
          validator: (String? s) {
            final int n = int.tryParse(s ?? '') ?? 0;
            if (n <= 0) return 'Ingrese una cantidad mayor a 0';
            if (n > widget.maximo) return 'Solo hay ${widget.maximo} disponibles';
            return null;
          },
        ),
      ),
      actions: <Widget>[
        OutlinedButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Cancelar')),
        FilledButton(onPressed: _aceptar, child: const Text('Agregar')),
      ],
    );
  }
}

/// Muestra el resultado de una operación (número de documento generado, etc.).
Future<void> mostrarResultado(BuildContext context, String titulo, String mensaje) {
  return showDialog<void>(
    context: context,
    builder: (BuildContext d) => AlertDialog(
      icon: const Icon(Icons.check_circle, color: Nd.primario, size: 44),
      title: Text(titulo, style: const TextStyle(fontWeight: FontWeight.w700, color: Nd.titulo, fontSize: 18)),
      content: Text(mensaje, textAlign: TextAlign.center, style: const TextStyle(height: 1.4)),
      actions: <Widget>[
        FilledButton(onPressed: () => Navigator.of(d).pop(), child: const Text('Aceptar')),
      ],
    ),
  );
}

// =====================================================================
//  Datos de quien transporta (salidas, transferencias, salidas de activos)
// =====================================================================

/// Campos del transportista y del vehículo, con los mismos nombres que espera la web.
class DatosTransportista {
  final TextEditingController nombre = TextEditingController();
  final TextEditingController apellido = TextEditingController();
  final TextEditingController ci = TextEditingController();
  final TextEditingController marca = TextEditingController();
  final TextEditingController modelo = TextEditingController();
  final TextEditingController chapa = TextEditingController();

  Map<String, String> aMapa() => <String, String>{
        'transportista_nombre': nombre.text.trim(),
        'transportista_apellido': apellido.text.trim(),
        'transportista_ci': ci.text.trim(),
        'vehiculo_marca': marca.text.trim(),
        'vehiculo_modelo': modelo.text.trim(),
        'vehiculo_chapa': chapa.text.trim(),
      };

  Widget campos({required bool obligatorio}) {
    String? req(String? v) => obligatorio && (v ?? '').trim().isEmpty ? 'Obligatorio' : null;
    Widget campo(String etiqueta, TextEditingController c, int max, {bool o = false, TextInputType? teclado}) =>
        CampoFormulario(
          etiqueta: etiqueta,
          obligatorio: o && obligatorio,
          child: TextFormField(
            controller: c,
            maxLength: max,
            keyboardType: teclado,
            textCapitalization: TextCapitalization.words,
            decoration: const InputDecoration(counterText: ''),
            validator: o ? req : null,
          ),
        );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Expanded(child: campo('Nombre', nombre, 100, o: true)),
            const SizedBox(width: 12),
            Expanded(child: campo('Apellido', apellido, 100, o: true)),
          ],
        ),
        campo('Cédula de identidad', ci, 20, o: true, teclado: TextInputType.number),
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Expanded(child: campo('Vehículo: marca', marca, 50)),
            const SizedBox(width: 12),
            Expanded(child: campo('Modelo', modelo, 50)),
          ],
        ),
        campo('Chapa', chapa, 20),
      ],
    );
  }

  void dispose() {
    for (final TextEditingController c in <TextEditingController>[nombre, apellido, ci, marca, modelo, chapa]) {
      c.dispose();
    }
  }
}

// =====================================================================
//  Listas largas (solo se dibujan las filas visibles)
// =====================================================================

/// Envuelve un widget común para usarlo entre slivers.
Widget caja(Widget hijo) => SliverToBoxAdapter(child: hijo);

/// Tarjeta blanca (igual a [Tarjeta]) con una lista que se construye a medida que se
/// desplaza: con 1000 filas solo se dibujan las que entran en pantalla.
class SliverTarjetaLista extends StatelessWidget {
  final Widget? cabecera;
  final int cantidad;
  final IndexedWidgetBuilder fila;
  final Widget? pie;

  const SliverTarjetaLista({super.key, this.cabecera, required this.cantidad, required this.fila, this.pie});

  @override
  Widget build(BuildContext context) {
    final BorderRadius radio = BorderRadius.circular(14);
    return SliverPadding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      sliver: DecoratedSliver(
        decoration: BoxDecoration(color: Colors.white, borderRadius: radio, boxShadow: Nd.sombra),
        sliver: DecoratedSliver(
          // el borde va por encima de las filas para que el fondo de una fila no lo tape
          position: DecorationPosition.foreground,
          decoration: BoxDecoration(borderRadius: radio, border: Border.all(color: Nd.borde)),
          sliver: SliverMainAxisGroup(
            slivers: <Widget>[
              if (cabecera != null) SliverToBoxAdapter(child: cabecera),
              SliverList.builder(itemCount: cantidad, itemBuilder: fila),
              SliverToBoxAdapter(child: pie ?? const SizedBox(height: 6)),
            ],
          ),
        ),
      ),
    );
  }
}
