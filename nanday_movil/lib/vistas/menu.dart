import 'package:flutter/material.dart';

import '../alertas.dart';
import '../api.dart';
import '../sesion.dart';
import '../tema.dart';
import 'activos/aprobaciones.dart';
import 'activos/lista_activos.dart';
import 'activos/solicitud_activos.dart';
import 'bitacora.dart';
import 'comunes.dart';
import 'compras/recepcion_oc.dart';
import 'graficos.dart';
import 'insumos/lista_insumos.dart';
import 'inventario/ajustes.dart';
import 'inventario/inventario_fisico.dart';
import 'inventario/kardex.dart';
import 'inventario/salida_insumos.dart';
import 'inventario/stock.dart';
import 'inventario/transferencias.dart';
import 'login.dart';
import 'notificaciones.dart';
import 'ot/detalle_ot.dart';
import 'ot/lista_ot.dart';
import 'perfil.dart';
import 'proveedores/lista_proveedores.dart';
import 'referenciales/pantallas.dart';

/// Pantalla principal: copia del tablero web ("Accesos Rápidos") con el menú lateral oscuro.
/// Cada módulo y cada cifra se muestran solo si el rol del usuario tiene el permiso.
class Menu extends StatefulWidget {
  const Menu({super.key});

  @override
  State<Menu> createState() => _MenuState();
}

/// Cifras del tablero (null = sin permiso o no se pudo obtener).
class _Tablero {
  Map<String, dynamic>? ot;
  int? ocPendientes;
  List<Map<String, dynamic>>? alertas;
  Map<String, dynamic>? activos;
  int? porAprobar;
  int? porRecibir;
}

class _MenuState extends State<Menu> {
  _Tablero _t = _Tablero();
  bool _cargando = true;
  bool _cargandoAhora = false;
  int _version = 0; // vuelve a pedir los gráficos al actualizar
  final GlobalKey<CampanaNotificacionesState> _campana = GlobalKey<CampanaNotificacionesState>();

  @override
  void initState() {
    super.initState();
    _cargar();
  }

  /// Pide las cifras en paralelo a los mismos controladores que usa el tablero web.
  Future<void> _cargar() async {
    if (_cargandoAhora) return; // evita pedidos repetidos si se desliza varias veces seguidas
    _cargandoAhora = true;
    setState(() => _cargando = true);
    final _Tablero t = _Tablero();
    Future<void> paso(Future<void> Function() f) async {
      try {
        await f();
      } on ApiError catch (e) {
        if (e.sesionVencida) rethrow;
      } catch (_) {
        // una cifra que falla no impide mostrar las demás
      }
    }

    try {
      await Future.wait(<Future<void>>[
        if (Sesion.verOT) paso(() async => t.ot = mapa(await Api.ctrl('dashboardcontrolador.jsp', 'resumenOT'))),
        if (Sesion.puede(Sesion.ocRecepcion)) paso(() async => t.ocPendientes = mapas(await Api.movil('ocPendientes')).length),
        if (Sesion.verAlertasStock) paso(() async => t.alertas = mapas(await Api.ctrl('inventarioControlador.jsp', 'alertasMinimo'))),
        if (Sesion.puede(Sesion.activos)) paso(() async => t.activos = mapa(await Api.ctrl('activosControlador.jsp', 'resumen'))),
        if (Sesion.puede(Sesion.activosAprobar))
          paso(() async => t.porAprobar =
              mapas(await Api.ctrl('activosMovControlador.jsp', 'listarSolicitudes', <String, String>{'estado': 'PENDIENTE'})).length),
        if (Sesion.puede(Sesion.activosRecepcion))
          paso(() async => t.porRecibir = mapas(await Api.ctrl('activosMovControlador.jsp', 'enRecepcion')).length),
      ]);
    } catch (e) {
      _cargandoAhora = false;
      if (mounted) manejarError(context, e);
      return;
    }
    _cargandoAhora = false;
    if (!mounted) return;
    setState(() {
      _t = t;
      _cargando = false;
      _version++;
    });
    _campana.currentState?.actualizar();
    // Notificación de stock mínimo: se usa la misma lista del tablero (antes se pedía dos veces)
    // y queda revisando cada 15 minutos mientras la app está abierta.
    // (sin esperar: el permiso de notificaciones no debe frenar el "deslizar para actualizar")
    AlertasStock.iniciar().then((_) => AlertasStock.procesar(t.alertas));
  }

  Future<void> _abrir(Widget pantalla) async {
    await Navigator.of(context).push(MaterialPageRoute<void>(builder: (BuildContext context) => pantalla));
    if (mounted) _cargar(); // al volver se actualizan las cifras
  }

  Future<void> _cerrarSesion() async {
    if (!await confirmar(context, 'Cerrar sesión', '¿Desea salir del sistema?', aceptar: 'Salir')) return;
    await Api.logout();
    Sesion.cerrar();
    if (!mounted) return;
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute<void>(builder: (BuildContext context) => const Login()),
      (Route<dynamic> ruta) => false,
    );
  }

  @override
  Widget build(BuildContext context) {
    final Map<String, dynamic>? ot = _t.ot;
    final List<TarjetaHeroe> heroes = <TarjetaHeroe>[
      if (ot != null && ot['sin_permiso'] != true)
        TarjetaHeroe(
          etiqueta: 'OT abiertas',
          numero: ent(ot['pendientes']) + ent(ot['en_proceso']),
          detalle: '${ent(ot['a_mi_cargo'])} a mi cargo · ${ent(ot['demoradas'])} demoradas',
          icono: Icons.assignment,
          color: Nd.primario,
          alTocar: () => _abrir(const ListaOTPantalla()),
        ),
      if (_t.ocPendientes != null)
        TarjetaHeroe(
          etiqueta: 'OC por recibir',
          numero: _t.ocPendientes,
          detalle: 'solicitadas o parciales',
          icono: Icons.local_shipping,
          color: Nd.info,
          alTocar: () => _abrir(const RecepcionOCPantalla()),
        ),
      if (_t.alertas != null)
        TarjetaHeroe(
          etiqueta: 'Bajo mínimo',
          numero: _t.alertas!.length,
          detalle: 'insumos en o bajo el mínimo',
          icono: Icons.warning_amber,
          color: _t.alertas!.isEmpty ? Nd.primario : Nd.acento,
          // El encargado de compras ve las alertas pero no la pantalla de stock
          alTocar: () {
            if (Sesion.puede(Sesion.inventario)) _abrir(const StockPantalla());
          },
        ),
      if (_t.activos != null)
        TarjetaHeroe(
          etiqueta: 'Activos fijos',
          numero: ent(_t.activos!['TOTAL']),
          detalle: '${ent(_t.activos!['DISPONIBLE'])} disponibles · ${ent(_t.activos!['ASIGNADO'])} asignados',
          icono: Icons.devices_other,
          color: Nd.teal,
          alTocar: () => _abrir(const ListaActivosPantalla()),
        ),
      if (_t.porAprobar != null)
        TarjetaHeroe(
          etiqueta: 'Por aprobar',
          numero: _t.porAprobar,
          detalle: 'salidas y bajas de activos',
          icono: Icons.approval,
          color: _t.porAprobar! > 0 ? Nd.peligro : Nd.apagado,
          alTocar: () => _abrir(const AprobacionesPantalla()),
        ),
      if (_t.porRecibir != null)
        TarjetaHeroe(
          etiqueta: 'Activos por recibir',
          numero: _t.porRecibir,
          detalle: 'en tránsito o reparación',
          icono: Icons.move_to_inbox,
          color: Nd.oscuro,
          alTocar: () => _abrir(const AprobacionesPantalla(porRecibir: true)),
        ),
    ];
    final List<Map<String, dynamic>> abiertas = mapas(ot?['abiertas']);

    return Scaffold(
      appBar: barraNanday(
        acciones: <Widget>[
          CampanaNotificaciones(key: _campana, alAbrir: _abrir),
          TextButton.icon(
            onPressed: _cerrarSesion,
            style: TextButton.styleFrom(foregroundColor: Nd.pizarra),
            icon: const Icon(Icons.logout, size: 20),
            label: const Text('Salir', style: TextStyle(fontWeight: FontWeight.w600)),
          ),
          const SizedBox(width: 6),
        ],
      ),
      drawer: MenuLateral(alAbrir: _abrir, alSalir: _cerrarSesion),
      // Deslizar hacia abajo actualiza todo (cifras, gráficos, campana) y vuelve a pedir
      // las listas guardadas en caché (almacenes, centros...).
      body: RefreshIndicator(
        onRefresh: () {
          Api.limpiarCache();
          return _cargar();
        },
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: <Widget>[
            const EncabezadoPagina(titulo: 'Accesos Rápidos', migas: <String>['Inicio']),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Alerta(
                titulo: 'Bienvenido a Ñanday S.A',
                texto: '${Sesion.nombreCompleto} (${Sesion.rolNombre}). Sistema de gestión de activos e insumos. '
                    'Utilice el menú lateral para administrar los diferentes módulos.',
              ),
            ),
            espacio,
            if (_cargando && heroes.isEmpty)
              const Padding(padding: EdgeInsets.all(30), child: Cargando())
            else if (heroes.isNotEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: LayoutBuilder(
                  builder: (BuildContext context, BoxConstraints c) {
                    final int columnas = c.maxWidth >= 900 ? 3 : (c.maxWidth >= 520 ? 2 : 1);
                    final double ancho = (c.maxWidth - (columnas - 1) * 12) / columnas;
                    return Wrap(
                      spacing: 12,
                      runSpacing: 12,
                      children: <Widget>[for (final Widget h in heroes) SizedBox(width: ancho, child: h)],
                    );
                  },
                ),
              ),
            if (_t.alertas != null && _t.alertas!.isNotEmpty) ...<Widget>[
              espacio,
              Tarjeta(
                acento: Nd.acento,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: <Widget>[
                    CabeceraTarjeta(
                      titulo: 'Insumos en o por debajo del stock mínimo',
                      icono: Icons.warning_amber,
                      derecha: Insignia(texto: '${_t.alertas!.length}', color: Nd.acento),
                    ),
                    for (final Map<String, dynamic> a in _t.alertas!.take(6))
                      ListTile(
                        dense: true,
                        title: Text('${tx(a['codigo'])} - ${tx(a['nombre'])}', style: const TextStyle(fontWeight: FontWeight.w600)),
                        subtitle: Text(tx(a['almacen'])),
                        trailing: Text('${ent(a['stock'])} / mín. ${ent(a['minimo'])}',
                            style: TextStyle(fontWeight: FontWeight.w700, color: ent(a['stock']) <= 0 ? Nd.peligro : Nd.acentoProfundo)),
                      ),
                  ],
                ),
              ),
            ],
            espacio,
            GraficosTablero(key: ValueKey<int>(_version)),
            if (abiertas.isNotEmpty) ...<Widget>[
              espacio,
              Tarjeta(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: <Widget>[
                    const CabeceraTarjeta(titulo: 'OT abiertas más antiguas', icono: Icons.hourglass_bottom),
                    for (final Map<String, dynamic> o in abiertas)
                      InkWell(
                        onTap: () => _abrir(DetalleOTPantalla(idOT: ent(o['id']))),
                        child: Container(
                          padding: const EdgeInsets.fromLTRB(16, 10, 12, 10),
                          decoration: BoxDecoration(
                            border: Border(
                              left: BorderSide(color: ent(o['dias']) > 15 ? Nd.peligro : Nd.primario, width: 3),
                              bottom: const BorderSide(color: Nd.borde),
                            ),
                          ),
                          child: Row(
                            children: <Widget>[
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: <Widget>[
                                    Text('${tx(o['numero'])} - ${tx(o['titulo'])}', style: const TextStyle(fontWeight: FontWeight.w600, color: Nd.titulo)),
                                    Text('${tx(o['centro'])}  ·  ${ent(o['items'])} insumo(s)${tx(o['responsable']).isEmpty ? '' : '  ·  ${tx(o['responsable'])}'}',
                                        style: const TextStyle(color: Nd.apagado, fontSize: 12.5)),
                                  ],
                                ),
                              ),
                              Text('${ent(o['dias'])} días',
                                  style: TextStyle(fontWeight: FontWeight.w700, color: ent(o['dias']) > 15 ? Nd.peligro : Nd.pizarra)),
                            ],
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ],
            const PiePagina(),
          ],
        ),
      ),
    );
  }
}

/// Tarjeta KPI del tablero web (.hero): franja de color arriba, etiqueta, cifra grande e ícono.
class TarjetaHeroe extends StatelessWidget {
  final String etiqueta;
  final int? numero;
  final String detalle;
  final IconData icono;
  final Color color;
  final VoidCallback alTocar;

  const TarjetaHeroe({
    super.key,
    required this.etiqueta,
    required this.numero,
    required this.detalle,
    required this.icono,
    required this.color,
    required this.alTocar,
  });

  @override
  Widget build(BuildContext context) {
    return Tarjeta(
      margen: EdgeInsets.zero,
      acento: color,
      child: Material(
        color: Colors.white,
        child: InkWell(
          onTap: alTocar,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(18, 16, 16, 16),
            child: Row(
              children: <Widget>[
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Text(
                        etiqueta.toUpperCase(),
                        style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.w600, letterSpacing: 0.6, color: Nd.apagado),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        numero == null ? '—' : guaranies(numero),
                        style: const TextStyle(fontSize: 32, fontWeight: FontWeight.w700, color: Nd.titulo, height: 1.15),
                      ),
                      Text(detalle, style: const TextStyle(fontSize: 12, color: Nd.apagado)),
                    ],
                  ),
                ),
                Container(
                  width: 46,
                  height: 46,
                  decoration: BoxDecoration(color: color.withValues(alpha: 0.12), borderRadius: BorderRadius.circular(12)),
                  child: Icon(icono, color: color, size: 22),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Menú lateral oscuro de la web (.main-sidebar): marca con el logo, panel de usuario
/// y los módulos que permite el rol, con sus opciones desplegables.
class MenuLateral extends StatelessWidget {
  final Future<void> Function(Widget pantalla) alAbrir;
  final VoidCallback alSalir;

  const MenuLateral({super.key, required this.alAbrir, required this.alSalir});

  @override
  Widget build(BuildContext context) {
    void ir(Widget pantalla) {
      Navigator.pop(context);
      alAbrir(pantalla);
    }

    final String inicial = Sesion.nombre.trim().isEmpty ? '?' : Sesion.nombre.trim()[0].toUpperCase();
    final bool inventario = Sesion.puede(Sesion.inventario);

    return Drawer(
      backgroundColor: Nd.oscuro,
      shape: const RoundedRectangleBorder(),
      child: SafeArea(
        child: ListView(
          padding: EdgeInsets.zero,
          children: <Widget>[
            // ---- .brand-link ----
            Container(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 14),
              decoration: const BoxDecoration(border: Border(bottom: BorderSide(color: Color(0x1FFFFFFF)))),
              child: Row(
                children: <Widget>[
                  Container(
                    padding: const EdgeInsets.all(2),
                    decoration: const BoxDecoration(color: Nd.primario, shape: BoxShape.circle),
                    child: ClipOval(child: Image.asset('assets/imagenes/logo_ave.png', width: 38, height: 38)),
                  ),
                  const SizedBox(width: 12),
                  const Text('Ñanday SIAA', style: TextStyle(color: Colors.white, fontSize: 19, fontWeight: FontWeight.w700)),
                ],
              ),
            ),
            // ---- .user-panel (toca para abrir el perfil) ----
            InkWell(
              onTap: () => ir(const PerfilPantalla()),
              child: Container(
                padding: const EdgeInsets.all(16),
                margin: const EdgeInsets.only(bottom: 8),
                decoration: const BoxDecoration(border: Border(bottom: BorderSide(color: Color(0x1FFFFFFF)))),
                child: Row(
                  children: <Widget>[
                    Container(
                      padding: const EdgeInsets.all(2),
                      decoration: const BoxDecoration(color: Nd.primario, shape: BoxShape.circle),
                      child: CircleAvatar(
                        radius: 20,
                        backgroundColor: Colors.white,
                        child: Sesion.foto.isEmpty
                            ? Text(inicial, style: const TextStyle(color: Nd.primario, fontWeight: FontWeight.w700, fontSize: 18))
                            : ClipOval(
                                child: Image.network(
                                  Api.urlFoto(Sesion.foto),
                                  headers: Api.cabecerasImagen,
                                  width: 40,
                                  height: 40,
                                  fit: BoxFit.cover,
                                  errorBuilder: (BuildContext c, Object e, StackTrace? s) =>
                                      Text(inicial, style: const TextStyle(color: Nd.primario, fontWeight: FontWeight.w700, fontSize: 18)),
                                ),
                              ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: <Widget>[
                          Text(Sesion.nombreCompleto,
                              maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
                          const SizedBox(height: 2),
                          Text(Sesion.rolNombre, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(color: Nd.menuTexto, fontSize: 12)),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
            _OpcionMenu(icono: Icons.dashboard, texto: 'Inicio', activa: true, alTocar: () => Navigator.pop(context)),
            if (inventario)
              _GrupoMenu(
                icono: Icons.inventory,
                texto: 'Inventarios',
                opciones: <Widget>[
                  _OpcionMenu(icono: Icons.inventory_2, texto: 'Stock', sub: true, alTocar: () => ir(const StockPantalla())),
                  _OpcionMenu(icono: Icons.timeline, texto: 'Kardex', sub: true, alTocar: () => ir(const KardexPantalla())),
                  _OpcionMenu(icono: Icons.fact_check, texto: 'Inventario físico', sub: true, alTocar: () => ir(const InventarioFisicoPantalla())),
                  _OpcionMenu(icono: Icons.local_shipping, texto: 'Salida de insumos', sub: true, alTocar: () => ir(const SalidaInsumosPantalla())),
                  _OpcionMenu(icono: Icons.swap_horiz, texto: 'Transferencias', sub: true, alTocar: () => ir(const TransferenciasPantalla())),
                  _OpcionMenu(icono: Icons.tune, texto: 'Ajustes de inventario', sub: true, alTocar: () => ir(const AjustesPantalla())),
                ],
              ),
            if (Sesion.verOT)
              _GrupoMenu(
                icono: Icons.assignment,
                texto: 'Órdenes de Trabajo',
                opciones: <Widget>[
                  if (Sesion.puede(Sesion.otGestion))
                    _OpcionMenu(icono: Icons.add_task, texto: 'Nueva OT', sub: true, alTocar: () => ir(const ListaOTPantalla(abrirNueva: true))),
                  _OpcionMenu(icono: Icons.list_alt, texto: 'Órdenes de trabajo', sub: true, alTocar: () => ir(const ListaOTPantalla())),
                  _OpcionMenu(icono: Icons.person_pin, texto: 'A mi cargo', sub: true, alTocar: () => ir(const ListaOTPantalla(mias: 'A_CARGO'))),
                ],
              ),
            if (Sesion.puede(Sesion.ocRecepcion))
              _GrupoMenu(
                icono: Icons.shopping_cart,
                texto: 'Compras',
                opciones: <Widget>[
                  _OpcionMenu(icono: Icons.move_to_inbox, texto: 'Recepción OC', sub: true, alTocar: () => ir(const RecepcionOCPantalla())),
                ],
              ),
            if (Sesion.verActivos)
              _GrupoMenu(
                icono: Icons.devices_other,
                texto: 'Activos Fijos',
                opciones: <Widget>[
                  if (Sesion.puede(Sesion.activos)) ...<Widget>[
                    _OpcionMenu(icono: Icons.list, texto: 'Listar activos', sub: true, alTocar: () => ir(const ListaActivosPantalla())),
                    _OpcionMenu(icono: Icons.outbox, texto: 'Salida o baja', sub: true, alTocar: () => ir(const SolicitudActivosPantalla())),
                  ],
                  _OpcionMenu(icono: Icons.approval, texto: 'Aprobaciones', sub: true, alTocar: () => ir(const AprobacionesPantalla())),
                ],
              ),
            if (Sesion.puedeAlguno(<String>[Sesion.proveedores, Sesion.insumosEditar, Sesion.refAdmin]))
              _GrupoMenu(
                icono: Icons.settings,
                texto: 'Referenciales',
                abierto: false,
                opciones: <Widget>[
                  // Igual que la web: centros, CC, almacenes, unidades, roles y usuarios solo con REF_ADMIN (Administrador)
                  if (Sesion.puede(Sesion.refAdmin)) ...<Widget>[
                    _OpcionMenu(icono: Icons.apartment, texto: 'Centros', sub: true, alTocar: () => ir(const ListaCentros())),
                    _OpcionMenu(icono: Icons.account_tree, texto: 'Centros de costo', sub: true, alTocar: () => ir(const ListaCentrosCosto())),
                    _OpcionMenu(icono: Icons.warehouse, texto: 'Almacenes', sub: true, alTocar: () => ir(const ListaAlmacenes())),
                    _OpcionMenu(icono: Icons.straighten, texto: 'Unidades de medida', sub: true, alTocar: () => ir(const ListaUnidades())),
                  ],
                  if (Sesion.puede(Sesion.proveedores))
                    _OpcionMenu(icono: Icons.local_shipping, texto: 'Proveedores', sub: true, alTocar: () => ir(const ListaProveedores())),
                  if (Sesion.puede(Sesion.insumosEditar))
                    _OpcionMenu(icono: Icons.category, texto: 'Insumos', sub: true, alTocar: () => ir(const ListaInsumos())),
                  if (Sesion.puede(Sesion.refAdmin)) ...<Widget>[
                    _OpcionMenu(icono: Icons.admin_panel_settings, texto: 'Roles', sub: true, alTocar: () => ir(const ListaRoles())),
                    _OpcionMenu(icono: Icons.manage_accounts, texto: 'Usuarios', sub: true, alTocar: () => ir(const ListaUsuarios())),
                  ],
                ],
              ),
            // Igual que la web: la bitácora solo la consulta el Administrador
            if (Sesion.esAdministrador)
              _GrupoMenu(
                icono: Icons.security,
                texto: 'Seguridad',
                abierto: false,
                opciones: <Widget>[
                  _OpcionMenu(icono: Icons.history, texto: 'Bitácora', sub: true, alTocar: () => ir(const BitacoraPantalla())),
                ],
              ),
            const Padding(padding: EdgeInsets.symmetric(horizontal: 16, vertical: 8), child: Divider(color: Color(0x1FFFFFFF))),
            _OpcionMenu(icono: Icons.person, texto: 'Mi perfil', alTocar: () => ir(const PerfilPantalla())),
            _OpcionMenu(
              icono: Icons.logout,
              texto: 'Cerrar sesión',
              alTocar: () {
                Navigator.pop(context);
                alSalir();
              },
            ),
          ],
        ),
      ),
    );
  }
}

/// Opción del menú lateral: texto gris claro; la activa con fondo y barra naranja a la izquierda.
class _OpcionMenu extends StatelessWidget {
  final IconData icono;
  final String texto;
  final VoidCallback alTocar;
  final bool activa;
  final bool sub;

  const _OpcionMenu({required this.icono, required this.texto, required this.alTocar, this.activa = false, this.sub = false});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 1),
      child: Material(
        color: activa ? const Color(0x1AFFFFFF) : Colors.transparent,
        borderRadius: BorderRadius.circular(8),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: alTocar,
          hoverColor: const Color(0x0FFFFFFF),
          child: Container(
            decoration: activa ? const BoxDecoration(border: Border(left: BorderSide(color: Nd.acento, width: 3))) : null,
            padding: EdgeInsets.fromLTRB(sub ? 22 : 13, 11, 12, 11),
            child: Row(
              children: <Widget>[
                Icon(icono, size: sub ? 18 : 20, color: activa ? Colors.white : Nd.menuTexto),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    texto,
                    style: TextStyle(
                      color: activa ? Colors.white : Nd.menuTexto,
                      fontSize: sub ? 14 : 15,
                      fontWeight: activa ? FontWeight.w600 : FontWeight.w400,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Módulo desplegable del menú lateral (.nav-treeview).
class _GrupoMenu extends StatefulWidget {
  final IconData icono;
  final String texto;
  final List<Widget> opciones;
  final bool abierto;

  const _GrupoMenu({required this.icono, required this.texto, required this.opciones, this.abierto = true});

  @override
  State<_GrupoMenu> createState() => _GrupoMenuState();
}

class _GrupoMenuState extends State<_GrupoMenu> {
  late bool _abierto = widget.abierto;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: <Widget>[
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 1),
          child: Material(
            color: Colors.transparent,
            borderRadius: BorderRadius.circular(8),
            clipBehavior: Clip.antiAlias,
            child: InkWell(
              onTap: () => setState(() => _abierto = !_abierto),
              child: Padding(
                padding: const EdgeInsets.fromLTRB(13, 11, 12, 11),
                child: Row(
                  children: <Widget>[
                    Icon(widget.icono, size: 20, color: Nd.menuTexto),
                    const SizedBox(width: 12),
                    Expanded(child: Text(widget.texto, style: const TextStyle(color: Nd.menuTexto, fontSize: 15))),
                    AnimatedRotation(
                      turns: _abierto ? -0.25 : 0,
                      duration: const Duration(milliseconds: 180),
                      child: const Icon(Icons.chevron_left, size: 20, color: Nd.menuTexto),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
        if (_abierto)
          Container(
            margin: const EdgeInsets.fromLTRB(8, 2, 8, 6),
            padding: const EdgeInsets.symmetric(vertical: 3),
            decoration: BoxDecoration(color: const Color(0x08FFFFFF), borderRadius: BorderRadius.circular(8)),
            child: Column(children: widget.opciones),
          ),
      ],
    );
  }
}

