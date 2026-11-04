package com.campusfit.domain.workouts

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class HumanSceneTest {
    private val catalogue=loadTestCatalogue()
    @Test fun everyExerciseHasFiniteDepthAwareKeyframesAndAClosedLoop() {
        catalogue.forEach { e ->
            e.validate()
            assertEquals(e.demo.size,e.demo3d.size)
            assertEquals(HumanScene.joints(e,0f),HumanScene.joints(e,e.demo3d.size.toFloat()))
            for(phase in listOf(0f,.5f,1f,1.5f)) {
                val joints=HumanScene.joints(e,phase)
                assertEquals(13,joints.size)
                joints.forEach { p -> assertTrue(listOf(p.x,p.y,p.z).all{it.isFinite() && abs(it)<=2.5f}) }
            }
        }
    }
    @Test fun squatsAndKneeLiftsHaveSagittalDepthInsteadOfLateralSubstitutes() {
        val squat=catalogue.first{it.id=="bodyweight-squat"}
        val standing=HumanScene.joints(squat,0f);val low=HumanScene.joints(squat,1f)
        assertTrue(low[8].y<standing[8].y)
        assertTrue(low[8].z<standing[8].z-.15f)
        assertTrue(low[9].z>standing[9].z+.15f)
        val knees=HumanScene.joints(catalogue.first{it.id=="high-knees"},1f)
        assertTrue(knees[9].z>.2f)
        assertTrue(abs(knees[9].x)<.2f)
        assertTrue(knees[9].y>knees[10].y)
    }
    @Test fun lyingAndPlankShouldersOrientTheTorsoInOppositeDirections() {
        val up=HumanScene.joints(catalogue.first{it.id=="glute-bridge"},0f)
        val down=HumanScene.joints(catalogue.first{it.id=="high-plank"},0f)
        fun front(j: List<V3>)=(j[3]-j[2]).cross(j[1]-j[8]).unit()
        assertTrue(front(up).y>.5f)
        assertTrue(front(down).y<-.5f)
    }
    @Test fun everyMeshHasVolumeAndFiniteUnitNormalsAtBothKeyPoses() {
        val builder=HumanMeshBuilder()
        catalogue.forEach { e -> for(phase in listOf(0f,1f)) {
            val mesh=builder.build(e,phase)
            assertTrue(mesh.count>1000 && mesh.count%3==0)
            var minimum=Float.MAX_VALUE;var maximum=-Float.MAX_VALUE
            for(i in 0 until mesh.count*10 step 10) {
                assertTrue(mesh.vertices.slice(i until i+10).all{it.isFinite()})
                val norm=sqrt(mesh.vertices[i+3]*mesh.vertices[i+3]+mesh.vertices[i+4]*mesh.vertices[i+4]+mesh.vertices[i+5]*mesh.vertices[i+5])
                assertEquals(1f,norm,.002f)
                minimum=minOf(minimum,mesh.vertices[i+2]);maximum=maxOf(maximum,mesh.vertices[i+2])
            }
            assertTrue(maximum-minimum>.3f)
        } }
    }
    @Test fun cameraDefaultsMakeFloorAndWallMovementsVisibleFromAnAngle() {
        assertEquals(62f,HumanScene.defaultYaw(catalogue.first{it.id=="push-up"}),0f)
        assertTrue(HumanScene.defaultYaw(catalogue.first{it.id=="wall-push-up"})>90f)
        assertTrue(HumanScene.defaultYaw(catalogue.first{it.id=="bodyweight-squat"}) in 10f..45f)
    }
}
