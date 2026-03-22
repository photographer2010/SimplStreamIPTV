package com.simplstudios.simplstream.presentation.splash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.simplstudios.simplstream.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Cinematic startup animation for SimplStream.
 *
 * Sequence (~5.5 seconds):
 * 1. "Simpl" fades in centered on black                  (0.0s – 0.8s)
 * 2. "Simpl" cross-fades into "Stream" in place          (1.1s – 1.8s)
 * 3. "Stream" fades out                                  (2.0s – 2.4s)
 * 4. "Simpl" drops in from above, then "Stream" follows  (2.6s – 3.4s)
 * 5. They lock together as "SimplStream" — hold           (3.4s – 5.4s)
 * 6. Zoom burst → profile selection                      (5.4s – 6.0s)
 */
class SplashFragment : Fragment() {

    private lateinit var textSimpl: TextView
    private lateinit var textStream: TextView
    private lateinit var textCombined: LinearLayout
    private lateinit var textCombinedSimpl: TextView
    private lateinit var textCombinedStream: TextView
    private lateinit var ambientGlow: View
    private lateinit var flashOverlay: View

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_splash, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textSimpl = view.findViewById(R.id.text_simpl)
        textStream = view.findViewById(R.id.text_stream)
        textCombined = view.findViewById(R.id.text_combined)
        textCombinedSimpl = view.findViewById(R.id.text_combined_simpl)
        textCombinedStream = view.findViewById(R.id.text_combined_stream)
        ambientGlow = view.findViewById(R.id.ambient_glow)
        flashOverlay = view.findViewById(R.id.flash_overlay)

        // Everything starts invisible
        textSimpl.alpha = 0f
        textStream.alpha = 0f
        textCombined.alpha = 0f
        ambientGlow.alpha = 0f
        flashOverlay.alpha = 0f

        viewLifecycleOwner.lifecycleScope.launch {
            playIntroAnimation()
        }
    }

    private suspend fun playIntroAnimation() {

        // ═══════════════════════════════════════════════════════════════
        // Phase 1: "Simpl" fades in at center — clean and confident
        // ═══════════════════════════════════════════════════════════════
        ambientGlow.animate()
            .alpha(0.3f)
            .setDuration(900)
            .setInterpolator(DecelerateInterpolator())
            .start()

        textSimpl.animate()
            .alpha(1f)
            .setDuration(700)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .start()

        delay(1100) // Let "Simpl" sit for a moment

        // ═══════════════════════════════════════════════════════════════
        // Phase 2: "Simpl" morphs into "Stream"
        //   — Simpl slides right and fades out
        //   — Stream fades in at the same center spot
        //   — feels like one word transforming into the other
        // ═══════════════════════════════════════════════════════════════
        textSimpl.animate()
            .alpha(0f)
            .translationX(60f)
            .setDuration(500)
            .setInterpolator(AccelerateInterpolator(1.2f))
            .start()

        delay(200) // slight overlap — Stream starts appearing before Simpl fully vanishes

        textStream.translationX = -60f
        textStream.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(500)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .start()

        // Glow reacts
        ambientGlow.animate()
            .alpha(0.45f)
            .scaleX(1.2f).scaleY(1.2f)
            .setDuration(600)
            .setInterpolator(DecelerateInterpolator())
            .start()

        delay(1000) // Let "Stream" sit

        // ═══════════════════════════════════════════════════════════════
        // Phase 3: "Stream" fades out — clean the stage
        // ═══════════════════════════════════════════════════════════════
        textStream.animate()
            .alpha(0f)
            .setDuration(400)
            .setInterpolator(AccelerateInterpolator())
            .start()

        ambientGlow.animate()
            .alpha(0.15f)
            .setDuration(400)
            .start()

        delay(500) // Brief dark beat — builds anticipation

        // ═══════════════════════════════════════════════════════════════
        // Phase 4: "Simpl" drops in from above, then "Stream" follows
        //   — fast, punchy drops with strong bounce
        //   — they land side by side forming "SimplStream"
        // ═══════════════════════════════════════════════════════════════
        textCombined.alpha = 1f
        textCombinedSimpl.alpha = 0f
        textCombinedStream.alpha = 0f

        // "Simpl" drops in — fast and bouncy
        textCombinedSimpl.translationY = -120f
        textCombinedSimpl.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(350)
            .setInterpolator(OvershootInterpolator(1.6f))
            .start()

        // Glow bursts on Simpl landing
        ambientGlow.animate()
            .alpha(0.6f)
            .scaleX(1.8f).scaleY(1.8f)
            .setDuration(350)
            .setInterpolator(DecelerateInterpolator())
            .start()

        delay(180) // Tight stagger — Stream snaps in right after

        // "Stream" drops in — same punchy energy
        textCombinedStream.translationY = -120f
        textCombinedStream.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(350)
            .setInterpolator(OvershootInterpolator(1.6f))
            .start()

        // Glow expands as both land
        ambientGlow.animate()
            .alpha(0.75f)
            .scaleX(2.2f).scaleY(2.2f)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator())
            .start()

        delay(500) // Let both settle

        // ═══════════════════════════════════════════════════════════════
        // Phase 5: Hold "SimplStream" — let the user appreciate it
        //   — big glow gently breathes to keep it alive
        // ═══════════════════════════════════════════════════════════════

        // Gentle glow breathe out
        ambientGlow.animate()
            .alpha(0.55f)
            .scaleX(2.5f).scaleY(2.5f)
            .setDuration(1000)
            .setInterpolator(DecelerateInterpolator())
            .start()

        delay(1000)

        // Gentle glow breathe in
        ambientGlow.animate()
            .alpha(0.7f)
            .scaleX(2.0f).scaleY(2.0f)
            .setDuration(800)
            .setInterpolator(DecelerateInterpolator())
            .start()

        delay(1000) // Total hold ≈ 2 seconds with breathing glow

        // ═══════════════════════════════════════════════════════════════
        // Phase 6: Zoom burst — logo rushes toward the viewer
        //   — quick flash of light for impact
        //   — everything scales up fast and fades to black
        //   — then navigate
        // ═══════════════════════════════════════════════════════════════

        // Subtle white flash
        flashOverlay.animate()
            .alpha(0.12f)
            .setDuration(80)
            .withEndAction {
                flashOverlay.animate()
                    .alpha(0f)
                    .setDuration(400)
                    .start()
            }
            .start()

        // Logo zooms in hard
        textCombined.animate()
            .scaleX(4f).scaleY(4f)
            .alpha(0f)
            .setDuration(700)
            .setInterpolator(AccelerateInterpolator(2.5f))
            .start()

        ambientGlow.animate()
            .alpha(0f)
            .scaleX(3f).scaleY(3f)
            .setDuration(600)
            .start()

        delay(550)

        // Navigate to profile selection
        if (isAdded) {
            findNavController().navigate(R.id.action_splash_to_profile)
        }
    }
}
