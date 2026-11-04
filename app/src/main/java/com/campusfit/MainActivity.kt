package com.campusfit
import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.campusfit.ui.CampusFitApp
import com.campusfit.ui.theme.CampusFitTheme
import dagger.hilt.android.AndroidEntryPoint
@AndroidEntryPoint class MainActivity : ComponentActivity() {
    private val openScreen=mutableStateOf<String?>(null)
    private val inboxUid=mutableStateOf<String?>(null)
    private fun route(intent:Intent?){friendInvite.value=intent?.dataString?.takeIf{it.startsWith("campusfit://friend/")};openScreen.value=if(intent?.action=="com.campusfit.OPEN_INBOX")"notifications" else intent?.getStringExtra("open_screen")?.takeIf{it=="challenges"};inboxUid.value=intent?.getStringExtra("inbox_uid")}
    private val friendInvite = mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        route(intent)
        enableEdgeToEdge()
        setContent { CampusFitTheme { CampusFitApp(invite = friendInvite.value, onInviteHandled = { friendInvite.value = null },openScreen=openScreen.value,inboxUid=inboxUid.value,onOpenHandled={openScreen.value=null;inboxUid.value=null}) } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); route(intent) }
}
