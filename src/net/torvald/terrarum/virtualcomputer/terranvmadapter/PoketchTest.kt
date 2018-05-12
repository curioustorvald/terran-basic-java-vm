package net.torvald.terrarum.virtualcomputer.terranvmadapter

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.backends.lwjgl.LwjglApplication
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import net.torvald.terranvm.assets.Loader
import net.torvald.terranvm.runtime.Assembler
import net.torvald.terranvm.runtime.GdxPeripheralWrapper
import net.torvald.terranvm.runtime.TerranVM
import net.torvald.terranvm.runtime.toUint
import net.torvald.terranvm.toReadableBin
import net.torvald.terranvm.toReadableOpcode

/**
 * Created by minjaesong on 2018-05-11.
 */
class PoketchTest : Game() {

    lateinit var batch: SpriteBatch

    lateinit var vm: TerranVM

    lateinit var sevensegFont: BitmapFont

    lateinit var peripheral: PeriMDA

    lateinit var vmThread: Thread

    lateinit var memvwr: Memvwr

    override fun create() {
        val vmDelay = 1


        batch = SpriteBatch()

        peripheral = Poketch()

        vm = TerranVM(4096, stdout = peripheral.printStream)

        //vm.peripherals[TerranVM.IRQ_KEYBOARD] = KeyboardAbstraction(vm)
        vm.peripherals[TerranVM.IRQ_RTC] = PeriRTC(vm)
        vm.peripherals[TerranVM.IRQ_PRIMARY_DISPLAY] = peripheral


        val assembler = Assembler(vm)


        val program = assembler("""
.stack; 4;

.data;

bytes clock0lsb,
03h,c0h,03h,c0h,03h,c0h,03h,c0h,
c3h,c3h,c3h,c3h,c3h,c3h,c3h,c3h,
c3h,c3h,c3h,c3h,c3h,c3h,c3h,c3h,
c3h,c3h,c3h,c3h,c3h,c3h,c3h,c3h,
c3h,c3h,c3h,c3h,c3h,c3h,c3h,c3h,
03h,c0h,03h,c0h,03h,c0h,03h,c0h;

bytes clock1lsb,
3fh,fch,3fh,fch,3fh,fch,3fh,fch,
3fh,fch,3fh,fch,3fh,fch,3fh,fch,
3fh,fch,3fh,fch,3fh,fch,3fh,fch,
3fh,fch,3fh,fch,3fh,fch,3fh,fch,
3fh,fch,3fh,fch,3fh,fch,3fh,fch,
3fh,fch,3fh,fch,3fh,fch,3fh,fch;

bytes clock2lsb,
03h,c0h,03h,c0h,03h,c0h,03h,c0h,
ffh,c3h,ffh,c3h,ffh,c3h,ffh,c3h,
ffh,c3h,ffh,c3h,03h,c0h,03h,c0h,
03h,c0h,03h,c0h,c3h,ffh,c3h,ffh,
c3h,ffh,c3h,ffh,c3h,ffh,c3h,ffh,
03h,c0h,03h,c0h,03h,c0h,03h,c0h;

bytes clock3lsb,
03h,c0h,03h,c0h,03h,c0h,03h,c0h,
ffh,c3h,ffh,c3h,ffh,c3h,ffh,c3h,
ffh,c3h,ffh,c3h,03h,c0h,03h,c0h,
03h,c0h,03h,c0h,ffh,c3h,ffh,c3h,
ffh,c3h,ffh,c3h,ffh,c3h,ffh,c3h,
03h,c0h,03h,c0h,03h,c0h,03h,c0h;

bytes clock4lsb,
c3h,c3h,c3h,c3h,c3h,c3h,c3h,c3h,
c3h,c3h,c3h,c3h,c3h,c3h,c3h,c3h,
c3h,c3h,c3h,c3h,03h,c0h,03h,c0h,
03h,c0h,03h,c0h,ffh,c3h,ffh,c3h,
ffh,c3h,ffh,c3h,ffh,c3h,ffh,c3h,
ffh,c3h,ffh,c3h,ffh,c3h,ffh,c3h;

bytes clock5lsb,
03h,c0h,03h,c0h,03h,c0h,03h,c0h,
c3h,ffh,c3h,ffh,c3h,ffh,c3h,ffh,
c3h,ffh,c3h,ffh,03h,c0h,03h,c0h,
03h,c0h,03h,c0h,ffh,c3h,ffh,c3h,
ffh,c3h,ffh,c3h,ffh,c3h,ffh,c3h,
03h,c0h,03h,c0h,03h,c0h,03h,c0h;

bytes clock6lsb,
c3h,ffh,c3h,ffh,c3h,ffh,c3h,ffh,
c3h,ffh,c3h,ffh,c3h,ffh,c3h,ffh,
c3h,ffh,c3h,ffh,03h,c0h,03h,c0h,
03h,c0h,03h,c0h,c3h,c3h,c3h,c3h,
c3h,c3h,c3h,c3h,c3h,c3h,c3h,c3h,
03h,c0h,03h,c0h,03h,c0h,03h,c0h;

bytes clock7lsb,
03h,c0h,03h,c0h,03h,c0h,03h,c0h,
ffh,c3h,ffh,c3h,ffh,c3h,ffh,c3h,
ffh,c3h,ffh,c3h,ffh,c3h,ffh,c3h,
ffh,c3h,ffh,c3h,ffh,c3h,ffh,c3h,
ffh,c3h,ffh,c3h,ffh,c3h,ffh,c3h,
ffh,c3h,ffh,c3h,ffh,c3h,ffh,c3h;

bytes clock8lsb,
03h,c0h,03h,c0h,03h,c0h,03h,c0h,
c3h,c3h,c3h,c3h,c3h,c3h,c3h,c3h,
c3h,c3h,c3h,c3h,03h,c0h,03h,c0h,
03h,c0h,03h,c0h,c3h,c3h,c3h,c3h,
c3h,c3h,c3h,c3h,c3h,c3h,c3h,c3h,
03h,c0h,03h,c0h,03h,c0h,03h,c0h;

bytes clock9lsb,
03h,c0h,03h,c0h,03h,c0h,03h,c0h,
c3h,c3h,c3h,c3h,c3h,c3h,c3h,c3h,
c3h,c3h,c3h,c3h,03h,c0h,03h,c0h,
03h,c0h,03h,c0h,ffh,c3h,ffh,c3h,
ffh,c3h,ffh,c3h,ffh,c3h,ffh,c3h,
ffh,c3h,ffh,c3h,ffh,c3h,ffh,c3h;

bytes clockColon,
C3h,C3h,C3h,C3h;

## ("Mon", "Tys", "Mid", "Tor", "Fre", "Lau", "Sun", "Ver") ##

bytes numbers_0_9_LSB,
18h,50h,13h,F3h,F3h,F3h,F3h,F3h,F0h,F8h,
FBh,F3h,F3h,F3h,F3h,F3h,F3h,F3h,F3h,F3h,
07h,03h,F3h,F3h,83h,07h,3Fh,3Fh,03h,03h,
07h,03h,F3h,F3h,83h,83h,F3h,F3h,03h,07h,
C3h,83h,13h,33h,03h,03h,F3h,F3h,F3h,F3h,
03h,03h,3Fh,07h,03h,F3h,F3h,F3h,03h,07h,
87h,07h,3Fh,3Fh,07h,03h,33h,33h,03h,87h,
03h,03h,F3h,E7h,E7h,E7h,CFh,CFh,CFh,CFh,
87h,03h,33h,33h,03h,03h,33h,33h,03h,87h,
87h,03h,33h,33h,03h,83h,F3h,F3h,83h,87h,
87h,03h,33h,33h,33h,33h,33h,33h,03h,87h;

bytes celcius_LSB,
18h,50h,13h,F3h,F3h,F3h,F3h,F3h,F0h,F8h,
3Fh,3Fh,FFh,FFh,FFh,FFh,FFh,FFh,3Fh,3Fh;

bytes fahrenheit_LSB,
10h,50h,13h,F3h,F0h,F0h,F3h,F3h,F3h,F3h,
3Fh,3Fh,FFh,FFh,7Fh,7Fh,FFh,FFh,FFh,FFh;

bytes dash,
FFh,FFh,FFh,FFh,83h,07h,FFh,FFh,FFh,FFh;

bytes ADEFILMNORSTUVY,
E1h,C0h,CCh,CCh,C0h,C0h,CCh,CCh,CCh,CCh,
E0h,C0h,CCh,CCh,CCh,CCh,CCh,CCh,C0h,E0h,
C0h,C0h,FCh,FCh,E0h,E0h,FCh,FCh,C0h,C0h,
C0h,C0h,FCh,FCh,E0h,E0h,FCh,FCh,FCh,FCh,
C0h,C0h,F3h,F3h,F3h,F3h,F3h,F3h,C0h,C0h,
FCh,FCh,FCh,FCh,FCh,FCh,FCh,FCh,C0h,C0h,
DEh,CCh,C0h,C0h,CCh,CCh,CCh,CCh,CCh,CCh,
CEh,CCh,C8h,C0h,C0h,C4h,CCh,CCh,CCh,CCh,
E1h,C0h,CCh,CCh,CCh,CCh,CCh,CCh,C0h,E1h,
E0h,C0h,CCh,CCh,C0h,E0h,C4h,CCh,CCh,CCh,
C1h,C0h,FCh,FCh,E0h,C1h,CFh,CFh,C0h,E0h,
C0h,C0h,F3h,F3h,F3h,F3h,F3h,F3h,F3h,F3h,
CCh,CCh,CCh,CCh,CCh,CCh,CCh,CCh,C0h,E1h,
CCh,CCh,CCh,CCh,CCh,CCh,C0h,E1h,E1h,F3h,
CCh,CCh,CCh,CCh,C0h,E1h,F3h,F3h,F3h,F3h;

int months, 0;
int weekday, 0;
int days, 0;
int hours, 0;
int minutes, 0;
bytes charTable,
 60, 80, 70,              # Mon
110,140,100,              # Tys
 60, 40, 10,              # Mid
110, 80, 90,              # Tor
 30, 90, 20,              # Fre
 50,  0,120,              # Lau
100,120, 70,              # Sun
130  20  90;              # Ver

int clock0addr, @clock0lsb;          # must be pre-multiplied by 4
int clock1addr, @clock1lsb;          # must be pre-multiplied by 4
int clock2addr, @clock2lsb;          # must be pre-multiplied by 4
int clock3addr, @clock3lsb;          # must be pre-multiplied by 4
int clock4addr, @clock4lsb;          # must be pre-multiplied by 4
int clock5addr, @clock5lsb;          # must be pre-multiplied by 4
int clock6addr, @clock6lsb;          # must be pre-multiplied by 4
int clock7addr, @clock7lsb;          # must be pre-multiplied by 4
int clock8addr, @clock8lsb;          # must be pre-multiplied by 4
int clock9addr, @clock9lsb;          # must be pre-multiplied by 4



.code;
loadwordi r3, @clock0Addr;           # r3 = (offset)
loadbytei r5, 4;                     # const four
mulint r3, r3, r5;                      # multiply r3 by 4; r3 = address
loadwordimem r1, @clock0Addr;        # r1 = value
loadbytei r4, 10;                    # loop count
loadbytei r5, 4;                     # const four
loadbytei r6, 0;                     # const zero
## loop
:premultiply_by_four1;
cmp r4, r6;
jz @end_of_premultiply_by_four1;
loadword r1, r3, r6;
mulint r1, r1, r5;                   # multiply by 4 to the value
storeword r1, r3, r6;
addint r3, r3, r5;                   # increment address (r3) by 4
dec r4;                              # r4--
jmp @premultiply_by_four1;
:end_of_premultiply_by_four1;



loadhwordi r1, 1001h;                #
call r1, 3;                          # clear screen

loadbytei r1, 6;                     #
call r1, 2;                          #
storewordimem r1, @months;           #

loadbytei r1, 7;                     #
call r1, 2;                          #
storewordimem r1, @weekday;          #

loadbytei r1, 5;                     #
call r1, 2;                          #
storewordimem r1, @days;             #

loadbytei r1, 4;                     #
call r1, 2;                          #
storewordimem r1, @hours;            #

loadbytei r1, 3;                     #
call r1, 2;                          #
storewordimem r1, @minutes;          #




loadbytei r5, 11;                    # common const; width of scanline in bytes
loadbytei r7, 0;                     # conmon const 0, used for loops



####################
## DRAW WEEK NAME ##
####################

loadwordi r1, 00010300h;             # memcpy -- length: 1; destination: 3; source: 0
loadwordi r3, @ADEFILMNORSTUVY;      # where is the font
loadbytei r4, 4;                     #
mulint r3, r3, r4;                   # r3 *= 4


########################################################################################################################

## pre-loop
loadbytei r6, 3;                     # length of each string
loadwordimem r8, @weekday;           # weekday in index, 0 for Mon
mulint r8, r8, r6;                   # r8 *= 3; offset of the word from charTable

loadwordi r2, @charTable;            # where is the character table
loadbytei r4, 4;
mulint r2, r2, r4;                   # r2 *= 4
addint r2, r2, r8;                   # r2 <- memAddr to current character
# add 1 to advance to the next char
loadbyte r2, r2, r7;                 # dereference r2

addint r2, r2, r3;                   # r2 += r3; current glyph data to draw by memcpy

loadbytei r4, 10;                    # height of the font, also loop counter
loadhwordi r6, 355;                  # where to print

## loop
:loop_print_single_char_for_weekname1;
cmp r4, r7;
jz @end_of_loop_print_single_char_for_weekname1;
memcpy r1, r2, r6;
dec r4; addint r6, r6, r5; inc r2;   # r4--; r6 += 11, r2++
jmp @loop_print_single_char_for_weekname1;

:end_of_loop_print_single_char_for_weekname1;

########################################################################################################################

## pre-loop
loadbytei r6, 3;                     # length of each string
loadwordimem r8, @weekday;           # weekday in index, 0 for Mon
mulint r8, r8, r6;                   # r8 *= 3; offset of the word from charTable

loadwordi r2, @charTable;            # where is the character table
loadbytei r4, 4;
mulint r2, r2, r4;                   # r2 *= 4
addint r2, r2, r8;                   # r2 <- memAddr to current character
inc r2;
loadbyte r2, r2, r7;                 # dereference r2

addint r2, r2, r3;                   # r2 += r3; current glyph data to draw by memcpy

loadbytei r4, 10;                    # height of the font, also loop counter
loadhwordi r6, 356;                  # where to print

## loop
:loop_print_single_char_for_weekname2;
cmp r4, r7;
jz @end_of_loop_print_single_char_for_weekname2;
memcpy r1, r2, r6;
dec r4; addint r6, r6, r5; inc r2;   # r4--; r6 += 11, r2++
jmp @loop_print_single_char_for_weekname2;

:end_of_loop_print_single_char_for_weekname2;

########################################################################################################################

## pre-loop
loadbytei r6, 3;                     # length of each string
loadwordimem r8, @weekday;           # weekday in index, 0 for Mon
mulint r8, r8, r6;                   # r8 *= 3; offset of the word from charTable

loadwordi r2, @charTable;            # where is the character table
loadbytei r4, 4;
mulint r2, r2, r4;                   # r2 *= 4
addint r2, r2, r8;                   # r2 <- memAddr to current character
inc r2; inc r2;
loadbyte r2, r2, r7;                 # dereference r2

addint r2, r2, r3;                   # r2 += r3; current glyph data to draw by memcpy

loadbytei r4, 10;                    # height of the font, also loop counter
loadhwordi r6, 357;                  # where to print

## loop
:loop_print_single_char_for_weekname3;
cmp r4, r7;
jz @end_of_loop_print_single_char_for_weekname3;
memcpy r1, r2, r6;
dec r4; addint r6, r6, r5; inc r2;   # r4--; r6 += 11, r2++
jmp @loop_print_single_char_for_weekname3;

:end_of_loop_print_single_char_for_weekname3;


##################
## DRAW A COLON ##
##################


## pre-loop
loadwordi r1, 00010300h;             # memcpy -- length: 1; destination: 3; source: 0
loadwordi r3, @clockColon;           # copy from
loadbytei r2, 2;                     # constant 2
shl r3, r3, r2;                      # multiply r3 by four (label is offset but MEMCPY expects actual address
loadbytei r2, 1;                     # constant 1
loadhwordi r4, 577; loadbytei r6, 4; # copy to (x- and y-position of the image; 1 == 8 px horizontally)
subint r3, r3, r2; subint r4, r4, r5;# r3 += 1, r4 += 11; pre-subtract for loop

## loop
:loop0;
cmp r6, r7;
jz @end_of_loop0;
addint r3, r3, r2; addint r4, r4, r5;# r3 += 1, r4 += 11
memcpy r1, r3, r4;
dec r6;
jmp @loop0;
:end_of_loop0;

## pre-loop
loadwordi r3, @clockColon;           # copy from
loadbytei r2, 2;                     # constant 2
shl r3, r3, r2;                      # multiply r3 by four (label is offset but MEMCPY expects actual address
loadbytei r2, 1;                     # constant 1
loadhwordi r4, 709; loadbytei r6, 4; # copy to (x- and y-position of the image; 1 == 8 px horizontally)
subint r3, r3, r2; subint r4, r4, r5;# r3 += 1, r4 += 11; pre-subtract for loop

## loop
:loop00;
cmp r6, r7;
jz @end_of_loop00;
addint r3, r3, r2; addint r4, r4, r5;# r3 += 1, r4 += 11
memcpy r1, r3, r4;
dec r6;
jmp @loop00;
:end_of_loop00;


loadbytei r2, 2;                     # constant 2
loadwordi r1, 00020300h;             # memcpy -- length: 2; destination: 3; source: 0



##################
## DRAW A CLOCK ##
##################

########################################################################################################################

## pre-loop
loadwordi r3, @clock0lsb;            # copy from
shl r3, r3, r2;                      # multiply r3 by four (label is offset but MEMCPY expects actual address
loadhwordi r4, 529; loadbytei r6, 24;# copy to (x- and y-position of the image; 1 == 8 px horizontally)
subint r3, r3, r2; subint r4, r4, r5;# r3 += 2, r4 += 11; pre-subtract for loop

## loop
:loop1;
cmp r6, r7;
jz @end_of_loop1;
addint r3, r3, r2; addint r4, r4, r5;# r3 += 2, r4 += 11
memcpy r1, r3, r4;
dec r6;
jmp @loop1;
:end_of_loop1;

########################################################################################################################

## pre-loop
loadwordi r3, @clock6lsb;            # copy from
shl r3, r3, r2;                      # multiply r3 by four (label is offset but MEMCPY expects actual address
loadhwordi r4, 531; loadbytei r6, 24;# copy to (x- and y-position of the image; 1 == 8 px horizontally)
subint r3, r3, r2; subint r4, r4, r5;# r3 += 2, r4 += 11; pre-subtract for loop

## loop
:loop2;
cmp r6, r7;
jz @end_of_loop2;
addint r3, r3, r2; addint r4, r4, r5;# r3 += 2, r4 += 11
memcpy r1, r3, r4;
dec r6;
jmp @loop2;
:end_of_loop2;

########################################################################################################################

## pre-loop
loadwordi r3, @clock4lsb;            # copy from
shl r3, r3, r2;                      # multiply r3 by four (label is offset but MEMCPY expects actual address
loadhwordi r4, 534; loadbytei r6, 24;# copy to (x- and y-position of the image; 1 == 8 px horizontally)
subint r3, r3, r2; subint r4, r4, r5;# r3 += 2, r4 += 11; pre-subtract for loop

## loop
:loop3;
cmp r6, r7;
jz @end_of_loop3;
addint r3, r3, r2; addint r4, r4, r5;# r3 += 2, r4 += 11
memcpy r1, r3, r4;
dec r6;
jmp @loop3;
:end_of_loop3;

########################################################################################################################

## pre-loop
loadwordi r3, @clock7lsb;            # copy from
shl r3, r3, r2;                      # multiply r3 by four (label is offset but MEMCPY expects actual address
loadhwordi r4, 536; loadbytei r6, 24;# copy to (x- and y-position of the image; 1 == 8 px horizontally)
subint r3, r3, r2; subint r4, r4, r5;# r3 += 2, r4 += 11; pre-subtract for loop

## loop
:loop4;
cmp r6, r7;
jz @end_of_loop4;
addint r3, r3, r2; addint r4, r4, r5;# r3 += 2, r4 += 11
memcpy r1, r3, r4;
dec r6;
jmp @loop4;
:end_of_loop4;

########################################################################################################################

nop;

        """)


        println("[PoketchTest] New program size: ${program.bytes.size} bytes")


        vm.delayInMills = vmDelay
        vm.loadProgram(program)



        memvwr = Memvwr(vm)


        Gdx.input.inputProcessor = TVMInputProcessor(vm)


        vmThread = Thread(vm)
        vmThread.start()

    }

    private val height: Int; get() = Gdx.graphics.height

    private val lcdOffX = 0f
    private val lcdOffY = 0f

    override fun render() {
        Gdx.graphics.setTitle("Pokétch Test — F: ${Gdx.graphics.framesPerSecond}")


        //vm.pauseExec()


        memvwr.update()



        (peripheral as Poketch).renderToFrameBuffer()
        val poketchTex = Texture((peripheral as Poketch).framebuffer)
        poketchTex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)

        batch.inUse {
            batch.color = Color.WHITE
            batch.draw(poketchTex, 0f, 0f)
            batch.draw(poketchTex, poketchTex.width + 4f, 0f, poketchTex.width * 4f, poketchTex.height * 4f)
        }


        poketchTex.dispose()

        //vm.resumeExec()
    }

    override fun dispose() {
        peripheral.dispose()
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
            (vm.peripherals[TerranVM.IRQ_KEYBOARD] as? GdxPeripheralWrapper)?.keyTyped(p0)
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

fun main(args: Array<String>) {
    val config = LwjglApplicationConfiguration()
    config.width  = 88 * 5 + 4
    config.height = 80 * 4
    config.foregroundFPS = 0
    config.resizable = false

    LwjglApplication(PoketchTest(), config)
}