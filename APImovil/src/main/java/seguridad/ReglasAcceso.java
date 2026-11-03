// Copiado del sistema web (proyecto Archivador) para que la API aplique exactamente las mismas reglas.
package seguridad;

import static seguridad.Permisos.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Qué permiso exige cada pantalla, controlador y operación (parámetro "tipo").
 * Basta con tener UNO de los permisos listados. "*" = cualquier usuario con sesión.
 * Lo que NO figura aquí queda reservado al ADMINISTRADOR (lo desconocido se bloquea).
 */
public final class ReglasAcceso {

    private static final Map<String, String[]> RUTAS = new HashMap<String, String[]>();
    private static final Map<String, String[]> OPERACIONES = new HashMap<String, String[]>();

    private static void ruta(String ruta, String... permisos) {
        RUTAS.put(ruta, permisos);
    }

    private static void op(String controlador, String tipos, String... permisos) {
        for (String t : tipos.split(",")) {
            OPERACIONES.put("/Controlador/" + controlador + "#" + t.trim(), permisos);
        }
    }

    static {
        String[] OT_CUALQUIERA = {OT_GESTION, OT_SUMINISTRAR, OT_FINALIZAR};
        // ---------------- Pantallas ----------------
        ruta("/dashboard.jsp", "*");
        ruta("/header.jsp", "*");
        ruta("/footer.jsp", "*");
        for (String p : new String[]{"centrosvista", "centro_costovista", "almacenesvista", "unidad_medidavista", "rolesvista", "usuariosvista"}) {
            ruta("/" + p + ".jsp", REF_ADMIN);
        }
        ruta("/proveedorvista.jsp", PROVEEDORES);
        ruta("/insumosvista.jsp", INSUMOS_EDITAR);
        ruta("/solicitudCompra.jsp", SOLICITUD_COMPRA);
        ruta("/listarOC.jsp", OC_VER);
        ruta("/recepcionOC.jsp", OC_RECEPCION);
        ruta("/otCrear.jsp", OT_GESTION);
        ruta("/otListado.jsp", OT_CUALQUIERA);
        ruta("/otSuministrar.jsp", OT_SUMINISTRAR);
        ruta("/otFinalizar.jsp", OT_FINALIZAR);
        for (String p : new String[]{"stockVista", "kardex", "salidaInsumos", "ajustesInventario", "transferencias", "inventarioFisico"}) {
            ruta("/" + p + ".jsp", INVENTARIO);
        }
        ruta("/reportesInventario.jsp", INVENTARIO, INFORMES);
        ruta("/informes.jsp", INFORMES);
        ruta("/activosFijosVista.jsp", ACTIVOS);
        ruta("/activosSalida.jsp", ACTIVOS);
        ruta("/activosBaja.jsp", ACTIVOS);
        ruta("/activosAprobaciones.jsp", ACTIVOS_APROBAR, ACTIVOS_RECEPCION);

        // ---------------- Reportes PDF ----------------
        ruta("/jspreportes/verOC.jsp", OC_VER);
        ruta("/jspreportes/verStock.jsp", INVENTARIO);
        ruta("/jspreportes/verHojaVida.jsp", ACTIVOS);
        ruta("/jspreportes/verProveedor.jsp", PROVEEDORES);
        ruta("/jspreportes/verInsumo.jsp", INSUMOS_EDITAR);
        for (String p : new String[]{"verAlmacen", "verCentro", "verCentroCosto", "verRol", "verUnidad", "verUsuario"}) {
            ruta("/jspreportes/" + p + ".jsp", REF_ADMIN);
        }

        // ---------------- Controladores (permiso base) ----------------
        for (String c : new String[]{"centrocontrolador", "centro_costocontrolador", "almacenescontrolador", "rolcontrolador",
            "usuariocontrolador", "unidadmedidacontrolador"}) {
            ruta("/Controlador/" + c + ".jsp", REF_ADMIN);
        }
        ruta("/Controlador/proveedorcontrolador.jsp", PROVEEDORES);
        ruta("/Controlador/insumoscontrolador.jsp", INSUMOS_EDITAR);
        ruta("/Controlador/solicitudCompraControlador.jsp", SOLICITUD_COMPRA);
        ruta("/Controlador/ocControlador.jsp", OC_VER);
        ruta("/Controlador/otControlador.jsp", OT_CUALQUIERA);
        ruta("/Controlador/inventarioControlador.jsp", INVENTARIO);
        ruta("/Controlador/stockControlador.jsp", INVENTARIO);
        ruta("/Controlador/activosControlador.jsp", ACTIVOS);
        ruta("/Controlador/activosMovControlador.jsp", ACTIVOS);
        ruta("/Controlador/informesControlador.jsp", INFORMES);
        ruta("/Controlador/dashboardcontrolador.jsp", "*");
        ruta("/Controlador/perfilcontrolador.jsp", "*");
        // Campana de notificaciones: cada aviso se filtra según los permisos del rol
        ruta("/Controlador/notificacionControlador.jsp", "*");
        // bitacoraControlador no figura: la consulta de la bitácora queda solo para el ADMINISTRADOR

        // ---------------- Operaciones puntuales ----------------
        op("unidadmedidacontrolador.jsp", "listarCombo", REF_ADMIN, INSUMOS_EDITAR);
        op("insumoscontrolador.jsp", "Borrar", INSUMOS_ELIMINAR);
        op("ocControlador.jsp", "cargarCombos,cargarInsumos,actualizarOC,actualizarDetalleOC,eliminarDetalleOC,agregarDetalleOC,anularOC", OC_MODIFICAR);
        op("ocControlador.jsp", "anularRecepcion", OC_ANULAR_RECEPCION);
        op("ocControlador.jsp", "listarOCPendientes,listarAlmacenesPorCentro,listarCentrosCostoPorCentro,obtenerDetalleRecepcion,procesarRecepcion", OC_RECEPCION);
        op("otControlador.jsp", "contextoUsuario,listarCentrosCosto,listarResponsables,crear,modificar,eliminar", OT_GESTION);
        op("otControlador.jsp", "listarAbiertas,listarInsumos,almacenesConStock,suministrar,quitarSuministro", OT_SUMINISTRAR);
        op("otControlador.jsp", "finalizar,reabrir,ahora", OT_FINALIZAR);
        op("inventarioControlador.jsp", "reporteConsumo,reporteMasUsados,reporteValorizacion", INVENTARIO, INFORMES);
        // Alertas de stock mínimo: igual que la web (inventario o solicitud de compra)
        op("inventarioControlador.jsp", "alertasMinimo", INVENTARIO, SOLICITUD_COMPRA);
        op("inventarioControlador.jsp", "imprimirRemision", INVENTARIO, OT_GESTION, OT_SUMINISTRAR, ACTIVOS);
        op("activosMovControlador.jsp", "aprobar,rechazar,anularSalida", ACTIVOS_APROBAR);
        op("activosMovControlador.jsp", "enRecepcion,recibir,rechazarRecepcion", ACTIVOS_RECEPCION);
        op("activosMovControlador.jsp", "listarSolicitudes,detalleSolicitud,imprimirActaBaja", ACTIVOS, ACTIVOS_APROBAR, ACTIVOS_RECEPCION);

        // ---------------- Aplicación móvil (Controlador/movil, MovilServlet) ----------------
        // Sin permiso base: una operación que no figure aquí queda solo para el administrador.
        movil("sesion,centros,almacenes,centrosCosto,insumos", "*");
        movil("stock,otsConPendientes", INVENTARIO);
        movil("ocPendientes", OC_RECEPCION);
        movil("proveedoresActivos", ACTIVOS, PROVEEDORES);
        movil("otAbiertas,usuarios", ACTIVOS);
        movil("proveedores,proveedor,guardarProveedor,eliminarProveedor", PROVEEDORES);
        movil("insumosCatalogo,insumo,guardarInsumo,unidades", INSUMOS_EDITAR);
        movil("eliminarInsumo", INSUMOS_ELIMINAR);
        // Referenciales de administración (centros, CC, almacenes, unidades, roles, usuarios): igual que la web
        movil(portado.ReferencialesControlador.OPERACIONES, REF_ADMIN);
    }

    private static void movil(String tipos, String... permisos) {
        for (String t : tipos.split(",")) {
            OPERACIONES.put("/Controlador/movil#" + t.trim(), permisos);
        }
    }

    private ReglasAcceso() {
    }

    /** Permisos exigidos para la ruta y operación; null = solo administrador. */
    static String[] requeridos(String ruta, String tipo) {
        if (tipo != null) {
            String[] porOperacion = OPERACIONES.get(ruta + "#" + tipo);
            if (porOperacion != null) {
                return porOperacion;
            }
        }
        return RUTAS.get(ruta);
    }

    public static boolean permitido(Permisos p, String ruta, String tipo) {
        if (p.esAdministrador()) {
            return true;
        }
        String[] req = requeridos(ruta, tipo);
        return req != null && p.tieneAlguno(req);
    }
}
