package net.torvald.tbasic

import net.torvald.tbasic.runtime.*

/**
 * Created by minjaesong on 2017-05-25.
 */
fun main(args: Array<String>) {
    Executable().main()
}

class Executable {

    val vm = VM(2048, BIOS = VMBIOS(), tbasic_remove_string_dupes = true)


    val beers = TBASOpcodeAssembler("""
.data;

string beerone of beer on the wall, ;

string beertwo of beer.
Take one down, passes it around, ;

string beerthree of beer on the wall.

;

string gotostore of beer.
Go to the store and buy some more, ;

string bottles bottles ;
string bottle bottle ;

string omore o more ;


.func;

:printspace;
loadnum 1, 32; putchar; return;


.code;

loadnum 1, 99;                   #
setvariable beercount;           # set beer count


:compare;
loadvariable beercount; peeknum; # r1 <- beercount (deferred)
mov 1, 2;                        # r2 <- beercount
loadnum 3, 1;                    # r3 <- 1.0
cmp;                             # m1 <- if (beercount > 1) 1 else if (beercount == 1) 0 else -1


jgt @pluralBeers;                # plural beers
jz  @singularBeers;              # singular beers
jls @noBeers;                    # no beers


:pluralBeers;
loadvariable beercount; peeknum; # r1 <- beercount (deferred)
printnum; gosub @printspace;     # beercount + ' '
loadptr 1, @bottles; printstr;   # 'bottles '
loadptr 1, @beerone; printstr;   # 'of beer on the wall, '
loadvariable beercount; peeknum; # r1 <- beercount (deferred)
printnum; gosub @printspace;     # beercount + ' '
loadptr 1, @bottles; printstr;   # 'bottles '
loadptr 1, @beertwo; printstr;   # 'of beer.\nTake one down and passes it around, '

loadvariable beercount; peeknum; # r1 <- beercount (deferred)
dec1; setvariable beercount;     # decrement beercount by one


printnum; gosub @printspace;     # beercount + ' '
loadptr 1, @bottles; printstr;   # 'bottles '
loadptr 1, @beerthree; printstr; # 'of beer on the wall.\n\n'

jmp @compare;



:singularBeers;
loadvariable beercount; peeknum; # r1 <- beercount (deferred)
printnum; gosub @printspace;     # beercount + ' '
loadptr 1, @bottle; printstr;    # 'bottle '
loadptr 1, @beerone; printstr;   # 'of beer on the wall, '
loadvariable beercount; peeknum; # r1 <- beercount (deferred)
printnum; gosub @printspace;     # beercount + ' '
loadptr 1, @bottle; printstr;    # 'bottle '
loadptr 1, @beertwo; printstr;   # 'of beer.\nTake one down and passes it around, '

loadvariable beercount; peeknum; # r1 <- beercount (deferred)
dec1; setvariable beercount;     # decrement beercount by one

printnum; gosub @printspace;     # beercount + ' '
loadptr 1, @bottles; printstr;   # 'bottles '
loadptr 1, @beerthree; printstr; # 'of beer on the wall.\n\n'

jmp @compare;



:noBeers;
loadnum 1, 78; putchar;          # 'N'
loadptr 1, @omore; printstr;     # 'o more '
loadptr 1, @bottles; printstr;   # 'bottles '
loadptr 1, @beerone; printstr;   # 'of beer on the wall, '
loadnum 1, 110; putchar;         # 'n'
loadptr 1, @omore; printstr;     # 'o more '
loadptr 1, @bottles; printstr;   # 'bottles '
loadptr 1, @gotostore; printstr; # 'of beer.\nGo to the store and buy some more, '

loadnum 1, 99                    # set beercount to 99
setvariable beercount;           #

printnum; gosub @printspace;     # beercount + ' '
loadptr 1, @bottles; printstr;   # 'bottles '
loadptr 1, @beerthree; printstr; # 'of beer on the wall.\n\n'

halt;

""")


    fun main() {
        //testProgram.forEach { print("$it ") }

        vm.loadProgram(beers)

        //(0..511).forEach { print("${vm.memory[it].toUint()} ") }; println()

        vm.execute()
        //(0..255).forEach { print("${vm.memory[it].toUint()} ") }; println()
    }


    private fun Int.KB() = this shl 10
    private fun Int.MB() = this shl 20
    private fun Byte.toUint() = java.lang.Byte.toUnsignedInt(this)

}