package com.campusfit.feature.social

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.campusfit.ui.components.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.campusfit.data.social.PhoneLink
import com.campusfit.domain.*
import com.campusfit.domain.social.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private fun Context.activity():Activity? { var current=this;while(current is ContextWrapper){if(current is Activity)return current;current=current.baseContext};return current as? Activity }
@Composable fun FriendsScreen(profile:Profile,state:SocialState,vm:SocialViewModel,scan:()->Unit,back:()->Unit){
 val context=LocalContext.current
 var handle by rememberSaveable{mutableStateOf("")}
 var phone by remember{mutableStateOf("")};var otp by remember{mutableStateOf("")}
 var groupName by rememberSaveable{mutableStateOf("")};var groupKind by rememberSaveable{mutableStateOf("friends")};var code by rememberSaveable{mutableStateOf("")}
 var showSharing by rememberSaveable{mutableStateOf(false)}
 var closing by remember{mutableStateOf<Group?>(null)}
 val link=remember(profile.uid,vm.cloud){if(vm.cloud)context.activity()?.let{PhoneLink(it,profile.uid,vm::info)}else null}
 val contact=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
  val uri=result.data?.data
  if(result.resultCode==Activity.RESULT_OK&&uri!=null){try{context.contentResolver.query(uri,arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER,ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER),null,null,null)?.use{cursor->if(cursor.moveToFirst()){val normalized=cursor.getString(1);phone=normalized?:cursor.getString(0).orEmpty();vm.info("Review the selected number and add its +country code if needed. Only Lookup sends it.")}}}catch(_:Exception){vm.info("The selected number could not be read. Enter it manually instead.")}}
 }
 DisposableEffect(vm){vm.watch();onDispose{vm.stopWatching()}}
 val me=state.summary?.me
 var add by rememberSaveable{mutableStateOf(false)}
 var phones by rememberSaveable{mutableStateOf(false)}
 var groups by rememberSaveable{mutableStateOf(false)}
 var qr by rememberSaveable{mutableStateOf(false)}
 LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  item{SocialHeading("Find your people.",back);SocialNotice(state)}
  item{HeroCard("Same crew.\nNew energy.","Your campus circle","Add a friend",{add=true},BuddyMood.TROPHY,Peach)}
  item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){ActionTile("My friend code",Icons.Default.QrCode,{qr=true},Modifier.weight(1f));ActionTile("Sharing",Icons.Default.Tune,{showSharing=true},Modifier.weight(1f))}}
  if(state.summary?.friends.isNullOrEmpty())item{EmptyMoment("Your crew starts with one.","Send a request to someone you know.")}
  items(state.summary?.friends.orEmpty(),key={it.id}){friend->Card{Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
   Text(friend.person.alias,style=MaterialTheme.typography.titleMedium);Text("@${friend.person.handle}",style=MaterialTheme.typography.labelMedium)
   SoftTag(if(friend.status=="accepted")"In your crew"else if(friend.to==profile.uid)"Wants to join your crew"else"Request sent",Icons.Default.People)
   Row{if(friend.status=="pending"&&friend.to==profile.uid)TextButton(onClick={vm.friend(friend.person.handle,"accept")},enabled=!state.busy){Text("Accept")};TextButton(onClick={vm.friend(friend.person.handle,if(friend.status=="accepted")"remove"else"decline")},enabled=!state.busy){Text(if(friend.status=="accepted")"Remove"else"Dismiss")};TextButton(onClick={vm.friend(friend.person.handle,"block")},enabled=!state.busy){Text("Block")}}
  }}}
  items(state.summary?.blocked.orEmpty(),key={"blocked:${it.uid}"}){person->Row{Text("Blocked: ${person.alias}",Modifier.weight(1f));TextButton(onClick={vm.unblock(person.uid)},enabled=!state.busy){Text("Unblock")}}}
  item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Your groups",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);IconButton(onClick={groups=true}){Icon(Icons.Default.Add,"Create or join a group")}}}
  if(state.summary?.groups.isNullOrEmpty())item{Text("Hostel, class or your favourite people.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
  items(state.summary?.groups.orEmpty(),key={it.id}){g->Card{Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
   Text(g.name,style=MaterialTheme.typography.titleMedium);Text("${g.memberUids.size}/50 people · ${g.kind}",style=MaterialTheme.typography.labelMedium)
   OutlinedButton(onClick={val share=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"Join ${g.name} on CampusFit. Invitation code: ${g.code}");context.startActivity(Intent.createChooser(share,"Share group invitation"))}){Text("Invite someone")}
   TextButton(onClick={if(g.owner==profile.uid)closing=g else vm.group("leave",id=g.id)},enabled=!state.busy){Text(if(g.owner==profile.uid)"Close group"else"Leave group")}
  }}}
 }
 if(add)DetailPopup("Who’s joining your crew?",{add=false}){
  SocialNotice(state)
  OutlinedTextField(handle,{handle=it},label={Text("Their exact username")},singleLine=true,modifier=Modifier.fillMaxWidth())
  Button(onClick={vm.friend(handle,"request")},enabled=vm.cloud&&me!=null&&!state.busy,modifier=Modifier.fillMaxWidth()){Text("Send request")}
  OutlinedButton(onClick={add=false;scan()},enabled=vm.cloud&&me!=null,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.QrCodeScanner,null);Text(" Scan their code")}
  TextButton(onClick={phones=true}){Text("Find by phone")}
  InfoBubble("You’re in control","Friends must accept before private friend challenges become available. Only supportive messages are allowed. No address book is uploaded. Phone discovery requires a verified number and separate opt-in.",Icons.Default.Lock)
 }
 if(showSharing)DetailPopup("Your social space.",{showSharing=false}){SocialNotice(state);SocialSharing(profile,state,vm)}
 if(qr)DetailPopup("Your crew pass.",{qr=false}){
  if(me!=null&&vm.cloud){Text("@${me.handle}",style=MaterialTheme.typography.titleLarge);Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){FriendQr(me.handle)}
   Button(onClick={val intent=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"Add me on CampusFit: campusfit://friend/${me.handle}");context.startActivity(Intent.createChooser(intent,"Share your friend code"))},enabled=vm.cloud,modifier=Modifier.fillMaxWidth()){Text("Share my code")}
  }else if(!vm.cloud){Text("Friend codes are available when you sign in. Your demo stays on this phone.")}else{Text("Choose your username first.");Button(onClick={qr=false;showSharing=true}){Text("Set up my alias")}}
 }
 if(phones)DetailPopup("Find a familiar number.",{phones=false}){
  SocialNotice(state)
  Text("Only the selected number is sent when you tap Lookup. Their verified phone must be discoverable.",style=MaterialTheme.typography.bodySmall)
  OutlinedTextField(phone,{phone=it},label={Text("Phone · +country code")},singleLine=true,modifier=Modifier.fillMaxWidth())
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
   OutlinedButton(onClick={try{contact.launch(Intent(Intent.ACTION_PICK,ContactsContract.CommonDataKinds.Phone.CONTENT_URI))}catch(_:ActivityNotFoundException){vm.info("No contact picker is installed. Enter a number manually.")}},enabled=vm.cloud){Text("Pick a contact")}
   Button(onClick={vm.phoneLookup(phone)},enabled=vm.cloud&&!state.busy){Text("Lookup")}
  }
  HorizontalDivider()
  Text("Want friends to find you?",style=MaterialTheme.typography.titleMedium)
  Text("Verify your own number, then opt into discovery in Sharing. Verification may send an SMS; carrier charges may apply.",style=MaterialTheme.typography.bodySmall)
  OutlinedButton(onClick={link?.send(phone)},enabled=link!=null&&!state.busy){Text("Verify my number by SMS")}
  OutlinedTextField(otp,{otp=it},label={Text("SMS code")},singleLine=true,modifier=Modifier.fillMaxWidth())
  Button(onClick={link?.confirm(otp)},enabled=link!=null){Text("Link my number")}
 }
 if(groups)DetailPopup("Build your little team.",{groups=false},BuddyMood.TROPHY){
  SocialNotice(state)
  OutlinedTextField(groupName,{groupName=it},label={Text("Group name")},modifier=Modifier.fillMaxWidth())
  LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(listOf("friends","hostel","department","class")){kind->FilterChip(groupKind==kind,{groupKind=kind},label={Text(kind.replaceFirstChar(Char::uppercase))})}}
  Button(onClick={vm.group("create",name=groupName,kind=groupKind)},enabled=vm.cloud&&me!=null&&!state.busy){Text("Create group")}
  HorizontalDivider()
  OutlinedTextField(code,{code=it},label={Text("Got an invite code?")},singleLine=true,modifier=Modifier.fillMaxWidth())
  OutlinedButton(onClick={vm.group("join",code=code.trim())},enabled=vm.cloud&&me!=null&&!state.busy){Text("Join their group")}
  Text("An invitation code grants group membership. Share it only with intended members. Up to 50 people can join.",style=MaterialTheme.typography.bodySmall)
 }
 closing?.let{g->AlertDialog(onDismissRequest={closing=null},title={Text("Close ${g.name}?")},text={Text("This removes the group, its invitation code and associated challenges. Private workout history remains yours.")},confirmButton={TextButton(onClick={closing=null;vm.group("close",id=g.id)}){Text("Close group")}},dismissButton={TextButton(onClick={closing=null}){Text("Keep group")}})}
}
@Composable private fun FriendQr(handle:String){
 val bitmap=remember(handle){val matrix=QRCodeWriter().encode("campusfit://friend/$handle",BarcodeFormat.QR_CODE,256,256);Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888).apply{for(y in 0 until 256)for(x in 0 until 256)setPixel(x,y,if(matrix[x,y])Color.BLACK else Color.WHITE)}}
 Image(bitmap.asImageBitmap(),contentDescription="CampusFit friend QR for @$handle",modifier=Modifier.size(240.dp))
}
@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable fun ScanFriendScreen(vm:SocialViewModel,back:()->Unit){
 val context=LocalContext.current;val owner=LocalLifecycleOwner.current
 var allowed by remember{mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)}
 var error by remember{mutableStateOf<String?>(null)}
 val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){allowed=it;if(!it)error="Camera permission was declined. You can enter a username instead."}
 val previewView=remember{PreviewView(context)}
 val onFound by rememberUpdatedState<(String)->Unit>({raw->try{SocialPolicy.friendCode(raw);vm.friendCode(raw);back()}catch(_:Exception){error="That QR code is not a CampusFit friend link."}})
 Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
  SocialHeading("Scan a friend code",back);Text("Point the camera at a CampusFit friend QR. Frames are processed on-device and discarded.")
  if(!allowed)Button(onClick={launcher.launch(Manifest.permission.CAMERA)}){Text("Allow camera")}
  error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  if(allowed)AndroidView(factory={previewView},modifier=Modifier.fillMaxWidth().height(360.dp))
 }
 DisposableEffect(allowed,owner){
  val alive=AtomicBoolean(true);val sent=AtomicBoolean(false);val executor=Executors.newSingleThreadExecutor();val scanner=BarcodeScanning.getClient()
  val preview=Preview.Builder().build().apply{setSurfaceProvider(previewView.surfaceProvider)}
  val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
  analysis.setAnalyzer(executor){proxy->val image=proxy.image;if(!alive.get()||sent.get()||image==null)proxy.close()else try{scanner.process(InputImage.fromMediaImage(image,proxy.imageInfo.rotationDegrees)).addOnSuccessListener{codes->if(alive.get())codes.mapNotNull{it.rawValue}.firstOrNull{it.startsWith("campusfit://friend/")}?.let{if(sent.compareAndSet(false,true))onFound(it)}}.addOnCompleteListener{proxy.close()}}catch(_:Exception){proxy.close()}}
  val future=ProcessCameraProvider.getInstance(context)
  if(allowed)future.addListener({if(alive.get())try{future.get().bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,preview,analysis)}catch(_:Exception){error="Camera could not be opened. Enter the username instead."}},ContextCompat.getMainExecutor(context))
  onDispose{alive.set(false);analysis.clearAnalyzer();if(future.isDone)try{future.get().unbind(preview,analysis)}catch(_:Exception){};scanner.close();executor.shutdown()}
 }
}
