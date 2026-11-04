package com.campusfit.domain.workouts

import kotlin.math.*

/** Small, platform-independent mesh rig. Units are metres; all geometry has real depth. */
data class V3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(b: V3) = V3(x+b.x,y+b.y,z+b.z)
    operator fun minus(b: V3) = V3(x-b.x,y-b.y,z-b.z)
    operator fun times(s: Float) = V3(x*s,y*s,z*s)
    fun dot(b: V3) = x*b.x+y*b.y+z*b.z
    fun cross(b: V3) = V3(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x)
    fun length() = sqrt(dot(this))
    fun unit(fallback: V3 = Y): V3 { val length=length(); return if(length>.0001f) this*(1f/length) else fallback }
    companion object { val X=V3(1f,0f,0f); val Y=V3(0f,1f,0f); val Z=V3(0f,0f,1f) }
}
data class MeshTint(val r: Float,val g: Float,val b: Float,val a: Float=1f)
/** Interleaved position, normal and RGBA, ten floats per vertex. */
data class HumanMesh(val vertices: FloatArray, val count: Int)

object HumanScene {
    val floorUp = setOf("glute-bridge","dead-bug","gentle-crunch","bent-knee-leg-raise","heel-slides")
    val floorDown = setOf("mountain-climbers","knee-push-up","push-up","cat-cow","child-pose","downward-dog","low-cobra","forearm-plank","high-plank","bird-dog","side-plank-from-knees")
    fun floor(exercise: Exercise) = exercise.id in floorUp || exercise.id in floorDown
    fun defaultYaw(exercise: Exercise) = when {
        exercise.id.contains("wall") -> 145f
        floor(exercise) || exercise.id in setOf("gentle-hip-hinge","standing-forward-fold") -> 62f
        else -> 25f
    }
    fun duration(exercise: Exercise): Int = when(exercise.id) {
        "fast-feet" -> 650
        "high-knees","butt-kicks","invisible-rope-skipping","easy-jog" -> 1300
        "jumping-jacks","step-jacks" -> 1800
        else -> if(exercise.category=="Yoga & stretch") 5500 else 3200
    }
    fun joints(exercise: Exercise, phase: Float): List<V3> {
        val frames=exercise.demo3d
        val value=((phase%frames.size)+frames.size)%frames.size
        val index=floor(value).toInt()
        val fraction=(1f-cos((value-floor(value))*PI.toFloat()))/2f
        val a=frames[index].points;val b=frames[(index+1)%frames.size].points
        return (0 until 13).map { i -> V3(
            a[i*3]+(b[i*3]-a[i*3])*fraction,
            a[i*3+1]+(b[i*3+1]-a[i*3+1])*fraction,
            a[i*3+2]+(b[i*3+2]-a[i*3+2])*fraction) }
    }
}

/** Reuses one output buffer per render worker. Unit spheres are pre-tessellated once. */
class HumanMeshBuilder {
    private var output=FloatArray(200_000)
    private var size=0
    private val skin=MeshTint(.68f,.40f,.25f)
    private val shirt=MeshTint(.61f,.42f,.86f)
    private val shorts=MeshTint(.16f,.14f,.23f)
    private val shoes=MeshTint(.71f,.89f,.32f)
    private val white=MeshTint(.96f,.96f,.94f)
    private val hair=MeshTint(.095f,.065f,.055f)
    private val eye=MeshTint(.045f,.035f,.03f)
    private data class Basis(val right: V3,val up: V3,val front: V3)
    private fun basis(up: V3,right: V3=V3.X): Basis {
        val u=up.unit();val f=right.cross(u).unit(V3.Z);val r=u.cross(f).unit(V3.X)
        return Basis(r,u,f)
    }
    private val sphere = sphere(7,12,PI.toFloat())
    private val cap = sphere(5,12,PI.toFloat()*.46f)
    private fun sphere(rings: Int,slices: Int,end: Float): FloatArray {
        val result=ArrayList<Float>()
        fun vertex(r: Int,s: Int) {
            val a=end*r/rings;val b=2f*PI.toFloat()*s/slices
            result.add(sin(a)*cos(b));result.add(cos(a));result.add(sin(a)*sin(b))
        }
        for(r in 0 until rings) for(s in 0 until slices) {
            vertex(r,s);vertex(r+1,s);vertex(r+1,s+1)
            vertex(r,s);vertex(r+1,s+1);vertex(r,s+1)
        }
        return result.toFloatArray()
    }
    private fun vertex(p: V3,n: V3,c: MeshTint) = vertex(p.x,p.y,p.z,n.x,n.y,n.z,c)
    private fun vertex(x: Float,y: Float,z: Float,nx: Float,ny: Float,nz: Float,c: MeshTint) {
        if(size+10>output.size) output=output.copyOf(output.size*2)
        output[size++]=x;output[size++]=y;output[size++]=z
        output[size++]=nx;output[size++]=ny;output[size++]=nz
        output[size++]=c.r;output[size++]=c.g;output[size++]=c.b;output[size++]=c.a
    }
    private fun ellipsoid(center: V3,scale: V3,tint: MeshTint,axes: Basis=basis(V3.Y),shape: FloatArray=sphere) {
        var i=0
        while(i<shape.size) {
            val x=shape[i++];val y=shape[i++];val z=shape[i++]
            val px=center.x+axes.right.x*x*scale.x+axes.up.x*y*scale.y+axes.front.x*z*scale.z
            val py=center.y+axes.right.y*x*scale.x+axes.up.y*y*scale.y+axes.front.y*z*scale.z
            val pz=center.z+axes.right.z*x*scale.x+axes.up.z*y*scale.y+axes.front.z*z*scale.z
            val nx=axes.right.x*x/scale.x+axes.up.x*y/scale.y+axes.front.x*z/scale.z
            val ny=axes.right.y*x/scale.x+axes.up.y*y/scale.y+axes.front.y*z/scale.z
            val nz=axes.right.z*x/scale.x+axes.up.z*y/scale.y+axes.front.z*z/scale.z
            val inverse=1f/sqrt(nx*nx+ny*ny+nz*nz).coerceAtLeast(.0001f)
            vertex(px,py,pz,nx*inverse,ny*inverse,nz*inverse,tint)
        }
    }
    private fun bone(a: V3,b: V3,radius: Float,tint: MeshTint,depth: Float=radius) {
        val delta=b-a; val length=delta.length()
        if(length<.001f)return
        ellipsoid((a+b)*.5f,V3(radius,length*.5f+radius*.38f,depth),tint,basis(delta))
    }
    private fun shirt(center: V3,axes: Basis,length: Float) {
        // A fitted, rounded shirt profile rather than a stack of balls or cylinders.
        val profile=arrayOf(floatArrayOf(-.50f,.17f,.105f),floatArrayOf(-.34f,.20f,.12f),floatArrayOf(.12f,.245f,.135f),floatArrayOf(.42f,.245f,.12f),floatArrayOf(.50f,.18f,.09f))
        fun point(r: Int,s: Int): Pair<V3,V3> {
            val angle=2f*PI.toFloat()*s/16f;val row=profile[r]
            val x=cos(angle);val z=sin(angle)
            return (center+axes.up*(row[0]*length)+axes.right*(x*row[1])+axes.front*(z*row[2])) to
                (axes.right*(x/row[1])+axes.front*(z/row[2])+axes.up*(if(r==0)-1.3f else if(r==4)1.3f else 0f)).unit()
        }
        for(r in 0 until profile.size-1) for(s in 0 until 16) {
            for((ring,side) in arrayOf(r to s,r+1 to s,r+1 to s+1,r to s,r+1 to s+1,r to s+1)) {
                val (p,n)=point(ring,side);vertex(p,n,shirt)
            }
        }
        ellipsoid(center+axes.up*(length*.42f),V3(.19f,.035f,.105f),shirt,axes)
    }
    private fun stage(dark: Boolean) {
        val top=if(dark)MeshTint(.19f,.18f,.25f) else MeshTint(.80f,.79f,.88f)
        val side=if(dark)MeshTint(.13f,.12f,.18f) else MeshTint(.64f,.63f,.75f)
        val radius=1.18f
        for(i in 0 until 48) {
            val a=i*2f*PI.toFloat()/48;val b=(i+1)*2f*PI.toFloat()/48
            val p=V3(cos(a)*radius,-.035f,sin(a)*radius);val q=V3(cos(b)*radius,-.035f,sin(b)*radius)
            vertex(V3(0f,-.035f,0f),V3.Y,top);vertex(p,V3.Y,top);vertex(q,V3.Y,top)
            val lowP=p-V3.Y*.045f;val lowQ=q-V3.Y*.045f
            for(v in arrayOf(p,lowP,lowQ,p,lowQ,q))vertex(v,V3(v.x,0f,v.z).unit(),side)
        }
        // Soft radial contact shadow, drawn over the podium and under the person.
        for(i in 0 until 32) {
            val a=i*2f*PI.toFloat()/32;val b=(i+1)*2f*PI.toFloat()/32
            vertex(V3(0f,-.032f,0f),V3.Y,MeshTint(.04f,.035f,.065f,.26f))
            vertex(V3(cos(a)*.90f,-.032f,sin(a)*.82f),V3.Y,MeshTint(.04f,.035f,.065f,0f))
            vertex(V3(cos(b)*.90f,-.032f,sin(b)*.82f),V3.Y,MeshTint(.04f,.035f,.065f,0f))
        }
    }
    fun build(exercise: Exercise,phase: Float,dark: Boolean=false): HumanMesh {
        size=0;stage(dark)
        val j=HumanScene.joints(exercise,phase)
        val shoulder=(j[2]+j[3])*.5f
        val right=(j[3]-j[2]).unit(V3.X)
        val torso=basis(j[1]-j[8],right)
        val hipLeft=j[8]-torso.right*.115f;val hipRight=j[8]+torso.right*.115f
        val pelvis=(hipLeft+hipRight)*.5f
        ellipsoid(pelvis,V3(.18f,.135f,.115f),shorts,torso)
        for((side,hip) in listOf(0 to hipLeft,1 to hipRight)) {
            val knee=j[9+side];val ankle=j[11+side]
            bone(hip,knee,.090f,shorts,.085f)
            ellipsoid(knee,V3(.065f,.065f,.063f),skin)
            bone(knee,ankle,.058f,skin,.053f)
            val shoeAxes=if(exercise.id in HumanScene.floorUp) basis(V3(0f,0f,-1f)) else basis(V3.Y)
            val toe=ankle+shoeAxes.front*.055f
            ellipsoid(toe-shoeAxes.up*.025f,V3(.074f,.020f,.137f),white,shoeAxes)
            ellipsoid(toe+shoeAxes.up*.010f,V3(.071f,.049f,.127f),shoes,shoeAxes)
            // A slim stripe on each shoe breaks up the solid material.
            ellipsoid(toe+shoeAxes.up*.047f,V3(.040f,.006f,.044f),white,shoeAxes)
        }
        val shirtCenter=(shoulder+j[8])*.5f
        shirt(shirtCenter,torso,(shoulder-j[8]).length()+.08f)
        val neckBottom=shoulder+torso.up*.025f
        bone(neckBottom,j[0],.047f,skin)
        for(side in 0..1) {
            val top=j[2+side];val elbow=j[4+side];val wrist=j[6+side]
            ellipsoid(top,V3(.087f,.091f,.087f),shirt)
            bone(top,elbow,.061f,skin)
            bone(top,top+(elbow-top)*.35f,.080f,shirt)
            ellipsoid(elbow,V3(.051f,.051f,.051f),skin)
            bone(elbow,wrist,.046f,skin)
            val handAxes=basis(wrist-elbow)
            ellipsoid(wrist+handAxes.up*.025f,V3(.039f,.063f,.027f),skin,handAxes)
            ellipsoid(wrist+handAxes.up*.010f+handAxes.right*.037f,V3(.017f,.032f,.020f),skin,handAxes)
        }
        val head=j[0];val headAxes=basis(head-j[1],right)
        ellipsoid(head,V3(.105f,.137f,.106f),skin,headAxes)
        ellipsoid(head+headAxes.up*.025f,V3(.108f,.122f,.110f),hair,headAxes,cap)
        for(sign in listOf(-1f,1f)) {
            ellipsoid(head+headAxes.right*(sign*.106f),V3(.020f,.032f,.021f),skin,headAxes)
            val eyeCenter=head+headAxes.front*.096f+headAxes.up*.013f+headAxes.right*(sign*.037f)
            ellipsoid(eyeCenter,V3(.020f,.014f,.013f),white,headAxes)
            ellipsoid(eyeCenter+headAxes.front*.011f,V3(.009f,.010f,.006f),eye,headAxes)
        }
        ellipsoid(head+headAxes.front*.109f-headAxes.up*.011f,V3(.018f,.025f,.026f),skin,headAxes)
        ellipsoid(head+headAxes.front*.099f-headAxes.up*.054f,V3(.024f,.004f,.006f),MeshTint(.30f,.13f,.10f),headAxes)
        // Chest badge sits on the shirt surface and rotates with the torso.
        ellipsoid(shirtCenter+torso.up*.095f+torso.front*.137f-torso.right*.085f,V3(.026f,.029f,.008f),shoes,torso)
        if(exercise.id.contains("wall")) {
            val rail=if(dark)MeshTint(.37f,.35f,.46f) else MeshTint(.62f,.60f,.72f)
            // Open frame keeps the contact surface legible from every angle.
            for(x in listOf(-.48f,.48f))bone(V3(x,0f,.76f),V3(x,1.85f,.76f),.016f,rail)
            for(y in listOf(.42f,.85f,1.28f,1.80f))bone(V3(-.48f,y,.76f),V3(.48f,y,.76f),.012f,rail)
        }
        if(exercise.id=="stair-climbing") {
            ellipsoid(V3(0f,.07f,.33f),V3(.40f,.075f,.22f),MeshTint(.43f,.40f,.55f))
        }
        return HumanMesh(output,size/10)
    }
    private operator fun Float.times(v: V3)=v*this
}
