package com.campusfit.domain.engagement

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class EngagementPolicyTest {
 private fun time(value:String)=Instant.parse(value).toEpochMilli()
 private val options=ReminderOptions(zone="UTC",daysMask=127)
 @Test fun reminderNeedsFiveMinuteBuffersOnBothSides(){
  val busy=listOf(BusySpan(time("2026-11-03T09:00:00Z"),time("2026-11-03T10:00:00Z")))
  assertFalse(ReminderPolicy.freeAt(time("2026-11-03T08:46:00Z"),options,busy))
  assertTrue(ReminderPolicy.freeAt(time("2026-11-03T08:45:00Z"),options,busy))
  assertFalse(ReminderPolicy.freeAt(time("2026-11-03T10:04:00Z"),options,busy))
  assertTrue(ReminderPolicy.freeAt(time("2026-11-03T10:05:00Z"),options,busy))
 }
 @Test fun quietHoursCannotBeginInMiddleOfWorkout(){assertFalse(ReminderPolicy.freeAt(time("2026-11-03T19:55:00Z"),options.copy(endMinute=1440,quietStart=1200,quietEnd=480),emptyList()))}
 @Test fun overnightQuietHoursDeferToNextLocalMorning(){val now=time("2026-11-03T23:00:00Z");assertTrue(ReminderPolicy.quiet(now,options));assertEquals(time("2026-11-04T08:00:00Z"),ReminderPolicy.quietEnd(now,options))}
 @Test fun zeroLengthQuietWindowDisablesQuietHours(){assertFalse(ReminderPolicy.quiet(time("2026-11-03T23:00:00Z"),options.copy(quietStart=0,quietEnd=0)))}
 @Test fun weekdaysAndRestSuppressionUseSelectedLocalDate(){val o=options.copy(zone="Pacific/Auckland",daysMask=1);val now=time("2026-11-01T19:00:00Z");val monday=LocalDate.of(2026,11,2).toEpochDay();val next=ReminderPolicy.next(now,o,emptyList(),setOf(monday));assertNull(next)}
 @Test fun nextRequiresNoticeAndFindsGapAfterClass(){val now=time("2026-11-03T09:00:00Z");val next=ReminderPolicy.next(now,options,listOf(BusySpan(now,now+3600000)))!!;assertEquals(time("2026-11-03T10:05:00Z"),next)}
 @Test fun daylightSavingWindowUsesWallClock(){val o=options.copy(zone="America/New_York",startMinute=8*60,endMinute=9*60);assertTrue(ReminderPolicy.freeAt(time("2026-11-01T13:00:00Z"),o,emptyList()));assertFalse(ReminderPolicy.freeAt(time("2026-11-01T12:00:00Z"),o,emptyList()))}
 @Test fun noSufficientGapReturnsNull(){assertNull(ReminderPolicy.next(time("2026-11-03T08:00:00Z"),options.copy(startMinute=480,endMinute=481),emptyList()))}
 @Test fun miniIsStableAndSupportsGentleAlternatives(){val task=DailyMini.task("u",20760);assertEquals(task,DailyMini.task("u",20760));assertEquals(2,task.minutes);assertNotEquals(task.id,DailyMini.task("u",20761).id);assertTrue(DailyMini.eligible(60,0));assertTrue(DailyMini.eligible(0,30));assertFalse(DailyMini.eligible(59,29))}
 @Test fun remoteRoutesNeverAcceptArbitraryLinks(){assertEquals("challenges",NotificationKinds.route("cheer","https://example.com"));assertEquals("challenge-detail/abc_12",NotificationKinds.route("cheer","abc_12"));assertEquals("notifications",NotificationKinds.route("untrusted","x"));assertFalse(NotificationKinds.all.contains("test"))}
 @Test(expected=IllegalArgumentException::class) fun invalidWindowRejected(){options.copy(startMinute=1000,endMinute=500).validate()}
}
