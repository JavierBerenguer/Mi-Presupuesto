package com.mipatrimonio.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.model.Category
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CategoryIconOption(
    val key: String,
    @StringRes val labelRes: Int,
    val searchTerms: String,
)

object CategoryIcons {
    val options = listOf(
        option("car", R.string.cat_icon_car, "coche automóvil vehículo"),
        option("fuel", R.string.cat_icon_fuel, "gasolina combustible"),
        option("parking", R.string.cat_icon_parking, "aparcamiento parking"),
        option("car_wash", R.string.cat_icon_car_wash, "lavado coche"),
        option("restaurant", R.string.cat_icon_restaurant, "restaurante comida"),
        option("bar", R.string.cat_icon_bar, "bar copa discoteca"),
        option("cafe", R.string.cat_icon_cafe, "café desayuno"),
        option("cinema", R.string.cat_icon_cinema, "cine película"),
        option("music", R.string.cat_icon_music, "música concierto"),
        option("sports", R.string.cat_icon_sports, "deporte pádel fútbol"),
        option("gym", R.string.cat_icon_gym, "gimnasio ejercicio"),
        option("pool", R.string.cat_icon_pool, "piscina natación"),
        option("travel", R.string.cat_icon_travel, "viaje vacaciones desplazamiento"),
        option("hotel", R.string.cat_icon_hotel, "hotel hospedaje"),
        option("train", R.string.cat_icon_train, "tren"),
        option("bus", R.string.cat_icon_bus, "autobús"),
        option("taxi", R.string.cat_icon_taxi, "taxi"),
        option("home", R.string.cat_icon_home, "casa hogar vivienda"),
        option("rent", R.string.cat_icon_rent, "alquiler hipoteca expensas"),
        option("electricity", R.string.cat_icon_electricity, "electricidad potencia luz"),
        option("water", R.string.cat_icon_water, "agua"),
        option("gas", R.string.cat_icon_gas, "gas"),
        option("internet", R.string.cat_icon_internet, "internet wifi"),
        option("phone", R.string.cat_icon_phone, "teléfono móvil"),
        option("tv", R.string.cat_icon_tv, "televisión streaming"),
        option("furniture", R.string.cat_icon_furniture, "muebles"),
        option("garden", R.string.cat_icon_garden, "jardín plantas"),
        option("pet", R.string.cat_icon_pet, "mascota animales"),
        option("pharmacy", R.string.cat_icon_pharmacy, "farmacia medicamentos"),
        option("health", R.string.cat_icon_health, "salud médico"),
        option("glasses", R.string.cat_icon_glasses, "gafas óptica lentillas"),
        option("haircut", R.string.cat_icon_haircut, "peluquería corte"),
        option("clothes", R.string.cat_icon_clothes, "ropa moda"),
        option("jewelry", R.string.cat_icon_jewelry, "joyería bisutería"),
        option("beauty", R.string.cat_icon_beauty, "belleza cosméticos"),
        option("education", R.string.cat_icon_education, "educación estudios"),
        option("book", R.string.cat_icon_book, "libro revista lectura"),
        option("school", R.string.cat_icon_school, "escuela clase seminario"),
        option("computer", R.string.cat_icon_computer, "ordenador portátil monitor"),
        option("smartphone", R.string.cat_icon_smartphone, "smartphone móvil"),
        option("camera", R.string.cat_icon_camera, "cámara fotografía"),
        option("printer", R.string.cat_icon_printer, "impresora"),
        option("ai", R.string.cat_icon_ai, "inteligencia artificial robot"),
        option("salary", R.string.cat_icon_salary, "salario nómina empleo"),
        option("gift", R.string.cat_icon_gift, "regalo"),
        option("savings", R.string.cat_icon_savings, "ahorro pensión subsidio"),
        option("interest", R.string.cat_icon_interest, "intereses ganancias"),
        option("dividends", R.string.cat_icon_dividends, "dividendos"),
        option("investment", R.string.cat_icon_investment, "inversión aportación"),
        option("fees", R.string.cat_icon_fees, "comisiones tarifas"),
        option("taxes", R.string.cat_icon_taxes, "impuestos tasas"),
        option("insurance", R.string.cat_icon_insurance, "seguro"),
        option("credit", R.string.cat_icon_credit, "crédito cuotas tarjeta"),
        option("shopping", R.string.cat_icon_shopping, "compras alimentos ventas"),
        option("subscriptions", R.string.cat_icon_subscriptions, "suscripciones servicios"),
        option("games", R.string.cat_icon_games, "juegos videojuegos"),
        option("events", R.string.cat_icon_events, "eventos ocio parque"),
        option("theater", R.string.cat_icon_theater, "teatro"),
        option("museum", R.string.cat_icon_museum, "museo cultura"),
        option("build", R.string.cat_icon_build, "reparación mantenimiento herramientas"),
        option("other", R.string.cat_icon_other, "otros categoría"),
    )
    val keys: Set<String> = options.mapTo(linkedSetOf(), CategoryIconOption::key)

    fun icon(key: String?): ImageVector = when (key?.takeIf(keys::contains)) {
        "car" -> Icons.Outlined.DirectionsCar
        "fuel" -> Icons.Outlined.LocalGasStation
        "parking" -> Icons.Outlined.LocalParking
        "car_wash" -> Icons.Outlined.LocalCarWash
        "restaurant" -> Icons.Outlined.Restaurant
        "bar" -> Icons.Outlined.LocalBar
        "cafe" -> Icons.Outlined.LocalCafe
        "cinema" -> Icons.Outlined.Movie
        "music" -> Icons.Outlined.MusicNote
        "sports" -> Icons.Outlined.SportsSoccer
        "gym" -> Icons.Outlined.FitnessCenter
        "pool" -> Icons.Outlined.Pool
        "travel" -> Icons.Outlined.Flight
        "hotel" -> Icons.Outlined.Hotel
        "train" -> Icons.Outlined.Train
        "bus" -> Icons.Outlined.DirectionsBus
        "taxi" -> Icons.Outlined.LocalTaxi
        "home", "rent" -> Icons.Outlined.Home
        "electricity" -> Icons.Outlined.ElectricBolt
        "water" -> Icons.Outlined.WaterDrop
        "gas" -> Icons.Outlined.LocalFireDepartment
        "internet" -> Icons.Outlined.Wifi
        "phone", "smartphone" -> Icons.Outlined.PhoneAndroid
        "tv" -> Icons.Outlined.Tv
        "furniture" -> Icons.Outlined.Chair
        "garden" -> Icons.Outlined.Yard
        "pet" -> Icons.Outlined.Pets
        "pharmacy" -> Icons.Outlined.LocalPharmacy
        "health" -> Icons.Outlined.Favorite
        "glasses" -> Icons.Outlined.Visibility
        "haircut" -> Icons.Outlined.ContentCut
        "clothes" -> Icons.Outlined.Checkroom
        "jewelry" -> Icons.Outlined.Diamond
        "beauty" -> Icons.Outlined.FaceRetouchingNatural
        "education", "school" -> Icons.Outlined.School
        "book" -> Icons.Outlined.MenuBook
        "computer" -> Icons.Outlined.Computer
        "camera" -> Icons.Outlined.CameraAlt
        "printer" -> Icons.Outlined.Print
        "ai" -> Icons.Outlined.SmartToy
        "salary" -> Icons.Outlined.Payments
        "gift" -> Icons.Outlined.CardGiftcard
        "savings" -> Icons.Outlined.Savings
        "interest" -> Icons.Outlined.Percent
        "dividends", "investment" -> Icons.Outlined.ShowChart
        "fees", "taxes" -> Icons.Outlined.ReceiptLong
        "insurance" -> Icons.Outlined.Security
        "credit" -> Icons.Outlined.CreditCard
        "shopping" -> Icons.Outlined.ShoppingCart
        "subscriptions" -> Icons.Outlined.Subscriptions
        "games" -> Icons.Outlined.SportsEsports
        "events" -> Icons.Outlined.Event
        "theater" -> Icons.Outlined.TheaterComedy
        "museum" -> Icons.Outlined.Museum
        "build" -> Icons.Outlined.Build
        else -> Icons.Outlined.Category
    }

    private fun option(key: String, labelRes: Int, terms: String) = CategoryIconOption(key, labelRes, normalize(terms))
    internal fun normalize(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
}

data class CategoryIconPickerState(
    val query: String = "",
    val selectedKey: String? = null,
    val filtered: List<CategoryIconOption> = CategoryIcons.options,
)

class CategoryIconPickerViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(CategoryIconPickerState())
    val state: StateFlow<CategoryIconPickerState> = mutableState.asStateFlow()

    fun reset(selectedKey: String?) {
        mutableState.value = CategoryIconPickerState(selectedKey = selectedKey)
    }

    fun setQuery(query: String) {
        val normalized = CategoryIcons.normalize(query.trim())
        mutableState.value = mutableState.value.copy(
            query = query,
            filtered = if (normalized.isEmpty()) CategoryIcons.options else CategoryIcons.options.filter {
                normalized in it.searchTerms || normalized in it.key
            },
        )
    }

    fun select(key: String?) {
        mutableState.value = mutableState.value.copy(selectedKey = key?.takeIf(CategoryIcons.keys::contains))
    }
}

fun Category.resolvedIconKey(categories: Collection<Category>): String? =
    icon ?: parentId?.let { parentId -> categories.firstOrNull { it.id == parentId }?.icon }

@Composable
fun CategoryIconBadge(
    category: Category?,
    categories: Collection<Category>,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val color = category?.let { Color(it.colorArgb) } ?: Color(0xFF8D99AE)
    Box(modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = CategoryIcons.icon(category?.resolvedIconKey(categories)),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}
