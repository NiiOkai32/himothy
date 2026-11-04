package com.campusfit.data.tracking

import androidx.room.withTransaction
import com.campusfit.data.local.*
import com.campusfit.domain.AuthRepository
import com.campusfit.domain.tracking.WeightPolicy
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

class TrackingSyncEngine @Inject constructor(private val db: CampusDatabase, private val auth: AuthRepository) {
    private suspend fun active(uid: String) = auth.account.value?.let { it.uid == uid && it.verified && !it.demo } == true && !db.extra().deleting(uid)
    private fun number(map: Map<*, *>, key: String) = map[key] as? Number ?: error("Invalid cloud record")
    private fun weight(uid: String, map: Map<*, *>, note: String = ""): WeighInEntity {
        val kg = number(map,"weightKg").toDouble(); WeightPolicy.validate(kg)
        return WeighInEntity(uid, number(map,"epochDay").toLong(), kg, map["flagged"] == true, false, number(map,"createdAt").toLong(), note)
    }
    private fun session(uid: String, map: Map<*, *>): WorkoutSessionEntity {
        val seconds = number(map,"durationSeconds").toInt()
        require(seconds in 1..1200)
        return WorkoutSessionEntity(map["id"] as String, uid, number(map,"startedAt").toLong(), seconds/60, number(map,"calories").toDouble(), false, seconds,
            map["source"] as String, number(map,"distanceMeters").toDouble(), number(map,"reps").toInt())
    }
    suspend fun sync(uid: String, pull: Boolean): SyncResult {
        if (!auth.cloudConfigured || !active(uid)) return SyncResult.DONE
        val store = FirebaseFirestore.getInstance()
        val functions = FirebaseFunctions.getInstance("us-central1")
        try {
            // Let the earlier profile write land before attempting health uploads.
            if (!store.collection("users").document(uid).get(com.google.firebase.firestore.Source.SERVER).await().exists()) return SyncResult.RETRY
            val pendingWeights = db.tracking().pendingWeights(uid)
            for (entry in pendingWeights) {
                if (!active(uid)) return SyncResult.DONE
                try {
                    val result = withTimeout(60_000) { functions.getHttpsCallable("syncTracking").call(mapOf("uid" to uid, "kind" to "weight", "record" to mapOf("epochDay" to entry.epochDay, "weightKg" to entry.weightKg, "createdAt" to entry.createdAt))).await().data as Map<*, *> }
                    val canonical = weight(uid, result["record"] as Map<*, *>, if (result["collision"] == true) "An earlier entry from another device was kept for this UTC day." else "")
                    db.withTransaction { if (active(uid)) db.tracking().merge(canonical) }
                } catch (e: FirebaseFunctionsException) {
                    val rejected = e.code in listOf(FirebaseFunctionsException.Code.INVALID_ARGUMENT, FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED)
                    db.tracking().weightIssue(uid, entry.epochDay, if (rejected) "Rejected: cloud validation or daily quota declined this record. Device copy kept; manual retry is available." else "Cloud upload failed. Check Firebase setup or retry; the device copy is kept.")
                    if (!rejected) throw e
                }
            }
            for (entry in db.tracking().pendingSessions(uid)) {
                if (!active(uid)) return SyncResult.DONE
                try {
                    val record = mapOf("id" to entry.id, "startedAt" to entry.startedAt, "durationSeconds" to entry.durationSeconds, "calories" to entry.calories,
                        "source" to entry.source, "distanceMeters" to entry.distanceMeters, "reps" to entry.reps)
                    val response = withTimeout(60_000) { functions.getHttpsCallable("syncTracking").call(mapOf("uid" to uid, "kind" to "session", "record" to record)).await().data as Map<*, *> }
                    val canonical = session(uid, response["record"] as Map<*, *>)
                    db.withTransaction { if (active(uid)) db.tracking().merge(canonical) }
                } catch (e: FirebaseFunctionsException) {
                    val rejected = e.code in listOf(FirebaseFunctionsException.Code.INVALID_ARGUMENT, FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED)
                    db.tracking().sessionIssue(uid, entry.id, if (rejected) "Rejected: cloud validation or daily quota declined this record. Device copy kept; manual retry is available." else "Cloud upload failed. Check Firebase setup or retry; the device copy is kept.")
                    if (!rejected) throw e
                }
            }
            if (!pull && pendingWeights.isEmpty()) return SyncResult.DONE
            // Bounded pages preserve large histories without an unbounded query response.
            for (collection in listOf("weighIns", "sessions")) {
                var query: Query = store.collection("users").document(uid).collection(collection).orderBy(com.google.firebase.firestore.FieldPath.documentId()).limit(200)
                while (active(uid)) {
                    val page = withTimeout(60_000) { query.get(com.google.firebase.firestore.Source.SERVER).await() }
                    db.withTransaction {
                        if (active(uid)) page.documents.forEach { doc ->
                            if (collection == "weighIns") {
                                val remote = weight(uid, doc.data!!)
                                val local = db.tracking().weight(uid, remote.epochDay)
                                if (local?.syncPending != true) db.tracking().merge(remote.copy(syncIssue = local?.syncIssue.orEmpty()))
                            } else {
                                val remote = session(uid, doc.data!!)
                                if (db.tracking().session(remote.id)?.syncPending != true) db.tracking().merge(remote)
                            }
                        }
                    }
                    if (page.size() < 200) break
                    query = query.startAfter(page.documents.last())
                }
            }
            return SyncResult.DONE
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) { return SyncResult.RETRY }
        catch (e: CancellationException) { throw e }
        catch (e: FirebaseFunctionsException) {
            return if (e.code in listOf(FirebaseFunctionsException.Code.INVALID_ARGUMENT, FirebaseFunctionsException.Code.PERMISSION_DENIED, FirebaseFunctionsException.Code.NOT_FOUND)) SyncResult.BLOCKED else SyncResult.RETRY
        } catch (_: Exception) { return SyncResult.RETRY }
    }
}
