package com.campusfit.feature.workouts

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.*
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import com.campusfit.domain.workouts.*
import com.campusfit.BuildConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.*

internal data class HumanRenderSpec(
    val exercise: Exercise, val playing: Boolean, val reducedMotion: Boolean,
    val active: Boolean, val yaw: Float, val background: Int, val dark: Boolean,
    val fps: Int,
)
internal data class HumanCamera(val eye: V3,val target: V3,val fov: Float=36f) {
    companion object {
        fun forExercise(exercise: Exercise,yaw: Float): HumanCamera {
            val floor=HumanScene.floor(exercise)
            val target=V3(0f,if(floor).46f else .88f,0f)
            val angle=yaw*PI.toFloat()/180f; val elevation=(if(floor)20f else 11f)*PI.toFloat()/180f
            val distance=3.35f
            return HumanCamera(target+V3(sin(angle)*cos(elevation),sin(elevation),cos(angle)*cos(elevation))*distance,target)
        }
    }
}

/** TextureView composes/clips correctly inside scrolling Compose cards. All views share
 * one GL worker/context, with depth testing and per-pixel lighting, rather than a GL thread
 * per card. A detached texture releases its EGL surface on that same worker. */
internal class HumanGlView(context: Context,initial: HumanRenderSpec) : TextureView(context), TextureView.SurfaceTextureListener {
    private val token=Any()
    private var spec=initial
    private var disposed=false
    var onReady: ()->Unit = {}
    var onFailure: ()->Unit = {}
    init { isOpaque=false; surfaceTextureListener=this }
    fun update(value: HumanRenderSpec) {
        if(spec==value || disposed)return
        spec=value
        HumanGlWorker.update(token,value)
    }
    fun dispose() { disposed=true; HumanGlWorker.detach(token);onReady={};onFailure={} }
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture,width: Int,height: Int) {
        if(BuildConfig.DEBUG)android.util.Log.d("Exercise3D","Surface available ${width}x${height}, hardware=$isHardwareAccelerated, active=${spec.active}")
        if(!disposed) HumanGlWorker.attach(token,surface,width,height,spec, { if(!disposed)onReady() }, { if(!disposed)onFailure() })
    }
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture,width: Int,height: Int) { HumanGlWorker.resize(token,width,height) }
    // A TextureView update can be its initial clear; only the worker confirms a rendered mesh.
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        // Returning false transfers release to the GL worker, after eglDestroySurface.
        HumanGlWorker.detach(token,surface)
        return false
    }
    // Compose can temporarily detach a View without destroying its SurfaceTexture.
    // Release only on actual surface destruction or AndroidView.onRelease.
}

private object HumanGlWorker {
    private val thread=HandlerThread("CampusFit-3D").apply { start() }
    private val handler=Handler(thread.looper)
    private val main=Handler(Looper.getMainLooper())
    private data class Client(
        val texture: SurfaceTexture,val window: Surface,var surface: EGLSurface,
        var width: Int,var height: Int,var spec: HumanRenderSpec,val ready: ()->Unit,val error: ()->Unit,
        var rendered: Boolean=false,
        var phase: Float=1f,var lastNs: Long=0,var dirty: Boolean=true,
    )
    private val clients=LinkedHashMap<Any,Client>()
    private var display: EGLDisplay=EGL14.EGL_NO_DISPLAY
    private var context: EGLContext=EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig?=null
    private var program=0
    private var bufferId=0
    private var scheduled=false
    private var floats: FloatBuffer=ByteBuffer.allocateDirect(2_000_000).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val mesh=HumanMeshBuilder()
    private val view=FloatArray(16);private val projection=FloatArray(16);private val vp=FloatArray(16)
    private var pos=0;private var normal=0;private var color=0;private var matrix=0;private var camera=0

    fun attach(key: Any,texture: SurfaceTexture,width: Int,height: Int,spec: HumanRenderSpec,ready: ()->Unit,error: ()->Unit) = handler.post {
        try {
            remove(key)
            initialize()
            val window=Surface(texture)
            val surface=EGL14.eglCreateWindowSurface(display,config,window,intArrayOf(EGL14.EGL_NONE),0)
            if(surface==EGL14.EGL_NO_SURFACE) { window.release();throw IllegalStateException("3D surface creation failed") }
            clients[key]=Client(texture,window,surface,width,height,spec,ready,error)
            if(BuildConfig.DEBUG)android.util.Log.d("Exercise3D","Attached graphics client, active=${spec.active}, playing=${spec.playing}, reduced=${spec.reducedMotion}")
            schedule()
        } catch(e: Exception) { android.util.Log.w("Exercise3D","Could not initialize 3D preview",e);main.post(error);if(clients.isEmpty())releaseGl() }
    }
    fun update(key: Any,spec: HumanRenderSpec) = handler.post {
        clients[key]?.let { c ->
            if(c.spec.exercise.id!=spec.exercise.id) { c.phase=1f;c.rendered=false }
            if(spec.reducedMotion)c.phase=1f
            c.spec=spec;c.lastNs=0;c.dirty=true;schedule()
        }
    }
    fun resize(key: Any,width: Int,height: Int) = handler.post { clients[key]?.let{it.width=width;it.height=height;it.dirty=true;schedule()} }
    fun detach(key: Any,release: SurfaceTexture?=null) = handler.post { remove(key);release?.release();if(clients.isEmpty())releaseGl() }
    private fun remove(key: Any) {
        clients.remove(key)?.let { c ->
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display,c.surface);c.window.release()
        }
    }
    private fun schedule() { if(!scheduled) { scheduled=true;handler.post(frame) } }
    private val frame=object: Runnable {
        override fun run() {
            scheduled=false
            val now=System.nanoTime()
            try {
                for(c in clients.values) {
                    if(!c.spec.active || c.width<=0 || c.height<=0) { c.lastNs=0;continue }
                    val animate=c.spec.playing && !c.spec.reducedMotion
                    if(!c.dirty && (!animate || now-c.lastNs<1_000_000_000L/c.spec.fps))continue
                    if(animate && c.lastNs!=0L) {
                        val delta=((now-c.lastNs)/1_000_000_000f).coerceAtMost(.15f)
                        c.phase=(c.phase+delta*1000f/HumanScene.duration(c.spec.exercise)*c.spec.exercise.demo3d.size)%c.spec.exercise.demo3d.size
                    }
                    draw(c);c.lastNs=now;c.dirty=false
                    if(!c.rendered) { c.rendered=true;if(BuildConfig.DEBUG)android.util.Log.d("Exercise3D","First mesh frame rendered");main.post(c.ready) }
                }
            } catch(e: Exception) {
                android.util.Log.w("Exercise3D","3D preview stopped",e)
                clients.values.forEach{main.post(it.error)}
                clients.keys.toList().forEach{remove(it)};releaseGl()
            }
            if(clients.values.any{it.spec.active && it.spec.playing && !it.spec.reducedMotion}) { scheduled=true;handler.postDelayed(this,16) }
        }
    }
    private fun initialize() {
        if(display!=EGL14.EGL_NO_DISPLAY)return
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display!=EGL14.EGL_NO_DISPLAY && EGL14.eglInitialize(display,IntArray(2),0,IntArray(2),0))
        val configs=arrayOfNulls<EGLConfig>(1);val count=IntArray(1)
        check(EGL14.eglChooseConfig(display,intArrayOf(EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,
            EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_DEPTH_SIZE,16,EGL14.EGL_NONE),0,configs,0,1,count,0) && count[0]>0)
        config=configs[0]
        context=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
        check(context!=EGL14.EGL_NO_CONTEXT)
        // The shader is compiled after a window surface makes this context current.
    }
    private fun shader(type: Int,source: String): Int {
        val id=GLES20.glCreateShader(type);GLES20.glShaderSource(id,source);GLES20.glCompileShader(id)
        val status=IntArray(1);GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,status,0)
        if(status[0]==0){val message=GLES20.glGetShaderInfoLog(id);GLES20.glDeleteShader(id);error(message)}
        return id
    }
    private fun initProgram() {
        val vertex=shader(GLES20.GL_VERTEX_SHADER,"""
            uniform mat4 uMatrix;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec4 aColor;
            varying mediump vec3 vPosition;
            varying mediump vec3 vNormal;
            varying mediump vec4 vColor;
            void main(){vPosition=aPosition;vNormal=aNormal;vColor=aColor;gl_Position=uMatrix*vec4(aPosition,1.0);}
        """.trimIndent())
        val fragment=shader(GLES20.GL_FRAGMENT_SHADER,"""
            precision mediump float;
            uniform vec3 uCamera;
            varying mediump vec3 vPosition;
            varying mediump vec3 vNormal;
            varying mediump vec4 vColor;
            void main(){
                vec3 n=normalize(vNormal);
                vec3 light=normalize(vec3(-3.0,5.0,4.0)-vPosition);
                vec3 fill=normalize(vec3(3.0,2.0,-2.0));
                vec3 eye=normalize(uCamera-vPosition);
                float diffuse=max(dot(n,light),0.0);
                float bounce=max(dot(n,fill),0.0);
                float spec=pow(max(dot(n,normalize(light+eye)),0.0),32.0)*0.13;
                float rim=pow(1.0-max(dot(n,eye),0.0),3.0)*0.07;
                vec3 lit=vColor.rgb*(0.40+0.57*diffuse+0.15*bounce)+vec3(spec+rim);
                gl_FragColor=vec4(clamp(lit,0.0,1.0),vColor.a);
            }
        """.trimIndent())
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vertex);GLES20.glAttachShader(program,fragment);GLES20.glLinkProgram(program)
        GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment)
        val status=IntArray(1);GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,status,0);check(status[0]!=0){GLES20.glGetProgramInfoLog(program)}
        pos=GLES20.glGetAttribLocation(program,"aPosition");normal=GLES20.glGetAttribLocation(program,"aNormal");color=GLES20.glGetAttribLocation(program,"aColor")
        matrix=GLES20.glGetUniformLocation(program,"uMatrix");camera=GLES20.glGetUniformLocation(program,"uCamera")
        val ids=IntArray(1);GLES20.glGenBuffers(1,ids,0);bufferId=ids[0]
    }
    private fun draw(c: Client) {
        check(EGL14.eglMakeCurrent(display,c.surface,c.surface,context))
        if(program==0)initProgram()
        GLES20.glViewport(0,0,c.width,c.height)
        val bg=c.spec.background
        GLES20.glClearColor(((bg shr 16)and 255)/255f,((bg shr 8)and 255)/255f,(bg and 255)/255f,1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(program)
        val cameraSpec=HumanCamera.forExercise(c.spec.exercise,c.spec.yaw)
        val eye=cameraSpec.eye;val target=cameraSpec.target
        Matrix.setLookAtM(view,0,eye.x,eye.y,eye.z,target.x,target.y,target.z,0f,1f,0f)
        Matrix.perspectiveM(projection,0,cameraSpec.fov,c.width.toFloat()/c.height,.1f,20f)
        Matrix.multiplyMM(vp,0,projection,0,view,0)
        GLES20.glUniformMatrix4fv(matrix,1,false,vp,0);GLES20.glUniform3f(camera,eye.x,eye.y,eye.z)
        val scene=mesh.build(c.spec.exercise,c.phase,c.spec.dark)
        val needed=scene.count*10
        if(floats.capacity()<needed)floats=ByteBuffer.allocateDirect(needed*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        floats.clear();floats.put(scene.vertices,0,needed);floats.flip()
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,bufferId)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,needed*4,floats,GLES20.GL_DYNAMIC_DRAW)
        GLES20.glEnableVertexAttribArray(pos);GLES20.glEnableVertexAttribArray(normal);GLES20.glEnableVertexAttribArray(color)
        GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,40,0)
        GLES20.glVertexAttribPointer(normal,3,GLES20.GL_FLOAT,false,40,12)
        GLES20.glVertexAttribPointer(color,4,GLES20.GL_FLOAT,false,40,24)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,scene.count)
        check(EGL14.eglSwapBuffers(display,c.surface))
    }
    private fun releaseGl() {
        handler.removeCallbacks(frame);scheduled=false
        if(display!=EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context)
            EGL14.eglTerminate(display)
        }
        display=EGL14.EGL_NO_DISPLAY;context=EGL14.EGL_NO_CONTEXT;config=null;program=0;bufferId=0
    }
}
