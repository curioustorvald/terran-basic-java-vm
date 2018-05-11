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
        val vmDelay = 10


        batch = SpriteBatch()

        peripheral = Poketch()

        vm = TerranVM(2048, stdout = peripheral.printStream)

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

bytes numberOffsetX, 8, 24, 48, 64;
bytes otherOffsets, 40, 48;          # colon offset X, clock offset Y
int   timestamp, 0;



.code;
loadhwordi r1, 1001h;                #
call r1, 3;                          # clear screen

loadbytei r1, 0;                     #
call r1, 2;                          # r1 <- current UNIX timestamp (lower 32 bits)
storewordimem r1, @timestamp;


## test write '0'
loadbytei r5, 11;                    # width of scanline in bytes
loadbytei r7, 0;                     # const 0, used for loops


##################
## DRAW A COLON ##
##################




## pre-loop
loadwordi r1, 00010300h;             # memcpy -- length: 2; destination: 3; source: 0
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