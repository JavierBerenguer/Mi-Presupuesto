package com.mipatrimonio.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_perfil WHERE deletedAt IS NULL ORDER BY orden, nombre") fun observePerfiles(): Flow<List<SyncPerfilEntity>>
    @Query("SELECT * FROM sync_producto WHERE perfilId = :perfilId AND deletedAt IS NULL ORDER BY nombre") fun observeProductos(perfilId: String): Flow<List<SyncProductoEntity>>
    @Query("SELECT * FROM sync_movimiento WHERE perfilId = :perfilId AND deletedAt IS NULL ORDER BY fecha DESC") fun observeMovimientos(perfilId: String): Flow<List<SyncMovimientoEntity>>
    @Query("SELECT * FROM sync_resumen WHERE perfilId = :perfilId") fun observeResumen(perfilId: String): Flow<SyncResumenEntity?>
    @Query("SELECT * FROM sync_movimiento WHERE id = :id") suspend fun movimiento(id: String): SyncMovimientoEntity?
    @Query("SELECT * FROM sync_movimiento WHERE cuentaId = :remoteAccountId AND deletedAt IS NULL")
    suspend fun movementsForAccount(remoteAccountId: String): List<SyncMovimientoEntity>
    @Query("SELECT COUNT(*) FROM sync_movimiento WHERE cuentaId = :remoteAccountId AND deletedAt IS NULL")
    suspend fun countMovementsForAccount(remoteAccountId: String): Int
    @Query("SELECT * FROM sync_producto WHERE id = :id") suspend fun producto(id: String): SyncProductoEntity?
    @Query("SELECT * FROM sync_outbox ORDER BY id LIMIT 1") suspend fun firstOutbox(): SyncOutboxEntity?
    @Query("SELECT COUNT(*) FROM sync_outbox WHERE tabla = :table AND filaId = :rowId")
    suspend fun pendingCount(table: String, rowId: String): Int
    @Query("SELECT * FROM sync_outbox ORDER BY id") fun observeOutbox(): Flow<List<SyncOutboxEntity>>
    @Query("SELECT * FROM sync_cursor WHERE tabla = :tabla") suspend fun cursor(tabla: String): SyncCursorEntity?
    @Query("SELECT * FROM sync_account_fingerprint WHERE accountId = :id") suspend fun fingerprint(id: String): SyncAccountFingerprintEntity?
    @Query("SELECT * FROM sync_account_fingerprint") suspend fun allFingerprints(): List<SyncAccountFingerprintEntity>
    @Upsert suspend fun upsertPerfil(value: SyncPerfilEntity)
    @Upsert suspend fun upsertProducto(value: SyncProductoEntity)
    @Upsert suspend fun upsertMovimiento(value: SyncMovimientoEntity)
    @Upsert suspend fun upsertResumen(value: SyncResumenEntity)
    @Upsert suspend fun upsertCursor(value: SyncCursorEntity)
    @Upsert suspend fun upsertFingerprint(value: SyncAccountFingerprintEntity)
    @Upsert suspend fun upsertOutbox(value: SyncOutboxEntity)
    @Query("DELETE FROM sync_outbox WHERE id = :id") suspend fun deleteOutbox(id: Long)
    @Query("DELETE FROM sync_perfil") suspend fun clearPerfiles()
    @Query("DELETE FROM sync_producto") suspend fun clearProductos()
    @Query("DELETE FROM sync_movimiento") suspend fun clearMovimientos()
    @Query("DELETE FROM sync_resumen") suspend fun clearResumen()
    @Query("DELETE FROM sync_outbox") suspend fun clearOutbox()
    @Query("DELETE FROM sync_cursor") suspend fun clearCursors()
    @Query("DELETE FROM sync_account_fingerprint") suspend fun clearFingerprints()
}
