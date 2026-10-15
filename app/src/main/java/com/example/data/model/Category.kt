package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class ExpenseCategory(
    val title: String,
    val icon: ImageVector,
    val colorHex: String,
    val color: Color
) {
    FOOD("Food & Dining", Icons.Default.Restaurant, "#FF6B6B", Color(0xFFFF6B6B)),
    GROCERIES("Groceries", Icons.Default.ShoppingCart, "#4ECDC4", Color(0xFF4ECDC4)),
    DATE_NIGHT("Date Night", Icons.Default.Favorite, "#FF4081", Color(0xFFFF4081)),
    SHOPPING("Shopping", Icons.Default.LocalMall, "#AB47BC", Color(0xFFAB47BC)),
    TRANSPORT("Transport", Icons.Default.DirectionsCar, "#42A5F5", Color(0xFF42A5F5)),
    COFFEE("Café & Drinks", Icons.Default.LocalCafe, "#FFA726", Color(0xFFFFA726)),
    HOME_BILLS("Home & Bills", Icons.Default.Home, "#26A69A", Color(0xFF26A69A)),
    GIFTS("Gifts", Icons.Default.CardGiftcard, "#EC407A", Color(0xFFEC407A)),
    OTHER("Other", Icons.Default.MoreHoriz, "#78909C", Color(0xFF78909C));

    companion object {
        fun fromString(value: String): ExpenseCategory {
            return entries.firstOrNull { 
                it.name.equals(value, ignoreCase = true) || it.title.equals(value, ignoreCase = true) 
            } ?: OTHER
        }
    }
}
