package com.campusfit.domain.engagement

import java.time.*
import java.time.format.DateTimeFormatter

/** Local RFC5545 subset. Unknown recurrence rules are rejected, never silently dropped. */
object TimetableParser {
 data class Import(val spans:List<BusySpan>,val validUntil:Long)
 private data class Property(val name:String,val params:Map<String,String>,val value:String)
 private data class Event(val properties:List<Property>){fun first(name:String)=properties.firstOrNull{it.name==name};fun all(name:String)=properties.filter{it.name==name}}
 private val dateTime=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
 private fun instant(property:Property,zone:ZoneId):Instant {
  val value=property.value.trim()
  if(property.params["VALUE"]=="DATE"||value.length==8)return LocalDate.parse(value,DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay(zone).toInstant()
  val selected=property.params["TZID"]?.let{ZoneId.of(it)}?:zone
  if(value.endsWith('Z'))return LocalDateTime.parse(value.dropLast(1),dateTime).toInstant(ZoneOffset.UTC)
  val local=LocalDateTime.parse(value,dateTime)
  require(selected.rules.getValidOffsets(local).isNotEmpty()){ "A calendar time falls in a daylight-saving gap. Correct that event before importing." }
  return (if(property.name=="DTEND")local.atZone(selected).withLaterOffsetAtOverlap()else local.atZone(selected)).toInstant()
 }
 private fun parseProperty(line:String):Property {
  val colon=line.indexOf(':');require(colon>0){"Invalid calendar property."}
  val parts=line.substring(0,colon).split(';');val parameters=parts.drop(1).associate{part->val key=part.substringBefore('=').uppercase();require(part.contains('='));key to part.substringAfter('=').trim('"')}
  return Property(parts.first().uppercase(),parameters,line.substring(colon+1))
 }
 fun parse(text:String,zoneName:String,now:Long):Import {
  require(text.toByteArray(Charsets.UTF_8).size<=1_048_576){"Choose a calendar file of 1 MB or less."}
  val zone=ZoneId.of(zoneName);val horizon=Instant.ofEpochMilli(now).atZone(zone).plusDays(90).toInstant()
  val lines=mutableListOf<String>()
  text.removePrefix("\uFEFF").replace("\r\n","\n").split('\n').forEach{line->
   if((line.startsWith(' ')||line.startsWith('\t'))&&lines.isNotEmpty())lines[lines.lastIndex]+=line.drop(1) else if(line.isNotBlank())lines+=line.trimEnd('\r')
  }
  require(lines.size<=20_000&&lines.firstOrNull()=="BEGIN:VCALENDAR"&&lines.lastOrNull()=="END:VCALENDAR"){"Choose a valid iCalendar (.ics) file."}
  val events=mutableListOf<Event>();var current:MutableList<Property>?=null;var alarm=0
  for(line in lines){
   when(line){
    "BEGIN:VEVENT"->{require(current==null);current=mutableListOf()}
    "END:VEVENT"->{events+=Event(checkNotNull(current));current=null}
    "BEGIN:VALARM"->alarm++
    "END:VALARM"->alarm--
    else->if(current!=null&&alarm==0)current.add(parseProperty(line))
   }
  }
  require(current==null&&alarm==0&&events.size<=500){"Calendar has malformed or too many events (maximum 500)."}
  for(event in events){
   require(event.first("EXRULE")==null){"EXRULE is unsupported. Export explicit date exceptions instead."}
   require(event.first("RECURRENCE-ID")?.params?.containsKey("RANGE")!=true){"Changes to all future recurrences are unsupported. Export individual replacements."}
   for(name in listOf("UID","DTSTART","DTEND","RECURRENCE-ID","RRULE"))require(event.all(name).size<=1){"Duplicate event properties are unsupported."}
  }
  require(events.filter{it.first("RECURRENCE-ID")==null}.map{it.first("UID")?.value}.distinct().size==events.count{it.first("RECURRENCE-ID")==null}){"Duplicate calendar event IDs are unsupported."}
  val overrides=events.filter{it.first("RECURRENCE-ID")!=null}
  val output=mutableMapOf<String,BusySpan>()
  val uidFor:(Event)->String={e->e.first("UID")?.value?:throw IllegalArgumentException("Every calendar event needs a UID.")}
  for(event in events.filter{it.first("RECURRENCE-ID")==null}){
   if(event.first("TRANSP")?.value=="TRANSPARENT"||event.first("STATUS")?.value=="CANCELLED")continue
   require(event.first("DURATION")==null){"Use events with explicit start and end times."}
   val start=event.first("DTSTART")?:throw IllegalArgumentException("Event start missing.")
   val end=event.first("DTEND")?:throw IllegalArgumentException("Event end missing.")
   val begin=instant(start,zone);val finish=instant(end,zone)
   require(finish>begin&&Duration.between(begin,finish)<=Duration.ofDays(7)){"Event duration must be positive and at most seven days."}
   val uid=uidFor(event);val eventZone=start.params["TZID"]?.let{ZoneId.of(it)}?:if(start.value.endsWith('Z'))ZoneOffset.UTC else zone
   val localStart=begin.atZone(eventZone);val localEnd=finish.atZone(eventZone)
   val duration=Duration.between(begin,finish)
   val dates=mutableListOf<Instant>()
   require(event.all("RRULE").size<=1){"Duplicate recurrence rules are unsupported."}
   val rule=event.first("RRULE")
   if(rule==null)dates+=begin else{
    require(rule.value.split(';').map{it.substringBefore('=')}.distinct().size==rule.value.split(';').size){"Duplicate recurrence fields are unsupported."}
    val fields=rule.value.split(';').associate{val k=it.substringBefore('=');require(it.contains('='));k to it.substringAfter('=')}
    require(fields.keys.all{it in setOf("FREQ","INTERVAL","BYDAY","UNTIL","COUNT","WKST")}){"This calendar has an unsupported recurrence rule. Export a simpler weekly timetable or add busy blocks manually."}
    require(start.params["TZID"]==end.params["TZID"]&&start.value.endsWith('Z')==end.value.endsWith('Z')){"Recurring event start and end must use the same timezone."}
    require(!(fields.containsKey("COUNT")&&fields.containsKey("UNTIL"))){"Use a recurrence count or an end date, not both."}
    val frequency=fields["FREQ"];require(frequency in setOf("WEEKLY","DAILY")){"Only daily and weekly repeating lectures are supported."}
    val interval=fields["INTERVAL"]?.let{it.toIntOrNull()?:throw IllegalArgumentException("Invalid recurrence interval.")}?:1;require(interval in 1..52)
    val count=fields["COUNT"]?.let{it.toIntOrNull()?:throw IllegalArgumentException("Invalid recurrence count.")}?:Int.MAX_VALUE;require(count>0)
    val until=fields["UNTIL"]?.let{instant(Property("UNTIL",if(it.endsWith('Z'))emptyMap()else start.params,it),zone)}
    val dayNames=mapOf("MO" to 1,"TU" to 2,"WE" to 3,"TH" to 4,"FR" to 5,"SA" to 6,"SU" to 7)
    val weekdays=fields["BYDAY"]?.split(',')?.map{dayNames[it]?:throw IllegalArgumentException("Numbered recurrence weekdays are unsupported.") }?.toSet()?:setOf(localStart.dayOfWeek.value)
    require(fields["WKST"]==null||fields["WKST"]=="MO"){"Use Monday-based recurring weeks."}
    var date=localStart.toLocalDate();var found=0;var iterations=0
    while(date.atStartOfDay(eventZone).toInstant()<horizon&&found<count){
     require(iterations++<36_600){"This recurrence begins too far in the past. Export a current term calendar."}
     val dayOffset=java.time.temporal.ChronoUnit.DAYS.between(localStart.toLocalDate(),date)
     val baseMonday=localStart.toLocalDate().minusDays((localStart.dayOfWeek.value-1).toLong())
     val weekOffset=java.time.temporal.ChronoUnit.DAYS.between(baseMonday,date)/7
     val matches=if(frequency=="DAILY")dayOffset%interval==0L&&(fields["BYDAY"]==null||date.dayOfWeek.value in weekdays) else weekOffset%interval==0L&&date.dayOfWeek.value in weekdays
     if(matches){val local=date.atTime(localStart.toLocalTime());require(eventZone.rules.getValidOffsets(local).isNotEmpty()){ "A repeating lecture falls in a daylight-saving gap." };val value=local.atZone(eventZone).toInstant();if(value>=begin){if(until!=null&&value>until)break;found++;dates+=value}}
     date=date.plusDays(1)
    }
   }
   event.all("RDATE").forEach{prop->prop.value.split(',').forEach{dates+=instant(prop.copy(value=it),zone)}}
   val excluded=event.all("EXDATE").flatMap{prop->prop.value.split(',').map{instant(prop.copy(value=it),zone)}}.toSet()
   for(occurrence in dates.distinct()){
    if(occurrence in excluded)continue
    val occurrenceEnd=if(start.params["VALUE"]=="DATE")occurrence.atZone(eventZone).plusDays(java.time.temporal.ChronoUnit.DAYS.between(localStart.toLocalDate(),localEnd.toLocalDate())).toInstant()else if(rule!=null)occurrence.atZone(eventZone).toLocalDate().plusDays(java.time.temporal.ChronoUnit.DAYS.between(localStart.toLocalDate(),localEnd.toLocalDate())).atTime(localEnd.toLocalTime()).atZone(eventZone).withLaterOffsetAtOverlap().toInstant()else occurrence.plus(duration)
    if(occurrenceEnd.toEpochMilli()>now&&occurrence<horizon)output["$uid/${occurrence.toEpochMilli()}"]=BusySpan(occurrence.toEpochMilli(),occurrenceEnd.toEpochMilli())
   }
  }
  for(event in overrides){
   val original=instant(checkNotNull(event.first("RECURRENCE-ID")),zone);val uid=uidFor(event);require(events.any{it.first("RECURRENCE-ID")==null&&uidFor(it)==uid}){"Replacement event has no matching base event."};output.remove("$uid/${original.toEpochMilli()}")
   if(event.first("STATUS")?.value=="CANCELLED"||event.first("TRANSP")?.value=="TRANSPARENT")continue
   val start=instant(event.first("DTSTART")?:throw IllegalArgumentException("Replacement start missing."),zone)
   val end=instant(event.first("DTEND")?:throw IllegalArgumentException("Replacement end missing."),zone)
   require(end>start&&Duration.between(start,end)<=Duration.ofDays(7))
   if(end.toEpochMilli()>now&&start<horizon)output["$uid/${original.toEpochMilli()}"]=BusySpan(start.toEpochMilli(),end.toEpochMilli())
  }
  require(output.size<=5000){"The expanded timetable has too many busy blocks."}
  return Import(output.values.sortedBy{it.start},horizon.toEpochMilli())
 }
}
