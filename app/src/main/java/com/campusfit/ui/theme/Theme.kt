package com.campusfit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.campusfit.R

private val Light=lightColorScheme(
    primary=Color(0xFF5140AB),onPrimary=Color.White,primaryContainer=Color(0xFFE9E2FF),onPrimaryContainer=Color(0xFF2C1C68),
    secondary=Color(0xFF46651B),onSecondary=Color.White,secondaryContainer=Color(0xFFD5F778),onSecondaryContainer=Color(0xFF203009),
    tertiary=Color(0xFF964C30),tertiaryContainer=Color(0xFFFFDCCB),onTertiaryContainer=Color(0xFF54260E),
    background=Color(0xFFF8F7F3),onBackground=Color(0xFF22242A),surface=Color(0xFFFFFEFB),onSurface=Color(0xFF22242A),
    surfaceVariant=Color(0xFFEEEAF4),onSurfaceVariant=Color(0xFF66616F),surfaceContainerHigh=Color(0xFFEEEAF4),
    outlineVariant=Color(0xFFE0DCE5),error=Color(0xFFAB303D)
)
private val Dark=darkColorScheme(
    primary=Color(0xFFC9BBFF),onPrimary=Color(0xFF2C1C68),primaryContainer=Color(0xFF423373),onPrimaryContainer=Color(0xFFE9E2FF),
    secondary=Color(0xFFD5F778),onSecondary=Color(0xFF203009),secondaryContainer=Color(0xFF35481E),onSecondaryContainer=Color(0xFFD5F778),
    tertiary=Color(0xFFFFB696),tertiaryContainer=Color(0xFF653B28),onTertiaryContainer=Color(0xFFFFDCCB),
    background=Color(0xFF17171C),onBackground=Color(0xFFF4F1F8),surface=Color(0xFF202026),onSurface=Color(0xFFF4F1F8),
    surfaceVariant=Color(0xFF302E3B),onSurfaceVariant=Color(0xFFC9C3D2),surfaceContainerHigh=Color(0xFF302E3B),outlineVariant=Color(0xFF45414F)
)
@OptIn(ExperimentalTextApi::class)
private val Outfit=FontFamily(
    Font(R.font.outfit,FontWeight.Normal,variationSettings=FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.outfit,FontWeight.Medium,variationSettings=FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.outfit,FontWeight.Bold,variationSettings=FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.outfit,FontWeight.ExtraBold,variationSettings=FontVariation.Settings(FontVariation.weight(800)))
)
private fun type(size:Int,weight:FontWeight=FontWeight.Normal,line:Int=size+7)=TextStyle(fontFamily=Outfit,fontSize=size.sp,fontWeight=weight,lineHeight=line.sp)
private val AppTypography=Typography(
    displayLarge=type(58,FontWeight.ExtraBold,62),displayMedium=type(46,FontWeight.ExtraBold,50),displaySmall=type(38,FontWeight.ExtraBold,42),
    headlineLarge=type(32,FontWeight.ExtraBold,36),headlineMedium=type(28,FontWeight.Bold,33),headlineSmall=type(24,FontWeight.Bold,29),
    titleLarge=type(22,FontWeight.Bold,27),titleMedium=type(17,FontWeight.Bold,23),titleSmall=type(15,FontWeight.Medium,21),
    bodyLarge=type(16,line=24),bodyMedium=type(14,line=21),bodySmall=type(12,line=18),
    labelLarge=type(14,FontWeight.Medium,20),labelMedium=type(12,FontWeight.Medium,17),labelSmall=type(11,FontWeight.Medium,16)
)
@Composable fun CampusFitTheme(content:@Composable ()->Unit){
    MaterialTheme(colorScheme=if(isSystemInDarkTheme())Dark else Light,typography=AppTypography,shapes=Shapes(small=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),medium=androidx.compose.foundation.shape.RoundedCornerShape(22.dp),large=androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),content=content)
}
