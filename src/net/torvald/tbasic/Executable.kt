package net.torvald.tbasic

import net.torvald.tbasic.runtime.TBASOpcodeAssembler
import net.torvald.tbasic.runtime.VM
import net.torvald.tbasic.runtime.toCString
import net.torvald.tbasic.runtime.toLittle

/**
 * Created by minjaesong on 2017-05-25.
 */
fun main(args: Array<String>) {
    Executable().main()
}

class Executable {

    val vm = VM(256)


    /*val rudimentaryHello = TBASOpcodeAssembler(""";
    LOADSTR 1, Helvetti world!
;                            # String with \n
PRINTSTR;
LOADSTR 1, wut;              # String without \n
PRINTSTR;
LOADSTR 1, face              # String with \n
;
PRINTSTR;
""")*/

    val testProgram = TBASOpcodeAssembler("""# Hello world on TBASASM
.data;
    string hai, Helvetti world!
;
.code;
    LOADPTR 1, @hai;
    PRINTSTR;
""")

    /*val testLoop = TBASOpcodeAssembler(""";
LOADMNUM 10;
LOADNUM 1, 1.0;

PRINTNUM;
MOV 1, 4;   # append \n
LOADSTR 1,  #
;           #
PRINTSTR;   #
MOV 4, 1;   # END append \n

MOV 1, 2;
MOV 1, 3;
MUL;

DECM;

JNZ 47;

LOADSTR 1, Ende;
PRINTSTR;

""")*/

    fun main() {
        //testProgram.forEach { print("$it ") }

        vm.loadProgram(testProgram)
        (0..255).forEach { print("${vm.memory[it]} ") }; println()
        vm.execute()
        //(0..255).forEach { print("${vm.memory[it]} ") }; println()
    }


    private fun Int.KB() = this shl 10
    private fun Int.MB() = this shl 20
}