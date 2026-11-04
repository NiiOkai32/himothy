package com.campusfit.domain.social
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class SocialPolicyTest {
    @Test fun usernamesAreCanonicalAndBounded(){assertEquals("ama_23",SocialPolicy.handle(" Ama_23 "));listOf("ab","a/b","a b","123abc","").forEach{value->assertThrows(IllegalArgumentException::class.java){SocialPolicy.handle(value)}}}
    @Test fun validFriendQrReturnsOnlyAUsername(){assertEquals("ama_23",SocialPolicy.friendCode("campusfit://friend/ama_23"))}
    @Test fun unrelatedSchemesHostsOrPathsAreRejected(){listOf("https://friend/ama_23","campusfit://evil/ama_23","campusfit://friend/a/b","campusfit://friend/ama_23?uid=other","campusfit://friend/ama_23#other","campusfit://user@friend/ama_23","campusfit://friend:443/ama_23").forEach{value->assertThrows(Exception::class.java){SocialPolicy.friendCode(value)}}}
    @Test fun encodedPathTraversalDoesNotBecomeAFriendIdentity(){assertThrows(Exception::class.java){SocialPolicy.friendCode("campusfit://friend/ama%2Fother")}}
    @Test fun ranksUseSharedPositionsAndGaps(){val rows=SocialPolicy.rank(listOf(RankRow(uid="b",score=5.0),RankRow(uid="a",score=5.0),RankRow(uid="c",score=4.0)));assertEquals(listOf("a","b","c"),rows.map{it.uid});assertEquals(listOf(1,1,3),rows.map{it.rank})}
    @Test fun missingAndInvalidScoresStayUnranked(){val rows=SocialPolicy.rank(listOf(RankRow(uid="a",score=Double.NaN),RankRow(uid="b",score=null),RankRow(uid="c",score=0.0)));assertEquals("c",rows.first().uid);assertTrue(rows.drop(1).all{it.rank==null})}
    @Test fun floatingPointNearTiesAreStableByUid(){val rows=SocialPolicy.rank(listOf(RankRow(uid="b",score=1.0000004),RankRow(uid="a",score=1.0000003)));assertEquals(listOf("a","b"),rows.map{it.uid});assertEquals(listOf(1,1),rows.map{it.rank})}
}
class DemoGameTest {
    private val now=Instant.parse("2026-11-03T12:00:00Z").toEpochMilli()
    private fun session(id:String,seconds:Int,dayOffset:Int=0)=SocialActivity(id,now+dayOffset*86_400_000L,seconds,1.0)
    @Test fun noActivityNeverInventsXpPeersOrBadges(){val stats=DemoGame.stats(emptyList(),now);assertEquals(0,stats.xp);assertEquals(1,stats.level);assertTrue(stats.badges.isEmpty())}
    @Test fun duplicateSessionsDoNotDoubleCredit(){assertEquals(14,DemoGame.stats(listOf(session("a",120),session("a",120)),now).xp)}
    @Test fun dailyCapStopsActivityAndXpCredit(){val stats=DemoGame.stats(listOf(session("a",2400),session("b",2400)),now);assertEquals(3600,stats.seconds);assertEquals(130,stats.xp);assertEquals(2,stats.level)}
    @Test fun aSevenDayHabitEarnsAStreakWithoutWeightRewards(){val stats=DemoGame.stats((0..6).map{session("$it",120,-it)},now);assertEquals(7,stats.streak);assertTrue("seven_day_streak" in stats.badges);assertEquals(98,stats.xp)}
    @Test fun aGapBreaksTheCurrentStreakButRetainsTheLongest(){val stats=DemoGame.stats(listOf(session("a",120),session("b",120,-2),session("c",120,-3)),now);assertEquals(1,stats.streak);assertEquals(2,stats.longestStreak)}
    @Test fun earlyBirdUsesUtcAndNeedsTwoMinutes(){val morning=Instant.parse("2026-11-03T06:00:00Z").toEpochMilli();assertTrue("early_bird" in DemoGame.stats(listOf(SocialActivity("a",morning,120,1.0)),now).badges);assertFalse("early_bird" in DemoGame.stats(listOf(SocialActivity("a",morning,60,1.0)),now).badges)}
}
