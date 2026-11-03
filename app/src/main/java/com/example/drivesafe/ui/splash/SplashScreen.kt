package com.example.drivesafe.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import com.example.drivesafe.R
import kotlinx.coroutines.launch

/**
 * Layar pembuka di dalam aplikasi: logo lengkap muncul membesar pelan. Ditampilkan sebentar
 * setelah splash bawaan sistem (yang hanya bisa menampilkan ikon maskot).
 */
@Composable
fun SplashScreen() {
    val scale = remember { Animatable(0.85f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(500)) }
        scale.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.White),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.logo_drivesafe),
            contentDescription = "DriveSafe",
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .scale(scale.value)
                .alpha(alpha.value)
        )
    }
}
