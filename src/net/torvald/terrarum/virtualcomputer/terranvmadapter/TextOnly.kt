package net.torvald.terrarum.virtualcomputer.terranvmadapter

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl.LwjglApplication
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import net.torvald.terranvm.Executable
import net.torvald.terranvm.runtime.TerranVM

/**
 * Created by minjaesong on 2017-11-17.
 */
class TextOnly : Game() {

    lateinit var background: Texture
    lateinit var execLed: Texture

    lateinit var batch: SpriteBatch

    lateinit var vm: TerranVM

    lateinit var sevensegFont: BitmapFont

    lateinit var peripheral: PeriMDA

    lateinit var vmThread: Thread

    lateinit var memvwr: Memvwr

    override fun create() {
        val vmDelay = 25

        background = Texture(Gdx.files.internal("assets/8025_textonly.png"))
        execLed = Texture(Gdx.files.internal("assets/led_green.tga"))

        batch = SpriteBatch()

        sevensegFont = SevensegFont()

        peripheral = PeriMDA(vmExecDelay = vmDelay)

        vm = TerranVM(1024, stdout = peripheral.printStream, tbasic_remove_string_dupes = true)

        vm.peripherals[1] = peripheral


        /*vm.loadProgram(Assembler("""
# loadnum r3, 1; # peripheral ID
# loadnum r2, 0; # peripheral mem addr
# loadnum r1, 0; # what byte to write


:loop;
jfw 1;
jmp @loop;


"""))*/

        vm.loadProgram(Executable.beers)
        vm.delayInMills = vmDelay


        memvwr = Memvwr(vm)


        vmThread = Thread(vm)
        vmThread.start()
    }

    private val height: Int; get() = Gdx.graphics.height

    private val lcdOffX = 74f
    private val lcdOffY = 56f

    override fun render() {
        Gdx.graphics.setTitle("TerranVM Debugging Console - Text Only Mode — F: ${Gdx.graphics.framesPerSecond}")


        vm.pauseExec()


        memvwr.update()


        batch.inUse {
            batch.color = Color.WHITE
            batch.draw(background, 0f, 0f)


            // exec lamp
            if (vm.isRunning) {
                batch.draw(execLed, 51f, 39f)
            }

            // draw whatever on the peripheral's memory
            peripheral.render(batch, Gdx.graphics.rawDeltaTime, lcdOffX, lcdOffY)


            // seven seg displays
            // -> memsize
            batch.color = Color(0x25e000ff)
            sevensegFont.draw(batch, vm.memory.size.toString().padStart(8, ' '), 307f, height - 18 - 500f)
            // -> program counter
            batch.color = Color(0xff9a4bff.toInt())
            sevensegFont.draw(batch, vm.pc.toString(16).padStart(6, ' '), 451f, height - 18 - 500f)
            // -> link register
            sevensegFont.draw(batch, vm.lr.toString(16).padStart(6, ' '), 585f, height - 18 - 500f)
            // -> stack pointer
            sevensegFont.draw(batch, vm.sp.toString(16).padStart(4, ' '), 719f, height - 18 - 500f)
            // -> string counter
            sevensegFont.draw(batch, vm.strCntr.toString().padStart(4, ' '), 851f, height - 18 - 500f)
        }


        vm.resumeExec()
    }






    private inline fun SpriteBatch.inUse(action: () -> Unit) {
        this.begin()
        action.invoke()
        this.end()
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