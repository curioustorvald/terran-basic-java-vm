package net.torvald.tbasic

import net.torvald.tbasic.runtime.*

/**
 * Created by minjaesong on 2017-05-25.
 */
fun main(args: Array<String>) {
    Executable().main()
}

class Executable {

    val vm = VM(256)


    /*val rudimentaryHello = TBASOpcodeAssembler("""# Hello world on TBASASM using in-line strings
    LOADSTRINLINE 1, Helvetti world! #
;                              # String with \n
PRINTSTR;
LOADSTRINLINE 1, wut;                # String without \n
PRINTSTR;
LOADSTRINLINE 1, face                #
;                              # String with \n
PRINTSTR;
""")*/

    val testProgram = TBASOpcodeAssembler("""# Hello world on TBASASM using data section
.data;
    string hai, Helvetti world!
;
.code;
    LOADPTR 1, @hai;
    PRINTSTR;
""")

    val testLoop = TBASOpcodeAssembler("""# Power of twos
.code; # when there's no other sections, this section marker is optional.

LOADMNUM 10;
LOADNUM 1, 1.0;

PRINTNUM;
MOV 1, 4;   # append \n
LOADSTRINLINE 1,  #
;           #
PRINTSTR;   #
MOV 4, 1;   # END append \n

LOADNUM 2, 2.0;
MOV 1, 3;
MUL;

DECM;

JNZ 47;

LOADSTRINLINE 1, You are terminated
;
PRINTSTR;
""")

    fun main() {
        //testProgram.forEach { print("$it ") }

        vm.loadProgram(testLoop)
        (0..255).forEach { print("${vm.memory[it].toUint()} ") }; println()
        vm.execute()
        //(0..255).forEach { print("${vm.memory[it]} ") }; println()
    }


    private fun Int.KB() = this shl 10
    private fun Int.MB() = this shl 20
    private fun Byte.toUint() = java.lang.Byte.toUnsignedInt(this)

}