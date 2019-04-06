package net.torvald.terrarum.virtualcomputer.terranvmadapter

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.backends.lwjgl.LwjglApplication
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import net.torvald.terranvm.assets.BiosSetup
import net.torvald.terranvm.assets.Loader
import net.torvald.terranvm.runtime.Assembler
import net.torvald.terranvm.runtime.GdxPeripheralWrapper
import net.torvald.terranvm.runtime.TerranVM
import net.torvald.terranvm.runtime.compiler.cflat.Cflat
import net.torvald.terranvm.runtime.toUint
import net.torvald.terranvm.toReadableBin
import net.torvald.terranvm.toReadableOpcode

/**
 * Created by minjaesong on 2017-11-17.
 */
class TextOnly : Game() {

    private val ipsCounter = """
            .code;
            loadwordilo r1, 42;
            loadbyte r2, r1, r0;
            storebyte r1, r2, r0;
            jmp 19h;
        """.trimIndent()

    lateinit var background: Texture
    lateinit var execLed: Texture
    lateinit var waitLed: Texture

    lateinit var batch: SpriteBatch

    lateinit var vm: TerranVM

    lateinit var sevensegFont: BitmapFont

    lateinit var peripheral: PeriMDA

    lateinit var vmThread: Thread

    lateinit var memvwr: Memvwr

    override fun create() {
        val vmDelay = 1

        background = Texture(Gdx.files.internal("assets/8025_textonly.png"))
        execLed = Texture(Gdx.files.internal("assets/led_green.tga"))
        waitLed = Texture(Gdx.files.internal("assets/led_orange.tga"))

        batch = SpriteBatch()

        sevensegFont = SevensegFont()

        peripheral = PeriMDA(vmExecDelay = vmDelay)

        vm = TerranVM(8192, stdout = peripheral.printStream)

        vm.peripherals[TerranVM.IRQ_KEYBOARD] = KeyboardAbstraction(vm)
        vm.peripherals[3] = peripheral


        val assembler = Assembler(vm)

        //val programImage = assembler(Loader())
        val programImage = assembler(ipsCounter)
        val code = programImage.bytes


        println("ASM size: ${code.size} (word-aligned: ${if (code.size % 4 == 0) "yes" else "HELL NAW!"})")
        code.printASM()

        vm.loadProgram(programImage)
        vm.delayInMills = vmDelay



        memvwr = Memvwr(vm)


        Gdx.input.inputProcessor = TVMInputProcessor(vm)


        vmThread = Thread(vm)
        vmThread.start()

    }

    private val height: Int; get() = Gdx.graphics.height

    private val lcdOffX = 74f
    private val lcdOffY = 56f

    override fun render() {
        Gdx.graphics.setTitle("TerranVM Debugging Console - Text Only Mode — F: ${Gdx.graphics.framesPerSecond}")


        //vm.pauseExec()


        memvwr.update()


        batch.inUse {
            batch.color = Color.WHITE
            batch.draw(background, 0f, 0f)


            // exec lamp
            if (vm.isRunning && !vm.isPaused) {
                batch.draw(execLed, 51f, 39f)
            }

            // wait lamp
            if (vm.isPaused) {
                batch.draw(waitLed, 51f, 19f)
            }

            // draw whatever on the peripheral's memory
            peripheral.render(batch, Gdx.graphics.rawDeltaTime, lcdOffX, lcdOffY)


            // seven seg displays
            // -> memsize
            batch.color = Color(0x25e000ff)
            sevensegFont.draw(batch, vm.memory.size.toString().padStart(8, ' '), 307f, height - 18 - 500f)
            // -> program counter
            batch.color = Color(0xff5a66ff.toInt())
            sevensegFont.draw(batch, vm.pc.toString(16).padStart(6, ' '), 451f, height - 18 - 500f)
            // -> link register
            sevensegFont.draw(batch, vm.lr.toString(16).padStart(6, ' '), 585f, height - 18 - 500f)
            // -> stack pointer
            sevensegFont.draw(batch, vm.sp.toString(16).padStart(4, ' '), 719f, height - 18 - 500f)
        }


        //vm.resumeExec()
    }

    override fun dispose() {
        background.dispose()
        execLed.dispose()
        waitLed.dispose()
    }

    private inline fun SpriteBatch.inUse(action: () -> Unit) {
        this.begin()
        action.invoke()
        this.end()
    }


    class TVMInputProcessor(val vm: TerranVM) : InputProcessor {
        override fun touchUp(p0: Int, p1: Int, p2: Int, p3: Int): Boolean {
            return false
        }

        override fun mouseMoved(p0: Int, p1: Int): Boolean {
            return false
        }

        override fun keyTyped(p0: Char): Boolean {
            (vm.peripherals[TerranVM.IRQ_KEYBOARD] as GdxPeripheralWrapper).keyTyped(p0)
            return true
        }

        override fun scrolled(p0: Int): Boolean {
            return false
        }

        override fun keyUp(p0: Int): Boolean {
            return false
        }

        override fun touchDragged(p0: Int, p1: Int, p2: Int): Boolean {
            return false
        }

        override fun keyDown(p0: Int): Boolean {
            return false
        }

        override fun touchDown(p0: Int, p1: Int, p2: Int, p3: Int): Boolean {
            return false
        }
    }

}

fun ByteArray.printASM() {
    for (i in 0 until this.size step 4) {
        val b = this[i].toUint() or this[i + 1].toUint().shl(8)  or this[i + 2].toUint().shl(16)  or this[i + 3].toUint().shl(24)

        print("${b.toReadableBin()}; ")
        print("${b.toReadableOpcode()}\n")
    }
}

fun main(args: Array<String>) {
    val config = LwjglApplicationConfiguration()
    config.width = 1106
    config.height = 556
    config.foregroundFPS = 0
    config.resizable = false

    LwjglApplication(TextOnly(), config)
}