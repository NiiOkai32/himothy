package com.campusfit.domain.engagement

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TimetableParserTest {
 private val now=Instant.parse("2026-11-01T00:00:00Z").toEpochMilli()
 private fun parse(event:String,zone:String="UTC",at:Long=now)=TimetableParser.parse("BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nUID:lecture\n$event\nEND:VEVENT\nEND:VCALENDAR",zone,at)
 private val event="DTSTART:20261102T090000Z\nDTEND:20261102T100000Z"
 @Test fun weeklyExpansionAndExclusion(){val result=parse("$event\nRRULE:FREQ=WEEKLY;BYDAY=MO,WE;COUNT=4\nEXDATE:20261104T090000Z");assertEquals(3,result.spans.size);assertEquals(Instant.parse("2026-11-02T09:00:00Z").toEpochMilli(),result.spans.first().start)}
 @Test fun rdateAddsOneOccurrence(){assertEquals(2,parse("$event\nRDATE:20261103T090000Z").spans.size)}
 @Test fun unfoldedPropertiesAndAlarmAreHandled(){assertEquals(2,parse("$event\nRRULE:FREQ=WEEKLY;\n COUNT=2\nBEGIN:VALARM\nACTION:DISPLAY\nDESCRIPTION:Private title\nEND:VALARM").spans.size)}
 @Test fun allDayBusyCoversWholeDay(){val span=parse("DTSTART;VALUE=DATE:20261102\nDTEND;VALUE=DATE:20261103").spans.single();assertEquals(86400000,span.end-span.start)}
 @Test fun floatingTimesUseSelectedTimeZone(){val span=parse("DTSTART:20261102T090000\nDTEND:20261102T100000","Asia/Kolkata").spans.single();assertEquals(Instant.parse("2026-11-02T03:30:00Z").toEpochMilli(),span.start)}
 @Test fun recurringLocalTimeSurvivesDstChange(){val start=Instant.parse("2026-10-24T00:00:00Z").toEpochMilli();val spans=parse("DTSTART;TZID=Europe/London:20261024T090000\nDTEND;TZID=Europe/London:20261024T100000\nRRULE:FREQ=DAILY;COUNT=3",at=start).spans;assertEquals(3,spans.size);assertEquals(25*3600000L,spans[1].start-spans[0].start);assertEquals(3600000,spans[1].end-spans[1].start)}
 @Test fun cancellationRemovesOneOccurrence(){val text="BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:a\n$event\nRRULE:FREQ=DAILY;COUNT=3\nEND:VEVENT\nBEGIN:VEVENT\nUID:a\nRECURRENCE-ID:20261103T090000Z\nSTATUS:CANCELLED\nEND:VEVENT\nEND:VCALENDAR";assertEquals(2,TimetableParser.parse(text,"UTC",now).spans.size)}
 @Test fun transparentEventsDoNotBlockTime(){assertTrue(parse("$event\nTRANSP:TRANSPARENT").spans.isEmpty())}
 @Test(expected=IllegalArgumentException::class) fun unsupportedRecurrenceRejected(){parse("$event\nRRULE:FREQ=MONTHLY;BYSETPOS=1")}
 @Test(expected=IllegalArgumentException::class) fun unsupportedExruleRejected(){parse("$event\nEXRULE:FREQ=DAILY")}
 @Test(expected=IllegalArgumentException::class) fun malformedRecurrenceCountRejected(){parse("$event\nRRULE:FREQ=DAILY;COUNT=oops")}
 @Test(expected=IllegalArgumentException::class) fun duplicateRecurrenceKeysRejected(){parse("$event\nRRULE:FREQ=DAILY;FREQ=WEEKLY")}
 @Test(expected=IllegalArgumentException::class) fun missingExplicitEndRejected(){parse("DTSTART:20261102T090000Z\nDURATION:PT1H")}
 @Test(expected=IllegalArgumentException::class) fun nonexistentDstStartRejected(){parse("DTSTART;TZID=America/New_York:20260308T023000\nDTEND;TZID=America/New_York:20260308T033000",at=Instant.parse("2026-03-01T00:00:00Z").toEpochMilli())}
 @Test fun horizonIsBoundedToNinetyDays(){assertEquals(89,parse("$event\nRRULE:FREQ=DAILY").spans.size);assertTrue(parse("$event\nRRULE:FREQ=DAILY").spans.size<=90)}
}
