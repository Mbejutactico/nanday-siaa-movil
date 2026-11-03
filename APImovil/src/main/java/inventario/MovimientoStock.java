// Copiado del sistema web (proyecto Archivador) para que la API aplique exactamente las mismas reglas.
package inventario;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * ÚNICA puerta para cambiar el stock de un insumo en un almacén.
 * Actualiza stock_almacen y registra el movimiento en el Kardex
 * (movimientos_stock) con el saldo resultante, dentro de la transacción
 * del llamador. Nunca deja stock negativo: si no alcanza, lanza
 * IllegalArgumentException con un mensaje para el usuario.
 *
 * Uso:
 *   MovimientoStock m = new MovimientoStock(MovimientoStock.SALIDA_OT, idInsumo, idAlmacen, -5, idUsuario);
 *   m.documento = "OT-20260925-0001"; m.idOt = 3; m.idCentroCosto = 2;
 *   int saldo = m.registrar(conn);
 */
public class MovimientoStock {

    public static final String ENTRADA_COMPRA = "ENTRADA_COMPRA";
    public static final String SALIDA_OT = "SALIDA_OT";
    public static final String DEVOLUCION_OT = "DEVOLUCION_OT";
    public static final String AJUSTE = "AJUSTE";
    public static final String ANULACION_AJUSTE = "ANULACION_AJUSTE";
    public static final String TRANSFERENCIA_SALIDA = "TRANSFERENCIA_SALIDA";
    public static final String TRANSFERENCIA_ENTRADA = "TRANSFERENCIA_ENTRADA";
    public static final String ANULACION_TRANSFERENCIA = "ANULACION_TRANSFERENCIA";
    /** Salida que revierte una recepción de OC anulada (migración 11). */
    public static final String ANULACION_RECEPCION = "ANULACION_RECEPCION";

    public final String tipo;
    public final int idInsumo;
    public final int idAlmacen;
    /** Positiva = entrada, negativa = salida. */
    public final int cantidad;
    public final int idUsuario;

    public String documento;
    public String observacion;
    public BigDecimal costoUnitario;   // si es null se usa el precio de compra del insumo
    public Integer idOt;
    public Integer idCompra;
    public Integer idAjuste;
    public Integer idTransferencia;
    public Integer idCentroCosto;

    public MovimientoStock(String tipo, int idInsumo, int idAlmacen, int cantidad, int idUsuario) {
        if (cantidad == 0) {
            throw new IllegalArgumentException("La cantidad del movimiento no puede ser 0");
        }
        this.tipo = tipo;
        this.idInsumo = idInsumo;
        this.idAlmacen = idAlmacen;
        this.cantidad = cantidad;
        this.idUsuario = idUsuario;
    }

    /** Aplica el movimiento y devuelve el saldo que queda en el almacén. */
    public int registrar(Connection conn) throws SQLException {
        int saldo;
        if (cantidad > 0) {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO stock_almacen (id_insumo, id_almacen, stock_actual) VALUES (?, ?, ?) "
                    + "ON CONFLICT (id_insumo, id_almacen) DO UPDATE SET stock_actual = stock_almacen.stock_actual + EXCLUDED.stock_actual "
                    + "RETURNING stock_actual");
            try {
                ps.setInt(1, idInsumo);
                ps.setInt(2, idAlmacen);
                ps.setInt(3, cantidad);
                ResultSet rs = ps.executeQuery();
                rs.next();
                saldo = rs.getInt(1);
                rs.close();
            } finally {
                ps.close();
            }
        } else {
            // Descuenta solo si alcanza (la fila queda bloqueada hasta el fin de la transacción)
            PreparedStatement ps = conn.prepareStatement(
                    "UPDATE stock_almacen SET stock_actual = stock_actual + ? "
                    + "WHERE id_insumo = ? AND id_almacen = ? AND stock_actual + ? >= 0 RETURNING stock_actual");
            try {
                ps.setInt(1, cantidad);
                ps.setInt(2, idInsumo);
                ps.setInt(3, idAlmacen);
                ps.setInt(4, cantidad);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) {
                    rs.close();
                    throw new IllegalArgumentException(mensajeSinStock(conn));
                }
                saldo = rs.getInt(1);
                rs.close();
            } finally {
                ps.close();
            }
        }

        PreparedStatement k = conn.prepareStatement(
                "INSERT INTO movimientos_stock (tipo, id_insumo, id_almacen, cantidad, saldo, costo_unitario, documento, "
                + "id_ot, id_compra, id_ajuste, id_transferencia, id_centro_costo, id_usuario, observacion) "
                + "VALUES (?, ?, ?, ?, ?, COALESCE(?, (SELECT precio_compra FROM insumos WHERE id_insumo = ?)), ?, ?, ?, ?, ?, ?, ?, ?)");
        try {
            int i = 1;
            k.setString(i++, tipo);
            k.setInt(i++, idInsumo);
            k.setInt(i++, idAlmacen);
            k.setInt(i++, cantidad);
            k.setInt(i++, saldo);
            if (costoUnitario == null) { k.setNull(i++, Types.NUMERIC); } else { k.setBigDecimal(i++, costoUnitario); }
            k.setInt(i++, idInsumo);
            texto(k, i++, documento, 60);
            entero(k, i++, idOt);
            entero(k, i++, idCompra);
            entero(k, i++, idAjuste);
            entero(k, i++, idTransferencia);
            entero(k, i++, idCentroCosto);
            k.setInt(i++, idUsuario);
            texto(k, i++, observacion, 2000);
            k.executeUpdate();
        } finally {
            k.close();
        }
        return saldo;
    }

    private String mensajeSinStock(Connection conn) throws SQLException {
        PreparedStatement p = conn.prepareStatement(
                "SELECT i.codigo, i.descripcion, a.nombre, COALESCE(s.stock_actual, 0) "
                + "FROM insumos i CROSS JOIN almacenes a "
                + "LEFT JOIN stock_almacen s ON s.id_insumo = i.id_insumo AND s.id_almacen = a.id_almacen "
                + "WHERE i.id_insumo = ? AND a.id_almacen = ?");
        try {
            p.setInt(1, idInsumo);
            p.setInt(2, idAlmacen);
            ResultSet r = p.executeQuery();
            String msg = "Stock insuficiente";
            if (r.next()) {
                msg = "Stock insuficiente de " + r.getString(1) + " - " + r.getString(2) + " en " + r.getString(3)
                        + ": disponible " + r.getInt(4) + ", se necesitan " + (-cantidad);
            }
            r.close();
            return msg;
        } finally {
            p.close();
        }
    }

    private static void entero(PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v == null) { ps.setNull(i, Types.INTEGER); } else { ps.setInt(i, v.intValue()); }
    }

    private static void texto(PreparedStatement ps, int i, String v, int max) throws SQLException {
        if (v == null || v.trim().isEmpty()) {
            ps.setNull(i, Types.VARCHAR);
        } else {
            String t = v.trim();
            ps.setString(i, t.length() > max ? t.substring(0, max) : t);
        }
    }
}
