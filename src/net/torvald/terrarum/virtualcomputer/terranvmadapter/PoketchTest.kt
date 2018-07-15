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

        vm = TerranVM(2048, stdout = peripheral.printStream, doNotInstallInterrupts = true)

        //vm.peripherals[TerranVM.IRQ_KEYBOARD] = KeyboardAbstraction(vm)
        vm.peripherals[TerranVM.IRQ_RTC] = PeriRTC(vm)
        vm.peripherals[TerranVM.IRQ_PRIMARY_DISPLAY] = peripheral


        val assembler = Assembler(vm)


        val program = assembler("""
.stack; 0;

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

bytes numbers_0_9_LSB,
E1h,C0h,CCh,CCh,CCh,CCh,CCh,CCh,C0h,E1h, # 0
DFh,CFh,CFh,CFh,CFh,CFh,CFh,CFh,CFh,CFh, # 1
E0h,C0h,CFh,CFh,C1h,E0h,FCh,FCh,C0h,C0h, # 2
E0h,C0h,CFh,CFh,C1h,C1h,CFh,CFh,C0h,E0h, # 3
C3h,C1h,C8h,CCh,C0h,C0h,CFh,CFh,CFh,CFh, # 4
C0h,C0h,FCh,E0h,C0h,CFh,CFh,CFh,C0h,E0h, # 5
E1h,E0h,FCh,FCh,E0h,C0h,CCh,CCh,C0h,E1h, # 6
C0h,C0h,CFh,E7h,E7h,E7h,F3h,F3h,F3h,F3h, # 7
E1h,C0h,CCh,CCh,C0h,C0h,CCh,CCh,C0h,E1h, # 8
E1h,C0h,CCh,CCh,C0h,C1h,CFh,CFh,C1h,E1h, # 9
C1h,E0h; # dash

bytes celcius_LSB,
18h,50h,13h,F3h,F3h,F3h,F3h,F3h,F0h,F8h,
3Fh,3Fh,FFh,FFh,FFh,FFh,FFh,FFh,3Fh,3Fh;

bytes fahrenheit_LSB,
10h,50h,13h,F3h,F0h,F0h,F3h,F3h,F3h,F3h,
3Fh,3Fh,FFh,FFh,7Fh,7Fh,FFh,FFh,FFh,FFh;

bytes dash,
FFh,FFh,FFh,FFh,83h,07h,FFh,FFh,FFh,FFh;

bytes ADEFILMNORSTUVY,
E1h,C0h,CCh,CCh,C0h,C0h,CCh,CCh,CCh,CCh, # A
E0h,C0h,CCh,CCh,CCh,CCh,CCh,CCh,C0h,E0h, # D
C0h,C0h,FCh,FCh,E0h,E0h,FCh,FCh,C0h,C0h, # E
C0h,C0h,FCh,FCh,E0h,E0h,FCh,FCh,FCh,FCh, # F
C0h,C0h,F3h,F3h,F3h,F3h,F3h,F3h,C0h,C0h, # I
FCh,FCh,FCh,FCh,FCh,FCh,FCh,FCh,C0h,C0h, # L
DEh,CCh,C0h,C0h,CCh,CCh,CCh,CCh,CCh,CCh, # M
CEh,CCh,C8h,C0h,C0h,C4h,CCh,CCh,CCh,CCh, # N
E1h,C0h,CCh,CCh,CCh,CCh,CCh,CCh,C0h,E1h, # O
E0h,C0h,CCh,CCh,C0h,E0h,C4h,CCh,CCh,CCh, # R
C1h,C0h,FCh,FCh,E0h,C1h,CFh,CFh,C0h,E0h, # S
C0h,C0h,F3h,F3h,F3h,F3h,F3h,F3h,F3h,F3h, # T
CCh,CCh,CCh,CCh,CCh,CCh,CCh,CCh,C0h,E1h, # U
CCh,CCh,CCh,CCh,CCh,CCh,C0h,E1h,E1h,F3h, # V
CCh,CCh,CCh,CCh,C0h,E1h,F3h,F3h,F3h,F3h; # Y

bytes charTable,
 60, 80, 70, #### Mon
110,140,100, #### Tys
 60, 40, 10, #### Mid
110, 80, 90, #### Tor
 30, 90, 20, #### Fre
 50,  0,120, #### Lau
100,120, 70, #### Sun
130  20  90; #### Ver

int months, 0;
int weekday, 0;
int days, 0;
int hours, 0;
int minutes, 0;

.code;


loadbytei r7, 0;                     # conmon const 0, used for loops
loadbytei r5, 11;                    # common const; width of scanline in bytes


loadhwordi r1, 1001h;                #
call r1, 3;                          # clear screen



##################
## DRAW A COLON ##
##################


## pre-loop
loadbytei r1, 132;                   # constant 132
loadbytei r3, C3h;                   # inline glyph for colon
loadbytei r2, 3;                     # constant 3
loadhwordi r4, 577; loadbytei r6, 4; # copy to (x- and y-position of the image; 1 == 8 px horizontally)

## loop
:loop0;
cmp r6, r7;
jz @end_of_loop0;
storebyte r3, r4, r2;                # directly write to peri
addint r4, r4, r1;
storebyte r3, r4, r2;                # directly write to peri
subint r4, r4, r1;

addint r4, r4, r5;                   # r4 += 11
dec r6;                              # r6--
jmp @loop0;
:end_of_loop0;


:clock_loop;


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
mulint r2, r2, r4;                   # r2 *= 4
addint r2, r2, r8;                   # r2 <- memAddr to current character
# add 1 to advance to the next char
loadbyte r2, r2, r7;                 # dereference r2

addint r2, r2, r3;                   # r2 += r3; current glyph data to draw by memcpy

loadbytei r8, 10;                    # height of the font, also loop counter
loadhwordi r6, 355;                  # where to print

## loop
:loop_print_single_char_for_weekname1;
cmp r8, r7;
jz @end_of_loop_print_single_char_for_weekname1;
memcpy r1, r2, r6;
dec r8; addint r6, r6, r5; inc r2;   # r8--; r6 += 11, r2++
jmp @loop_print_single_char_for_weekname1;

:end_of_loop_print_single_char_for_weekname1;

########################################################################################################################

## pre-loop
loadbytei r6, 3;                     # length of each string
loadwordimem r8, @weekday;           # weekday in index, 0 for Mon
mulint r8, r8, r6;                   # r8 *= 3; offset of the word from charTable

loadwordi r2, @charTable;            # where is the character table
mulint r2, r2, r4;                   # r2 *= 4
addint r2, r2, r8;                   # r2 <- memAddr to current character
inc r2;
loadbyte r2, r2, r7;                 # dereference r2

addint r2, r2, r3;                   # r2 += r3; current glyph data to draw by memcpy

loadbytei r8, 10;                    # height of the font, also loop counter
loadhwordi r6, 356;                  # where to print

## loop
:loop_print_single_char_for_weekname2;
cmp r8, r7;
jz @end_of_loop_print_single_char_for_weekname2;
memcpy r1, r2, r6;
dec r8; addint r6, r6, r5; inc r2;   # r8--; r6 += 11, r2++
jmp @loop_print_single_char_for_weekname2;

:end_of_loop_print_single_char_for_weekname2;

########################################################################################################################

## pre-loop
loadbytei r6, 3;                     # length of each string
loadwordimem r8, @weekday;           # weekday in index, 0 for Mon
mulint r8, r8, r6;                   # r8 *= 3; offset of the word from charTable

loadwordi r2, @charTable;            # where is the character table
mulint r2, r2, r4;                   # r2 *= 4
addint r2, r2, r8;                   # r2 <- memAddr to current character
inc r2; inc r2;
loadbyte r2, r2, r7;                 # dereference r2

addint r2, r2, r3;                   # r2 += r3; current glyph data to draw by memcpy

loadbytei r8, 10;                    # height of the font, also loop counter
loadhwordi r6, 357;                  # where to print

## loop
:loop_print_single_char_for_weekname3;
cmp r8, r7;
jz @end_of_loop_print_single_char_for_weekname3;
memcpy r1, r2, r6;
dec r8; addint r6, r6, r5; inc r2;   # r8--; r6 += 11, r2++
jmp @loop_print_single_char_for_weekname3;

:end_of_loop_print_single_char_for_weekname3;


#########################
## DRAW MONTH AND DATE ##
#########################

loadwordi r1, 00010300h;             # memcpy -- length: 1; destination: 3; source: 0


## pre-loop
loadwordimem r8, @months;            # r8 <- months
loadbytei r6, 10;                    #
divint r8, r8, r6;                   # r8 <- (months) / 10
mulint r8, r8, r6;                   # r8 *= 10 to get the right glyph (i mean, offset)

cmp r8, r7;
jz @end_of_loop_print_single_char_for_mmdd1; # do not print if month 1-9

loadwordi r2, @numbers_0_9_LSB;      # where is the font
mulint r2, r2, r4;                   # r2 *= 4
addint r2, r2, r8;                   # r2 <- memAddr to current character

loadbytei r8, 10;                    # height of the font, also loop counter
loadhwordi r6, 358;                  # where to print

## loop
:loop_print_single_char_for_mmdd1;
cmp r8, r7;
jz @end_of_loop_print_single_char_for_mmdd1;
memcpy r1, r2, r6;
dec r8; addint r6, r6, r5; inc r2;   # r8--; r6 += 11, r2++
jmp @loop_print_single_char_for_mmdd1;

:end_of_loop_print_single_char_for_mmdd1;

########################################################################################################################

## pre-loop
loadwordimem r8, @months;            # r8 <- months
loadbytei r6, 10;                    #
modint r8, r8, r6;                   # r8 <- (months) % 10
mulint r8, r8, r6;                   # r8 *= 10 to get the right glyph (i mean, offset)


loadwordi r2, @numbers_0_9_LSB;      # where is the font
mulint r2, r2, r4;                   # r2 *= 4
addint r2, r2, r8;                   # r2 <- memAddr to current character

loadbytei r8, 10;                    # height of the font, also loop counter
loadhwordi r6, 359;                  # where to print

## loop
:loop_print_single_char_for_mmdd2;
cmp r8, r7;
jz @end_of_loop_print_single_char_for_mmdd2;
memcpy r1, r2, r6;
dec r8; addint r6, r6, r5; inc r2;   # r8--; r6 += 11, r2++
jmp @loop_print_single_char_for_mmdd2;

:end_of_loop_print_single_char_for_mmdd2;

########################################################################################################################

## pre-loop
loadwordi r8, 100;

loadwordi r2, @numbers_0_9_LSB;      # where is the font
mulint r2, r2, r4;                   # r2 *= 4
addint r2, r2, r8;                   # r2 <- memAddr to current character

loadbytei r8, 2;                     # height of the font, also loop counter
loadhwordi r6, 393;                  # where to print

## loop
:loop_print_single_char_for_mmdd3;
cmp r8, r7;
jz @end_of_loop_print_single_char_for_mmdd3;
memcpy r1, r2, r6;
dec r8; addint r6, r6, r5; inc r2;   # r8--; r6 += 11, r2++
jmp @loop_print_single_char_for_mmdd3;

:end_of_loop_print_single_char_for_mmdd3;

########################################################################################################################

## pre-loop
loadwordimem r8, @days;              # r8 <- days
loadbytei r6, 10;                    #
divint r8, r8, r6;                   # r8 <- (days) / 10
mulint r8, r8, r6;                   # r8 *= 10 to get the right glyph (i mean, offset)


loadwordi r2, @numbers_0_9_LSB;      # where is the font
mulint r2, r2, r4;                   # r2 *= 4
addint r2, r2, r8;                   # r2 <- memAddr to current character

loadbytei r8, 10;                    # height of the font, also loop counter
loadhwordi r6, 361;                  # where to print

## loop
:loop_print_single_char_for_mmdd4;
cmp r8, r7;
jz @end_of_loop_print_single_char_for_mmdd4;
memcpy r1, r2, r6;
dec r8; addint r6, r6, r5; inc r2;   # r8--; r6 += 11, r2++
jmp @loop_print_single_char_for_mmdd4;

:end_of_loop_print_single_char_for_mmdd4;

########################################################################################################################

## pre-loop
loadwordimem r8, @days;              # r8 <- days
loadbytei r6, 10;                    #
modint r8, r8, r6;                   # r8 <- (days) % 10
mulint r8, r8, r6;                   # r8 *= 10 to get the right glyph (i mean, offset)


loadwordi r2, @numbers_0_9_LSB;      # where is the font
mulint r2, r2, r4;                   # r2 *= 4
addint r2, r2, r8;                   # r2 <- memAddr to current character

loadbytei r8, 10;                    # height of the font, also loop counter
loadhwordi r6, 362;                  # where to print

## loop
:loop_print_single_char_for_mmdd5;
cmp r8, r7;
jz @end_of_loop_print_single_char_for_mmdd5;
memcpy r1, r2, r6;
dec r8; addint r6, r6, r5; inc r2;   # r8--; r6 += 11, r2++
jmp @loop_print_single_char_for_mmdd5;

:end_of_loop_print_single_char_for_mmdd5;


##################
## DRAW A CLOCK ##
##################

loadbytei r2, 2;                     # constant 2
loadwordi r1, 00020300h;             # memcpy -- length: 2; destination: 3; source: 0

########################################################################################################################

## pre-loop
loadwordimem r8, @hours;             # r8 <- hours
loadbytei r6, 10;                    #
divint r8, r8, r6;                   # r8 <- (hours) / 10

loadbytei r6, 48;                    # size of glyph in bytes
mulint r8, r6, r8;                   # r8 <- offset from clock0lsb

loadwordi r3, @clock0lsb;            # get actual bytes from address (clock0lsb + offset)
shl r3, r3, r2;                      # multiply r3 by four (label is offset but MEMCPY expects actual address
addint r3, r3, r8;                   # move the cursor (r3) to the starting position

loadhwordi r4, 529; loadbytei r6, 24;# copy to (x- and y-position of the image; 1 == 8 px horizontally)

## loop
:loop1;
cmp r6, r7;
jz @end_of_loop1;
memcpy r1, r3, r4;
addint r3, r3, r2; addint r4, r4, r5;# r3 += 2, r4 += 11
dec r6;
jmp @loop1;
:end_of_loop1;

########################################################################################################################

## pre-loop
loadwordimem r8, @hours;             # r8 <- hours
loadbytei r6, 10;                    #
modint r8, r8, r6;                   # r8 <- (hours) % 10

loadbytei r6, 48;                    # size of glyph in bytes
mulint r8, r6, r8;                   # r8 <- offset from clock0lsb

loadwordi r3, @clock0lsb;            # get actual bytes from address (clock0lsb + offset)
shl r3, r3, r2;                      # multiply r3 by four (label is offset but MEMCPY expects actual address
addint r3, r3, r8;                   # move the cursor (r3) to the starting position

loadhwordi r4, 531; loadbytei r6, 24;# copy to (x- and y-position of the image; 1 == 8 px horizontally)

## loop
:loop2;
cmp r6, r7;
jz @end_of_loop2;
memcpy r1, r3, r4;
addint r3, r3, r2; addint r4, r4, r5;# r3 += 2, r4 += 11
dec r6;
jmp @loop2;
:end_of_loop2;

########################################################################################################################

## pre-loop
loadwordimem r8, @minutes;           # r8 <- minutes
loadbytei r6, 10;                    #
divint r8, r8, r6;                   # r8 <- (minutes) / 10

loadbytei r6, 48;                    # size of glyph in bytes
mulint r8, r6, r8;                   # r8 <- offset from clock0lsb

loadwordi r3, @clock0lsb;            # get actual bytes from address (clock0lsb + offset)
shl r3, r3, r2;                      # multiply r3 by four (label is offset but MEMCPY expects actual address
addint r3, r3, r8;                   # move the cursor (r3) to the starting position

loadhwordi r4, 534; loadbytei r6, 24;# copy to (x- and y-position of the image; 1 == 8 px horizontally)

## loop
:loop3;
cmp r6, r7;
jz @end_of_loop3;
memcpy r1, r3, r4;
addint r3, r3, r2; addint r4, r4, r5;# r3 += 2, r4 += 11
dec r6;
jmp @loop3;
:end_of_loop3;

########################################################################################################################

## pre-loop
loadwordimem r8, @minutes;           # r8 <- minutes
loadbytei r6, 10;                    #
modint r8, r8, r6;                   # r8 <- (minutes) % 10

loadbytei r6, 48;                    # size of glyph in bytes
mulint r8, r6, r8;                   # r8 <- offset from clock0lsb

loadwordi r3, @clock0lsb;            # get actual bytes from address (clock0lsb + offset)
shl r3, r3, r2;                      # multiply r3 by four (label is offset but MEMCPY expects actual address
addint r3, r3, r8;                   # move the cursor (r3) to the starting position

loadhwordi r4, 536; loadbytei r6, 24;# copy to (x- and y-position of the image; 1 == 8 px horizontally)

## loop
:loop4;
cmp r6, r7;
jz @end_of_loop4;
memcpy r1, r3, r4;
addint r3, r3, r2; addint r4, r4, r5;# r3 += 2, r4 += 11
dec r6;
jmp @loop4;
:end_of_loop4;

########################################################################################################################

# yield;
jmp @clock_loop;

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