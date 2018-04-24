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
import net.torvald.terranvm.runtime.toUint
import net.torvald.terranvm.toReadableBin
import net.torvald.terranvm.toReadableOpcode

/**
 * Created by minjaesong on 2017-11-17.
 */
class TextOnly : Game() {

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
            int literalbuffer 0;
            int startingptr 0;
            bytes funckeys 6Bh 70h 72h 74h;

            .code;

            jsri @reset_buffer;
            jmp @code;

            :reset_buffer; # garbles r1
            loadbytei r1, 0;
            storewordimem r1, @literalbuffer;
            return;

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

            :to_nibble; # push argument first; '0' to 0h, '1' to 1h, etc.; garbles r1, r2, r7; stack top has return value
            pop r2;                         # return addr
            pop r1;                         # actual arg
            loadwordi r8, 39h;              # '9'

            cmp r1, r8;                     # IF
                                            # (r1 < r8) aka r1 in '0'..'9' THEN
            loadwordils r7, 48;                 # r1 = r1 - 48
            subls r1, r1, r7;                   #
                                            # (r1 > r8) THEN
            loadwordigt r7, 55;                 # r1 = r1 - 55
            subgt r1, r1, r7;                   #
                                            # ENDIF

            loadbytei r7, 1111b;            # sanitise r1 by
            and r1, r1, r7;                 # ANDing with 1111b

            push r1;                        # push return value
            push r2;                        # push returning PC address
            return;

            :putchar_verbatim;
            push r3;                        # putchar
            jsri @putchar;                  #
            return;

            :putchar_capital;
            loadbytei r1, 32;               #
            sub r8, r3, r1;                 #
            push r8;                        # putchar
            jsri @putchar;                  #
            return;


            ############################################################################################################


            :code;

            ################
            ## initialise ##
            ################

            loadbytei r5, 0;                # byte accumulator
            loadbytei r6, 0;                # byte literal read and acc counter (7 downTo 0)
            loadbytei r8, 0;                # constant zero

            pushwordi @loadertext;          # print out LOADER
            jsri @putstring;                #

            loadwordi r2, 2048;             # allocate buffer, r4 contains address (NOT an offset)
            malloc r4, r2;                  # (2 KBytes)
            storewordimem r4, @startingptr; #
            loadbytei r4, 0;                # now r4 contains distance to the starting pointer



            #################################
            :loop; ##########################
            #################################



            loadwordi r1, 00000102h;        # r3 <- getchar
            call r1, FFh;                   #

            ###############################
            ## print 'a'..'f' as capital ##
            ###############################

            loadbytei r1, 61h;              # r1 <- 'a'
            cmp r3, r1;                     # IF (r3, 'a')
            jgt @r1_geq_a;                  # 'a'
            jz  @r1_geq_a;                  # 'b'..'f'
            jls @r1_ls_a;                   # lesser
            :r1_geq_a;
            loadbytei r1, 66h;              # r1 <- 'f'
            cmp r3, r1;                     # IF (r3, 'f')
            jsriz  @putchar_capital;            # 'f'
            jz @accept_byte_literal;            #
            jsrils @putchar_capital;            # 'a'..'e'
            jls @accept_byte_literal;           #
            jgt @r1_gt_f;                   # greater than f
                                            # ENDIF

            :r1_gt_f;                       # alias
            jmp @function_keys;             # derp


            :r1_ls_a;
            # TODO compare if r3 in '0'..'9'
            # putchar and goto accept_byte_literal

            loadbytei r1, 30h;              # r1 <- '0'
            cmp r3, r1;                     # IF (r3, '0')
            jls @loop;                          # deny if r3 < '0'
            loadbytei r1, 39h;              # r1 <- '9'
            cmp r3, r1;                     # IF (r3, '9')
            jgt @loop;                          # deny if r3 > '9'

            jsri @putchar_verbatim;         #
            jmp @accept_byte_literal;       #


            ##################
            :function_keys; ##
            ##################

            loadbytei r1, 70h;              #
            cmp r3, r1;                     # IF (r3 == 'p') THEN
            jsriz @putchar_verbatim;            # printout
            jz @write_to_mem;                   # goto write_to_mem
                                            # ENDIF
            loadbytei r1, 74h;              #
            cmp r3, r1;                     # IF (r3 == 't') THEN
            jsriz @putchar_verbatim;            # printout
            jz @move_pointer;                   # goto move_pointer
                                            # ENDIF
            loadbytei r1, 6Bh;              #
            cmp r3, r1;                     # IF (r3 == 'k') THEN
            jsriz @putchar_verbatim;            # printout
            jz @peek_buffer;                    # goto peek_buffer
                                            # ENDIF
            loadbytei r1, 72h;              #
            cmp r3, r1;                     # IF (r3 == 'r') THEN
            jsriz @putchar_verbatim;            # printout
            jz @execute;                    # goto execute
                                            # ENDIF
            jnz @loop;                      # deny

            ########################
            :accept_byte_literal; ##
            ########################


            loadbytei r8, 57;               # '9'
            cmp r3, r8;                     # IF
                                            # (r3 < r8) aka r1 in '0'..'9' THEN
            loadbyteils r7, 30h;                # r3 = r3 - 48
            subls r3, r3, r7;                   #
                                            # (r3 > r8) THEN
            loadbyteigt r7, 55;                 # r3 = r3 - 55
            subgt r3, r3, r7;                   #
                                            # ENDIF

            loadbytei r7, 1111b;            # sanitise r3 by
            and r3, r3, r7;                 # ANDing with 1111b


            #######################
            ## now r3 has nibble ##
            #######################

            loadbytei r2, 1;                # flip about with r6, keep it to r2
            xor r2, r6, r2;                 # r2 = r6 xor 1 (01234567 -> 10325476)

            ## accumulate to r5 ##
            loadbytei r7, 4;                #
            mulint r7, r7, r2;              # r8 = 8, 12, 0, 4 for r6: 2, 3, 0, 1
            shl r3, r3, r7;                 #
            or r5, r5, r3;                  # r5 = r5 or (r3 shl r7)

            storewordimem r5, @literalbuffer;# put r5 into literalbuffer

            loadbytei r1, 7;                # a number to compare against
            cmp r6, r1;                     # IF
                                            # (r6 == 7) THEN
            loadbyteiz r6, 0;                   # r6 = 0
            loadbyteiz r5, 0;                   # r5 = 0
                                            # (r6 != 7) THEN
            incnz r6;                           # r6++
                                            # ENDIF

            jmp @loop;

            #################
            :write_to_mem; ##
            #################

            loadwordimem r5, @literalbuffer;# deref literalbuffer into r5 (r5 is zero in this case if full word is written in buffer)
            loadbytei r1, 0;                #

            loadwordimem r2, @startingptr;  #
            add r2, r2, r4;                 # r2 now contains real address (startingptr + distance)
            storeword r5, r2, r1;           #

            loadbytei r1, 4;                # r4 += 4
            add r4, r4, r1;                 #

            jsri @reset_buffer;
            loadbyteiz r6, 0;               # r6 = 0
            loadbyteiz r5, 0;               # r5 = 0

            jmp @loop;

            #################
            :move_pointer; ##
            #################

            loadwordimem r5, @literalbuffer;# deref literalbuffer into r5 (r5 is zero in this case if full word is written in buffer)

            loadbytei r1, 2;                # make r5 word-aligned
            ushr r5, r5, r1; shl r5, r5, r1;#

            mov r4, r5;                     # r4 = literalbuffer

            itox r1, r4;                    # r1 = r4 (distance from startingptr) as a String

            loadhwordi r7, 020Ah;           #
            call r7, FFh;                   # print '\n'

            loadhwordi r8, 023Eh;           #
            call r8, FFh;                   # print '>'

            loadwordi r8, 02000000h;        # base BIOS call for print string
            or r8, r8, r1;                  #
            call r8, FFh;                   # print out new offset (aka distance)

            call r7, FFh; # print '\n'      # print '\n' using r7 we overwrote above

            jsri @reset_buffer;
            loadbyteiz r6, 0;               # r6 = 0
            loadbyteiz r5, 0;               # r5 = 0

            jmp @loop;

            ################
            :peek_buffer; ##
            ################

            itox r1, r4;                    # r1 = r4 (distance from startingptr) as a String
            loadwordimem r2, @startingptr;  #
            add r2, r2, r4;                 # r2 now contains real address (startingptr + distance)

            loadwordi r8, 02000000h;        # base BIOS call for print string
            loadbytei r3, 0;

            ## PRINT 1 ##

            loadhwordi r3, 020Ah; call r3, FFh; # print '\n'
            loadhwordi r7, 023Eh; call r7, FFh; # print '>'
            or r3, r8, r1;        call r3, FFh; # print distance from startingptr

            ## END OF PRINT 1 ##

            ## PRINT 2 ##

            loadhwordi r3, 023Ah; call r3, FFh; # print ':'
            loadhwordi r3, 0220h; call r3, FFh; # print ' '

            ## print out 4 consecutive bytes

            loadbytei r3, 0;
            # loadwordi r8, 02000000h;
            loadhwordi r7, 0220h;

            loadbyte r1, r2, r3;            # r1 now contains whatever byte was contained in old r2
            itox r1, r1;                    # r1 now contains string pointer for hex str
            or r1, r1, r8; call r1, FFh;    # printout r1

            # call r2, FFh;                   # printout ' '

            inc r2;                         #
            loadbyte r1, r2, r3;            # r1 now contains whatever byte was contained in old r2
            itox r1, r1;                    # r1 now contains string pointer for hex str
            or r1, r1, r8; call r1, FFh;    # printout r1

            # call r2, FFh;                   # printout ' '

            inc r2;                         #
            loadbyte r1, r2, r3;            # r1 now contains whatever byte was contained in old r2
            itox r1, r1;                    # r1 now contains string pointer for hex str
            or r1, r1, r8; call r1, FFh;    # printout r1

            # call r2, FFh;                   # printout ' '

            inc r2;                         #
            loadbyte r1, r2, r3;            # r1 now contains whatever byte was contained in old r2
            itox r1, r1;                    # r1 now contains string pointer for hex strW
            or r1, r1, r8; call r1, FFh;    # printout r1

            loadhwordi r3, 020Ah; call r3, FFh; # print '\n'

            ## END OF PRINT 2 ##

            jmp @loop;

            ############
            :execute; ##
            ############

            loadwordimem r2, @startingptr;  #
            add r2, r2, r4;                 # r2 now contains real address (startingptr + distance)

            srw r1, r2;                     # pc <- r2


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