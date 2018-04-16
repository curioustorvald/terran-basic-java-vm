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
        val vmDelay = 1

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

        /*val biosEchoTest = assembler("""
            jmp @code;

            :getchar; # r1 <- char
            loadwordi r1, 00000100h;
            call r1, FFh;
            return;

            :putchar; # push char first before call; garbles r2
            pop r2; # return addr
            pop r1; # actual arg
            push r2;
            loadwordi r2, 00000200h;
            or r1, r1, r2;
            call r1, FFh;
            return;


            :code;

            loadhwordi r1, 40h; push r1;
            loadhwordi r1, 43h; push r1;
            loadhwordi r1, 42h; push r1;
            loadhwordi r1, 41h; push r1;
            jsri @putchar;
            jsri @putchar;
            jsri @putchar;
            jsri @putchar;

            :loop;
            jsri @getchar;
            push r1;
            jsri @putchar;
            jmp @loop;

        """.trimIndent())*/


        val loader = assembler("""
            .data;
            string loadertext "LOADER
            ";

            .code;

            jmp @code;

            :getchar; # r1 <- char
            loadwordi r1, 00000100h;
            call r1, FFh;
            return;

            :putchar; # push char first before call; garbles r1 and r2
            pop r2;                         # return addr
            pop r1;                         # actual arg
            push r2;
            loadwordi r2, 00000200h;
            or r1, r1, r2;
            call r1, FFh;
            return;

            :putstring; # push label first before call; garbles r1 and r2
            pop r2;                         # return addr
            pop r1;                         # actual arg
            push r2;
            loadwordi r2, 02000000h;
            or r1, r1, r2;
            call r1, FFh;
            return;

            :to_nibble; # push argument first; '0' to 0h, '1' to 1h, etc.; garbles r7; stack top has return value
            pop r2;                         # return addr
            pop r1;                         # actual arg
            loadwordi r8, 39h;              # '9'

            cmp r1, r8;                     # IF :
                                            # (r1 < r8) aka r1 in '0'..'9' :
            loadwordils r7, 48;                 # r1 = r1 - 48
            subls r1, r1, r7;                   #
                                            # (r1 > r8) :
            loadwordigt r7, 55;                 # r1 = r1 - 55
            subgt r1, r1, r7;                   #
                                            # ENDIF

            loadbytei r7, 1111b;            # sanitise r1 by
            and r1, r1, r7;                 # ANDing with 1111b

            push r1;                        # push return value
            push r2;                        # push returning PC address
            return;

            :code;

            loadbytei r5, 0;                # byte accumulator
            loadbytei r6, 0;                # byte literal read counter (0 or 1)
            loadbytei r8, 0;                # constant zero

            pushwordi @loadertext;          # print out LOADER
            jsri @putstring;                #

            loadwordi r2, 2048;             # allocate buffer, r4 contains address (NOT an offset)
            malloc r4, r2;                  # (2 KBytes)

            :loop;

            jsri @getchar;                  # getchar
            mov r3, r1;                     # r3 has character just read

            push r1;                        # putchar
            jsri @putchar;                  #

            ## turn byte literal (r3) into nibble ##
            push r3;                        # r3 before: char just read
            jsri @to_nibble;                # r3 after : '0' to 0h, '1' to 1h, etc.
            pop r3;                         #


            loadbytei r8, 0;
            cmp r6, r8;                     # IF :
                                            # (r6 == r8) :
            loadbyteiz r7, 4;                   # r5 = r3 << 4
            shlz r5, r3, r7;                    #
            loadbyteiz r6, 1;                   #
                                            # (r6 != r8) :
            ornz r5, r5, r3;                    # r5 = r5 or r3
            storebytenz r5, r4, r8;             # write r5 as byte to mem[r4]
            incnz r4; loadbyteinz r6, 0;        #
                                            # ENDIF



            jmp @loop;


        """.trimIndent())


        println("ASM size: ${loader.size} (word-aligned: ${if (loader.size % 4 == 0) "yes" else "HELL NAW!"})")
        loader.printASM()

        vm.loadProgram(loader)
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