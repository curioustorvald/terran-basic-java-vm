package net.torvald.terranvm.runtime.compiler.cflat

import net.torvald.terranvm.runtime.to8HexString
import net.torvald.terranvm.runtime.toHexString


/**
 * Compilation step:
 *
 *     User Input -> Tree Representation -> IR1 -> IR2 -> ASM => Fed into Assembler
 *
 * Reference:
 * - *Compiler Design: Virtual Machines*, Reinhard Wilhelm and Helmut Seidi, 2011, ISBN 3642149081
 */
object NewCompiler {


    val testProgram = """

    """.trimIndent()

    // lexer and parser goes here //


    /**
     * An example of (3 + 4) * (7 - 2).
     *
     * This is also precisely what **IR1** is supposed to be.
     * Invoke this "Function" and you will get a string, which is **IR2**.
     * IR2 is
     */
    val TEST_CODE: () -> CodeR = { { MULINT(0, { ADDINT(0, { LIT(0, 3) }, { LIT(0, 4) }) }, { SUBINT(0, { LIT(0, 7) }, { LIT(0, 2) }) }) } }

    /**
     * If (3) 7200 Else 5415; 3 + 4; 7 * 2
     */
    val TEST_CODE2 = {
        {
            IFELSE(1,
                { LIT(1, 3) }, // cond
                { LIT(2, 7200) }, // true
                { LIT(4, 5415) }  // false
            )
        } bind { // AM I >>=ing right ?
            ADDINT(6, { LIT(6, 3) }, { LIT(6, 4) })
        } bind {
            MULINT(7, { LIT(7, 7) }, { LIT(7, 2) })
        }

    }

    /**
     * var a = (b + (b * c)); // a |> 5; b |> 6; c |> 7
     *
     * Expected output: loadc 6; load; loadc 6; load; ... -- as per our reference book
     */
    val TEST_VARS = {
        // function for variable address
        val aTable = hashMapOf("a" to 5, "b" to 6, "c" to 7)
        val aenv: Rho = { aTable[it]!! }

        // the actual code
        {
            ASSIGN(1, {
                VAR_L(1, "a", aenv)
            }, {
                ADDINT(1, {
                    VAR_R(1, "b", aenv)
                }, {
                    MULINT(1, {
                        VAR_R(1, "b", aenv)
                    }, {
                        VAR_R(1, "c", aenv)
                    })
                })
            }, aenv
            )
        }
    }

    /** Shitty >>= (toilet plunger) */
    private infix fun (CodeR).bind(other: CodeR) = sequenceOf(this, other)
    private infix fun (Sequence<CodeR>).bind(other: CodeR) = this + other


    // TREE TO IR1

    fun toIR1(tree: Cflat.SyntaxTreeNode): CodeR {
        val parentCode: CodeR = { "" }


        // TODO recursion is a key
        /*if (tree.expressionType == Cflat.ExpressionType.FUNCTION_CALL) {
            if (tree.name == "+") {
                parentCode += { ADDINT(tree.lineNumber, {
                    toIR1(tree.arguments[0])
                }, {
                    toIR1(tree.arguments[1])
                }) }
            }
        }*/

        return { "null" }
    }











    // These are the definition of IR1. Invoke this "function" and string will come out, which is IR2.
    // l: Int means Line Number

    /** e1 ; e2 ; addint ; */
    object ADDINT {
        operator fun invoke(l: Int, e1: CodeR, e2: CodeR) = e1() + e2() + "ADDINT;"
    }
    object SUBINT {
        operator fun invoke(l: Int, e1: CodeR, e2: CodeR) = e1() + e2() + "SUBINT;"
    }
    object MULINT {
        operator fun invoke(l: Int, e1: CodeR, e2: CodeR) = e1() + e2() + "MULINT;"
    }
    object DIVINT {
        operator fun invoke(l: Int, e1: CodeR, e2: CodeR) = e1() + e2() + "DIVINT;"
    }
    object NEG {
        operator fun invoke(l: Int, e: CodeR) = e() + "DIVINT;"
    }
    /** loadconst ; literal ; */
    object LIT {
        operator fun invoke(l: Int, e: Int) = "LOADCONST ${e.to8HexString()};" // always ends with 'h'
        operator fun invoke(l: Int, e: Float) = "LOADCONST ${e}f;" // always ends with 'f'
    }
    /** address(int) ; value(word) ; store ; */
    object ASSIGN {
        operator fun invoke(l: Int, e1: CodeL, e2: CodeR, aenv: Rho) = e2() + e1(aenv) + "STORE;"
    }
    /** ( address -- value stored in that address ) */ // this is Forth stack notation
    object VAR_R {
        operator fun invoke(l: Int, varname: String, aenv: Rho) = "LOADCONST ${aenv(varname)};LOAD;"
    }
    /** ( address -- ); memory gets changed */
    object VAR_L {
        operator fun invoke(l: Int, varname: String, aenv: Rho) = "LOADCONST ${aenv(varname)};" // no need for extra store; handled by ASSIGN
    }
    object JUMP {
        operator fun invoke(l: Int, newPC: CodeR) = "JUMP $newPC;"
    }
    object JUMPZ { // zero means FALSE !!
        operator fun invoke(l: Int, newPC: CodeR) = "JUMPZ $newPC;"
    }
    object JUMPNZ {
        operator fun invoke(l: Int, newPC: CodeR) = "JUMPNZ $newPC;"
    }
    object IF {
        operator fun invoke(l: Int, cond: CodeR, invokeTrue: CodeR) =
                cond() + "JUMPZ ${labelFalse(l, cond)};" + invokeTrue() + "LABEL ${labelFalse(l, cond)};"
    }
    object IFELSE {
        operator fun invoke(l: Int, cond: CodeR, invokeTrue: CodeR, invokeFalse: CodeR) =
                cond() + "JUMPZ ${labelFalse(l, cond)};" + invokeTrue() + "JUMP ${labelThen(l, cond)};" +
                        "LABEL ${labelFalse(l, cond)};" + invokeFalse() + "LABEL ${labelThen(l, cond)};"
    }
    object WHILE {
        operator fun invoke(l: Int, cond: CodeR, invokeWhile: CodeR) =
                "LABEL ${labelWhile(l, cond)};" +
                        cond() + "JUMPZ ${labelThen(l, cond)};" +
                        invokeWhile() + "JUMP ${labelWhile(l, cond)};" +
                        "LABEL ${labelThen(l, cond)};"
    }
    object FOR {
        // TODO  for ( e1 ; e2 ; e3 ) s' === e1 ; while ( e2 ) { s' ; e3 ; }
    }


    private fun labelUnit(lineNum: Int, code: CodeR): IR2 {
        return "\$LN${lineNum}_${code.hashCode().toHexString().dropLast(1)}" // hopefully there'll be no hash collision...
    }
    private fun labelFalse(lineNum: Int, code: CodeR) = labelUnit(lineNum, code) + "_FALSE"
    private fun labelThen(lineNum: Int, code: CodeR) = labelUnit(lineNum, code) + "_THEN"
    private fun labelWhile(lineNum: Int, code: CodeR) = "\$WHILE_" + labelUnit(lineNum, code).drop(1)

    annotation class CodeLFun
}

// IR1 is the bunch of functions above
typealias IR2 = String
/**
 * Address Environment; a function to map a variable name to its memory address.
 *
 * The address is usually virtual. Can be converted to real address at either (IR2 -> ASM) or (ASM -> Machine) stage.
 */
typealias Rho = (String) -> Int
typealias CodeL = (Rho) -> IR2
typealias CodeR = () -> IR2


fun main(args: Array<String>) {
    val code = NewCompiler.TEST_VARS

    //code.invoke().forEach { println(it.invoke()) } // this is probably not the best sequence comprehension

    println(code.invoke().invoke())
}