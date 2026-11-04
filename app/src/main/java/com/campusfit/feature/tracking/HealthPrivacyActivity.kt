package com.campusfit.feature.tracking
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.campusfit.ui.theme.CampusFitTheme
class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CampusFitTheme { Surface { Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Your health data stays yours", style = MaterialTheme.typography.headlineMedium)
            Text("CampusFit reads today's step total and exercise duration only after you connect Health Connect. These totals are displayed privately; they do not award verified scores or get uploaded. We do not write or delete Health Connect records.")
            Text("Weight and CampusFit workout logs use encrypted device storage and private account sync. Camera frames are processed on-device and discarded. GPS routes stay on-device; cloud sync includes only session totals.")
            Text("You can disconnect Health Connect in Progress, revoke access in system settings, or request account and data deletion in your CampusFit profile. Removing a device copy does not itself erase cloud data.")
            Button(onClick = { finish() }) { Text("Close") }
        } } } }
    }
}
