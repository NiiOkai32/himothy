package com.campusfit.domain
import org.junit.Assert.*
import org.junit.Test
class HealthPolicyTest {
    private val valid = Profile("u", "Ama", 21, 170.0, 75.0, 68.0, "Beginner", 10, "University of Ghana", "", true, true, true)
    @Test fun healthyProfileAccepted() { assertNull(HealthPolicy.validate(valid)) }
    @Test fun underweightGoalBlocked() { assertNotNull(HealthPolicy.validate(valid.copy(goalKg = 50.0))) }
    @Test fun bmiBoundaryAccepted() { assertNull(HealthPolicy.validate(valid.copy(heightCm = 200.0, goalKg = 74.0))) }
    @Test fun nonFiniteMeasurementsBlocked() {
        assertNotNull(HealthPolicy.validate(valid.copy(weightKg = Double.NaN)))
        assertNotNull(HealthPolicy.validate(valid.copy(heightCm = Double.POSITIVE_INFINITY)))
        assertNotNull(HealthPolicy.validate(valid.copy(goalKg = Double.NaN)))
    }
    @Test fun minorsBlocked() { assertNotNull(HealthPolicy.validate(valid.copy(age = 17))) }
    @Test fun acknowledgementRequired() { assertNotNull(HealthPolicy.validate(valid.copy(healthAcknowledged = false))) }
    @Test fun quickWorkoutTimeBoundsEnforced() {
        assertNotNull(HealthPolicy.validate(valid.copy(minutes = 1)))
        assertNotNull(HealthPolicy.validate(valid.copy(minutes = 21)))
    }
    @Test fun gradualTimelineUsesHalfKgPerWeek() { assertEquals(14, HealthPolicy.suggestedWeeks(75.0, 68.0)) }
    @Test fun maintenanceNeverSuggestsNegativeTimeline() { assertEquals(1, HealthPolicy.suggestedWeeks(65.0, 68.0)) }
}
