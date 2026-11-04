package de.noah.nachtfalter

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.hypot
import kotlin.math.max

/**
 * Glasvorhang beim Laden einer Seite:
 * dunkles Glas mit Farbwolken und einer pulsierenden lila Kugel.
 * Ist die Seite da, wird die Kugel zu einem Loch, das sich öffnet, bis die Seite frei ist.
 */
class VorhangView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private val grund = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wolkeLila = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wolkeBlau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val kugel = Paint(Paint.ANTI_ALIAS_FLAG)
    private val schein = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glanz = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 255, 255, 255) }
    private val rand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#b794ff")
    }
    private val pfad = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private val oval = RectF()

    private var loch = 0f
    private var puls = 0f
    private var skala = 0f
    private var oeffnet = false
    private var gezeigtSeit = 0L
    private var pulsAnim: ValueAnimator? = null
    private var auftrittAnim: ValueAnimator? = null
    private var lochAnim: ValueAnimator? = null
    private val zeitlimit = Runnable { oeffnen() }
    private val spaeterOeffnen = Runnable { oeffnen() }

    private val kugelRadius get() = 22f * d

    init {
        visibility = GONE
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val wf = w.toFloat(); val hf = h.toFloat(); val gross = max(wf, hf)
        grund.shader = LinearGradient(0f, 0f, 0f, hf,
            Color.parseColor("#F7161926"), Color.parseColor("#F70b0c12"), Shader.TileMode.CLAMP)
        wolkeLila.shader = RadialGradient(wf * 0.15f, hf * 0.2f, gross * 0.55f,
            Color.parseColor("#666b3fd6"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        wolkeBlau.shader = RadialGradient(wf * 0.9f, hf * 0.75f, gross * 0.5f,
            Color.parseColor("#4D2c4fd8"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
    }

    /** Vorhang zuziehen (Seite beginnt zu laden) */
    fun zeigen() {
        removeCallbacks(zeitlimit)
        removeCallbacks(spaeterOeffnen)
        oeffnet = false
        lochAnim?.cancel()
        loch = 0f
        gezeigtSeit = SystemClock.uptimeMillis()
        if (visibility != VISIBLE) {
            visibility = VISIBLE
            alpha = 0f
            animate().alpha(1f).setDuration(160).start()
            skala = 0f
            auftrittAnim?.cancel()
            auftrittAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 520
                interpolator = OvershootInterpolator(2.2f)
                addUpdateListener { skala = it.animatedValue as Float; invalidate() }
                start()
            }
        } else {
            animate().cancel()
            alpha = 1f
            if (auftrittAnim?.isRunning != true) skala = 1f
        }
        if (pulsAnim == null) {
            pulsAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 850
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { puls = it.animatedValue as Float; invalidate() }
                start()
            }
        }
        // Spätestens nach 4 Sekunden geht der Vorhang auf, auch wenn die Seite noch lädt
        postDelayed(zeitlimit, 4000)
        invalidate()
    }

    /** Loch in der Kugel öffnen, bis die Seite frei ist */
    fun oeffnen() {
        if (visibility != VISIBLE || oeffnet) return
        // Die Kugel soll mindestens kurz zu sehen sein, sonst flackert es
        val gezeigt = SystemClock.uptimeMillis() - gezeigtSeit
        if (gezeigt < 380) {
            removeCallbacks(spaeterOeffnen)
            postDelayed(spaeterOeffnen, 380 - gezeigt)
            return
        }
        oeffnet = true
        removeCallbacks(zeitlimit)
        val ziel = hypot(width / 2f, height / 2f) + 6f * d
        lochAnim = ValueAnimator.ofFloat(kugelRadius * 0.6f, ziel).apply {
            duration = 560
            interpolator = AccelerateInterpolator(1.6f)
            addUpdateListener { loch = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (oeffnet) weg()
                }
            })
            start()
        }
    }

    /** Ohne Animation verschwinden (z. B. beim Tab-Wechsel) */
    fun sofortWeg() {
        oeffnet = false
        lochAnim?.cancel()
        removeCallbacks(zeitlimit)
        removeCallbacks(spaeterOeffnen)
        weg()
    }

    private fun weg() {
        oeffnet = false
        pulsAnim?.cancel(); pulsAnim = null
        auftrittAnim?.cancel()
        animate().cancel()
        visibility = GONE
        loch = 0f
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        sofortWeg()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f

        // Glasfläche, bei Bedarf mit rundem Loch in der Mitte
        pfad.reset()
        pfad.addRect(0f, 0f, w, h, Path.Direction.CW)
        if (loch > 0f) pfad.addCircle(cx, cy, loch, Path.Direction.CW)
        canvas.drawPath(pfad, grund)
        canvas.drawPath(pfad, wolkeLila)
        canvas.drawPath(pfad, wolkeBlau)

        if (loch > 0f) {
            // leuchtender Rand am Loch
            rand.strokeWidth = 10f * d; rand.alpha = 50
            canvas.drawCircle(cx, cy, loch, rand)
            rand.strokeWidth = 2.5f * d; rand.alpha = 220
            canvas.drawCircle(cx, cy, loch, rand)
            return
        }

        // Pulsierende Glaskugel
        val r = kugelRadius * skala * (1f + 0.07f * puls)
        if (r <= 1f) return
        schein.shader = RadialGradient(cx, cy, r * 3f,
            intArrayOf(Color.argb((90 + 50 * puls).toInt(), 183, 148, 255), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * 3f, schein)
        kugel.shader = RadialGradient(cx - r * 0.35f, cy - r * 0.4f, r * 1.5f,
            intArrayOf(Color.WHITE, Color.parseColor("#d9c6ff"), Color.parseColor("#8f63f0")),
            floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, kugel)
        oval.set(cx - r * 0.55f, cy - r * 0.7f, cx - r * 0.05f, cy - r * 0.35f)
        canvas.drawOval(oval, glanz)
    }
}
