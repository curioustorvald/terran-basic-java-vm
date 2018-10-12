package net.torvald.terranvm.runtime.compiler.cflat


/**
 * Compilation step:
 *
 *     User Input -> Tree Representation -> IR1 -> IR2 -> ASM => Fed into Assembler
 */
object NewCompiler {



    // lexer and parser goes here //


    /**
     * An example of (3 + 4) * (7 - 2).
     *
     * This is also precisely what **IR1** is supposed to be.
     * Invoke this "Function" and you will get a string, which is **IR2**.
     * IR2 is
     */
    val TEST_CODE = { MULINT({ ADDINT({ LIT(3) }, { LIT(4) }) }, { SUBINT({ LIT(7) }, { LIT(2) }) }) }


    // These are the definition of IR1. Invoke this "function" and string will come out, which is IR2.

    object ADDINT {
        operator fun invoke(e1: CodeR, e2: CodeR) = e1() + e2() + "ADDINT;"
    }
    object SUBINT {
        operator fun invoke(e1: CodeR, e2: CodeR) = e1() + e2() + "SUBINT;"
    }
    object MULINT {
        operator fun invoke(e1: CodeR, e2: CodeR) = e1() + e2() + "MULINT;"
    }
    object DIVINT {
        operator fun invoke(e1: CodeR, e2: CodeR) = e1() + e2() + "DIVINT;"
    }
    object LIT {
        operator fun invoke(e: Int) = "LOADCONST $e;"
        //operator fun invoke(e: Float) = "LOADCONST $e;"
    }
    object ASSIGN {
        operator fun invoke(e1: CodeL, e2: CodeR, aenv: Rho) = e1(aenv) + e2() + "ASSIGN;"
    }
    object NEWVAR {
        //
    }
    object JUMP {
        operator fun invoke(newPC: CodeR) = "JMP $newPC;"
    }


}

typealias Rho = HashMap<String, Int>
typealias CodeL = (Rho) -> String
typealias CodeR = () -> String


fun main(args: Array<String>) {
    val code = NewCompiler.TEST_CODE

    println(code.invoke())
}