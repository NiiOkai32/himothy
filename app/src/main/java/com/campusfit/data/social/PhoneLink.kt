package com.campusfit.data.social

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import java.util.concurrent.TimeUnit

/** User-initiated SMS linking. No phone number is written to the public directory. */
class PhoneLink(private val activity:Activity,private val uid:String,private val status:(String)->Unit){
 private var verificationId:String?=null
 fun send(number:String){
  if(!number.matches(Regex("\\+[1-9]\\d{7,14}"))){status("Use an international +country-code number.");return}
  val auth=FirebaseAuth.getInstance()
  if(auth.currentUser?.uid!=uid){status("Account changed; sign in again.");return}
  val options=PhoneAuthOptions.newBuilder(auth).setPhoneNumber(number).setActivity(activity).setTimeout(60,TimeUnit.SECONDS)
   .setCallbacks(object:PhoneAuthProvider.OnVerificationStateChangedCallbacks(){
    override fun onVerificationCompleted(credential:PhoneAuthCredential){link(credential)}
    override fun onVerificationFailed(exception:FirebaseException){status("SMS verification failed. Check the number, connection and Firebase Phone provider setup.")}
    override fun onCodeSent(id:String,token:PhoneAuthProvider.ForceResendingToken){verificationId=id;status("Code sent. Enter it to link your verified phone.")}
   }).build()
  PhoneAuthProvider.verifyPhoneNumber(options)
 }
 fun confirm(code:String){val id=verificationId;if(id==null||!code.matches(Regex("\\d{6}"))){status("Request a code and enter its six digits.");return};link(PhoneAuthProvider.getCredential(id,code))}
 private fun link(credential:PhoneAuthCredential){
  val user=FirebaseAuth.getInstance().currentUser
  if(user?.uid!=uid){status("Account changed; sign in again.");return}
  user.linkWithCredential(credential).addOnSuccessListener{status("Phone verified. Enable phone discovery in your social sharing preferences if you want it.")}
   .addOnFailureListener{status("Phone could not be linked. It may already belong to an account; sign in again and check provider settings.")}
 }
}
