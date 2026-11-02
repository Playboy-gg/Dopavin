package com.dopavin.ui.glass.backdrop.effects
import androidx.compose.ui.graphics.RenderEffect
import com.dopavin.ui.glass.backdrop.BackdropEffectScope
import com.dopavin.ui.glass.backdrop.RuntimeShader
import com.dopavin.ui.glass.backdrop.internal.RuntimeShaderEffect
import com.dopavin.ui.glass.backdrop.internal.chain
import com.dopavin.ui.glass.backdrop.isRenderEffectSupported
import com.dopavin.ui.glass.backdrop.isRuntimeShaderSupported
import kotlin.contracts.ExperimentalContracts
import org.intellij.lang.annotations.Language
fun BackdropEffectScope.effect(effect: RenderEffect) {
    if (!isRenderEffectSupported()) return
    renderEffect = renderEffect.chain(effect)
}
@OptIn(ExperimentalContracts::class)
fun BackdropEffectScope.runtimeShaderEffect(
    key: String,
    @Language("AGSL") shaderString: String,
    uniformShaderName: String,
    block: RuntimeShader.() -> Unit
) {
    if (!isRuntimeShaderSupported()) return
    val effect =
        RuntimeShaderEffect(
            runtimeShader = obtainRuntimeShader(key, shaderString).apply(block),
            uniformShaderName = uniformShaderName
        )
    renderEffect = renderEffect.chain(effect)
}
