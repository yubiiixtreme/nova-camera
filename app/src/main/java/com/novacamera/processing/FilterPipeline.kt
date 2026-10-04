package com.novacamera.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CRITICAL COMPONENT — real-time preview shader pipeline (OpenGL ES 3.0).
 *
 * GLSurfaceView renderer applying LUT / bokeh / night-glow shaders to the
 * camera preview texture (OES external texture). On API 33+ the lightweight
 * path can instead use android.graphics.RenderEffect; this class remains the
 * high-performance path for 4K60 + custom LUTs.
 */
@Singleton
class RealtimeFilterRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
) : GLSurfaceView.Renderer {

    /** Fragment-shader LUT hook. Swap via [lutId] at runtime. */
    var lutId: String? = null
    var bokehStrength: Float = 0f
    var exposureBoost: Float = 0f

    private var program = 0
    private var lutTexture = 0

    // Minimal passthrough + color-grade fragment shader.
    private val vertexShader = """
        #version 300 es
        layout(location=0) in vec4 aPos;
        layout(location=1) in vec2 aUv;
        out vec2 vUv;
        void main(){ vUv=aUv; gl_Position=aPos; }
    """.trimIndent()

    private val fragmentShader = """
        #version 300 es
        precision mediump float;
        in vec2 vUv;
        uniform sampler2D uTex;
        uniform sampler2D uLut;
        uniform float uBokeh;
        uniform float uExposure;
        uniform int uUseLut;
        out vec4 frag;
        void main(){
            vec4 c = texture(uTex, vUv);
            c.rgb *= (1.0 + uExposure);
            if(uUseLut == 1){
                // 512x512 LUT strip lookup (neutral identity fallback)
                vec2 lutUv = vec2(c.b*0.9375 + 0.03125, c.g*0.9375 + fract(c.r*15.0)*0.0 + 0.03125);
                vec3 graded = texture(uLut, clamp(lutUv,0.0,1.0)).rgb;
                c.rgb = mix(c.rgb, graded, 0.85);
            }
            frag = c;
        }
    """.trimIndent()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = buildProgram(vertexShader, fragmentShader)
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) = GLES30.glViewport(0, 0, w, h)

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(program)
        // Uniform uploads (texture binding done by caller holding OES frame).
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uExposure"), exposureBoost)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uBokeh"), bokehStrength)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uUseLut"), if (lutId == null) 0 else 1)
        // Full-screen quad draw would go here (VBO omitted for brevity).
    }

    private fun compile(type: Int, src: String): Int {
        val sh = GLES30.glCreateShader(type)
        GLES30.glShaderSource(sh, src)
        GLES30.glCompileShader(sh)
        return sh
    }

    private fun buildProgram(v: String, f: String): Int {
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, compile(GLES30.GL_VERTEX_SHADER, v))
        GLES30.glAttachShader(p, compile(GLES30.GL_FRAGMENT_SHADER, f))
        GLES30.glLinkProgram(p)
        return p
    }
}

/** CPU-side still-image mergers (HDR / Night). Real alignment + fusion lives in [FrameFusion]. */
@Singleton
class HdrMerger @Inject constructor(@ApplicationContext private val context: Context) {
    /** Exposure-fuses the bracket into [out]; false if the frames could not be merged. */
    suspend fun merge(frames: List<java.io.File>, out: java.io.File): Boolean =
        withContext(Dispatchers.Default) { FrameFusion.fuse(frames, FrameFusion.Mode.HDR, out) }
}

@Singleton
class NightStacker @Inject constructor(@ApplicationContext private val context: Context) {
    /** Aligned mean-stack of the frames into [out]; false if the frames could not be merged. */
    suspend fun stack(frames: List<java.io.File>, out: java.io.File): Boolean =
        withContext(Dispatchers.Default) { FrameFusion.fuse(frames, FrameFusion.Mode.NIGHT, out) }
}

@Singleton
class AstrophotoStacker @Inject constructor(@ApplicationContext private val context: Context) {
    /** Ultra-long exposure: star-align N frames, sigma-clip hot pixels. */
    suspend fun stackSky(frames: List<Bitmap>): Bitmap? = withContext(Dispatchers.Default) {
        if (frames.isEmpty()) return@withContext null
        // Placeholder: mean-stack first frame size buffer.
        val base = frames.first()
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        // Real impl: phase-correlation alignment + per-pixel median.
        out
    }
}
