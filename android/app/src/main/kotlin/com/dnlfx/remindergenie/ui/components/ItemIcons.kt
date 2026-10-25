package com.dnlfx.remindergenie.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalCarWash
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Items store the iOS app's icon names (SF Symbols) so the two apps' data stays compatible.
 * This maps each one to the closest Material icon.
 */
object ItemIcons {
    private val icons: Map<String, ImageVector> = mapOf(
        "eye" to Icons.Outlined.Visibility,
        "eye.circle" to Icons.Outlined.RemoveRedEye,
        "mouth" to Icons.Outlined.SentimentSatisfied,
        "bolt" to Icons.Outlined.Bolt,
        "scissors" to Icons.Outlined.ContentCut,
        "paintbrush" to Icons.Outlined.Brush,
        "pills" to Icons.Outlined.Medication,
        "cross.case" to Icons.Outlined.MedicalServices,
        "figure.run" to Icons.AutoMirrored.Outlined.DirectionsRun,
        "wind" to Icons.Outlined.Air,
        "battery.100" to Icons.Outlined.BatteryFull,
        "bed.double" to Icons.Outlined.Bed,
        "leaf" to Icons.Outlined.Eco,
        "drop" to Icons.Outlined.WaterDrop,
        "refrigerator" to Icons.Outlined.Kitchen,
        "sparkles" to Icons.Outlined.AutoAwesome,
        "car" to Icons.Outlined.DirectionsCar,
        "car.rear.waves.up" to Icons.Outlined.LocalCarWash,
        "waterbottle" to Icons.Outlined.LocalDrink,
        "flame" to Icons.Outlined.LocalFireDepartment,
        "house" to Icons.Outlined.Home,
        "tshirt" to Icons.Outlined.Checkroom,
        "pawprint" to Icons.Outlined.Pets,
        "heart" to Icons.Outlined.FavoriteBorder,
        "lightbulb" to Icons.Outlined.Lightbulb,
        "trash" to Icons.Outlined.Delete,
        "calendar" to Icons.Outlined.CalendarMonth,
        "clock" to Icons.Outlined.Schedule,
    )

    fun forName(name: String): ImageVector = icons[name] ?: Icons.Outlined.Schedule

    fun has(name: String): Boolean = name in icons
}
