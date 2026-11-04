package com.campusfit.domain.tracking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class WeightPolicyTest {
    @Test fun utcDayDoesNotDependOnDeviceTimeZone() {
        assertEquals(1L,WeightPolicy.utcDay(86_400_000))
        assertEquals(0L,WeightPolicy.utcDay(86_399_999))
        assertEquals(-1L,WeightPolicy.utcDay(-1))
    }
    @Test fun invalidWeightsAreBlocked() { listOf(Double.NaN,Double.POSITIVE_INFINITY,34.9,350.1).forEach { value -> assertThrows(IllegalArgumentException::class.java){WeightPolicy.validate(value)} } }
    @Test fun lossPercentIsFairAcrossBodySizes() { assertEquals(WeightPolicy.lossPercent(100.0,99.0),WeightPolicy.lossPercent(70.0,69.3),1e-8) }
    @Test fun exactlyTwoPercentIsNotFlagged() { assertFalse(WeightPolicy.unusual(100.0,98.0,1));assertTrue(WeightPolicy.unusual(100.0,97.9,1)) }
    @Test fun gainsNeverEarnLossOrUnusualLossFlags() { assertTrue(WeightPolicy.lossPercent(70.0,71.0)<0);assertFalse(WeightPolicy.unusual(70.0,71.0,1)) }
    @Test fun multipleDaysUseCompoundDailyChange() { assertFalse(WeightPolicy.unusual(100.0,96.04,2));assertTrue(WeightPolicy.unusual(100.0,95.0,2)) }
    @Test fun flagsSuppressActivityBadgeEvenWithLongWorkouts() { assertFalse(WeightPolicy.supported(true,1200));assertFalse(WeightPolicy.supported(false,299));assertTrue(WeightPolicy.supported(false,300)) }
}
class RoutePolicyTest {
    private val run=OutdoorRun("r","u","walk",70.0,1,paused=false,segment=1)
    private fun point(lat:Double,lon:Double,time:Long,accuracy:Float=3f,segment:Int=1)=GeoPoint(lat,lon,accuracy,time,segment)
    @Test fun haversineHandlesRealDistanceAndDateLine() {
        assertEquals(111.2,RoutePolicy.meters(point(0.0,0.0,0),point(.001,0.0,1000)),.3)
        assertEquals(222.4,RoutePolicy.meters(point(0.0,179.999,0),point(0.0,-179.999,1000)),.5)
    }
    @Test fun badFixesAndMocksAreIgnored() {
        assertEquals(run,RoutePolicy.add(run,point(Double.NaN,0.0,0),false))
        assertEquals(run,RoutePolicy.add(run,point(0.0,0.0,0,100f),false))
        assertEquals(run,RoutePolicy.add(run,point(0.0,0.0,0),true))
    }
    @Test fun acceptedMovementAddsDistanceButJumpsDoNot() {
        val first=RoutePolicy.add(run,point(0.0,0.0,1000),false)
        val next=RoutePolicy.add(first,point(.00005,0.0,3000),false)
        assertTrue(next.distanceMeters>5)
        assertEquals(next,RoutePolicy.add(next,point(.5,0.0,4000),false))
    }
    @Test fun pauseAndRebootSegmentsNeverBridgeDistance() {
        val first=RoutePolicy.add(run,point(0.0,0.0,100_000),false)
        val resumed=first.copy(segment=2)
        val next=RoutePolicy.add(resumed,point(1.0,1.0,1000,segment=2),false)
        assertEquals(2,next.points.size);assertEquals(0.0,next.distanceMeters,0.0)
    }
    @Test fun staleFixesAndLongGapsNeverAddDistance() {
        val first=RoutePolicy.add(run,point(0.0,0.0,1000),false)
        assertEquals(first,RoutePolicy.add(first,point(.00005,0.0,500),false))
        val gap=RoutePolicy.add(first,point(.01,0.0,30_000),false)
        assertEquals(0.0,gap.distanceMeters,0.0)
        assertNotEquals(first.points.last().segment,gap.points.last().segment)
    }
    @Test fun slowerMovementWithOrdinaryGpsAccuracyStillAccumulatesDistance() {
        var result=RoutePolicy.add(run,point(0.0,0.0,1000,20f),false)
        for(i in 1..12) result=RoutePolicy.add(result,point(i*.00002,0.0,1000+i*2000L,20f),false)
        assertTrue(result.distanceMeters>19)
        assertEquals(1,result.segment)
    }
    @Test fun pausedTimeDoesNotCountAndMaximumStopsExactly() {
        assertEquals(run.copy(paused=true),RoutePolicy.tick(run.copy(paused=true),5000))
        val ended=RoutePolicy.tick(run,9_000_000);assertTrue(ended.finished && ended.paused);assertEquals(1_200_000L,ended.elapsedMillis)
    }
}
class RepCounterTest {
    @Test fun requiresAFullNeutralDeepNeutralCycle() {
        val c=RepCounter(100.0,155.0)
        assertEquals(0,c.frame(90.0,100));assertEquals(0,c.frame(160.0,200))
        assertEquals(0,c.frame(95.0,700));assertEquals(1,c.frame(160.0,1200))
        assertEquals(1,c.frame(160.0,1300))
    }
    @Test fun missingFramesResetTheCycle() {
        val c=RepCounter(100.0,155.0);c.frame(160.0,100);c.frame(90.0,500);c.frame(null,600)
        assertEquals(0,c.frame(160.0,1000))
    }
    @Test fun longGapsAndOutOfOrderFramesNeverInventReps() {
        val c=RepCounter(100.0,155.0);c.frame(160.0,100);c.frame(90.0,400)
        assertEquals(0,c.frame(160.0,3000));assertEquals(0,c.frame(90.0,2000));assertEquals(0,c.frame(160.0,3200))
    }
    @Test fun rapidNoiseDoesNotCreateAnotherRep() {
        val c=RepCounter(.3,.7);c.frame(1.0,100);c.frame(0.0,200);assertEquals(1,c.frame(1.0,800))
        c.frame(0.0,900);assertEquals(1,c.frame(1.0,1000))
    }
    @Test fun angleCalculationRejectsMissingGeometry() { assertNull(PoseAngles.angle(0.0,0.0,0.0,0.0,1.0,1.0));assertEquals(90.0,PoseAngles.angle(0.0,1.0,0.0,0.0,1.0,0.0)!!,.001) }
}
