const {test} = require('node:test');
const assert = require('node:assert/strict');
const p = require('../policy');
const now = Date.UTC(2026,10,3,12);
const day = Math.floor(now/p.DAY);
const weight = {epochDay:day,weightKg:70,createdAt:now};
const session = {id:'run-1',startedAt:now-60000,durationSeconds:60,calories:4,source:'guided',distanceMeters:0,reps:0};
test('valid weight keeps only the supported fields',()=>assert.deepEqual(p.weight({...weight,uid:'someone-else',flagged:false},now),weight));
test('invalid, non-finite and out-of-range weights are rejected',()=>{for(const value of [NaN,Infinity,0,34.9,350.1,'70']) assert.throws(()=>p.weight({...weight,weightKg:value},now));});
test('future UTC day and mismatched capture timestamp are rejected',()=>{assert.throws(()=>p.weight({...weight,epochDay:day+1},now));assert.throws(()=>p.weight({...weight,createdAt:(day-1)*p.DAY},now));});
test('exact 2% daily loss is not flagged but larger loss is',()=>{assert.equal(p.unusual({epochDay:day-1,weightKg:100},{epochDay:day,weightKg:98}),false);assert.equal(p.unusual({epochDay:day-1,weightKg:100},{epochDay:day,weightKg:97.9}),true);});
test('gains and missing baselines do not trigger a loss flag',()=>{assert.equal(p.unusual(null,weight),false);assert.equal(p.unusual({epochDay:day-1,weightKg:69},weight),false);});
test('multi-day safety classification uses compound daily rate',()=>{assert.equal(p.unusual({epochDay:day-2,weightKg:100},{epochDay:day,weightKg:96.04}),false);assert.equal(p.unusual({epochDay:day-2,weightKg:100},{epochDay:day,weightKg:95}),true);});
test('workout totals are explicitly self-reported',()=>{assert.equal(p.session(session,now).verification,'self_reported');assert.equal(p.session({...session,verification:'verified'},now).verification,'self_reported');});
test('invalid durations, calories and IDs are rejected',()=>{for(const record of [{...session,durationSeconds:0},{...session,durationSeconds:1201},{...session,calories:Infinity},{...session,id:'../other'},{...session,startedAt:now+1}])assert.throws(()=>p.session(record,now));});
test('distance and reps must match session source',()=>{assert.throws(()=>p.session({...session,distanceMeters:10},now));assert.throws(()=>p.session({...session,reps:3},now));assert.equal(p.session({...session,source:'gps-walk',distanceMeters:50},now).distanceMeters,50);assert.equal(p.session({...session,source:'camera',reps:5},now).reps,5);});
test('speed and rep-rate bounds reject impossible totals',()=>{assert.throws(()=>p.session({...session,source:'gps-run',distanceMeters:721},now));assert.throws(()=>p.session({...session,source:'camera',reps:241},now));});

// Execute the actual callable guards. Deliberately unreachable emulator prevents any
// accidental production RPC if a guard regresses; these are not emulator integration tests.
process.env.GCLOUD_PROJECT='demo-campusfit';
process.env.FIRESTORE_EMULATOR_HOST='127.0.0.1:1';
const handlers=require('../index');
const authenticated={uid:'owner',token:{email_verified:true,auth_time:Math.floor(Date.now()/1000)}};
test('callable rejects unauthenticated uploads before storage access',async()=>{
  await assert.rejects(handlers.syncTracking.run({data:{uid:'owner',kind:'weight',record:weight}}),e=>e.code==='unauthenticated');
});
test('both callables bind the requested UID to the authenticated account',async()=>{
  await assert.rejects(handlers.syncTracking.run({auth:authenticated,data:{uid:'different',kind:'weight',record:weight}}),e=>e.code==='permission-denied');
  await assert.rejects(handlers.deleteCampusFitAccount.run({auth:authenticated,data:{uid:'different'}}),e=>e.code==='permission-denied');
});
test('unverified accounts cannot upload private health claims',async()=>{
  await assert.rejects(handlers.syncTracking.run({auth:{...authenticated,token:{email_verified:false}},data:{uid:'owner',kind:'weight',record:weight}}),e=>e.code==='permission-denied');
});
test('account deletion requires a recent sign-in',async()=>{
  await assert.rejects(handlers.deleteCampusFitAccount.run({auth:{...authenticated,token:{auth_time:1}},data:{uid:'owner'}}),e=>e.code==='failed-precondition');
});
test('invalid health uploads fail before any storage transaction',async()=>{
  await assert.rejects(handlers.syncTracking.run({auth:authenticated,data:{uid:'owner',kind:'weight',record:{...weight,weightKg:Infinity}}}),e=>e.code==='invalid-argument');
});
