package com.mipatrimonio.app.data.sync

import com.mipatrimonio.app.domain.model.MoneyMath
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class PortfolioTotal(val valueMinor: Long, val contributedMinor: Long, val gainMinor: Long, val returnRate: BigDecimal, val irr: BigDecimal?, val realizedMinor: Long)
data class PortfolioClass(val name: String, val valueMinor: Long, val weight: BigDecimal)
data class PortfolioProduct(
    val id: String, val name: String, val type: String, val valueMinor: Long,
    val contributedMinor: Long, val gainMinor: Long, val returnRate: BigDecimal,
    val irr: BigDecimal?, val units: BigDecimal, val priceMinor: Long?, val weight: BigDecimal,
)
data class PortfolioHistoryPoint(val date: LocalDate, val investmentMinor: Long)
data class PortfolioSummary(
    val generated: String, val currency: String, val total: PortfolioTotal,
    val classes: List<PortfolioClass>, val products: List<PortfolioProduct>, val warnings: List<String>,
    val history: List<PortfolioHistoryPoint>? = null,
)

sealed interface PortfolioSummaryResult {
    data class Success(val summary: PortfolioSummary) : PortfolioSummaryResult
    data class Error(val message: String) : PortfolioSummaryResult
}

object PortfolioSummaryParser {
    fun parse(json: String): PortfolioSummaryResult = runCatching {
        val root = JSONObject(json)
        require(root.optInt("version", -1) == 1) { "La versión del resumen no es compatible" }
        val totalJson = root.getJSONObject("total")
        val array = root.getJSONArray("productos")
        val excludedProducts = buildList {
            repeat(array.length()) { index ->
                val value = array.getJSONObject(index)
                if (value.getString("id").startsWith("cuenta_app:")) add(value)
            }
        }
        val rawProducts = buildList {
            repeat(array.length()) { index ->
                val value = array.getJSONObject(index)
                if (!value.getString("id").startsWith("cuenta_app:")) add(value)
            }
        }
        val excludedValue = excludedProducts.fold(0L) { sum, value -> Math.addExact(sum, value.moneyMinor("valor")) }
        val portfolioValue = Math.subtractExact(totalJson.moneyMinor("valor"), excludedValue)
        val products = rawProducts.map { value ->
            val productValue = value.moneyMinor("valor")
            PortfolioProduct(
                value.getString("id"), value.getString("nombre"), value.getString("tipo"), productValue,
                value.moneyMinor("aportado"), value.moneyMinor("ganancia"), value.decimal("rentabilidad"),
                value.nullableDecimal("tir"), value.decimal("unidades"),
                value.nullableDecimal("precio")?.let { MoneyMath.toMinor(it, "EUR") },
                weight(productValue, portfolioValue),
            )
        }
        val excludedByClass = excludedProducts.groupBy { it.getString("tipo") }
            .mapValues { (_, values) -> values.fold(0L) { sum, item -> Math.addExact(sum, item.moneyMinor("valor")) } }
        val classesJson = root.optJSONArray("porClase") ?: JSONArray()
        val classes = buildList {
            repeat(classesJson.length()) { index ->
                val value = classesJson.getJSONObject(index)
                val name = value.getString("clase")
                val classValue = Math.subtractExact(value.moneyMinor("valor"), excludedByClass[name] ?: 0L)
                if (classValue != 0L) add(PortfolioClass(name, classValue, weight(classValue, portfolioValue)))
            }
        }.sortedByDescending(PortfolioClass::valueMinor)
        val warnings = root.optJSONArray("avisos")
        val history = root.optJSONObject("serie")?.validHistory()
        PortfolioSummary(
            root.getString("generado"), root.optString("moneda", "EUR"),
            PortfolioTotal(
                portfolioValue, totalJson.moneyMinor("aportado"), totalJson.moneyMinor("ganancia"),
                totalJson.decimal("rentabilidad"), totalJson.nullableDecimal("tir"), totalJson.moneyMinor("realizado"),
            ),
            classes, products,
            buildList { if (warnings != null) repeat(warnings.length()) { add(warnings.getString(it)) } },
            history,
        )
    }.fold(
        onSuccess = { PortfolioSummaryResult.Success(it) },
        onFailure = { PortfolioSummaryResult.Error(it.message ?: "El resumen de la cartera está incompleto") },
    )

    private fun weight(value: Long, total: Long): BigDecimal = if (total == 0L) BigDecimal.ZERO else
        BigDecimal.valueOf(value).divide(BigDecimal.valueOf(total), 10, RoundingMode.HALF_EVEN)
    private fun JSONObject.moneyMinor(name: String) = MoneyMath.toMinor(decimal(name), "EUR")
    private fun JSONObject.decimal(name: String) = BigDecimal(get(name).toString())
    private fun JSONObject.nullableDecimal(name: String): BigDecimal? = if (!has(name) || isNull(name)) null else decimal(name)

    private fun JSONObject.validHistory(): List<PortfolioHistoryPoint>? {
        val dates = optJSONArray("fechas") ?: return null
        val investments = optJSONArray("inversion") ?: return null
        if (dates.length() == 0 || dates.length() != investments.length()) return null
        return runCatching {
            buildList {
                repeat(dates.length()) { index ->
                    add(
                        PortfolioHistoryPoint(
                            date = LocalDate.parse(dates.getString(index)),
                            investmentMinor = MoneyMath.toMinor(BigDecimal(investments.get(index).toString()), "EUR"),
                        ),
                    )
                }
            }.sortedBy(PortfolioHistoryPoint::date)
        }.getOrNull()
    }
}
