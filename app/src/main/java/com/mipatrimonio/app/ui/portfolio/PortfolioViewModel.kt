package com.mipatrimonio.app.ui.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.db.SyncMovimientoEntity
import com.mipatrimonio.app.data.db.SyncProductoEntity
import com.mipatrimonio.app.data.db.SyncResumenEntity
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.sync.PortfolioSummary
import com.mipatrimonio.app.data.sync.PortfolioSummaryParser
import com.mipatrimonio.app.data.sync.PortfolioSummaryResult
import com.mipatrimonio.app.data.sync.SyncRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.MoneyMath
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

interface PortfolioGateway {
    val products: Flow<List<SyncProductoEntity>>
    val movements: Flow<List<SyncMovimientoEntity>>
    val summary: Flow<SyncResumenEntity?>
    suspend fun hasSession(): Boolean
    suspend fun synchronize()
    suspend fun createPurchase(productId: String, date: LocalDate, amountMinor: Long, units: BigDecimal?, commissionMinor: Long?, accountId: String?, note: String?)
    suspend fun editMovement(id: String, date: LocalDate, amountMinor: Long, units: BigDecimal?, commissionMinor: Long?, accountId: String?, note: String?)
    suspend fun deleteMovement(id: String)
}

class SyncPortfolioGateway(private val repository: SyncRepository) : PortfolioGateway {
    override val products = repository.productos
    override val movements = repository.movimientos
    override val summary = repository.resumen
    override suspend fun hasSession() = repository.hasSession()
    override suspend fun synchronize() = repository.synchronize()
    override suspend fun createPurchase(productId: String, date: LocalDate, amountMinor: Long, units: BigDecimal?, commissionMinor: Long?, accountId: String?, note: String?) {
        repository.crearCompra(productId, date, amountMinor, units, commissionMinor, accountId, "movil", note)
    }
    override suspend fun editMovement(id: String, date: LocalDate, amountMinor: Long, units: BigDecimal?, commissionMinor: Long?, accountId: String?, note: String?) =
        repository.editarMovimientoPropio(id, date, amountMinor, units, commissionMinor, accountId, note)
    override suspend fun deleteMovement(id: String) = repository.borrarMovimientoPropio(id)
}

enum class PortfolioStatus { NO_SESSION, NO_SUMMARY, READY, ERROR }

data class PortfolioUiState(
    val loading: Boolean = true,
    val status: PortfolioStatus = PortfolioStatus.NO_SESSION,
    val summary: PortfolioSummary? = null,
    val products: List<SyncProductoEntity> = emptyList(),
    val movements: List<SyncMovimientoEntity> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val syncing: Boolean = false,
    val error: String? = null,
)

data class PurchaseValues(
    val productId: String,
    val date: LocalDate,
    val amountMinor: Long,
    val units: BigDecimal?,
    val commissionMinor: Long?,
    val accountId: String?,
    val note: String?,
)

fun validatePurchase(
    productId: String?, date: LocalDate, amountText: String, unitsText: String,
    commissionText: String, accountId: String?, note: String,
    products: List<SyncProductoEntity>, accounts: List<Account>, today: LocalDate,
): Result<PurchaseValues> = runCatching {
    val product = products.firstOrNull { it.id == productId && it.tipo !in setOf("efectivo", "deuda", "inmueble") }
        ?: throw IllegalArgumentException("Selecciona un producto")
    require(!date.isAfter(today)) { "La fecha no puede ser futura" }
    val amount = MoneyMath.parse(amountText)?.let { MoneyMath.toMinor(it, "EUR") }
        ?: throw IllegalArgumentException("El importe no es válido")
    require(amount > 0) { "El importe debe ser mayor que cero" }
    val units = unitsText.takeIf(String::isNotBlank)?.let {
        MoneyMath.parse(it) ?: throw IllegalArgumentException("Las unidades no son válidas")
    }
    val commission = commissionText.takeIf(String::isNotBlank)?.let {
        val value = MoneyMath.parse(it)?.let { decimal -> MoneyMath.toMinor(decimal, "EUR") }
            ?: throw IllegalArgumentException("La comisión no es válida")
        require(value >= 0) { "La comisión no puede ser negativa" }
        require(value < amount) { "La comisión debe ser menor que el importe" }
        value
    }
    accountId?.let { id ->
        val account = accounts.firstOrNull { it.id == id } ?: throw IllegalArgumentException("La cuenta no existe")
        require(account.currency == "EUR" && !account.archived) { "La cuenta de liquidez debe ser una cuenta EUR activa" }
    }
    PurchaseValues(product.id, date, amount, units, commission, accountId, note.trim().takeIf(String::isNotEmpty))
}

class PortfolioViewModel(
    private val gateway: PortfolioGateway,
    ledger: LedgerRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val session = MutableStateFlow<Boolean?>(null)
    private val syncing = MutableStateFlow(false)
    private val actionError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PortfolioUiState> = combine(
        session, gateway.products, gateway.movements, gateway.summary, ledger.accounts,
        combine(syncing, actionError, ::Pair),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val products = values[1] as List<SyncProductoEntity>
        @Suppress("UNCHECKED_CAST")
        val movements = values[2] as List<SyncMovimientoEntity>
        val rawSummary = values[3] as SyncResumenEntity?
        @Suppress("UNCHECKED_CAST")
        val accounts = values[4] as List<Account>
        @Suppress("UNCHECKED_CAST")
        val local = values[5] as Pair<Boolean, String?>
        val parsed = rawSummary?.let { PortfolioSummaryParser.parse(it.datosJson) }
        val status = when {
            values[0] == null -> PortfolioStatus.NO_SESSION
            values[0] == false -> PortfolioStatus.NO_SESSION
            rawSummary == null -> PortfolioStatus.NO_SUMMARY
            parsed is PortfolioSummaryResult.Error -> PortfolioStatus.ERROR
            else -> PortfolioStatus.READY
        }
        PortfolioUiState(
            loading = values[0] == null, status = status,
            summary = (parsed as? PortfolioSummaryResult.Success)?.summary,
            products = products.filter { it.tipo !in setOf("efectivo", "deuda", "inmueble") },
            movements = movements, accounts = accounts, syncing = local.first,
            error = local.second ?: (parsed as? PortfolioSummaryResult.Error)?.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PortfolioUiState())

    init { viewModelScope.launch { session.value = runCatching { gateway.hasSession() }.getOrDefault(false) } }

    fun refresh() = viewModelScope.launch {
        syncing.value = true
        runCatching { gateway.synchronize() }.onFailure { actionError.value = it.message ?: "No se pudo sincronizar" }
        session.value = runCatching { gateway.hasSession() }.getOrDefault(false)
        syncing.value = false
    }

    fun savePurchase(productId: String?, date: LocalDate, amount: String, units: String, commission: String, accountId: String?, note: String, onSaved: () -> Unit) {
        val validation = validatePurchase(productId, date, amount, units, commission, accountId, note, uiState.value.products, uiState.value.accounts, today())
        validation.onFailure { actionError.value = it.message }.onSuccess { value ->
            viewModelScope.launch {
                runCatching { gateway.createPurchase(value.productId, value.date, value.amountMinor, value.units, value.commissionMinor, value.accountId, value.note) }
                    .onSuccess { actionError.value = null; onSaved() }
                    .onFailure { actionError.value = it.message }
            }
        }
    }

    fun editMovement(id: String, date: LocalDate, amount: String, units: String, commission: String, accountId: String?, note: String, onSaved: () -> Unit) {
        val movement = uiState.value.movements.firstOrNull { it.id == id } ?: return
        val validation = validatePurchase(movement.productoId, date, amount, units, commission, accountId, note, uiState.value.products, uiState.value.accounts, today())
        validation.onFailure { actionError.value = it.message }.onSuccess { value ->
            viewModelScope.launch {
                runCatching { gateway.editMovement(id, value.date, value.amountMinor, value.units, value.commissionMinor, value.accountId, value.note) }
                    .onSuccess { actionError.value = null; onSaved() }.onFailure { actionError.value = it.message }
            }
        }
    }

    fun deleteMovement(id: String) = viewModelScope.launch {
        runCatching { gateway.deleteMovement(id) }.onFailure { actionError.value = it.message }
    }
    fun clearError() { actionError.value = null }
}
