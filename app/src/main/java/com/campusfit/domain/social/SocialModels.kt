package com.campusfit.domain.social

data class PublicPerson(val uid:String="",val handle:String="",val alias:String="",val campusLabel:String="",val campusKey:String="",val campusOptIn:Boolean=false,val leagueOptIn:Boolean=false,val discoverByPhone:Boolean=false)
data class GameStats(val xp:Int=0,val level:Int=1,val seconds:Int=0,val streak:Int=0,val streakCapped:Boolean=false,val longestStreak:Int=0,val tier:Int=0,val badges:List<String> = emptyList(),val backfillDone:Boolean=false)
data class Friend(val id:String="",val from:String="",val to:String="",val status:String="",val person:PublicPerson=PublicPerson())
data class Group(val id:String="",val name:String="",val kind:String="",val owner:String="",val code:String="",val memberUids:List<String> = emptyList())
data class Challenge(val id:String="",val title:String="",val owner:String="",val period:String="weekly",val metric:String="minutes",val visibility:String="friends",val groupId:String="",val start:Long=0,val end:Long=0,val teamMode:Boolean=false,val teams:List<String> = emptyList(),val memberUids:List<String> = emptyList(),val finalized:Boolean=false)
data class RankRow(val uid:String="",val alias:String="",val handle:String="",val score:Double?=null,val rank:Int?=null,val reason:String="",val team:String="",val count:Int=0)
data class Cheer(val id:String="",val from:String="",val alias:String="",val target:String="",val phrase:String="",val at:Long=0)
data class Award(val id:String="",val title:String="",val kind:String="",val at:Long=0)
data class SocialSummary(val me:PublicPerson=PublicPerson(),val game:GameStats=GameStats(),val friends:List<Friend> = emptyList(),val groups:List<Group> = emptyList(),val challenges:List<Challenge> = emptyList(),val awards:List<Award> = emptyList(),val phrases:List<String> = emptyList(),val blocked:List<PublicPerson> = emptyList())
data class ChallengeBoard(val challenge:Challenge=Challenge(),val rows:List<RankRow> = emptyList(),val teams:List<RankRow> = emptyList(),val messages:List<Cheer> = emptyList(),val joined:Boolean=false,val serverTime:Long=0)
data class CampusBoard(val campus:String="",val rows:List<RankRow> = emptyList(),val weekStart:Long=0,val metric:String="minutes")
data class LeagueBoard(val optedIn:Boolean=false,val id:String="",val tier:Int=0,val start:Long=0,val end:Long=0,val finalized:Boolean=false,val rows:List<RankRow> = emptyList(),val pendingFinalization:Boolean=false)
object SocialPolicy {
    fun handle(value:String):String { val name=value.trim().lowercase(java.util.Locale.ROOT);require(name.matches(Regex("[a-z][a-z0-9_]{2,19}"))) { "Use 3–20 letters, numbers or underscores, starting with a letter." };return name }
    fun friendCode(text:String):String {
        val uri=java.net.URI(text.trim())
        require(uri.scheme=="campusfit"&&uri.host=="friend"&&uri.rawQuery==null&&uri.fragment==null&&uri.userInfo==null&&uri.port==-1) { "This is not a CampusFit friend code." }
        return handle(uri.path.removePrefix("/").also { require(!it.contains('/')) })
    }
    fun rank(rows:List<RankRow>):List<RankRow> {
        val sorted=rows.map{if(it.score?.isFinite()==false)it.copy(score=null)else it}.sortedWith(compareByDescending<RankRow>{it.score?.let{n->kotlin.math.round(n*1e6)}?:Double.NEGATIVE_INFINITY}.thenBy{it.uid})
        var last:Double?=null;var rank=0
        return sorted.mapIndexed { index,row -> if(row.score==null)row.copy(rank=null)else{val rounded=kotlin.math.round(row.score*1e6)/1e6;if(last!=rounded){rank=index+1;last=rounded};row.copy(rank=rank)} }
    }
}

data class SocialActivity(val id:String,val startedAt:Long,val seconds:Int,val calories:Double)
object DemoGame {
    /** Local preview only; never writes authoritative ranks, XP or badges to Firebase. */
    fun stats(sessions:List<SocialActivity>,now:Long):GameStats {
        val byDay=sessions.distinctBy{it.id}.groupBy{Math.floorDiv(it.startedAt,86_400_000L)}
        var total=0;var xp=0;var count=0;var early=false
        val active=mutableSetOf<Long>()
        byDay.forEach{(day,items)->
            var seconds=0
            items.sortedWith(compareBy<SocialActivity>{it.startedAt}.thenBy{it.id}).forEach{item->
                if(item.seconds>0&&seconds<3600){seconds+=(3600-seconds).coerceAtMost(item.seconds);if(item.seconds>=120){count++;val hour=java.time.Instant.ofEpochMilli(item.startedAt).atZone(java.time.ZoneOffset.UTC).hour;if(hour in 5..7)early=true}}
            }
            total+=seconds;xp+=seconds/60*2+if(seconds>=120)10 else 0
            if(seconds>=120)active+=day
        }
        var today=Math.floorDiv(now,86_400_000L);if(today !in active)today--
        var streak=0;while(today in active&&streak<400){streak++;today--}
        var longest=0;var run=0;var prior:Long?=null
        active.sorted().forEach{day->run=if(prior!=null&&day==prior!!+1)run+1 else 1;longest=maxOf(longest,run);prior=day}
        val badges=buildList{if(total>=120)add("first_move");if(count>=10)add("ten_sessions");if(total>=6000)add("hundred_minutes");if(early)add("early_bird");if(longest>=7)add("seven_day_streak")}
        return GameStats(xp=xp,level=1+xp/100,seconds=total,streak=streak,streakCapped=streak==400,longestStreak=longest,badges=badges,backfillDone=true)
    }
}
