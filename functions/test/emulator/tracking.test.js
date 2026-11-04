const {test,before,after} = require('node:test');
const assert = require('node:assert/strict');
const {initializeTestEnvironment,assertSucceeds,assertFails} = require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc} = require('firebase/firestore');
const {initializeApp:adminApp} = require('firebase-admin/app');
const {getFirestore} = require('firebase-admin/firestore');
const {getAuth} = require('firebase-admin/auth');
const fs = require('node:fs');
const path = require('node:path');
const projectId='demo-campusfit';
let env,admin,auth,uid,token;
before(async()=>{
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST && process.env.FIREBASE_AUTH_EMULATOR_HOST,'Tests require local emulators');
  admin=adminApp({projectId});auth=getAuth(admin);
  env=await initializeTestEnvironment({projectId,firestore:{rules:fs.readFileSync(path.join(__dirname,'../../../firestore.rules'),'utf8')}});
  const user=await auth.createUser({email:`tracking-${Date.now()}@example.test`,password:'Test-only-12345',emailVerified:true});uid=user.uid;
  const result=await fetch(`http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:user.email,password:'Test-only-12345',returnSecureToken:true})});
  const data=await result.json();assert.ok(data.idToken);token=data.idToken;
  await getFirestore(admin).doc(`users/${uid}`).set({schemaVersion:1,profileJson:'{}'});
});
after(async()=>{await env?.cleanup();});
async function call(name,data){
 const response=await fetch(`http://127.0.0.1:5001/${projectId}/us-central1/${name}`,{method:'POST',headers:{'Content-Type':'application/json','Authorization':`Bearer ${token}`},body:JSON.stringify({data:{uid,...data}})});
 const body=await response.json();return {status:response.status,value:body.result??body.data,error:body.error};
}
test('private tracking sync, immutable UTC entries, ownership and erasure',async()=>{
 const day=Math.floor(Date.now()/86400000);
 const record={epochDay:day,weightKg:70,createdAt:Date.now()};
 const wrong=await call("syncTracking",{uid:"someone-else",kind:"weight",record});assert.notEqual(wrong.status,200);
 const owner=env.authenticatedContext(uid,{email_verified:true}).firestore();
 const outsider=env.authenticatedContext('outsider',{email_verified:true}).firestore();
 await assertFails(setDoc(doc(owner,`users/${uid}/weighIns/${day}`),record));
 let result=await call('syncTracking',{kind:'weight',record});assert.equal(result.status,200,JSON.stringify(result.error));assert.equal(result.value.record.weightKg,70);
 result=await call('syncTracking',{kind:'weight',record:{...record,weightKg:69}});assert.equal(result.value.record.weightKg,70);assert.equal(result.value.collision,true);
 await assertSucceeds(getDoc(doc(owner,`users/${uid}/weighIns/${day}`)));
 await assertFails(getDoc(doc(outsider,`users/${uid}/weighIns/${day}`)));
 await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(),`users/${uid}/weighIns/${day}`)));
 const yesterday={epochDay:day-1,weightKg:75,createdAt:(day-1)*86400000+1000};
 result=await call('syncTracking',{kind:'weight',record:yesterday});assert.equal(result.status,200);
 const changed=await getFirestore(admin).doc(`users/${uid}/weighIns/${day}`).get();assert.equal(changed.data().flagged,true,'out-of-order baseline updates the successor flag');
 const session={id:'test-run',startedAt:Date.now()-60000,durationSeconds:60,calories:4,source:'guided',distanceMeters:0,reps:0};
 for(let i=0;i<2;i++){result=await call('syncTracking',{kind:'session',record:session});assert.equal(result.status,200);}
 const sessions=await getFirestore(admin).collection(`users/${uid}/sessions`).get();assert.equal(sessions.size,1);
 assert.equal(sessions.docs[0].data().verification,'self_reported');
 await assertFails(setDoc(doc(owner,`users/${uid}/sessions/forged`),session));
 result=await call('deleteCampusFitAccount',{});assert.equal(result.status,200,JSON.stringify(result.error));assert.equal(result.value.removed,true);
 assert.equal((await getFirestore(admin).doc(`users/${uid}`).get()).exists,false);
 assert.equal((await getFirestore(admin).collection(`users/${uid}/weighIns`).get()).size,0);
 assert.equal((await getFirestore(admin).collection(`users/${uid}/sessions`).get()).size,0);
 await assert.rejects(auth.getUser(uid),e=>e.code==='auth/user-not-found');
 // A pre-deletion JWT remains structurally valid until expiry; the tombstone blocks reuse.
 await assertFails(setDoc(doc(owner,`users/${uid}`),{profileJson:'{}',schemaVersion:1}));
 result=await call('syncTracking',{kind:'weight',record});assert.notEqual(result.status,200);
});
