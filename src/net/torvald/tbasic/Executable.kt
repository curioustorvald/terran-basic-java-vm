package net.torvald.tbasic

import net.torvald.tbasic.runtime.*

/**
 * Created by minjaesong on 2017-05-25.
 */
fun main(args: Array<String>) {
    Executable().main()
}

class Executable {

    val vm = VM(2.KB(), BIOS = VMBIOS(), tbasic_remove_string_dupes = true)


    /*val rudimentaryHello = TBASOpcodeAssembler("""# Hello world on TBASASM using in-line strings
    loadstrinline 1, Helvetti world! #
;                              # String with \n
printstr;
loadstrinline 1, wut;                # String without \n
printstr;
loadstrinline 1, face                #
;                              # String with \n
printstr;
""")*/

    /*val testProgram = TBASOpcodeAssembler("""# Hello world on TBASASM using data section
.data;
    string hai, Helvetti world!
;
.code;
    LOADPTR 1, @hai;
    PRINTSTR;
""")*/

    /*val testLoop = TBASOpcodeAssembler("""# Power of twos
.data;

string theend, You are terminated
;

.code; # when there's no other sections, this section marker is optional.

loadmnum 32;
loadnum 1, 1.0;

:loopstart;

printnum;
gosub @printnewline;

loadnum 2, 2.0;
mov 1, 3;
mul;

decm;
jnz @loopstart;

loadptr 1, @theend;
printstr;

halt;


:printnewline;
mov 1, 4;       # append \n
loadstrinline 1,#
;               #
printstr;       #
mov 4, 1;       # END append \n
return;
""")*/

    /*val BIOS_POST = TBASOpcodeAssembler("""# Power On Self Test
call 255, 0;                  # load memSize to r1
printnum;                     # print out current memSize

loadnum 1, 32;                #
putchar;                      # print out a space

loadstrinline 1, bytes OK     #
;                             #
printstr;                     # " bytes OK \n"
""")*/

    val funcSectionTest = TBASOpcodeAssembler("""# func section test
.func;
:hai;
loadptr 1, @message;
printstr;
return;


.data;
string message, Helvetti world!
;


.code;
gosub @hai;

""")

    fun main() {
        //testProgram.forEach { print("$it ") }

        vm.loadProgram(funcSectionTest)
        (0..255).forEach { print("${vm.memory[it].toUint()} ") }; println()

        vm.execute()
        //(0..255).forEach { print("${vm.memory[it].toUint()} ") }; println()
    }


    private fun Int.KB() = this shl 10
    private fun Int.MB() = this shl 20
    private fun Byte.toUint() = java.lang.Byte.toUnsignedInt(this)

}