// Copiado del sistema web (RemisionDAOJdbc) para que la API guarde el detalle igual que la web.
package inventario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Detalle de las remisiones (tabla remisiones_detalle).
 * Al emitir una remisión se guarda una copia fija de lo que salió: insumos de la OT,
 * insumos de la transferencia o activos de la solicitud. Se llama dentro de la
 * transacción del llamador, después de dejar unidos los ítems a la remisión.
 */
public final class Remisiones {

    private static final String COPIAR = "INSERT INTO remisiones_detalle (id_remision, orden, tipo_item, id_insumo, id_equipo, codigo, descripcion, unidad, cantidad, id_almacen, origen) "
        + "SELECT r.id_remision, ROW_NUMBER() OVER (ORDER BY x.orden), x.tipo_item, x.id_insumo, x.id_equipo, "
        + "       x.codigo, x.descripcion, x.unidad, x.cantidad, x.id_almacen, x.origen "
        + "FROM remisiones r "
        + "JOIN LATERAL ( "
        + "    SELECT 'INSUMO'::varchar AS tipo_item, d.id_insumo, NULL::integer AS id_equipo, i.codigo, i.descripcion, um.abreviatura AS unidad, "
        + "           d.cantidad, d.id_almacen, a.nombre::varchar AS origen, d.id_detalle_ot AS orden "
        + "      FROM detalle_ot d JOIN insumos i ON i.id_insumo = d.id_insumo "
        + "      LEFT JOIN unidad_medida um ON um.id_unidad_medida = i.id_unidad_medida "
        + "      LEFT JOIN almacenes a ON a.id_almacen = d.id_almacen "
        + "     WHERE r.tipo_remision = 'SALIDA_INSUMOS' AND d.id_remision = r.id_remision "
        + "    UNION ALL "
        + "    SELECT 'INSUMO', td.id_insumo, NULL, i.codigo, i.descripcion, um.abreviatura, td.cantidad, t.id_almacen_origen, ao.nombre, td.id_detalle "
        + "      FROM transferencias_detalle td JOIN transferencias t ON t.id_transferencia = td.id_transferencia "
        + "      JOIN insumos i ON i.id_insumo = td.id_insumo "
        + "      LEFT JOIN unidad_medida um ON um.id_unidad_medida = i.id_unidad_medida "
        + "      LEFT JOIN almacenes ao ON ao.id_almacen = t.id_almacen_origen "
        + "     WHERE r.tipo_remision = 'TRANSFERENCIA' AND td.id_transferencia = r.id_transferencia "
        + "    UNION ALL "
        + "    SELECT 'EQUIPO', NULL, sd.id_equipo, e.serial_activo, "
        + "           COALESCE(i.descripcion, 'Activo') || COALESCE(' - Serie ' || e.numero_serie, ''), 'u', 1, NULL, "
        + "           COALESCE(co.nombre, '') || COALESCE(' / ' || cco.codigo, ''), sd.id_detalle "
        + "      FROM solicitudes_activos_detalle sd JOIN equipos e ON e.id_equipo = sd.id_equipo "
        + "      LEFT JOIN insumos i ON i.id_insumo = e.id_insumo_base "
        + "      LEFT JOIN centros co ON co.id_centro = sd.id_centro_origen "
        + "      LEFT JOIN centros_costo cco ON cco.id_centro_costo = sd.id_centro_costo_origen "
        + "     WHERE r.tipo_remision = 'SALIDA_ACTIVOS' AND sd.id_solicitud = r.id_solicitud_activo "
        + ") x ON true "
        + "WHERE r.id_remision = ?";

    private Remisiones() {
    }

    /** Copia el detalle de la remisión. Devuelve la cantidad de ítems guardados. */
    public static int copiarDetalle(Connection conn, int idRemision) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(COPIAR);
        try {
            ps.setInt(1, idRemision);
            return ps.executeUpdate();
        } finally {
            ps.close();
        }
    }
}
