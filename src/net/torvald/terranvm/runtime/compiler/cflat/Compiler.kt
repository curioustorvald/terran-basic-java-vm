package net.torvald.terranvm.runtime.compiler.cflat

import net.torvald.terranvm.VMOpcodesRISC
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * A compiler for C-flat language that compiles into TerranVM Terra Instruction Set.
 *
 * # Disclaimer
 *
 * 0. This compiler, BY NO MEANS, guarantees to implement standard C language; c'mon, $100+ for a standard document?
 * 1. I suck at code and test. Please report bugs!
 * 2. Please move along with my terrible sense of humour.
 *
 * # About C-flat
 *
 * C-flat is a stupid version of C. Everything is global and a word (or byte if it's array).
 *
 * ## New Features
 *
 * - Typeless (everything is a word), non-zero is truthy and zero is falsy
 * - Infinite loop using ```forever``` block. You can still use ```for (;;)```, ```while (true)```
 * - Counted simple loop (without loop counter ref) using ```repeat``` block
 *
 *
 * ## Important Changes from C
 *
 * - All function definition must specify return type, even if the type is ```void```.
 * - ```float``` is IEEE 754 Binary32.
 * - Everything is global
 * - Everything is a word (32-bit)
 * - Everything is ```int```, ```float``` and ```pointer``` at the same time. You decide.
 * - Unary pre- and post- increments/decrements are considered _evil_ and thus prohibited.
 * - Unsigned types are also considered _evil_ and thus prohibited.
 * - Everything except function's local variable is ```extern```, any usage of the keyword will throw error.
 * - Function cannot have non-local variable defined inside, as ```static``` keyword is illegal.
 * - And thus, following keywords will throw error:
 *      - auto, register, volatile (not supported)
 *      - signed, unsigned (prohibited)
 *      - static (no global inside function)
 *      - extern (everything is global)
 * - Assignment does not return shit.
 *
 *
 * ## Issues
 * - FIXME  arithmetic ops will not work with integers, need to autodetect types and slap in ADDINT instead of just ADD
 *
 *
 * Created by minjaesong on 2017-06-04.
 */
object Cflat {

    private val structOpen = '{'
    private val structClose = '}'

    private val parenOpen = '('
    private val parenClose = ')'

    private val preprocessorTokenSep = Regex("""[ \t]+""")

    private val nullchar = 0.toChar()

    private val infiniteLoops = arrayListOf<Regex>(
            Regex("""while\(true\)"""), // whitespaces are filtered on preprocess
            Regex("""for\([\s]*;[\s]*;[\s]*\)""")
    ) // more types of infinite loops are must be dealt with (e.g. while (0xFFFFFFFF < 0x7FFFFFFF))

    private val regexRegisterLiteral = Regex("""^[Rr][0-9]+$""") // same as the assembler
    private val regexBooleanWhole = Regex("""^(true|false)$""")
    private val regexHexWhole = Regex("""^(0[Xx][0-9A-Fa-f_]+?)$""") // DIFFERENT FROM the assembler
    private val regexOctWhole = Regex("""^(0[0-7_]+)$""")
    private val regexBinWhole = Regex("""^(0[Bb][01_]+)$""") // DIFFERENT FROM the assembler
    private val regexFPWhole =  Regex("""^([-+]?[0-9]*[.][0-9]+[eE]*[-+0-9]*[fF]*|[-+]?[0-9]+[.eEfF][0-9+-]*[fF]?)$""") // same as the assembler
    private val regexIntWhole = Regex("""^([-+]?[0-9_]+[Ll]?)$""") // DIFFERENT FROM the assembler

    private fun String.matchesNumberLiteral() = this.matches(regexHexWhole) || this.matches(regexOctWhole) || this.matches(regexBinWhole) || this.matches(regexIntWhole) || this.matches(regexFPWhole)
    private fun String.matchesFloatLiteral() = this.matches(regexFPWhole)
    private fun String.matchesStringLiteral() = this.endsWith(0.toChar())
    private fun generateTemporaryVarName(inst: String, arg1: String, arg2: String) = "$$${inst}_${arg1}_$arg2"
    private fun generateSuperTemporaryVarName(lineNum: Int, inst: String, arg1: String, arg2: String) = "$$${inst}_${arg1}_${arg2}_\$l$lineNum"


    private val regexVarNameWhole = Regex("""^([A-Za-z_][A-Za-z0-9_]*)$""")

    private val regexWhitespaceNoSP = Regex("""[\t\r\n\v\f]""")
    private val regexIndents = Regex("""^ +|^\t+|(?<=\n) +|(?<=\n)\t+""")

    private val digraphs = hashMapOf(
            "<:" to '[',
            ":>" to ']',
            "<%" to '{',
            "%>" to '}',
            "%:" to '#'
    )
    private val trigraphs = hashMapOf(
            "??=" to "#",
            "??/" to "'",
            "??'" to "^",
            "??(" to "[",
            "??)" to "]",
            "??!" to "|",
            "??<" to "{",
            "??>" to "}",
            "??-" to "~"
    )
    private val keywords = hashSetOf(
            // classic C
            "auto","break","case","char","const","continue","default","do","double","else","enum","extern","float",
            "for","goto","if","int","long","register","return","short","signed","static","struct","switch","sizeof", // is an operator
            "typedef","union","unsigned","void","volatile","while",


            // C-flat code blocks
            "forever","repeat"

            // C-flat dropped keywords (keywords that won't do anything/behave differently than C95, etc.):
            //  - auto, register, signed, unsigned, volatile, static: not implemented; WILL THROW ERROR
            //  - float: will act same as double
            //  - extern: everthing is global, anyway; WILL THROW ERROR

            // C-flat exclusive keywords:
            //  - bool, true, false: bool algebra
    )
    private val unsupportedKeywords = hashSetOf(
            "auto","register","signed","unsigned","volatile","static",
            "extern",
            "long", "short", "double", "bool"
    )
    private val operatorsHierarchyInternal = arrayOf(
            // opirator precedence in internal format (#_nameinlowercase)  PUT NO PARENS HERE!   TODO [ ] are allowed? pls chk
            // most important
            hashSetOf("++","--","[", "]",".","->"),
            hashSetOf("#_preinc","#_predec","#_unaryplus","#_unaryminus","!","~","#_ptrderef","#_addressof","sizeof","(char *)","(short *)","(int *)","(long *)","(float *)","(double *)","(bool *)","(void *)", "(char)","(short)","(int)","(long)","(float)","(double)","(bool)"),
            hashSetOf("*","/","%"),
            hashSetOf("+","-"),
            hashSetOf("<<",">>",">>>"),
            hashSetOf("<","<=",">",">="),
            hashSetOf("==","!="),
            hashSetOf("&"),
            hashSetOf("^"),
            hashSetOf("|"),
            hashSetOf("&&"),
            hashSetOf("||"),
            hashSetOf("?",":"),
            hashSetOf("=","+=","-=","*=","/=","%=","<<=",">>=","&=","^=","|="),
            hashSetOf(",")
            // least important
    ).reversedArray() // this makes op with highest precedence have bigger number
    // operators must return value when TREE is evaluated -- with NO EXCEPTION; '=' must return value too! (not just because of C standard, but design of #_assignvar)
    private val unaryOps = hashSetOf(
            "++","--",
            "#_preinc","#_predec","#_unaryplus","#_unaryminus","!","~","#_ptrderef","#_addressof","sizeof","(char *)","(short *)","(int *)","(long *)","(float *)","(double *)","(bool *)","(void *)", "(char)","(short)","(int)","(long)","(float)","(double)","(bool)"
    )
    private val operatorsHierarchyRTL = arrayOf(
            false,
            true,
            false,false,false,false,false,false,false,false,false,false,
            true,true,
            false
    )
    private val operatorsNoOrder = HashSet<String>()
    init {
        operatorsHierarchyInternal.forEach { array ->
            array.forEach { word -> operatorsNoOrder.add(word) }
        }
    }
    private val splittableTokens = arrayOf( // order is important!
            "<<=",">>=","...",
            "++","--","&&","||","<<",">>","->","<=",">=","==","!=","+=","-=","*=","/=","%=","&=","^=","|=",
            "<",">","^","|","?",":","=",",",".","+","-","!","~","*","&","/","%","(",")",
            " "
    )
    private val argumentDefBadTokens = splittableTokens.toMutableList().minus(",").minus("*").minus("...").toHashSet()
    private val evilOperators = hashSetOf(
            "++","--"
    )
    private val funcAnnotations = hashSetOf(
            "auto", // does nothing; useless even in C (it's derived from B language, actually)
            "extern" // not used in C-flat
    )
    private val funcTypes = hashSetOf(
            "char", "short", "int", "long", "float", "double", "bool", "void"
    )
    private val varAnnotations = hashSetOf(
            "auto", // does nothing; useless even in C (it's derived from B language, actually)
            "extern", // not used in C-flat
            "const",
            "register" // not used in C-flat
    )
    private val varTypes = hashSetOf(
            "struct", "char", "short", "int", "long", "float", "double", "bool", "var", "val"
    )
    private val validFuncPreword = (funcAnnotations + funcTypes).toHashSet()
    private val validVariablePreword = (varAnnotations + varTypes).toHashSet()
    private val codeBlockKeywords = hashSetOf(
            "do", "else", "enum", "for", "if", "struct", "switch", "union", "while", "forever", "repeat"
    )
    private val functionalKeywordsWithOneArg = hashSetOf(
            "goto", "return"
    )
    private val functionalKeywordsNoArg = hashSetOf(
            "break", "continue",
            "return" // return nothing
    )
    private val preprocessorKeywords = hashSetOf(
            "#include","#ifndef","#ifdef","#define","#if","#else","#elif","#endif","#undef","#pragma"
    )
    private val escapeSequences = hashMapOf<String, Char>(
            """\a""" to 0x07.toChar(), // Alert (Beep, Bell)
            """\b""" to 0x08.toChar(), // Backspace
            """\f""" to 0x0C.toChar(), // Formfeed
            """\n""" to 0x0A.toChar(), // Newline (Line Feed)
            """\r""" to 0x0D.toChar(), // Carriage Return
            """\t""" to 0x09.toChar(), // Horizontal Tab
            """\v""" to 0x0B.toChar(), // Vertical Tab
            """\\""" to 0x5C.toChar(), // Backslash
            """\'""" to 0x27.toChar(), // Single quotation mark
            """\"""" to 0x22.toChar(), // Double quotation mark
            """\?""" to 0x3F.toChar()  // uestion mark (used to avoid trigraphs)
    )
    private val builtinFunctions = hashSetOf(
            "#_declarevar" // #_declarevar(SyntaxTreeNode<RawString> varname, SyntaxTreeNode<RawString> vartype)
    )
    private val functionWithSingleArgNoParen = hashSetOf(
            "return", "goto", "comefrom"
    )
    private val compilerInternalFuncArgsCount = hashMapOf(
            "#_declarevar" to 2,
            "endfuncdef" to 1,
            "=" to 2,

            "endif" to 0,
            "endelse" to 0
    )


    /* Error messages */

    val errorUndeclaredVariable = "Undeclared variable"
    val errorIncompatibleType = "Incompatible type(s)"
    val errorRedeclaration = "Redeclaration"


    fun sizeofPrimitive(type: String) = when (type) {
        "char" -> 1
        "short" -> 2
        "int" -> 4
        "long" -> 8
        "float" -> 4
        "double" -> 8
        "bool" -> 1
        "void" -> 1 // GCC feature
        else -> throw IllegalArgumentException("Unknown primitive type: $type")
    }

    private val functionsImplicitEnd = hashSetOf(
            "if", "else", "for", "while", "switch"
    )

    private val exprToIR = hashMapOf(
            "#_declarevar" to "DECLARE",
            "return" to "RETURN",

            "+" to "ADD",
            "-" to "SUB",
            "*" to "MUL",
            "/" to "DIV",
            "^" to "POW",
            "%" to "MOD",

            "<<" to "SHL",
            ">>" to "SHR",
            ">>>" to "USHR",
            "and" to "AND",
            "or" to "OR",
            "xor" to "XOR",
            "not" to "NOT",

            "=" to "ASSIGN",

            "==" to "ISEQ",
            "!=" to "ISNEQ",
            ">" to "ISGT",
            "<" to "ISLS",
            ">=" to "ISGTEQ",
            "<=" to "ISLSEQ",

            "if" to "IF",
            "endif" to "ENDIF",
            "else" to "ELSE",
            "endelse" to "ENDELSE",

            "goto" to "GOTOLABEL",
            "comefrom" to "DEFLABEL",

            "asm" to "INLINEASM",

            "funcdef" to "FUNCDEF",
            "endfuncdef" to "ENDFUNCDEF",

            "stackpush" to "STACKPUSH"
    )

    private val irCmpInst = hashSetOf(
            "ISEQ_II", "ISEQ_IF", "ISEQ_FI", "ISEQ_FF",
            "ISNEQ_II", "ISNEQ_IF", "ISNEQ_FI", "ISNEQ_FF",
            "ISGT_II", "ISGT_IF", "ISGT_FI", "ISGT_FF",
            "ISLS_II", "ISLS_IF", "ISLS_FI", "ISLS_FF",
            "ISGTEQ_II", "ISGTEQ_IF", "ISGTEQ_FI", "ISGTEQ_FF",
            "ISLSEQ_II", "ISLSEQ_IF", "ISLSEQ_FI", "ISLSEQ_FF"
    )

    private val jmpCommands = hashSetOf(
            "JMP", "JZ", "JNZ", "JGT", "JLS"
    )






    // compiler options
    var useDigraph = true
    var useTrigraph = false
    var errorIncompatibles = true

    operator fun invoke(
            program: String,
            // options
            useDigraph: Boolean = false,
            useTrigraph: Boolean = false,
            errorIncompatible: Boolean = true
    ) {
        this.useDigraph = useDigraph
        this.useTrigraph = useTrigraph
        this.errorIncompatibles = errorIncompatible


        //val tree = tokenise(preprocess(program))
        TODO()
    }


    private val structDict = ArrayList<CStruct>()
    private val structNameDict = ArrayList<String>()
    private val funcDict = ArrayList<CFunction>()
    private val funcNameDict = ArrayList<String>()
    private val varDict = HashSet<CData>()
    private val varNameDict = HashSet<String>()


    private val includesUser = HashSet<String>()
    private val includesLib = HashSet<String>()


    private fun getFuncByName(name: String): CFunction? {
        funcDict.forEach {
            if (it.name == name) return it
        }
        return null
    }
    private fun structSearchByName(name: String): CStruct? {
        structDict.forEach {
            if (it.name == name) return it
        }
        return null
    }


    fun preprocess(program: String): String {
        var program = program
                //.replace(regexIndents, "") // must come before regexWhitespaceNoSP
                //.replace(regexWhitespaceNoSP, "")

        var out = StringBuilder()

        if (useTrigraph) {
            trigraphs.forEach { from, to ->
                program = program.replace(from, to)
            }
        }

        val rules = PreprocessorRules()


        // Scan thru line by line (assuming single command per line...?)
        program.lines().forEach {
            if (it.startsWith('#')) {
                val tokens = it.split(preprocessorTokenSep)
                val cmd = tokens[0].drop(1).toLowerCase()


                when (cmd) {
                    "include" -> TODO("Preprocessor keyword 'include'")
                    "define" -> rules.addDefinition(tokens[1], tokens.subList(2, tokens.size).joinToString(" "))
                    "undef" -> rules.removeDefinition(tokens[1])
                    else -> throw UndefinedStatement("Preprocessor macro '$cmd' is not supported.")
                }
            }
            else {
                // process each line according to rules
                var line = it
                rules.forEachKeywordForTokens { replaceRegex, replaceWord ->
                    line = line.replace(replaceRegex, " $replaceWord ")
                }

                out.append("$line\n")
            }
        }


        println(out.toString())

        return out.toString()
    }

    /** No preprocessor should exist at this stage! */
    fun tokenise(program: String): ArrayList<LineStructure> {
        fun debug1(any: Any) { if (false) println(any) }

        ///////////////////////////////////
        // STEP 0. Divide things cleanly //
        ///////////////////////////////////
        // a.k.a. tokenise properly e.g. {extern int foo ( int initSize , SomeStruct strut , )} or {int foo = getch ( ) * ( ( num1 + num3 % 16 ) - 1 )}

        val lineStructures = ArrayList<LineStructure>()
        var currentProgramLineNumber = 1
        var currentLine = LineStructure(currentProgramLineNumber, 0, ArrayList<String>())


        // put things to lineStructure, kill any whitespace
        val sb = StringBuilder()
        var charCtr = 0
        var structureDepth = 0
        fun splitAndMoveAlong() {
            if (sb.isNotEmpty()) {
                if (errorIncompatibles && unsupportedKeywords.contains(sb.toString())) {
                    throw IllegalTokenException("at line $currentProgramLineNumber with token '$sb'")
                }

                debug1("!! split: depth $structureDepth, word $sb")

                currentLine.depth = structureDepth // !important
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
        var isLineComment = false
        var isBlockComment = false
        while (charCtr < program.length) {
            var char = program[charCtr]

            var lookahead4 = program.substring(charCtr, minOf(charCtr + 4, program.length)) // charOfIndex {0, 1, 2, 3}
            var lookahead3 = program.substring(charCtr, minOf(charCtr + 3, program.length)) // charOfIndex {0, 1, 2}
            var lookahead2 = program.substring(charCtr, minOf(charCtr + 2, program.length)) // charOfIndex {0, 1}
            var lookbehind2 = program.substring(maxOf(charCtr - 1, 0), charCtr + 1) // charOfIndex {-1, 0}


            // count up line num
            if (char == '\n' && !isCharLiteral && !isLiteralMode) {
                currentProgramLineNumber += 1
                currentLine.lineNum = currentProgramLineNumber

                if (isLineComment) isLineComment = false
            }
            else if (char == '\n' && isLiteralMode) {
                //throw SyntaxError("at line $currentProgramLineNumber -- line break used inside of string literal")

                // ignore \n by doing nothing
            }
            else if (lookahead2 == "//" && !isLineComment) {
                isLineComment = true
                charCtr += 1
            }
            else if (!isBlockComment && lookahead2 == "/*") {
                isBlockComment = true
                charCtr += 1
            }
            else if (!isBlockComment && lookahead2 == "*/") {
                isBlockComment = false
                charCtr += 1
            }
            else if (!isLiteralMode && !isCharLiteral && !isBlockComment && !isLineComment && char.toString().matches(regexWhitespaceNoSP)) {
                // do nothing
            }
            else if (!isLiteralMode && !isCharLiteral && !isBlockComment && !isLineComment) {
                // replace digraphs
                if (useDigraph && digraphs.containsKey(lookahead2)) { // replace digraphs
                    char = digraphs[lookahead2]!!
                    lookahead4 = char + lookahead4.substring(0..lookahead4.lastIndex)
                    lookahead3 = char + lookahead3.substring(0..lookahead3.lastIndex)
                    lookahead2 = char + lookahead2.substring(0..lookahead2.lastIndex)
                    lookbehind2 = lookbehind2.substring(0..lookahead2.lastIndex - 1) + char
                    charCtr += 1
                }


                // filter shits
                if (lookahead2 == "//" || lookahead2 == "/*" || lookahead2 == "*/") {
                    throw SyntaxError("at line $currentProgramLineNumber -- illegal token '$lookahead2'")
                }


                // do the real jobs
                if (char == structOpen) {
                    debug1("!! met structOpen at line $currentProgramLineNumber")

                    splitAndMoveAlong()
                    gotoNewline()
                    structureDepth += 1 // must go last, because of quirks with 'codeblock{' and 'codeblock  {'
                }
                else if (char == structClose) {
                    debug1("!! met structClose at line $currentProgramLineNumber")

                    structureDepth -= 1 // must go first
                    splitAndMoveAlong()
                    gotoNewline()
                }
                // double quotes
                else if (char == '"' && lookbehind2[0] != '\\') {
                    isLiteralMode = !isLiteralMode
                    sb.append(char)
                }
                // char literal
                else if (!isCharLiteral && char == '\'' && lookbehind2[0] != '\'') {
                    if ((lookahead4[1] == '\\' && lookahead4[3] != '\'') || (lookahead4[1] != '\\' && lookahead4[2] != '\''))
                        throw SyntaxError("Illegal usage of char literal")
                    isCharLiteral = !isCharLiteral
                }
                /*else if (char == ')' && forStatementEngaged) {
                    forStatementEngaged = false
                    TODO()
                }
                else if (lookahead3 == "for") {
                    if (forStatementEngaged) throw SyntaxError("keyword 'for' used inside of 'for' statement")
                    forStatementEngaged = true
                    TODO()
                }*/
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

                        if (evilOperators.contains(lookahead2)) {
                            throw IllegalTokenException("at line $currentProgramLineNumber -- evil operator '$lookahead2'")
                        }
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


                                debug1("[C-flat.tokenise] decimal number token: $sb$numberWord, on line $currentProgramLineNumber")
                                sb.append(numberWord)
                                splitAndMoveAlong()


                                charCtr += travelForth - 1
                            }
                            else { // reference call
                                splitAndMoveAlong() // split previously accumulated word

                                debug1("[C-flat.tokenise] splittable token: $char, on line $currentProgramLineNumber")
                                sb.append(char)
                                splitAndMoveAlong()
                            }
                        }
                        else if (char != ' ') {
                            splitAndMoveAlong() // split previously accumulated word

                            debug1("[C-flat.tokenise] splittable token: $char, on line $currentProgramLineNumber")
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


            charCtr++
        }


        return lineStructures
    }

    val rootNodeName = "cflat_node_root"

    fun buildTree(lineStructures: List<LineStructure>): SyntaxTreeNode {
        fun debug1(any: Any) { if (true) println(any) }


        ///////////////////////////
        // STEP 1. Create a tree //
        ///////////////////////////

        val ASTroot = SyntaxTreeNode(ExpressionType.FUNCTION_DEF, ReturnType.NOTHING, name = rootNodeName, isRoot = true, lineNumber = 1)

        val workingNodes = Stack<SyntaxTreeNode>()
        workingNodes.push(ASTroot)

        fun getWorkingNode() = workingNodes.peek()



        fun printStackDebug(): String {
            val sb = StringBuilder()

            sb.append("Node stack: [")
            workingNodes.forEachIndexed { index, it ->
                if (index > 0) { sb.append(", ") }
                sb.append("l"); sb.append(it.lineNumber)
            }
            sb.append("]")

            return sb.toString()
        }

        lineStructures.forEachIndexed { index, it -> val (lineNum, depth, tokens) = it
            val nextLineDepth = if (index != lineStructures.lastIndex) lineStructures[index + 1].depth else null

            debug1("buildtree!!  tokens: $tokens")
            debug1("call #$index from buildTree()")
            val nodeBuilt = asTreeNode(lineNum, tokens)
            getWorkingNode().addStatement(nodeBuilt)
            debug1("end call #$index from buildTree()")

            if (nextLineDepth != null) {
                // has code block
                if (nextLineDepth > depth) {
                    workingNodes.push(nodeBuilt)
                }
                // code block escape
                else if (nextLineDepth < depth) {
                    repeat(depth - nextLineDepth) { workingNodes.pop() }
                }
            }


        }


        return ASTroot
    }

    fun treeToProperNotation(root: SyntaxTreeNode): MutableList<String> {
        fun debug1(any: Any) { if (true) print(any) }


        // turn into ASM-ique notation
        // strat:
        //  visitNode() -- ".func; :funcName;"
        //  for each statements: recurse;
        //  for each arguments: recurse;
        //  visitNode() -- "return;"
        //  (return)


        root.expandImplicitEnds()
        root.updateDepth()


        fun SyntaxTreeNode.getReadableNodeName(): String {
            if (this.expressionType == ExpressionType.VARIABLE_READ || this.expressionType == ExpressionType.VARIABLE_WRITE) {
                return "$${this.name}"
            }
            else if (this.name != null && this.literalValue != null) {
                return ("${this.name} ${this.literalValue}")
            }
            else if (this.name != null) {
                return (this.name ?: "null")
            }
            else if (this.literalValue != null) {
                return (this.literalValue?.toString() ?: "null")
            }
            else {
                throw NullPointerException()
            }
        }


        //val commands = Stack<SyntaxTreeNode>()
        val string = StringBuilder()
        val programOut = ArrayList<String>()
            // contains all the strings; should be array of strings (with extra info like line number?)
            // format: ARG${lineNumber}\t${cmd}\t${otherCmd}\t ...
            // format: STA${lineNumber}\t${cmd}\t${otherCmd}\t ...

        var cmdRightB4 = ""


        // pre-traverse to get the last element of the traverse

        val traversedNodes = ArrayList<SyntaxTreeNode>()
        fun preTraverse(node: SyntaxTreeNode) {
            node.arguments.forEach { preTraverse(it) }
            traversedNodes.add(node)
            node.statements.forEach { preTraverse(it) }
        }
        preTraverse(root)

        // pop out root from the traversed list
        traversedNodes.removeAt(0)

        fun isLastOfTraverse(node: SyntaxTreeNode) = node == traversedNodes.last()



        // process using pre-traversed list
        debug1("== Traversed nodes ==\n")
        // FIXME interpretation of the traversed tree is wrong
        traversedNodes.forEachIndexed { index, node ->

            // test print
            debug1("${node.getReadableNodeName()}")
            debug1("; ${node.expressionType}")
            if (node.isPartOfArgumentsNode) {
                debug1("; PART_OF_ARGS_NODE")
            }
            debug1('\n')



            if ((node.expressionType == ExpressionType.INTERNAL_FUNCTION_CALL) && index > 0) {
                // prev inst is the arg (e.g. [foo, endfuncdef])
                // we read from it; after read, we remove it from the output array
                //

                val argsCount = compilerInternalFuncArgsCount[node.getReadableNodeName()] ?: 0

                for (i in argsCount downTo 1) {
                    val preNode = traversedNodes[index - i]
                    string.append("${preNode.getReadableNodeName()}\t")
                    programOut.removeAt(programOut.lastIndex) // pop out from it
                }

                programOut.add("${node.lineNumber}\t${node.getReadableNodeName()}\t$string")
                string.delete(0, string.length)
            }
            else if (node.expressionType == ExpressionType.FUNCTION_DEF) {
                string.append("${node.getReadableNodeName()}\t")

                programOut.add("${node.lineNumber}\tfuncdef\t$string")
                string.delete(0, string.length)
            }
            else {
                programOut.add("${node.lineNumber}\t${node.getReadableNodeName()}\t")
            }
        }



        var i = 0
        while (i < programOut.size - 1) {
            val it = programOut[i].split('\t')
            val next = programOut[i + 1].split('\t')

            // 1. remove ENDIF if it's accompanied by ELSE (don't want to have both ENDIF and ENDELSE at the same time)
            if (it[1] == "endif" && next[1] == "else") {
                programOut.removeAt(i)
                i++ // we skip ELSE
            }
            else {
                i++
            }
        }



        debug1("========\n   OP   \n========\n")
        programOut.forEach { debug1(it + "\n") }
        debug1("========\n")



        return programOut
    }

    /**
     * @param type "variable", "literal_i", "literal_f"
     * @param value string for variable name, int for integer and float literals
     */
    private data class VirtualStackItem(val type: String, val value: String)

    /**
    Intermediate Representation:

    int a; a = 42; would be:
        DECLARE $a
        MOV $a, 42

    This will be converted as actual assembly, like:
        .data;
            int a;
            (...)
        .code:
            loadwordi r1, 42;
            storewordimem r1, @a;

     */
    fun notationToIR(notatedProgram: MutableList<String>): MutableList<IntermediateRepresentation> {
        fun debug1(any: Any) { if (true) print(any) }


        //fun String.toIRVar() = if (this.matchesNumberLiteral()) this else "$" + this
        fun String.isLiteral() = this.matchesNumberLiteral()


        val IRs = ArrayList<IntermediateRepresentation>()
        val virtualStack = Stack<VirtualStackItem>()
        val virtualGlobalVars = HashMap<String, String>() // name, type
        val nestedStatementsCommonLabelName = Stack<String>()

        notatedProgram.forEachIndexed { index, it ->
            val words = it.split('\t')

            val lineNumber = words[0].toInt()

            val newcmds = ArrayList<IntermediateRepresentation>()

            // turn expression to IR
            if (exprToIR.containsKey(words[1])) {
                newcmds.add(IntermediateRepresentation(lineNumber, exprToIR[words[1]]!!))
            }
            // or is it variable?
            else if (words[1].isVariable()) {
                newcmds.add(IntermediateRepresentation(lineNumber, "STACKPUSHIPTR", words[1]!!))
                virtualStack.push(VirtualStackItem("variable_" +
                        "${((virtualGlobalVars[words[1]]) ?: throw InternalError("No such variable: ${words[1]}"))[0]}",
                        words[1]))
            }
            // or is it literal?
            else if (words[1].isLiteral() && words[2].isBlank()) {
                newcmds.add(IntermediateRepresentation(lineNumber, "STACKPUSHICONST", words[1]!!))

                if (words[1].matchesFloatLiteral())
                    virtualStack.push(VirtualStackItem("literal_f", words[1]))
                else
                    virtualStack.push(VirtualStackItem("literal_i", words[1]))


                debug1("!! STACKPUSHICONST ${words[1]} with type ${virtualStack.peek().type}\n")
            }
            else {
                // or assume as function call
                newcmds.add(IntermediateRepresentation(lineNumber, "FUNCCALL", words[1]!!))
            }

            var newcmd = newcmds[0]

            fun addNextNewCmd() {
                newcmds.add(IntermediateRepresentation(lineNumber))
            }


            //debug1("L$lineNumber: inst: ${newcmd.instruction}, wordCount: ${words.size}\n")


            // convert internal notation into IR command
            when (newcmd.instruction) {
                "DECLARE" -> {
                    newcmd.instruction = when (words[3]) {
                        "int" -> "DECLAREI"
                        "float" -> "DECLAREF"
                        else -> "DECLARE"
                    }
                    newcmd.arg1 = "$" + words[2]

                    virtualGlobalVars[newcmd.arg1!!] = words[3]
                }
                "ASSIGN" -> {
                    repeat(2) { addNextNewCmd() }

                    newcmds[0] = IntermediateRepresentation(lineNumber, "STACKPOP", "r2")
                    newcmds[1] = IntermediateRepresentation(lineNumber, "STACKPOP", "r1")

                    val typeRhand = virtualStack.pop()
                    //val typeLhand = virtualStack.pop()
                    // do not pop othe elem: intended cmd is: pop(), push_what_just_popped()

                    var cmd = if (typeRhand.type.startsWith("literal"))
                        "ASSIGNCONST"
                    else
                        "ASSIGNVAR"

                    newcmds[2] = IntermediateRepresentation(lineNumber, cmd)

                }
                in VMOpcodesRISC.threeArgsCmd -> {
                    val oldCmd = newcmd.instruction
                    val returnType = VMOpcodesRISC.getReturnType(newcmd.instruction)
                    repeat(5) { addNextNewCmd() }

                    newcmds[0] = IntermediateRepresentation(lineNumber, "STACKPOP", "r3")
                    newcmds[1] = IntermediateRepresentation(lineNumber, "STACKPOP", "r2")

                    val typeRhand = virtualStack.pop()
                    val typeLhand = virtualStack.pop()

                    // command relative to hand types (literal or pointer)

                    if (!typeRhand.type.startsWith("literal")) {
                        // not a literal; dereference pointer
                        // if it were a literal, the value is already on the register, so no further jobs required
                        newcmds[2] = IntermediateRepresentation(lineNumber, "DEREFPTR", "r3")
                    }
                    else {
                        newcmds[2] = IntermediateRepresentation(lineNumber, "NOP")
                    }

                    if (!typeLhand.type.startsWith("literal")) {
                        // not a literal; dereference pointer
                        // if it were a literal, the value is already on the register, so no further jobs required
                        newcmds[3] = IntermediateRepresentation(lineNumber, "DEREFPTR", "r2")
                    }
                    else {
                        newcmds[3] = IntermediateRepresentation(lineNumber, "NOP")
                    }


                    // TODO type-awareness of INT and FLOAT

                    newcmds[4] = IntermediateRepresentation(lineNumber, oldCmd)

                    if (returnType != null) {
                        newcmds[5] = IntermediateRepresentation(lineNumber, "STACKPUSH")
                        virtualStack.push(VirtualStackItem("literal_${returnType[0]}", "something"))
                    }
                }
                "ISEQ", "ISNEQ", "ISGT", "ISLS", "ISGTEQ", "ISLSEQ" -> {
                    //newcmd.arg1 = words[2]
                    //newcmd.arg2 = words[3]

                    //if (newcmd.arg2!!.drop(1).isEmpty()) {
                    //    newcmd.arg2 = nestedStatementsCommonLabelName.pop()
                    //}

                    nestedStatementsCommonLabelName.push(
                            generateSuperTemporaryVarName(lineNumber, "IF${newcmd.instruction}", "TEMPO", "RARY")
                    )
                }
                "IF" -> {
                    val sourceExpr = nestedStatementsCommonLabelName.peek() // $$IFISEQ_42_$x_$l5
                    val sourceCmpCmd = sourceExpr.split('_')[0].drop(2)

                    repeat(8) { addNextNewCmd() }
                    newcmd      = newcmds[5] // jump
                    val newcmd2 = newcmds[6] // jump
                    val newcmd3 = newcmds[7] // jump
                    val newcmd4 = newcmds[8] // label for IF_TRUE

                    newcmds[0] = IntermediateRepresentation(lineNumber, "STACKPOP", "r3")
                    newcmds[1] = IntermediateRepresentation(lineNumber, "STACKPOP", "r2")

                    val rhand = virtualStack.pop().type // r3 // variable_[if] | literal_[if]
                    val lhand = virtualStack.pop().type // r2 // variable_[if] | literal_[if]

                    if (rhand.startsWith("variable")) {
                        // add dereference inst
                        newcmds[2] = IntermediateRepresentation(lineNumber, "DEREFPTR", "r3")
                    }
                    else {
                        newcmds[2] = IntermediateRepresentation(lineNumber, "NOP")
                    }

                    if (lhand.startsWith("variable")) {
                        // add dereference inst
                        newcmds[3] = IntermediateRepresentation(lineNumber, "DEREFPTR", "r2")
                    }
                    else {
                        newcmds[3] = IntermediateRepresentation(lineNumber, "NOP")
                    }

                    // add type flags into prev command
                    val typeAppendix = "_${lhand.last().minus(0x20)}" + // capitalise [if] to [IF]
                                       "${rhand.last().minus(0x20)}" // capitalise [if] to [IF]

                    debug1("!! ${IRs.last().instruction} -> ${IRs.last().instruction}$typeAppendix\n") // expecting ISEQ or others

                    val newCmpInst = IRs.last().instruction + typeAppendix
                    // NOPify prev cmp commands;
                    // Expected: STACKPOP r3; STACKPOP r2; (deref if needed); ISEQ_FF
                    // At this point we get: ISEQ_FF; STACKPOP r3; STACKPOP r2; (deref if needed)
                    // Expecting result: NOP; STACKPOP r3; STACKPOP r2; (deref if needed); ISEQ_FF
                    IRs[IRs.lastIndex] = IntermediateRepresentation(lineNumber, "NOP")
                    newcmds[4] = IntermediateRepresentation(lineNumber, newCmpInst)


                    when (sourceCmpCmd) {
                        "IFISEQ" -> {
                            newcmd.instruction = "JZ"
                            newcmd.arg1 = "${sourceExpr}_TRUE"

                            newcmd2.instruction = "JNZ"
                            newcmd2.arg1 = "${sourceExpr}_FALSE"

                            newcmd3.instruction = "NOP"
                        }
                        "IFISNEQ" -> {
                            newcmd.instruction = "JZ"
                            newcmd.arg1 = "${sourceExpr}_FALSE"

                            newcmd2.instruction = "JNZ"
                            newcmd2.arg1 = "${sourceExpr}_TRUE"

                            newcmd3.instruction = "NOP"
                        }
                        "IFISGT" -> {
                            newcmd.instruction = "JGT"
                            newcmd.arg1 = "${sourceExpr}_TRUE"

                            newcmd2.instruction = "JLS"
                            newcmd2.arg1 = "${sourceExpr}_FALSE"

                            newcmd3.instruction = "JZ"
                            newcmd3.arg1 = "${sourceExpr}_FALSE"
                        }
                        "IFISLS" -> {
                            newcmd.instruction = "JGT"
                            newcmd.arg1 = "${sourceExpr}_FALSE"

                            newcmd2.instruction = "JLS"
                            newcmd2.arg1 = "${sourceExpr}_TRUE"

                            newcmd3.instruction = "JZ"
                            newcmd3.arg1 = "${sourceExpr}_FALSE"
                        }
                        "IFISGTEQ" -> {
                            newcmd.instruction = "JGT"
                            newcmd.arg1 = "${sourceExpr}_TRUE"

                            newcmd2.instruction = "JLS"
                            newcmd2.arg1 = "${sourceExpr}_FALSE"

                            newcmd3.instruction = "JZ"
                            newcmd3.arg1 = "${sourceExpr}_TRUE"
                        }
                        "IFISLSEQ" -> {
                            newcmd.instruction = "JGT"
                            newcmd.arg1 = "${sourceExpr}_FALSE"

                            newcmd2.instruction = "JLS"
                            newcmd2.arg1 = "${sourceExpr}_TRUE"

                            newcmd3.instruction = "JZ"
                            newcmd3.arg1 = "${sourceExpr}_TRUE"
                        }
                        else -> throw InternalError("Unknown comparison operator: $sourceCmpCmd")
                    }


                    newcmd4.instruction = "LABEL"
                    newcmd4.arg1 = "${sourceExpr}_TRUE"
                }
                "ELSE" -> {
                    val sourceExpr = nestedStatementsCommonLabelName.peek() // $$IFISEQ_42_$x_$l5

                    addNextNewCmd()
                    val newcmd2 = newcmds[1] // label for IF_FALSE

                    newcmd.instruction = "JMP"
                    newcmd.arg1 = "${sourceExpr}_ENDE" // ENDELSE

                    newcmd2.instruction = "LABEL"
                    newcmd2.arg1 = "${sourceExpr}_FALSE"
                }
                "ENDIF" -> {
                    val sourceExpr = nestedStatementsCommonLabelName.pop() // POP DA FUGGER

                    newcmd.instruction = "LABEL"
                    newcmd.arg1 = "${sourceExpr}_FALSE"
                }
                "ENDELSE" -> {
                    val sourceExpr = nestedStatementsCommonLabelName.pop() // POP DA FUGGER

                    newcmd.instruction = "LABEL"
                    newcmd.arg1 = "${sourceExpr}_ENDE"
                }
                "DEFLABEL" -> {
                    newcmd.instruction = "LABEL"
                    newcmd.arg1 = "$" + words[2]
                }
                "GOTOLABEL" -> {
                    newcmd.instruction = "JMP"
                    newcmd.arg1 = "$" + words[2]
                }
                "INLINEASM" -> {
                    newcmd.arg1 = words[2].trimIndent().dropLast(1) // drop null terminator
                }
                "FUNCCALL" -> {
                    try { // !! STARTS FROM ONE !! //
                        newcmd.arg1 = if (words[1].isBlank()) null else words[1]
                        newcmd.arg2 = if (words[2].isBlank()) null else words[2]
                        newcmd.arg3 = if (words[3].isBlank()) null else words[3]
                        newcmd.arg4 = if (words[4].isBlank()) null else words[4]
                        newcmd.arg5 = if (words[5].isBlank()) null else words[5]
                    }
                    catch (e: IndexOutOfBoundsException) {}
                }
                "FUNCDEF", "RETURN", "ENDFUNCDEF" -> {
                    try { // !! STARTS FROM 2 !! //
                        newcmd.arg1 = if (words[2].isBlank()) null else words[2]
                        newcmd.arg2 = if (words[3].isBlank()) null else words[3]
                        newcmd.arg3 = if (words[4].isBlank()) null else words[4]
                        newcmd.arg4 = if (words[5].isBlank()) null else words[5]
                        newcmd.arg5 = if (words[6].isBlank()) null else words[6]
                    }
                    catch (e: IndexOutOfBoundsException) {}
                }
                "STACKPUSH", "STACKPUSHICONST", "STACKPUSHIPTR" -> {
                    // no further jobs required
                }
                else -> {
                    throw InternalError("Unknown IR instruction: ${newcmd.instruction}")
                }
            }


            newcmds.forEach { IRs.add(it) }
        }



        debug1("========\n   IR   \n========\n")
        IRs.forEach { debug1(it.toString() + "\n") }
        debug1("========\n")



        return IRs
    }


    fun preprocessIR(initialIR: MutableList<IntermediateRepresentation>): MutableList<IntermediateRepresentation> {
        fun debug1(any: Any) { if (true) print(any) }


        val irDeclaresOnly = ArrayList<IntermediateRepresentation>()
        // copy DECLAREs into the new 'ir'
        initialIR.forEach {
            if (it.instruction.startsWith("DECLARE")) {
                irDeclaresOnly.add(it)
            }
        }
        // remove all the DECLAREs from the copied initialIR
        initialIR.removeAll { it.instruction.startsWith("DECLARE") }


        // define our new IR list that has all the DECLARES at its head
        val ir = irDeclaresOnly + initialIR


        // TODO: do I need to declare temporary variables that exist in un-processed IRs?


        // test print
        debug1(ir + "\n")


        val newIR = ArrayList<IntermediateRepresentation>()


        var i = 0
        while (i < ir.size) {
            val it = ir[i]
            val next = ir.getOrNull(i + 1)


            if (next != null) {
                // check for compare instructions
                // FIXME now
                /*if (next.instruction in irCmpInst && it.arg1 == next.arg2) {
                    val newIR1 = IntermediateRepresentation(it.lineNum, it.instruction, "r4", it.arg2, it.arg3)
                    val newIR2 = IntermediateRepresentation(next.lineNum, next.instruction, next.arg1, "r4")
                    newIR.add(newIR1)
                    newIR.add(newIR2)
                    i++ // skip next instruction
                }*/
            }



            //if (it.instruction == "LOADVARASCONST") {
            //}
            //else {
                newIR.add(it)
            //}


            i++
        }


        debug1("=========\n  NEWIR  \n=========\n")
        newIR.forEach { debug1(it.toString() + "\n") }
        debug1("=========\n")


        return newIR
    }

    fun String.isVariable() = this.startsWith('$')
    fun String.isRegister() = this.matches(regexRegisterLiteral)

    fun ArrayList<String>.append(str: String) = if (str.endsWith(';'))
        this.add(str)
    else
        throw IllegalArgumentException("You missed a semicolon for: $str")


    /**
     * All the DECLAREs are expected to be at the head of the list
     */
    fun IRtoASM(ir: MutableList<IntermediateRepresentation>): List<String> {
        fun debug1(any: Any) { if (true) print(any) }


        fun String.asProperAsmData() = if (this.isVariable()) "@${this.drop(1)}" else this


        val ASMs = ArrayList<String>()

        val varTable = HashMap<String, String>()

        val irDeclares = ir.filter { it.instruction.startsWith("DECLARE") }
        // remove DECLAREs from the 'ir' list
        ir.removeAll { it.instruction.startsWith("DECLARE") }


        ASMs.append(".data;")


        irDeclares.forEach {
            when (it.instruction) {
                "DECLAREI" -> {
                    ASMs.append("INT ${it.arg1!!.drop(1)} 0;")
                    varTable.put(it.arg1!!.drop(1), "INT")
                }
                "DECLAREF" -> {
                    ASMs.append("FLOAT ${it.arg1!!.drop(1)} 0.0;")
                    varTable.put(it.arg1!!.drop(1), "FLOAT")
                }
                else -> TODO("Other declaration types (e.g. STRING, BYTES)")
            }
        }

        ASMs.append(".code;")

        ir.forEachIndexed { index, it ->
            val prev = if (index == 0) null else ir[index - 1]
            val next = if (index == ir.lastIndex) null else ir[index + 1]

            when (it.instruction) {
                "NOP" -> ASMs.append("NOP;")
                "MOV" -> {
                    if (it.arg2!!.isRegister()) {
                        // do nothing
                    }
                    else if (it.arg2!!.isVariable()) {
                        ASMs.append("LOADWORDIMEM r1, ${it.arg2!!.asProperAsmData()};")
                    }
                    else {
                        ASMs.append("LOADWORDI r1, ${it.arg2!!.asProperAsmData()};")
                    }


                    ASMs.append("STOREWORDIMEM r1, ${it.arg1!!.asProperAsmData()};")
                }
                in VMOpcodesRISC.threeArgsCmd -> {
                    // using with 2 stackpops
                    if (it.arg1 == null && it.arg2 == null && it.arg3 == null) {
                        ASMs.append("${it.instruction} r1, r2, r3;")
                    }
                    else {
                        // TODO are they even being used?
                        if (it.arg2!!.isRegister() && it.arg3!!.isRegister()) {
                            // uh... do nothing?
                        }
                        else {
                            if (it.arg2!!.isVariable()) {
                                ASMs.append("LOADWORDIMEM r1, ${it.arg2!!.asProperAsmData()};")
                            }
                            else {
                                ASMs.append("LOADWORDI r1, ${it.arg2!!.asProperAsmData()};")
                            }

                            if (it.arg3!!.isVariable()) {
                                ASMs.append("LOADWORDIMEM r2, ${it.arg3!!.asProperAsmData()};")
                            }
                            else {
                                ASMs.append("LOADWORDI r2, ${it.arg3!!.asProperAsmData()};")
                            }
                        }



                        if (it.arg1!!.isRegister()) {
                            if (next?.instruction == "RETURN") {
                                if (it.arg1!!.isRegister() && it.arg2!!.isRegister() && it.arg3!!.isRegister()) {
                                    ASMs.append("${it.instruction} r1, ${it.arg2!!}, ${it.arg3!!};")
                                }
                                else {
                                    ASMs.append("${it.instruction} r1, r1, r2;")
                                }
                            }
                            else {
                                if (it.arg1!!.isRegister() && it.arg2!!.isRegister() && it.arg3!!.isRegister()) {
                                    ASMs.append("${it.instruction} ${it.arg1!!}, ${it.arg2!!}, ${it.arg3!!};")
                                }
                                else {
                                    ASMs.append("${it.instruction} ${it.arg1}, r1, r2;")
                                }
                            }
                        }
                        else {
                            ASMs.append("${it.instruction} r3, r1, r2;")
                            ASMs.append("STOREWORDIMEM r3, ${it.arg1!!.asProperAsmData()};")
                        }
                    }
                }
                in jmpCommands -> {
                    ASMs.append("${it.instruction} @${it.arg1!!.drop(1)};")
                }
                "LABEL" -> {
                    ASMs.append(":${it.arg1!!.drop(1)};")
                }
                in irCmpInst -> {
                    /*if (it.arg1 == null || it.arg2 == null) {
                        throw IllegalArgumentException("One or two argument is null; ${it.instruction} ${it.arg1} ${it.arg2}")
                    }

                    val lhand = it.arg1!!
                    val rhand = it.arg2!!

                    val lhandType = if (lhand.isVariable())
                        varTable[lhand.drop(1)] ?: throw IllegalArgumentException("Undeclared variable: $lhand at line ${it.lineNum}")
                    else if (lhand.matchesFloatLiteral())
                        "FLOATLITERAL"
                    else if (lhand.matchesNumberLiteral())
                        "INTLITERAL"
                    else
                        throw IllegalArgumentException("Unknown literal type: $lhand at line ${it.lineNum}")

                    val rhandType = if (rhand.isVariable())
                        varTable[rhand.drop(1)] ?: throw IllegalArgumentException("Undeclared variable: $lhand at line ${it.lineNum}")
                    else if (lhand.matchesFloatLiteral())
                        "FLOATLITERAL"
                    else if (lhand.matchesNumberLiteral())
                        "INTLITERAL"
                    else
                        throw IllegalArgumentException("Unknown literal type: $rhand at line ${it.lineNum}")


                    //debug1("LH: $lhand, RH: $rhand, LHT: $lhandType, RHT: $rhandType\n")


                    val lIsLiteral = lhandType.endsWith("LITERAL")
                    val rIsLiteral = rhandType.endsWith("LITERAL")*/

                    val cmpInst = "CMP${it.instruction[it.instruction.lastIndex - 1]}${it.instruction[it.instruction.lastIndex]}"
                    // all the required variables must be popped to r2 and r3 previously

                    ASMs.append("$cmpInst r2, r3;")
                }
                "INLINEASM" -> {
                    ASMs.append(it.arg1!!)
                }
                "FUNCDEF" -> {
                    ASMs.append("JMP @\$ENDFUNCDEF_${it.arg1!!};")
                    ASMs.append(":${it.arg1!!};")
                }
                "RETURN" -> {
                    if (it.arg1 != null) {
                        if (it.arg2 != null) {
                            throw InternalError("RETURN with 2 or more args -- '$it'")
                        }

                        ASMs.append("LOADWORDI r8, ${it.arg1};")
                        ASMs.append("RETURN;")
                    }
                    else {
                        ASMs.append("RETURN;")
                    }
                }
                "FUNCCALL" -> {
                    ASMs.append("LOADWORDI r1, @${it.arg1!!};")
                    ASMs.append("JSR r1;")
                }
                "ENDFUNCDEF" -> {
                    if (prev?.instruction != "RETURN") {
                        ASMs.append("RETURN;") // RETURN guard
                    }
                    ASMs.append(":\$ENDFUNCDEF_${it.arg1!!};")
                }
                "DEREFPTR" -> {
                    if (!it.arg1!!.isRegister()) throw InternalError("arg1 is not a register (${it.arg1})")

                    ASMs.append("LOADBYTEI r5, 0;")
                    ASMs.append("LOADWORD ${it.arg1!!}, ${it.arg1!!}, r5;")
                }
                "STACKPUSH" -> {
                    ASMs.append("PUSH r1;")
                }
                "STACKPOP" -> {
                    ASMs.append("POP ${it.arg1!!};")
                }
                "STACKPUSHIPTR" -> {
                    ASMs.append("PUSHWORDI ${it.arg1!!.asProperAsmData()};")
                }
                "STACKPUSHICONST" -> {
                    ASMs.append("LOADWORDI r1, ${it.arg1!!};")
                    ASMs.append("PUSH r1;")
                }
                "ASSIGNCONST" -> {
                    ASMs.append("LOADBYTEI r3, 0;")
                    ASMs.append("STOREWORD r2, r1, r3;")
                }
                "CONST" -> {
                    TODO("Please check! Did you possibly mean ASSIGNCONST?")
                    //ASMs.append("LOADWORDI r1, ${it.arg1!!};")
                }
                "LOADVARASCONST" -> {
                    ASMs.append("LOADWORDIMEM r1, ${it.arg1!!.asProperAsmData()};")
                    ASMs.append("PUSH r1;")
                }
                else -> throw InternalError("Unknown IR: ${it.instruction}")
            }
        }

        ASMs.append("HALT;")


        debug1("=========\n   ASM   \n=========\n")
        ASMs.forEach { debug1(it + "\n") }
        debug1("=========\n")



        return ASMs
    }




    ///////////////////////////////////////////////////
    // publicising things so that they can be tested //
    ///////////////////////////////////////////////////

    fun resolveTypeString(type: String, isPointer: Boolean = false): ReturnType {
        /*val isPointer = type.endsWith('*') or type.endsWith("_ptr") or isPointer

        return when (type) {
            "void" -> if (isPointer) ReturnType.NOTHING_PTR else ReturnType.NOTHING
            "char" -> if (isPointer) ReturnType.CHAR_PTR else ReturnType.CHAR
            "short" -> if (isPointer) ReturnType.SHORT_PTR else ReturnType.SHORT
            "int" -> if (isPointer) ReturnType.INT_PTR else ReturnType.INT
            "long" -> if (isPointer) ReturnType.LONG_PTR else ReturnType.LONG
            "float" -> if (isPointer) ReturnType.FLOAT_PTR else ReturnType.FLOAT
            "double" -> if (isPointer) ReturnType.DOUBLE_PTR else ReturnType.DOUBLE
            "bool" -> if (isPointer) ReturnType.BOOL_PTR else ReturnType.BOOL
            else -> if (isPointer) ReturnType.STRUCT_PTR else ReturnType.STRUCT
        }*/
        return when (type.toLowerCase()) {
            "void" -> ReturnType.NOTHING
            "int" -> ReturnType.INT
            "float" -> ReturnType.FLOAT
            else -> throw SyntaxError("Unknown type: $type")
        }
    }



    fun asTreeNode(lineNumber: Int, tokens: List<String>): SyntaxTreeNode {
        fun splitContainsValidVariablePreword(split: List<String>): Int {
            var ret = -1
            for (stage in 0..minOf(3, split.lastIndex)) {
                if (validVariablePreword.contains(split[stage])) ret += 1
            }
            return ret
        }
        fun debug1(any: Any?) { if (true) println(any) }




        // contradiction: auto AND extern

        val firstAssignIndex = tokens.indexOf("=")
        val firstLeftParenIndex = tokens.indexOf("(")
        val lastRightParenIndex = tokens.lastIndexOf(")")
        val functionCallTokens: List<String>? =
                if (firstLeftParenIndex == -1)
                    null
                else if (firstAssignIndex == -1)
                    tokens.subList(0, firstLeftParenIndex)
                else
                    tokens.subList(firstAssignIndex + 1, firstLeftParenIndex)
        val functionCallTokensContainsTokens = if (functionCallTokens == null) false else
            (functionCallTokens.map { if (splittableTokens.contains(it)) 1 else 0 }.sum() > 0)
        // if TRUE, it's not a function call/def (e.g. foobar = funccall ( arg arg arg )


        debug1("!!##[asTreeNode] line $lineNumber; functionCallTokens: $functionCallTokens; contains tokens?: $functionCallTokensContainsTokens")

        /////////////////////////////
        // unwrap (((((parens))))) //
        /////////////////////////////
        // FIXME ( asrtra ) + ( feinov ) forms are errenously stripped its paren away
        /*if (tokens.first() == "(" && tokens.last() == ")") {
            var wrapSize = 1
            while (tokens[wrapSize] == "(" && tokens[tokens.lastIndex - wrapSize] == ")") {
                wrapSize++
            }
            return asTreeNode(lineNumber, tokens.subList(wrapSize, tokens.lastIndex - wrapSize + 1))
        }*/


        debug1("!!##[asTreeNode] input token: $tokens")


        ////////////////////////////
        // as Function Definition //
        ////////////////////////////
        if (!functionCallTokensContainsTokens && functionCallTokens != null && functionCallTokens.size >= 2 && functionCallTokens.size <= 4) { // e.g. int main , StructName fooo , extern void doSomething , extern unsigned StructName uwwse
            val actualFuncType = functionCallTokens[functionCallTokens.lastIndex - 1]
            val returnType = resolveTypeString(actualFuncType)
            val funcName = functionCallTokens.last()

            // get arguments
            // int  *  index  ,  bool  *  *  isSomething  ,  double  someNumber  , ...
            val argumentsDef = tokens.subList(firstLeftParenIndex + 1, lastRightParenIndex)
            val argTypeNamePair = ArrayList<Pair<ReturnType, String?>>()


            debug1("!! func def args")
            debug1("!! <- $argumentsDef")


            // chew it down to more understandable format
            var typeHolder: ReturnType? = null
            var nameHolder: String? = null
            argumentsDef.forEachIndexed { index, token ->
                if (argumentDefBadTokens.contains(token)) {
                    throw IllegalTokenException("at line $lineNumber -- illegal token '$token' used on function argument definition")
                }


                if (token == ",") {
                    if (typeHolder == null) throw SyntaxError("at line $lineNumber -- type not specified")
                    argTypeNamePair.add(typeHolder!! to nameHolder)
                    typeHolder = null
                    nameHolder = null
                }
                else if (token == "*") {
                    if (typeHolder == null) throw SyntaxError("at line $lineNumber -- type not specified")
                    typeHolder = resolveTypeString(typeHolder.toString().toLowerCase(), true)
                }
                else if (typeHolder == null) {
                    typeHolder = resolveTypeString(token)
                }
                else if (typeHolder != null) {
                    nameHolder = token


                    if (index == argumentsDef.lastIndex) {
                        argTypeNamePair.add(typeHolder!! to nameHolder)
                    }
                }
                else {
                    throw InternalError("uncaught shit right there")
                }
            }


            debug1("!! -> $argTypeNamePair")
            debug1("================================")


            val funcDefNode = SyntaxTreeNode(ExpressionType.FUNCTION_DEF, returnType, funcName, lineNumber)
            //if (returnType == ReturnType.STRUCT || returnType == ReturnType.STRUCT_PTR) {
            //    funcDefNode.structName = actualFuncType
            //}

            argTypeNamePair.forEach { val (type, name) = it
                // TODO struct and structName
                val funcDefArgNode = SyntaxTreeNode(ExpressionType.FUNC_ARGUMENT_DEF, type, name, lineNumber, isPartOfArgumentsNode = true)
                funcDefNode.addArgument(funcDefArgNode)
            }


            return funcDefNode
        }
        //////////////////////
        // as Function Call // (also works as keyworded code block (e.g. if, for, while))
        //////////////////////
        else if (tokens.size >= 3 /* foo, (, ); guaranteed to be at least three */ && tokens[1] == "(" &&
                !functionCallTokensContainsTokens && functionCallTokens != null && functionCallTokens.size == 1) { // e.g. if ( , while ( ,
            val funcName = functionCallTokens.last()


            // get arguments
            // complex_statements , ( value = funccall ( arg ) ) , "string,arg" , 42f
            val argumentsDef = tokens.subList(firstLeftParenIndex + 1, lastRightParenIndex)


            debug1("!! func call args:")
            debug1("!! <- $argumentsDef")


            // split into tokens list, splitted by ','
            val functionCallArguments = ArrayList<ArrayList<String>>() // double array is intended (e.g. [["tsrasrat"], ["42"], [callff, (, "wut", )]] for input ("tsrasrat", "42", callff("wut"))
            var tokensHolder = ArrayList<String>()
            argumentsDef.forEachIndexed { index, token ->
                if (index == argumentsDef.lastIndex) {
                    tokensHolder.add(token)
                    functionCallArguments.add(tokensHolder)
                    tokensHolder = ArrayList<String>() // can't reuse; must make new one
                }
                else if (token == ",") {
                    if (tokensHolder.isEmpty()) {
                        throw SyntaxError("at line $lineNumber -- misplaced comma")
                    }
                    else {
                        functionCallArguments.add(tokensHolder)
                        tokensHolder = ArrayList<String>() // can't reuse; must make new one
                    }
                }
                else {
                    tokensHolder.add(token)
                }
            }


            debug1("!! -> $functionCallArguments")


            val funcCallNode = SyntaxTreeNode(ExpressionType.FUNCTION_CALL, null, funcName, lineNumber)

            functionCallArguments.forEach {
                debug1("!! forEach $it")

                debug1("call from asTreeNode().asFunctionCall")
                val argNodeLeaf = asTreeNode(lineNumber, it); argNodeLeaf.isPartOfArgumentsNode = true
                funcCallNode.addArgument(argNodeLeaf)
            }


            debug1("================================")


            return funcCallNode
        }
        ////////////////////////
        // as Var Call / etc. //
        ////////////////////////
        else {
            // filter illegal lines (absurd keyword usage)
            tokens.forEach {
                if (codeBlockKeywords.contains(it)) {
                    // code block without argumenets; give it proper parens and redirect
                    val newTokens = tokens.toMutableList()
                    if (newTokens.size != 1) {
                        throw SyntaxError("Number of tokens is not 1 (got size of ${newTokens.size}): ${newTokens}")
                    }

                    newTokens.add("("); newTokens.add(")")
                    debug1("call from asTreeNode().filterIllegalLines")
                    return asTreeNode(lineNumber, newTokens)
                }
            }

            ///////////////////////
            // Bunch of literals //
            ///////////////////////
            if (tokens.size == 1) {
                val word = tokens[0]


                debug1("!! literal, token: '$word'")
                //debug1("================================")


                // filtered String literals
                if (word.startsWith('"') && word.endsWith('"')) {
                    val leafNode = SyntaxTreeNode(ExpressionType.LITERAL_LEAF, ReturnType.DATABASE, null, lineNumber)
                    leafNode.literalValue = tokens[0].substring(1, tokens[0].lastIndex) + nullchar
                    return leafNode
                }
                // bool literals
                else if (word.matches(regexBooleanWhole)) {
                    val leafNode = SyntaxTreeNode(ExpressionType.LITERAL_LEAF, ReturnType.INT, null, lineNumber)
                    leafNode.literalValue = word == "true"
                    return leafNode
                }
                // hexadecimal literals
                else if (word.matches(regexHexWhole)) {
                    val leafNode = SyntaxTreeNode(
                            ExpressionType.LITERAL_LEAF,
                            ReturnType.INT,
                            null, lineNumber
                    )
                    try {
                        leafNode.literalValue =
                            word.replace(Regex("""[^0-9A-Fa-f]"""), "").toLong(16).and(0xFFFFFFFFL).toInt()
                    }
                    catch (e: NumberFormatException) {
                        throw IllegalTokenException("at line $lineNumber -- $word is too large to be represented as ${leafNode.returnType?.toString()?.toLowerCase()}")
                    }

                    return leafNode
                }
                // octal literals
                else if (word.matches(regexOctWhole)) {
                    val leafNode = SyntaxTreeNode(
                            ExpressionType.LITERAL_LEAF,
                            ReturnType.INT,
                            null, lineNumber
                    )
                    try {
                        leafNode.literalValue =
                            word.replace(Regex("""[^0-7]"""), "").toLong(8).and(0xFFFFFFFFL).toInt()
                    }
                    catch (e: NumberFormatException) {
                        throw IllegalTokenException("at line $lineNumber -- $word is too large to be represented as ${leafNode.returnType?.toString()?.toLowerCase()}")
                    }

                    return leafNode
                }
                // binary literals
                else if (word.matches(regexBinWhole)) {
                    val leafNode = SyntaxTreeNode(
                            ExpressionType.LITERAL_LEAF,
                            ReturnType.INT,
                            null, lineNumber
                    )
                    try {
                        leafNode.literalValue =
                            word.replace(Regex("""[^01]"""), "").toLong(2).and(0xFFFFFFFFL).toInt()
                    }
                    catch (e: NumberFormatException) {
                        throw IllegalTokenException("at line $lineNumber -- $word is too large to be represented as ${leafNode.returnType?.toString()?.toLowerCase()}")
                    }

                    return leafNode
                }
                // int literals
                else if (word.matches(regexIntWhole)) {
                    val leafNode = SyntaxTreeNode(
                            ExpressionType.LITERAL_LEAF,
                            ReturnType.INT,
                            null, lineNumber
                    )
                    try {
                        leafNode.literalValue =
                            word.replace(Regex("""[^0-9]"""), "").toLong().and(0xFFFFFFFFL).toInt()
                    }
                    catch (e: NumberFormatException) {
                        throw IllegalTokenException("at line $lineNumber -- $word is too large to be represented as ${leafNode.returnType?.toString()?.toLowerCase()}")
                    }

                    return leafNode
                }
                // floating point literals
                else if (word.matches(regexFPWhole)) {
                    val leafNode = SyntaxTreeNode(
                            ExpressionType.LITERAL_LEAF,
                            ReturnType.FLOAT,
                            null, lineNumber
                    )
                    try {
                        leafNode.literalValue = if (word.endsWith('F', true))
                            word.slice(0..word.lastIndex - 1).toDouble() // DOUBLE when C-flat; replace it with 'toFloat()' if you're standard C
                        else
                            word.toDouble()
                    }
                    catch (e: NumberFormatException) {
                        throw InternalError("at line $lineNumber, while parsing '$word' as Double")
                    }

                    return leafNode
                }
                //////////////////////////////////////
                // variable literal (VARIABLE_LEAF) // usually function call arguments
                //////////////////////////////////////
                else if (word.matches(regexVarNameWhole)) {
                    val leafNode = SyntaxTreeNode(ExpressionType.VARIABLE_READ, null, word, lineNumber)
                    return leafNode
                }
            }
            else {

                /////////////////////////////////////////////////
                // return something; goto somewhere (keywords) //
                /////////////////////////////////////////////////
                if (tokens[0] == "goto" || tokens[0] == "comefrom") {
                    val nnode = SyntaxTreeNode(
                            ExpressionType.FUNCTION_CALL,
                            null,
                            tokens[0],
                            lineNumber
                    )

                    val rawTreeNode = tokens[1].toRawTreeNode(lineNumber); rawTreeNode.isPartOfArgumentsNode = true
                    nnode.addArgument(rawTreeNode)
                    return nnode
                }
                else if (tokens[0] == "return") {
                    val returnNode = SyntaxTreeNode(
                            ExpressionType.FUNCTION_CALL,
                            null,
                            "return",
                            lineNumber
                    )
                    val node = turnInfixTokensIntoTree(lineNumber, tokens.subList(1, tokens.lastIndex + 1)); node.isPartOfArgumentsNode = true
                    returnNode.addArgument(node)
                    return returnNode
                }

                //////////////////////////
                // variable declaration //
                //////////////////////////
                // extern auto struct STRUCTID foobarbaz
                // extern auto int foobarlulz
                else if (splitContainsValidVariablePreword(tokens) != -1) {
                    val prewordIndex = splitContainsValidVariablePreword(tokens)
                    val realType = tokens[prewordIndex]

                    try {
                        val hasAssignment: Boolean

                        if (realType == "struct")
                            hasAssignment = tokens.lastIndex > prewordIndex + 2
                        else
                            hasAssignment = tokens.lastIndex > prewordIndex + 1


                        // deal with assignment
                        if (hasAssignment) {
                            // TODO support type_ptr_ptr_ptr...

                            // use turnInfixTokensIntoTree and inject it to assignment node
                            val isPtrType = tokens[1] == "*"
                            val typeStr = tokens[0] + if (isPtrType) "_ptr" else ""

                            val tokensWithoutType = if (isPtrType)
                                tokens.subList(2, tokens.size)
                            else
                                tokens.subList(1, tokens.size)

                            val infixNode = turnInfixTokensIntoTree(lineNumber, tokensWithoutType)


                            //#_assignvar(SyntaxTreeNode<RawString> varname, SyntaxTreeNode<RawString> vartype, SyntaxTreeNode value)

                            val returnNode = SyntaxTreeNode(ExpressionType.FUNCTION_CALL, ReturnType.NOTHING, "#_assignvar", lineNumber)

                            val nameNode = tokensWithoutType.first().toRawTreeNode(lineNumber); nameNode.isPartOfArgumentsNode = true
                            returnNode.addArgument(nameNode)

                            val typeNode = typeStr.toRawTreeNode(lineNumber); typeNode.isPartOfArgumentsNode = true
                            returnNode.addArgument(typeNode)

                            infixNode.isPartOfArgumentsNode = true
                            returnNode.addArgument(infixNode)

                            return returnNode
                        }
                        else {
                            // #_declarevar(SyntaxTreeNode<RawString> varname, SyntaxTreeNode<RawString> vartype)

                            val leafNode = SyntaxTreeNode(ExpressionType.INTERNAL_FUNCTION_CALL, ReturnType.NOTHING, "#_declarevar", lineNumber)

                            val valueNode = tokens[1].toRawTreeNode(lineNumber); valueNode.isPartOfArgumentsNode = true
                            leafNode.addArgument(valueNode)

                            val typeNode = tokens[0].toRawTreeNode(lineNumber); typeNode.isPartOfArgumentsNode = true
                            leafNode.addArgument(typeNode)

                            return leafNode
                        }
                    }
                    catch (syntaxFuck: ArrayIndexOutOfBoundsException) {
                        throw SyntaxError("at line $lineNumber -- missing statement(s)")
                    }
                }
                else {
                    debug1("!! infix in: $tokens")

                    // infix notation
                    return turnInfixTokensIntoTree(lineNumber, tokens)
                }
                TODO()
            } // end if (tokens.size == 1)


            TODO()
        }
    }

    fun turnInfixTokensIntoTree(lineNumber: Int, tokens: List<String>): SyntaxTreeNode {
        // based on https://stackoverflow.com/questions/1946896/conversion-from-infix-to-prefix

        // FIXME: differentiate parens for function call from grouping

        fun debug(any: Any) { if (true) println(any) }


        fun precedenceOf(token: String): Int {
            if (token == "(" || token == ")") return -1

            operatorsHierarchyInternal.forEachIndexed { index, hashSet ->
                if (hashSet.contains(token)) return index
            }

            throw SyntaxError("at $lineNumber -- unknown operator '$token'")
        }

        val tokens = tokens.reversed()


        val stack = Stack<String>()
        val treeArgsStack = Stack<Any>()

        fun addToTree(token: String) {
            debug("!! adding '$token'")

            fun argsCountOf(operator: String) = if (unaryOps.contains(operator)) 1 else 2
            fun popAsTree(): SyntaxTreeNode {
                val rawElem = treeArgsStack.pop()

                if (rawElem is String) {
                    debug("call from turnInfixTokensIntoTree().addToTree().popAsTree()")
                    return asTreeNode(lineNumber, listOf(rawElem))
                }
                else if (rawElem is SyntaxTreeNode)
                    return rawElem
                else
                    throw InternalError("I said you to put String or SyntaxTreeNode only; what's this? ${rawElem.javaClass.simpleName}?")
            }


            if (!operatorsNoOrder.contains(token)) {
                debug("-> not a operator; pushing to args stack")
                treeArgsStack.push(token)
            }
            else {
                debug("-> taking ${argsCountOf(token)} value(s) from stack")

                val treeNode = SyntaxTreeNode(ExpressionType.FUNCTION_CALL, null, token, lineNumber)
                repeat(argsCountOf(token)) {
                    val poppedTree = popAsTree(); poppedTree.isPartOfArgumentsNode = true
                    treeNode.addArgument(poppedTree)
                }
                treeArgsStack.push(treeNode)
            }
        }


        debug("reversed tokens: $tokens")


        tokens.forEachIndexed { index, rawToken ->
            // contextually decide what is real token
            val token =
                    // if prev token is operator (used '+' as token list is reversed)
                    if (index == tokens.lastIndex || operatorsNoOrder.contains(tokens[index + 1])) {
                        if (rawToken == "+") "#_unaryplus"
                        else if (rawToken == "-") "#_unaryminus"
                        else if (rawToken == "&") "#_addressof"
                        else if (rawToken == "*") "#_ptrderef"
                        else if (rawToken == "++") "#_preinc"
                        else if (rawToken == "--") "#_predec"
                        else rawToken
                    }
                    else rawToken


            if (token == ")") {
                stack.push(token)
            }
            else if (token == "(") {
                while (stack.isNotEmpty()) {
                    val t = stack.pop()
                    if (t == ")") break

                    addToTree(t)
                }
            }
            else if (!operatorsNoOrder.contains(token)) {
                addToTree(token)
            }
            else {
                // XXX: associativity should be considered here
                // https://en.wikipedia.org/wiki/Operator_associativity
                while (stack.isNotEmpty() && precedenceOf(stack.peek()) > precedenceOf(token)) {
                    addToTree(stack.pop())
                }
                stack.add(token)
            }
        }

        while (stack.isNotEmpty()) {
            addToTree(stack.pop())
        }


        if (treeArgsStack.size != 1) {
            throw InternalError("Stack size is wrong -- supposed to be 1, but it's ${treeArgsStack.size}\nstack: $treeArgsStack")
        }
        debug("finalised tree:\n${treeArgsStack.peek()}")


        return if (treeArgsStack.peek() is SyntaxTreeNode)
                treeArgsStack.peek() as SyntaxTreeNode
        else {
            debug("call from turnInfixTokensIntoTree().if (treeArgsStack.peek() is SyntaxTreeNode).else")
            asTreeNode(lineNumber, listOf(treeArgsStack.peek() as String))
        }
    }



    data class LineStructure(var lineNum: Int, var depth: Int, val tokens: MutableList<String>)

    class SyntaxTreeNode(
            val expressionType: ExpressionType,
            val returnType: ReturnType?, // STATEMENT, LITERAL_LEAF: valid ReturnType; VAREABLE_LEAF: always null
            var name: String?,
            val lineNumber: Int, // used to generate error message
            val isRoot: Boolean = false,
            //val derefDepth: Int = 0 // how many ***s are there for pointer
            var isPartOfArgumentsNode: Boolean = false
    ) {

        var literalValue: Any? = null // for LITERALs only
        var structName: String? = null // for STRUCT return type

        val arguments = ArrayList<SyntaxTreeNode>() // for FUNCTION, CODE_BLOCK
        val statements = ArrayList<SyntaxTreeNode>()

        var depth: Int? = null

        fun addArgument(node: SyntaxTreeNode) {
            arguments.add(node)
        }
        fun addStatement(node: SyntaxTreeNode) {
            statements.add(node)
        }


        fun updateDepth() {
            if (!isRoot) throw Error("Updating depth only make sense when used as root")

            this.depth = 0

            arguments.forEach { it._updateDepth(1) }
            statements.forEach { it._updateDepth(1) }
        }

        private fun _updateDepth(recursiveDepth: Int) {
            this.depth = recursiveDepth

            arguments.forEach { it._updateDepth(recursiveDepth + 1) }
            statements.forEach { it._updateDepth(recursiveDepth + 1) }
        }

        fun expandImplicitEnds() {
            if (!isRoot) throw Error("Expanding implicit 'end's only make sense when used as root")

            // fixme no nested ifs
            statements.forEach { it.statements.forEach { it._expandImplicitEnds() } } // root level if OF FUNCDEF
            statements.forEach { it._expandImplicitEnds() } // root level if
        }

        private fun _expandImplicitEnds() {
            if (this.name in functionsImplicitEnd) {
                this.statements.add(SyntaxTreeNode(
                        ExpressionType.INTERNAL_FUNCTION_CALL, null, "end${this.name}", this.lineNumber, this.isRoot
                ))
            }
            else if (this.expressionType == ExpressionType.FUNCTION_DEF) {
                val endfuncdef = SyntaxTreeNode(
                        ExpressionType.INTERNAL_FUNCTION_CALL, null, "endfuncdef", this.lineNumber, this.isRoot
                )
                endfuncdef.addArgument(SyntaxTreeNode(ExpressionType.FUNC_ARGUMENT_DEF, null, this.name!!, this.lineNumber, isPartOfArgumentsNode = true))
                this.statements.add(endfuncdef)
            }
        }

        val isLeaf: Boolean
            get() = expressionType.toString().endsWith("_LEAF") ||
                    (arguments.isEmpty() && statements.isEmpty())

        override fun toString() = toStringRepresentation(0)

        private fun toStringRepresentation(depth: Int): String {
            val header = "│ ".repeat(depth) + if (isRoot) "⧫AST (name: $name)" else if (isLeaf) "◊AST$depth (name: $name)" else "☐AST$depth (name: $name)"
            val lines = arrayListOf(
                    header,
                    "│ ".repeat(depth+1) + "ExprType : $expressionType",
                    "│ ".repeat(depth+1) + "RetnType : $returnType",
                    "│ ".repeat(depth+1) + "LiteralV : '$literalValue'",
                    "│ ".repeat(depth+1) + "isArgNod : $isPartOfArgumentsNode"
            )

            if (!isLeaf) {
                lines.add("│ ".repeat(depth+1) + "# of arguments: ${arguments.size}")
                arguments.forEach { lines.add(it.toStringRepresentation(depth + 1)) }
                lines.add("│ ".repeat(depth+1) + "# of statements: ${statements.size}")
                statements.forEach { lines.add(it.toStringRepresentation(depth + 1)) }
            }

            lines.add("│ ".repeat(depth) + "╘" + "═".repeat(header.length - 1 - 2*depth))

            val sb = StringBuilder()
            lines.forEachIndexed { index, line ->
                sb.append(line)
                if (index < lines.lastIndex) { sb.append("\n") }
            }

            return sb.toString()
        }
    }

    private fun String.toRawTreeNode(lineNumber: Int): SyntaxTreeNode {
        val node = SyntaxTreeNode(ExpressionType.LITERAL_LEAF, ReturnType.DATABASE, null, lineNumber)
        node.literalValue = this
        return node
    }

    enum class ExpressionType {
        FUNCTION_DEF, FUNC_ARGUMENT_DEF, // expect Arguments and Statements

        INTERNAL_FUNCTION_CALL,
        FUNCTION_CALL, // expect Arguments and Statements
        // the case of OPERATOR CALL //
        // returnType: variable type; name: "="; TODO add description for STRUCT
        // arg0: name of the variable (String)
        // arg1: (optional) assigned value, either LITERAL or FUNCTION_CALL or another OPERATOR CALL
        // arg2: (if STRUCT) struct identifier (String)

        LITERAL_LEAF, // literals, also act as a leaf of the tree; has returnType of null
        VARIABLE_WRITE, // CodeL; loads memory address to be written onto
        VARIABLE_READ   // CodeR; the actual value stored in its memory address
    }
    enum class ReturnType {
        INT, FLOAT,
        NOTHING, // null
        DATABASE // array of bytes, also could be String
    }


    class PreprocessorRules {
        private val kwdRetPair = HashMap<String, String>()

        fun addDefinition(keyword: String, ret: String) {
            kwdRetPair[keyword] = ret
        }
        fun removeDefinition(keyword: String) {
            kwdRetPair.remove(keyword)
        }
        fun forEachKeywordForTokens(action: (String, String) -> Unit) {
            kwdRetPair.forEach { key, value ->
                action("""[ \t\n]+""" + key + """(?=[ \t\n;]+)""", value)
            }
        }
    }

    /**
     * Notation rules:
     * - Variable: prepend with '$' (e.g. $duplicantsCount) // totally not a Oxygen Not Included reference
     * - Register: prepend with 'r' (e.g. r3)
     */
    data class IntermediateRepresentation(
            var lineNum: Int,
            var instruction: String = "DUMMY",
            var arg1: String? = null,
            var arg2: String? = null,
            var arg3: String? = null,
            var arg4: String? = null,
            var arg5: String? = null
    ) {
        constructor(other: IntermediateRepresentation) : this(
                other.lineNum,
                other.instruction,
                other.arg1,
                other.arg2,
                other.arg3,
                other.arg4,
                other.arg5
        )

        override fun toString(): String {
            val sb = StringBuilder()
            sb.append(instruction)
            arg1?.let { sb.append(" $it") }
            arg2?.let { sb.append(", $it") }
            arg3?.let { sb.append(", $it") }
            arg4?.let { sb.append(", $it") }
            arg5?.let { sb.append(", $it") }
            sb.append("; (at line $lineNum)")
            return sb.toString()
        }
    }


}

open class SyntaxError(msg: String? = null) : Exception(msg)
class IllegalTokenException(msg: String? = null) : SyntaxError(msg)
class UnresolvedReference(msg: String? = null) : SyntaxError(msg)
class UndefinedStatement(msg: String? = null) : SyntaxError(msg)
class DuplicateDefinition(msg: String? = null) : SyntaxError(msg)
class PreprocessorErrorMessage(msg: String) : SyntaxError(msg)
