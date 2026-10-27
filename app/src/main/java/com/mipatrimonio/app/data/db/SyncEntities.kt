package com.mipatrimonio.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_perfil")
data class SyncPerfilEntity(@PrimaryKey val id: String, val nombre: String, val orden: Int, val deletedAt: String?, val updatedAt: String)

@Entity(tableName = "sync_producto")
data class SyncProductoEntity(@PrimaryKey val id: String, val perfilId: String, val nombre: String, val corto: String?, val tipo: String, val moneda: String, val identificador: String?, val deletedAt: String?, val updatedAt: String)

@Entity(tableName = "sync_movimiento")
data class SyncMovimientoEntity(
    @PrimaryKey val id: String, val perfilId: String, val productoId: String, val fecha: Long,
    val tipo: String, val unidades: String, val importeMinor: Long, val comisionMinor: Long?,
    val cuentaId: String?, val origen: String, val nota: String?, val deletedAt: String?, val updatedAt: String,
)

@Entity(tableName = "sync_resumen")
data class SyncResumenEntity(@PrimaryKey val perfilId: String, val datosJson: String, val generadoAt: String, val updatedAt: String)

@Entity(tableName = "sync_outbox")
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val tabla: String, val filaId: String,
    val json: String, val intentos: Int = 0, val ultimoError: String? = null, val creadoAt: Long,
)

@Entity(tableName = "sync_cursor")
data class SyncCursorEntity(@PrimaryKey val tabla: String, val ultimoUpdatedAt: String)

@Entity(tableName = "sync_account_fingerprint")
data class SyncAccountFingerprintEntity(@PrimaryKey val accountId: String, val fingerprint: String)
