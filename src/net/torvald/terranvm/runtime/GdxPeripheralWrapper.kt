package net.torvald.terranvm.runtime

import com.badlogic.gdx.graphics.g2d.SpriteBatch

/**
 * Created by minjaesong on 2017-11-18.
 */
abstract class GdxPeripheralWrapper(memSize: Int, suppressWarnings: Boolean = false) :
        VMPeripheralWrapper(memSize, suppressWarnings) {

    abstract fun render(batch: SpriteBatch, delta: Float, offsetX: Float, offsetY: Float)

    abstract fun keyDown(keycode: Int): Boolean

    abstract fun keyTyped(char: Char): Boolean

    abstract fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean

    abstract fun dispose()

}