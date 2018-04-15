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
import net.torvald.terranvm.runtime.Assembler
import net.torvald.terranvm.runtime.GdxPeripheralWrapper
import net.torvald.terranvm.runtime.TerranVM
import net.torvald.terranvm.runtime.compiler.cflat.Cflat
import net.torvald.terranvm.runtime.toUint
import net.torvald.terranvm.toReadableBin
import net.torvald.terranvm.toReadableOpcode
import kotlin.experimental.or

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
        val vmDelay = 10

        background = Texture(Gdx.files.internal("assets/8025_textonly.png"))
        execLed = Texture(Gdx.files.internal("assets/led_green.tga"))

        batch = SpriteBatch()

        sevensegFont = SevensegFont()

        peripheral = PeriMDA(vmExecDelay = vmDelay)

        vm = TerranVM(4096, stdout = peripheral.printStream)

        vm.peripherals[TerranVM.IRQ_KEYBOARD] = KeyboardAbstraction(vm)
        vm.peripherals[3] = peripheral


        val assembler = Assembler(vm)


        /*val testProgram = """
            float x;
            float y;
            float z;
            float z1;

            float returnsomething() {
                return 3.3333333333333;
            }

            z = returnsomething();
        """.trimIndent()
        val program = Cflat.buildTree(Cflat.tokenise(Cflat.preprocess(testProgram)))

        println(program)

        val notatedProgram = Cflat.treeToProperNotation(program)

        val programInIR = Cflat.notationToIR(notatedProgram)
        val programInNewIR = Cflat.preprocessIR(programInIR)
        val programASM = Cflat.IRtoASM(programInNewIR)
        val code = assembler(programASM.joinToString("\n"))*/


        //val mdaFiller = assembler("loadbytei r1, 0;loadbytei r2, 3;:loope;inc r1;storebyte r1, r1, r2;jmp @loope;")


        /*val intFloatTest = assembler("""
            #loadbytei r1, 42;
            #loadbytei r2, 42f;
            loadwordi r1, 42;
            loadwordi r2, 42f;
        """.trimIndent())*/

        val biosEchoTest = assembler("""
            .data;

            string teststr "There are a lot of 8 and 16-bit single-board hobbyist computers available these days.  But every one of them falls short in some way or another from what I dream of.  I'd just design one myself, but I'm not really good enough with electronics to do that.  So, I'm hoping somebody else will make my dream come true.  So I've compiled a list of things I think it should have.

            Off the Shelf Components

            So, basically I would not want the computer to use any old components that cannot be purchased anymore.  That includes custom chips from Atari or Commodore or whatever.  I would hope that the system would eventually get a larger user base and I wouldn't want to see any shortage of parts arise preventing mass production.  However, I prefer to avoid any FPGA or microcontrollers if possible, but that's not a deal-breaker.

            CPU

            I would want the CPU to be 6502 or compatible, such as 65816.  However, I'd be fine with the traditional 6502.  I would prefer a faster clock speed, such as 8 Mhz or better.  That way people could write code in BASIC and it would actually run fast enough to be useful.  As long as we aren't stuck using something like Commodore's VIC or VIC-2 chips, then this shouldn't be a problem.

            Memory

            I would want 128K or 256 of static RAM, with possibly the ability to upgrade it.  If using 6502 then there will need to be some sort of banking, but with 65816 it should be able to access all of it directly.";

            .code;


            loadwordi r1, 00000100h;   # wait for a key press
            call r1, FFh;              #

            loadwordi r1, 02000000h;
            loadhwordi r2, @teststr;
            or r1, r1, r2;
            call r1, FFh;


        """.trimIndent())

        println("ASM size: ${biosEchoTest.size} (word-aligned: ${if (biosEchoTest.size % 4 == 0) "yes" else "HELL NAW!"})")
        biosEchoTest.printASM()

        vm.loadProgram(biosEchoTest)
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