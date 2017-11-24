package net.torvald.terrarum.virtualcomputer.terranvmadapter

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import net.torvald.terranvm.runtime.GdxPeripheralWrapper

/**
 * Created by minjaesong on 2017-11-23.
 */
class KeyboardAbstraction : GdxPeripheralWrapper(16) {



    override fun render(batch: SpriteBatch, delta: Float, offsetX: Float, offsetY: Float) {  }

    override fun keyTyped(char: Char): Boolean {
        memory[0] = char.toByte()

        return true
    }

    override fun keyDown(keycode: Int): Boolean {
        memory[8] = keycode.toByte()

        return true
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean = false

    override fun dispose() {  }

    override fun call(arg: Int) {

    }
}