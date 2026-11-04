package com.campusfit.feature.workouts

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.campusfit.domain.workouts.*
import kotlin.math.*

/** CPU projection of the same solid mesh if a device cannot initialize OpenGL.
 * This is a still 3D preview, never substituted with the old flat illustration. */
internal object HumanSnapshot {
    fun render(exercise: Exercise,yaw: Float,dark: Boolean,background: Int,width: Int,height: Int): Bitmap {
        val mesh=HumanMeshBuilder().build(exercise,1f,dark)
        val camera=HumanCamera.forExercise(exercise,yaw)
        val forward=(camera.target-camera.eye).unit()
        val right=forward.cross(V3.Y).unit(V3.X);val up=right.cross(forward)
        val focal=1f/tan(camera.fov*PI.toFloat()/360f)
        val aspect=width.toFloat()/height
        data class Triangle(val points: FloatArray,val depth: Float,val color: Int)
        val triangles=ArrayList<Triangle>(mesh.count/3)
        val light=V3(-.45f,.75f,.65f).unit()
        var i=0
        while(i<mesh.count*10) {
            val points=FloatArray(6);var depth=0f;var shade=0f;var alpha=0f
            val start=i
            for(v in 0..2) {
                val p=V3(mesh.vertices[i],mesh.vertices[i+1],mesh.vertices[i+2])-camera.eye
                val z=p.dot(forward).coerceAtLeast(.1f)
                points[v*2]=width*(.5f+p.dot(right)*focal/aspect/z*.5f)
                points[v*2+1]=height*(.5f-p.dot(up)*focal/z*.5f)
                depth+=z
                shade+=.40f+.57f*max(0f,V3(mesh.vertices[i+3],mesh.vertices[i+4],mesh.vertices[i+5]).dot(light))
                alpha+=mesh.vertices[i+9];i+=10
            }
            shade/=3f;alpha/=3f
            val color=Color.argb((alpha*255).toInt(),(mesh.vertices[start+6]*shade*255).toInt().coerceIn(0,255),
                (mesh.vertices[start+7]*shade*255).toInt().coerceIn(0,255),(mesh.vertices[start+8]*shade*255).toInt().coerceIn(0,255))
            triangles.add(Triangle(points,depth,color))
        }
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);canvas.drawColor(background)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG);val path=Path()
        for(t in triangles.sortedByDescending{it.depth}) {
            path.rewind();path.moveTo(t.points[0],t.points[1]);path.lineTo(t.points[2],t.points[3]);path.lineTo(t.points[4],t.points[5]);path.close()
            paint.color=t.color;canvas.drawPath(path,paint)
        }
        return bitmap
    }
}
