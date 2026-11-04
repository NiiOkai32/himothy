package com.campusfit.domain

data class Profile(
    val uid: String, val name: String, val age: Int, val heightCm: Double,
    val weightKg: Double, val goalKg: Double, val level: String,
    val minutes: Int, val university: String, val group: String,
    val activityOnly: Boolean, val percentageRanking: Boolean,
    val healthAcknowledged: Boolean,
)
object HealthPolicy {
    fun validate(p: Profile): String? = when {
        p.name.isBlank() || p.name.length > 80 -> "Enter a name of up to 80 characters."
        p.age !in 18..100 -> "CampusFit is currently for adults aged 18 and over."
        !p.heightCm.isFinite() || p.heightCm !in 120.0..230.0 -> "Enter a height between 120 and 230 cm."
        !p.weightKg.isFinite() || p.weightKg !in 35.0..350.0 -> "Enter a weight between 35 and 350 kg."
        !p.goalKg.isFinite() || p.goalKg !in 35.0..350.0 -> "Enter a goal between 35 and 350 kg."
        p.goalKg / ((p.heightCm / 100) * (p.heightCm / 100)) < 18.5 -> "Choose a goal with a BMI of at least 18.5."
        p.level !in listOf("Beginner", "Intermediate", "Advanced") -> "Choose a fitness level."
        p.minutes !in 2..20 -> "Choose 2 to 20 minutes per day."
        p.university.isBlank() || p.university.length > 120 || p.group.length > 120 -> "Enter a university (up to 120 characters)."
        !p.healthAcknowledged -> "Please review and acknowledge the health guidance."
        else -> null
    }
    fun suggestedWeeks(currentKg: Double, goalKg: Double): Int =
        kotlin.math.ceil(((currentKg - goalKg).coerceAtLeast(0.0)) / 0.5).toInt().coerceAtLeast(1)
}
