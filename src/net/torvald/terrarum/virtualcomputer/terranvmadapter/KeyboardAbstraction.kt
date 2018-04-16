package net.torvald.terrarum.virtualcomputer.terranvmadapter

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import net.torvald.terranvm.Register
import net.torvald.terranvm.runtime.GdxPeripheralWrapper
import net.torvald.terranvm.runtime.TerranVM
import net.torvald.terranvm.runtime.toUint

/**
 * Created by minjaesong on 2017-11-23.
 */
class KeyboardAbstraction(val vm: TerranVM) : GdxPeripheralWrapper(16) {


    private var getKeyRequested = false
    private var keyRequestDest: Register? = null

    override fun render(batch: SpriteBatch, delta: Float, offsetX: Float, offsetY: Float) {  }

    override fun keyTyped(char: Char): Boolean {
        memory[0] = char.toByte()


        println("[KeyboardAbstraction] key typed: ${memory[0].toUint().toChar()}")


        if (getKeyRequested && vm.isPaused) {
            vm.writeregInt(keyRequestDest!!, memory[0].toUint())

            getKeyRequested = false
            keyRequestDest = null

            vm.resumeExec()
        }


        return true
    }

    override fun keyDown(keycode: Int): Boolean {
        memory[8] = keycode.toByte()

        return true
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean = false

    override fun dispose() {  }

    override fun call(arg: Int) {
        getKeyRequested = true
        keyRequestDest = arg + 1

        vm.pauseExec()
    }
}