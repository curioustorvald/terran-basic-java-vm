package net.torvald.terranvm.runtime.compiler.cflat

import net.torvald.terranvm.runtime.to8HexString
import net.torvald.terranvm.runtime.toHexString
import net.torvald.terranvm.runtime.compiler.cflat.Cflat.ExpressionType.*
import kotlin.UnsupportedOperationException
import kotlin.collections.HashMap


/**
 * Compilation step:
 *
 *     User Input -> Tree Representation -> IR1 (preorder?) -> IR2 (postorder) -> ASM => Fed into Assembler
 *
 * Reference:
 * - *Compiler Design: Virtual Machines*, Reinhard Wilhelm and Helmut Seidi, 2011, ISBN 3642149081
 */
object NewCompiler {


    val testProgram = """
        int c;
        c = (3 + 4) * (7 - 2);
    """.trimIndent()

    // lexer and parser goes here //


    /**
     * An example of (3 + 4) * (7 - 2).
     *
     * This is also precisely what **IR1** is supposed to be.
     * Invoke this "Function" and you will get a string, which is **IR2**.
     * IR2 is
     */
    val TEST_CODE: () -> CodeR = { { MUL(0, { ADD(0, { LIT(0, 3) }, { LIT(0, 4) }) }, { SUB(0, { LIT(0, 7) }, { LIT(0, 2) }) }) } }

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
            ADD(6, { LIT(6, 3) }, { LIT(6, 4) })
        } bind {
            MUL(7, { LIT(7, 7) }, { LIT(7, 2) })
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
        val aenv: Rho = { name -> aTable[name]!! }

        // the actual code
        {
            ASSIGN(1, {
                VAR_L(1, "a", aenv)
            }, {
                ADD(1, {
                    VAR_R(1, "b", aenv)
                }, {
                    MUL(1, {
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
        //val parentCode: Sequence<CodeR> = sequenceOf( { "" } )
        val variableAddr = HashMap<String, Int>()
        var varCnt = 0
        fun String.toVarName() = "$$this"

        val aenv: Rho = { name ->
            //println("Fetch var ${name.toVarName()}")
            variableAddr[name.toVarName()]!!
        }
        fun addvar(name: String, ln: Int) {
            //println("Add var ${name.toVarName()}")
            variableAddr[name.toVarName()] = varCnt
            varCnt++
        }


        // TODO recursion is a key
        // Traverse order: Self -> Arguments (e1, e2, ...) -> Statements (s)
        fun traverse1(node: Cflat.SyntaxTreeNode) : CodeR {
            val l = node.lineNumber


            // termination conditions //

            if (node.expressionType == LITERAL_LEAF) {
                return when (node.returnType!!) {
                    Cflat.ReturnType.INT -> {{ LIT(l, node.literalValue as Int) }}
                    Cflat.ReturnType.FLOAT -> {{ LIT(l, node.literalValue as Float) }}
                    // TODO Cflat.ReturnType.DATABASE
                    else -> {{ _REM(l, "Unsupported literal with type: ${node.returnType}") }}
                }
            }
            else if (node.expressionType == VARIABLE_READ) {
                addvar(node.name!!, l)
                return { VAR_R(l, node.name!!, aenv) }
            }
            else if (node.expressionType == VARIABLE_WRITE) {
                if (!variableAddr.containsKey(node.name!!)) {
                    throw UnresolvedReference("No such variable defined: ${node.name!!}")
                }
                return { VAR_L(l, node.name!!, aenv) }
            }

            // recursion conditions //

            else {

                return if (node.expressionType == FUNCTION_CALL) {
                    when (node.name) {
                        "+" -> {{ ADD(l, traverse1(node.arguments[0]), traverse1(node.arguments[1])) }}
                        "-" -> {{ SUB(l, traverse1(node.arguments[0]), traverse1(node.arguments[1])) }}
                        "*" -> {{ MUL(l, traverse1(node.arguments[0]), traverse1(node.arguments[1])) }}
                        "/" -> {{ DIV(l, traverse1(node.arguments[0]), traverse1(node.arguments[1])) }}
                        "=" -> {{ ASSIGN(l, { VAR_L(l, node.arguments[0].name!!, aenv) }, traverse1(node.arguments[1]), aenv) }}
                        else -> {{ _REM(l, "Unknown OP or func call is WIP: ${node.name}") }}
                    }
                }
                else if (node.expressionType == INTERNAL_FUNCTION_CALL) {
                    when (node.name) {
                        "#_declarevar" -> {
                            addvar(node.arguments[0].literalValue as String, l)
                            return { NEWVAR(l, node.arguments[1].literalValue as String, node.arguments[0].literalValue as String) }
                        }
                        else -> { return { _REM(l, "Unknown internal function: ${node.name}") }}
                    }
                }
                else if (node.name == Cflat.rootNodeName) {
                    return { _PARSE_HEAD(node.statements.map { traverse1(it) }) }
                }
                else {
                    throw UnsupportedOperationException("Unsupported node:\n[NODE START]\n$node\n[NODE END]")
                }
            }
        }


        return traverse1(tree)
    }











    // These are the definition of IR1. Invoke this "function" and string will come out, which is IR2.
    // l: Int means Line Number

    // screw the CodeL/CodeR typechecking: I CAN'T EVEN

    private fun _REM(l: Int, message: String): IR2 = "\nREM Ln$l : ${message.replace('\n', '$')};\n"
    private fun _PARSE_HEAD(e: List<CodeR>): IR2 = e.fold("") { acc, it -> acc + it.invoke() } + "HALT;\n"
    /** e1 ; e2 ; add ; */
    fun ADD(l: Int, e1: CodeR, e2: CodeR): IR2 = e1() + e2() + "ADD;\n"
    fun SUB(l: Int, e1: CodeR, e2: CodeR) = e1() + e2() + "SUB;\n"
    fun MUL(l: Int, e1: CodeR, e2: CodeR) = e1() + e2() + "MUL;\n"
    fun DIV(l: Int, e1: CodeR, e2: CodeR) = e1() + e2() + "DIV;\n"
    fun NEG(l: Int, e: CodeR) = e() + "NEG;\n"
    /** loadconst ; literal ; */
    fun LIT(l: Int, e: Int) = "LOADCONST ${e.to8HexString()};  # Literal\n" // always ends with 'h'
    fun LIT(l: Int, e: Float) = "LOADCONST ${e}f; # Literal\n" // always ends with 'f'
    /** address(int) ; value(word) ; store ; */
    fun ASSIGN(l: Int, e1: CodeL, e2: CodeR, aenv: Rho) = e2() + e1(aenv) + "STORE;\n"
    /** ( address -- value stored in that address ) */ // this is Forth stack notation
    fun VAR_R(l: Int, varname: String, aenv: Rho) = "LOADCONST ${aenv(varname).toHexString()}; LOAD;  # Read from variable\n" // NOT using 8hexstring is deliberate
    /** ( address -- ); memory gets changed */
    fun VAR_L(l: Int, varname: String, aenv: Rho) = "LOADCONST ${aenv(varname).toHexString()};  # Write to variable, if following command is STORE\n" // NOT using 8hexstring is deliberate; no need for extra store; handled by ASSIGN
    fun NEWVAR(l: Int, type: String, varname: String) = "NEWVAR${type.toUpperCase()} $varname;\n"
    fun JUMP(l: Int, newPC: CodeR) = "JUMP $newPC;\n"
    fun JUMPZ(l: Int, newPC: CodeR) = "JUMPZ $newPC;\n" // zero means false
    fun JUMPNZ(l: Int, newPC: CodeR) = "JUMPNZ $newPC;\n"
    fun IF(l: Int, cond: CodeR, invokeTrue: CodeR) =
            cond() + "JUMPZ ${labelFalse(l, cond)};\n" + invokeTrue() + "LABEL ${labelFalse(l, cond)};\n"
    fun IFELSE(l: Int, cond: CodeR, invokeTrue: CodeR, invokeFalse: CodeR) =
            cond() + "JUMPZ ${labelFalse(l, cond)};\n" + invokeTrue() + "JUMP ${labelThen(l, cond)};\n" +
                    "LABEL ${labelFalse(l, cond)};\n" + invokeFalse() + "LABEL ${labelThen(l, cond)};\n"
    fun WHILE(l: Int, cond: CodeR, invokeWhile: CodeR) =
            "LABEL ${labelWhile(l, cond)};\n" +
                    cond() + "JUMPZ ${labelThen(l, cond)};\n" +
                    invokeWhile() + "JUMP ${labelWhile(l, cond)};\n" +
                    "LABEL ${labelThen(l, cond)};\n"
    //fun FOR
    // TODO  for ( e1 ; e2 ; e3 ) s' === e1 ; while ( e2 ) { s' ; e3 ; }


    private fun labelUnit(lineNum: Int, code: CodeR): IR2 {
        return "\$LN${lineNum}_${code.hashCode().toHexString().dropLast(1)}" // hopefully there'll be no hash collision...
    }
    private fun labelFalse(lineNum: Int, code: CodeR) = labelUnit(lineNum, code) + "_FALSE"
    private fun labelThen(lineNum: Int, code: CodeR) = labelUnit(lineNum, code) + "_THEN"
    private fun labelWhile(lineNum: Int, code: CodeR) = "\$WHILE_" + labelUnit(lineNum, code).drop(1)

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
    val testProg = NewCompiler.testProgram
    val tree = Cflat.buildTree(Cflat.tokenise(testProg))

    println(tree)

    //val code = NewCompiler.TEST_VARS
    val ir1 = NewCompiler.toIR1(tree)
    val ir2 = ir1.invoke()

    //code.invoke().forEach { println(it.invoke()) } // this is probably not the best sequence comprehension

    println(ir2)
}