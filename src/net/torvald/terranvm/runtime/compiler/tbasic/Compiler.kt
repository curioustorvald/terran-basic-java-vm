package net.torvald.terranvm.runtime.compiler.tbasic

/*import net.torvald.terranvm.runtime.compiler.simplec.SimpleC.LineStructure
import net.torvald.terranvm.runtime.compiler.simplec.SyntaxError

/**
 * Created by minjaesong on 2017-06-26.
 */
object TBasic {

    private val operatorsHierarchyInternal = arrayOf(
            // opirator precedence in internal format (#_nameinlowercase)  PUT NO PARENS HERE!   TODO [ ] are allowed? pls chk
            // most important
            hashSetOf("#_preinc","#_predec","#_unaryplus","#_unaryminus","!","~","#_ptrderef","#_addressof","sizeof","(char *)","(short *)","(int *)","(long *)","(float *)","(double *)","(bool *)","(void *)", "(char)","(short)","(int)","(long)","(float)","(double)","(bool)"),
            hashSetOf("*","/","%"),
            hashSetOf("+","-"),
            hashSetOf("<<",">>"),
            hashSetOf("<","<=",">",">="),
            hashSetOf("==","!="),
            hashSetOf("&"),
            hashSetOf("^"),
            hashSetOf("|"),
            hashSetOf("&&"),
            hashSetOf("||"),
            hashSetOf("=","+=","-=","*=","/=","%=","<<=",">>=","&=","^=","|=")
            // least important
    ).reversedArray() // this makes op with highest precedence have bigger number
    // operators must return value when TREE is evaluated -- with NO EXCEPTION; '=' must return value too! (not just because of C standard, but design of #_assignvar)
    private val unaryOps = hashSetOf(
            "++","--",
            "#_preinc","#_predec","#_unaryplus","#_unaryminus","!","~","#_ptrderef","#_addressof","sizeof","(char *)","(short *)","(int *)","(long *)","(float *)","(double *)","(bool *)","(void *)", "(char)","(short)","(int)","(long)","(float)","(double)","(bool)"
    )

    private val splittableTokens = arrayOf( // order is important!
            "minus",
            "step",
            ">>>","and","xor","not",
            "<>","><","<<",">>",":=","<=",">=","==","!=","+=","-=","*=","/=","%=","&=","=<","=>","to",
            "<",">","^","|","=",",",".","+","-","!","~","*","&","/","%","(",")",";",
            " "
    )

    private val regexWhitespaceNoSP = Regex("""[\t\r\n\v\f]""")
    private val lineNumberMatch = Regex("""^([0-9]+(?=\s+))""")




    /** No preprocessor should exist at this stage! */
    fun tokenise(fullProgram: String): ArrayList<LineStructure> {
        fun debug1(any: Any) { if (false) println(any) }

        var currentProgramLineNumber = -1337

        ///////////////////////////////////
        // STEP 0. Divide things cleanly //
        ///////////////////////////////////
        // a.k.a. tokenise properly e.g. {extern int foo ( int initSize , SomeStruct strut , )} or {int foo = getch ( ) * ( ( num1 + num3 % 16 ) - 1 )}

        val lineStructures = ArrayList<LineStructure>()
        var currentLine = LineStructure(currentProgramLineNumber, -1, ArrayList<String>())


        // put things to lineStructure, kill any whitespace
        val sb = StringBuilder()
        var charCtr = 0
        fun splitAndMoveAlong() {
            if (sb.isNotEmpty()) {

                debug1("!! split: word $sb")

                currentLine.depth = -1 // !important
                currentLine.tokens.add(sb.toString())
                sb.setLength(0)
            }
        }
        fun gotoNewline() {
            if (currentLine.tokens.isNotEmpty()) {
                lineStructures.add(currentLine)
                sb.setLength(0)
                currentLine = LineStructure(currentProgramLineNumber, -1337, ArrayList<String>())
            }
        }
        var forStatementEngaged = false // to filter FOR range semicolon from statement-end semicolon
        var isLiteralMode = false // ""  ''
        var isCharLiteral = false
        var isBlockComment = false

        fullProgram.split('\n').forEach { program ->

            currentProgramLineNumber = lineNumberMatch.find(program)?.value?.toInt() ?: throw SyntaxError("BASIC line number not prepended.")


            while (charCtr < program.length) {
                var char = program[charCtr]

                var lookahead4 = program.substring(charCtr, minOf(charCtr + 4, program.length)) // charOfIndex {0, 1, 2, 3}
                var lookahead3 = program.substring(charCtr, minOf(charCtr + 3, program.length)) // charOfIndex {0, 1, 2}
                var lookahead2 = program.substring(charCtr, minOf(charCtr + 2, program.length)) // charOfIndex {0, 1}
                var lookbehind2 = program.substring(maxOf(charCtr - 1, 0), charCtr + 1) // charOfIndex {-1, 0}


                // count up line num
                if (char == '\n' && isLiteralMode) {
                    throw SyntaxError("at line $currentProgramLineNumber -- line break used inside of string literal")
                }
                else if (!isBlockComment && lookahead2 == "/*") {
                    isBlockComment = true
                    charCtr += 1
                }
                else if (!isBlockComment && lookahead2 == "*/") {
                    isBlockComment = false
                    charCtr += 1
                }
                else if (!isLiteralMode && !isCharLiteral && !isBlockComment && char.toString().matches(regexWhitespaceNoSP)) {
                    // do nothing
                }
                else if (!isLiteralMode && !isCharLiteral && !isBlockComment) {
                    // do the real jobs

                    // double quotes
                    if (char == '"' && lookbehind2[0] != '\\') {
                        isLiteralMode = !isLiteralMode
                        sb.append(char)
                    }
                    // char literal
                    else if (!isCharLiteral && char == '\'' && lookbehind2[0] != '\'') {
                        if ((lookahead4[1] == '\\' && lookahead4[3] != '\'') || (lookahead4[1] != '\\' && lookahead4[2] != '\''))
                            throw SyntaxError("Illegal usage of char literal")
                        isCharLiteral = !isCharLiteral
                    }
                    else if (!forStatementEngaged && char == ';') {
                        splitAndMoveAlong()
                        gotoNewline()
                    }
                    else {
                        if (splittableTokens.contains(lookahead3)) { // three-char operator
                            splitAndMoveAlong() // split previously accumulated word

                            sb.append(lookahead3)
                            splitAndMoveAlong()
                            charCtr += 2
                        }
                        else if (splittableTokens.contains(lookahead2)) { // two-char operator
                            splitAndMoveAlong() // split previously accumulated word

                            sb.append(lookahead2)
                            splitAndMoveAlong()
                            charCtr += 1
                        }
                        else if (splittableTokens.contains(char.toString())) { // operator and ' '
                            if (char == '.') { // struct reference or decimal point, depending on the context
                                // it's decimal if:
                                // .[number]
                                // \.e[+-]?[0-9]+   (exponent)
                                // [number].[ fF]?
                                // spaces around decimal points are NOT ALLOWED
                                if (lookahead2.matches(Regex("""\.[0-9]""")) or
                                        lookahead4.matches(Regex("""\.e[+-]?[0-9]+""")) or
                                        (lookbehind2.matches(Regex("""[0-9]+\.""")) and lookahead2.matches(Regex("""\.[ Ff,)]""")))
                                        ) {
                                    // get match length
                                    var charHolder: Char
                                    // we don't need travel back because 'else' clause on the far bottom have been already putting numbers into the stringBuilder

                                    var travelForth = 0
                                    do {
                                        travelForth += 1
                                        charHolder = program[charCtr + travelForth]
                                    } while (charHolder in '0'..'9' || charHolder.toString().matches(Regex("""[-+eEfF]""")))


                                    val numberWord = program.substring(charCtr..charCtr + travelForth - 1)


                                    debug1("[SimpleC.tokenise] decimal number token: $sb$numberWord, on line $currentProgramLineNumber")
                                    sb.append(numberWord)
                                    splitAndMoveAlong()


                                    charCtr += travelForth - 1
                                }
                                else { // reference call
                                    splitAndMoveAlong() // split previously accumulated word

                                    debug1("[SimpleC.tokenise] splittable token: $char, on line $currentProgramLineNumber")
                                    sb.append(char)
                                    splitAndMoveAlong()
                                }
                            }
                            else if (char != ' ') {
                                splitAndMoveAlong() // split previously accumulated word

                                debug1("[SimpleC.tokenise] splittable token: $char, on line $currentProgramLineNumber")
                                sb.append(char)
                                splitAndMoveAlong()
                            }
                            else { // space detected, split only
                                splitAndMoveAlong()
                            }
                        }
                        else {
                            sb.append(char)
                        }
                    }
                }
                else if (isCharLiteral && !isLiteralMode) {
                    if (char == '\\') { // escape sequence of char literal
                        sb.append(escapeSequences[lookahead2]!!.toInt())
                        charCtr++
                    }
                    else {
                        sb.append(char.toInt())
                    }
                }
                else if (isLiteralMode && !isCharLiteral) {
                    if (char == '"' && lookbehind2[0] != '\\') {
                        isLiteralMode = !isLiteralMode
                    }

                    sb.append(char)
                }
                else {
                    // do nothing
                }


                charCtr +
            }
        }

        return lineStructures
    }


}*/