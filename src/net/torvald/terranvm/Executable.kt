package net.torvald.terranvm

import net.torvald.terranvm.runtime.*

/**
 * Created by minjaesong on 2017-05-25.
 */
object Executable {

    val vm = TerranVM(2048, tbasic_remove_string_dupes = true)


    val beers = Assembler("""
.data;

string beerone "of beer on the wall, ";

string beertwo "of beer.
Take one down, pass it around; ";

string beerthree "of beer on the wall.

";

string gotostore "of beer.
Go to the store and buy some more, ";

string bottles "bottles ";
string bottle "bottle ";

string omore "o more ";  # Using "omore" because of capitalisation of N. (No more/no more)


.func;

:printspace;
loadnum r1, 32; putchar; return;


:printbeercnt;
loadvariable beercount; peeknum; # r1 <- beercount (dereferenced)
mov r1, r2;
loadnum r3, 1;
cmp;
jz  @printonebeer;
jgt @printbeerasnum;
jls @printnomore;


:printonebeer;
printnum; gosub @printspace;     # beercount + ' '
loadptr r1, @bottle; printstr;   # 'bottle '
return;                          #


:printbeerasnum;
printnum; gosub @printspace;     # beercount + ' '
loadptr r1, @bottles; printstr;  # 'bottles '
return;                          #


:printnomore;
loadnum r1, 110; putchar;        # 'n'
loadptr r1, @omore; printstr;    # 'o more '
loadptr r1, @bottles; printstr;  # 'bottles '
return;                          #


.code;

loadnum r1, 99;                  #
setvariable beercount;           # set beer count
setvariable originalcount;       # set original count


:compare;
loadvariable beercount; peeknum; # r1 <- beercount (dereferenced)
mov r1, r2;                      # r2 <- beercount
loadnum r3, 1;                   # r3 <- 1.0
cmp;                             # m1 <- if (beercount > 1) 1 else if (beercount == 1) 0 else -1


jgt @pluralBeers;                # plural beers
jz  @singularBeers;              # singular beers
jls @noBeers;                    # no beers


:pluralBeers;
gosub @printbeercnt;             # print beer count
loadptr r1, @beerone; printstr;  # 'of beer on the wall, '
gosub @printbeercnt;             # print beer count'
loadptr r1, @beertwo; printstr;  # 'of beer.\nTake one down and pass it around, '

loadvariable beercount; peeknum; # r1 <- beercount (dereferenced)
dec1; setvariable beercount;     # decrement beercount by one


gosub @printbeercnt;             # print beer count
loadptr r1, @beerthree; printstr;# 'of beer on the wall.\n\n'

jmp @compare;



:singularBeers;
gosub @printbeercnt;             # print beer count
loadptr r1, @beerone; printstr;  # 'of beer on the wall, '
gosub @printbeercnt;             # print beer count
loadptr r1, @beertwo; printstr;  # 'of beer.\nTake one down and pass it around, '

loadvariable beercount; peeknum; # r1 <- beercount (dereferenced)
dec1; setvariable beercount;     # decrement beercount by one

gosub @printbeercnt;             # print beer count
loadptr r1, @beerthree; printstr;# 'of beer on the wall.\n\n'

jmp @compare;



:noBeers;
loadnum r1, 78; putchar;         # 'N'
loadptr r1, @omore; printstr;    # 'o more '
loadptr r1, @bottles; printstr;  # 'bottles '
loadptr r1, @beerone; printstr;  # 'of beer on the wall, '
gosub @printbeercnt;             # print beer count ('no more bottles ')
loadptr r1, @gotostore; printstr;# 'of beer.\nGo to the store and buy some more, '

loadvariable originalcount;      # r1 <- originalcount.pointer
peeknum;                         # dereference the pointer
setvariable beercount;           # set beercount to originalcount

gosub @printbeercnt;             # print beer count
loadptr r1, @beerthree; printstr;# 'of beer on the wall.\n\n'

halt;

""")


    val keyboardTest = Assembler("""

# loadstrinline r1, What's your name?
# ; printstr;
#
# readstr; mov r1, 2;
#
# loadstrinline r1, Hello, ; printstr;
# mov r2, r1; printstr;
# loadnum r1, 33; putchar;


getchar;

putchar;putchar;putchar;putchar;putchar;

""")


    val oomtest = Assembler("""
loadnum r1, 99;
loadnum r2, 256;
:loop;
poke;
inc2;
jmp @loop;
""")


    fun main() {
        //testProgram.forEach { print("$it ") }

        vm.loadProgram(beers)

        //(0..511).forEach { print("${vm.memory[it].toUint()} ") }; println()

        vm.delayInMills = 10

        vm.execute()
        //(0..255).forEach { print("${vm.memory[it].toUint()} ") }; println()
    }


    private fun Int.KB() = this shl 10
    private fun Int.MB() = this shl 20
    private fun Byte.toUint() = java.lang.Byte.toUnsignedInt(this)

}

fun main(args: Array<String>) {
    Executable.main()
}
