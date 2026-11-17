package com.dsh.liquidglass

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
internal class KyantGlassView(
    context: Context,
    private val snapshot: Bitmap,
    initialCornerRadiusPx: Float
) : ComposeView(context), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    private var cornerRadiusPx by mutableFloatStateOf(initialCornerRadiusPx)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false

        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        ViewTreeLifecycleOwner.set(this, this)
        ViewTreeSavedStateRegistryOwner.set(this, this)

        setContent {
            GlassSurface(
                snapshot = snapshot,
                cornerRadiusPx = cornerRadiusPx
            )
        }
    }

    fun updateCornerRadius(px: Float) {
        if (px > 0f) cornerRadiusPx = px
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (lifecycleRegistry.currentState == Lifecycle.State.CREATED) {
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onDetachedFromWindow()
    }
}

@Composable
private fun GlassSurface(
    snapshot: Bitmap,
    cornerRadiusPx: Float
) {
    val density = LocalDensity.current
    val radius = if (cornerRadiusPx > 0f) {
        with(density) { cornerRadiusPx.toDp() }
    } else {
        24.dp
    }

    val image = remember(snapshot) { snapshot.asImageBitmap() }

    // 修复了原代码中不存在的 drawImage 函数，使用标准的 Image 组件铺满 Box
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        Box(
            Modifier
                .fillMaxSize()
                .drawBackdrop(
                    backdrop = rememberCanvasBackdrop { null }, // 使用下方的 Image 作为背景，这里置空
                    shape = {
                        RoundedRectangle(
                            cornerRadius = radius,
                            style = RoundedCornerStyle.Continuous
                        )
                    },
                    effects = {
                        blur(16.dp.toPx())
                        lens(
                            refractionHeight = 16.dp.toPx(),
                            refractionAmount = 32f,
                            depthEffect = true,
                            chromaticAberration = true
                        )
                    },
                    highlight = { Highlight.Plain },
                    shadow = null
                )
        )
    }
}
