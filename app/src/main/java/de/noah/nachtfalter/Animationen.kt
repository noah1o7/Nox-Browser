package de.noah.nachtfalter

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Outline
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Umriss mit runden Ecken, dessen Radius sich animieren lässt */
class RundUmriss(var radius: Float) : ViewOutlineProvider() {
    override fun getOutline(view: View, outline: Outline) {
        outline.setRoundRect(0, 0, view.width, view.height, radius)
    }
}

/** Alle Nachtfalter-Animationen an einem Ort */
object Animationen {

    private fun dp(v: View, x: Float) = x * v.resources.displayMetrics.density

    /** Knöpfe geben beim Drücken wie eine Blase nach und federn zurück */
    @SuppressLint("ClickableViewAccessibility")
    fun drueckEffekt(vararg views: View) {
        for (v in views) {
            v.setOnTouchListener { view, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.animate().setStartDelay(0)
                        .scaleX(0.86f).scaleY(0.86f).setDuration(90)
                        .setInterpolator(DecelerateInterpolator()).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().setStartDelay(0)
                        .scaleX(1f).scaleY(1f).setDuration(340)
                        .setInterpolator(OvershootInterpolator(3f)).start()
                }
                false
            }
        }
    }

    /** Neuer Tab: die Seite quillt als runde Blase aus einem Knopf heraus */
    fun blaseAus(ziel: View, von: View) {
        if (ziel.width == 0 || ziel.height == 0) return
        ziel.animate().cancel()
        ziel.scaleX = 1f; ziel.scaleY = 1f
        val a = IntArray(2); val b = IntArray(2)
        ziel.getLocationInWindow(a); von.getLocationInWindow(b)
        ziel.pivotX = (b[0] - a[0] + von.width / 2f).coerceIn(0f, ziel.width.toFloat())
        ziel.pivotY = (b[1] - a[1] + von.height / 2f).coerceIn(0f, ziel.height.toFloat())

        val startRadius = min(ziel.width, ziel.height) / 2f
        val umriss = RundUmriss(startRadius)
        ziel.outlineProvider = umriss
        ziel.clipToOutline = true
        ValueAnimator.ofFloat(startRadius, 0f).apply {
            duration = 540
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener { umriss.radius = it.animatedValue as Float; ziel.invalidateOutline() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    ziel.clipToOutline = false
                    ziel.outlineProvider = ViewOutlineProvider.BACKGROUND
                }
            })
            start()
        }
        ziel.scaleX = 0.05f; ziel.scaleY = 0.05f; ziel.alpha = 0.3f
        ziel.animate().setStartDelay(0).scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(540).setInterpolator(OvershootInterpolator(0.8f)).start()
    }

    /** Kurzes Aufhüpfen, z. B. wenn der Werbeblocker-Zähler steigt */
    fun huepfen(v: View, staerke: Float = 1.35f) {
        v.animate().cancel()
        v.scaleX = staerke; v.scaleY = staerke
        v.animate().setStartDelay(0).scaleX(1f).scaleY(1f).setDuration(420)
            .setInterpolator(OvershootInterpolator(4f)).start()
    }

    /** Tab schließen: die Karte bläht sich kurz auf und zerplatzt in Glasperlen */
    fun zerplatzen(karte: View, ebene: ViewGroup, fertig: () -> Unit) {
        karte.isEnabled = false
        val a = IntArray(2); val b = IntArray(2)
        ebene.getLocationInWindow(a); karte.getLocationInWindow(b)
        val mx = b[0] - a[0] + karte.width / 2f
        val my = b[1] - a[1] + karte.height / 2f
        karte.animate().cancel()
        karte.animate().setStartDelay(0).scaleX(1.05f).scaleY(1.14f).setDuration(90)
            .setInterpolator(DecelerateInterpolator()).withEndAction {
                perlen(ebene, mx, my, karte.width, karte.height)
                karte.animate().setStartDelay(0).scaleX(0.3f).scaleY(0.3f).alpha(0f)
                    .setDuration(170).setInterpolator(AccelerateInterpolator())
                    .withEndAction(fertig).start()
            }.start()
    }

    /** Glasperlen, die von einem Punkt aus wegspritzen */
    fun perlen(ebene: ViewGroup, mx: Float, my: Float, w: Int, h: Int, anzahl: Int = 16) {
        val d = ebene.resources.displayMetrics.density
        repeat(anzahl) {
            val groesse = ((5 + Random.nextFloat() * 9) * d).toInt()
            val perle = View(ebene.context).apply { setBackgroundResource(R.drawable.perle) }
            ebene.addView(perle, FrameLayout.LayoutParams(groesse, groesse))
            val sx = mx + (Random.nextFloat() - 0.5f) * w * 0.8f
            val sy = my + (Random.nextFloat() - 0.5f) * h * 0.6f
            perle.x = sx - groesse / 2f
            perle.y = sy - groesse / 2f
            val winkel = atan2(sy - my, sx - mx) + (Random.nextFloat() - 0.5f) * 0.8f
            val weite = (40 + Random.nextFloat() * 110) * d
            perle.scaleX = 0.4f; perle.scaleY = 0.4f
            perle.animate()
                .translationXBy(cos(winkel) * weite)
                .translationYBy(sin(winkel) * weite + 30 * d)
                .scaleX(1f).scaleY(1f).alpha(0f)
                .setDuration((450 + Random.nextInt(250)).toLong())
                .setInterpolator(DecelerateInterpolator(1.6f))
                .withEndAction { ebene.removeView(perle) }
                .start()
        }
    }
}
