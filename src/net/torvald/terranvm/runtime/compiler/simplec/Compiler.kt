package net.torvald.terranvm.runtime.compiler.simplec

/**
 * A compiler for SimpleC language that compiles into TBASOpcode.
 *
 * # Disclaimer
 *
 * 0. This compiler, BY NO MEANS, guarantees to implement standard C language; c'mon, $100+ for a standard document?
 * 1. I suck at code and test. Please report bugs!
 * 2. Please move along with my terrible sense of humour.
 *
 * # About SimpleC
 *
 * SimpleC is an simplified version of C. It adapts Java's philosophy that thinks unsigned math is crystal meth.
 *
 * ## New Features
 *
 * - New data type ```bool```
 * - Infinite loop using ```forever``` block. You can still use ```for (;;)```, ```while (true)```
 * - Counted simple loop (without loop counter ref) using ```repeat``` block
 *
 *
 * ## Important Changes from C
 *
 * - All function definition must specify return type, even if the type is ```void```.
 * - ```float``` is same as ```double```.
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
 * Created by minjaesong on 2017-06-04.
 */
/*object SimpleC {

    private val structOpen = '{'
    private val structClose = '}'

    private val parenOpen = '('
    private val parenClose = ')'

    private val genericTokenSeparator = Regex("""[ \t]+""")

    private val nullchar = 0.toChar()

    private val infiniteLoops = arrayListOf<Regex>(
            Regex("""while\(true\)"""), // whitespaces are filtered on preprocess
            Regex("""for\([\s]*;[\s]*;[\s]*\)""")
    ) // more types of infinite loops are must be dealt with (e.g. while (0xFFFFFFFF < 0x7FFFFFFF))

    private val regexBooleanWhole = Regex("""^(true|false)$""")
    private val regexHexWhole = Regex("""^(0[Xx][0-9A-Fa-f_]+?)$""")
    private val regexOctWhole = Regex("""^(0[0-7_]+)$""")
    private val regexBinWhole = Regex("""^(0[Bb][01_]+)$""")
    private val regexFPWhole =  Regex("""^([0-9]*\.[0-9]+([Ee][-+]?[0-9]+)?[Ff]?|[0-9]+\.?([Ee][-+]?[0-9]+)?[Ff]?)$""")
    private val regexIntWhole = Regex("""^([0-9_]+[Ll]?)$""")

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
            // SimpleC bool
            "bool","true","false",
            // SimpleC code blocks
            "forever","repeat"

            // SimpleC dropped keywords (keywords that won't do anything/behave differently than C95, etc.):
            //  - auto, register, signed, unsigned, volatile, static: not implemented; WILL THROW ERROR
            //  - float: will act same as double
            //  - extern: everthing is global, anyway; WILL THROW ERROR

            // SimpleC exclusive keywords:
            //  - bool, true, false: bool algebra
    )
    private val unsupportedKeywords = hashSetOf(
            "auto","register","signed","unsigned","volatile","static",
            "extern"
    )
    private val operatorsHierarchyInternal = arrayOf(
            // opirator precedence in internal format (#_nameinlowercase)  PUT NO PARENS HERE!   TODO [ ] are allowed? pls chk
            // most important
            hashSetOf("++","--","[", "]",".","->"),
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
            "extern" // not used in SimpleC
    )
    private val funcTypes = hashSetOf(
            "char", "short", "int", "long", "float", "double", "bool", "void"
    )
    private val varAnnotations = hashSetOf(
            "auto", // does nothing; useless even in C (it's derived from B language, actually)
            "extern", // not used in SimpleC
            "const",
            "register" // not used in SimpleC
    )
    private val varTypes = hashSetOf(
            "struct", "char", "short", "int", "long", "float", "double", "bool"
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
            "#_assignvar", "#_plusassignvar", "#_minusassignvar", // #_assignvar(SyntaxTreeNode<RawString> varname, SyntaxTreeNode<RawString> vartype, SyntaxTreeNode value)
            "#_declarevar" // #_declarevar(SyntaxTreeNode<RawString> varname, SyntaxTreeNode<RawString> vartype)
    )
    private val functionWithSingleArgNoParen = hashSetOf(
            "return", "goto"
    )


    fun sizeofPrimitive(type: String) = when (type) {
        "char" -> 1
        "short" -> 2
        "int" -> 4
        "long" -> 8
        "float" -> 8 // in SimpleC, float is same as double
        "double" -> 8
        "bool" -> 1
        "void" -> 1 // GCC feature
        else -> throw IllegalArgumentException("Unknown primitive type: $type")
    }

    // compiler options
    var useDigraph = false
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


        val tree = tokenise(preprocess(program))
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

        if (useTrigraph) {
            trigraphs.forEach { from, to ->
                program = program.replace(from, to)
            }
        }


        program.lines().forEach {
            if (it.startsWith('#')) {
                val tokens = it.split(genericTokenSeparator)
                val cmd = tokens[0]

                if (!preprocessorKeywords.contains(cmd)) {
                    throw UndefinedStatement("Preprocessor macro $cmd is not supported.")
                }
                else {
                    TODO()
                }
            }
        }


        return program
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
                throw SyntaxError("at line $currentProgramLineNumber -- line break used inside of string literal")
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


            charCtr++
        }


        return lineStructures
    }

    fun buildTree(lineStructures: List<LineStructure>): SyntaxTreeNode {
        fun debug1(any: Any) { if (false) println(any) }


        ///////////////////////////
        // STEP 1. Create a tree //
        ///////////////////////////

        val ASTroot = SyntaxTreeNode(ExpressionType.FUNCTION_DEF, ReturnType.VOID, name = "root", isRoot = true, lineNumber = 1)

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

            val nodeBuilt = asTreeNode(lineNum, tokens)
            getWorkingNode().addStatement(nodeBuilt)


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

    fun traverseAST(root: SyntaxTreeNode): String? {
        // make postfix
        // strat:
        //  visitNode() -- ".func; :funcName;"
        //  for each statements: recurse;
        //  for each arguments: recurse;
        //  visitNode() -- "return;"
        //  (return)


        val pileOfLeaves = Stack<SyntaxTreeNode>()
        val assembly = StringBuilder()
        //var currentFunctionDef: CFunction

        fun traverse1(node: SyntaxTreeNode) {

            // visit 1
            when (node.expressionType) {
                ExpressionType.FUNCTION_DEF -> {
                    if (funcNameDict.contains(node.name!!)) {
                        throw DuplicatedDefinition("at line ${node.lineNumber} -- function '${node.name}' already defined")
                    }

                    currentFunctionDef = CFunction(node.name!!, node.returnType!!)
                    funcDict.add(currentFunctionDef)
                    funcNameDict.add(node.name!!)

                    assembly.append(".func;\n:${node.name!!};\n")
                }
                ExpressionType.FUNCTION_CALL -> {
                    if (funcNameDict.contains(node.name!!)) {
                        val args = ArrayList<SyntaxTreeNode>()
                        repeat(node.arguments.size) { args.add(pileOfLeaves.pop()) }
                        getFuncByName(node.name!!)!!.call(args.toTypedArray())
                    }
                    else {
                        throw UnresolvedReference("at line ${node.lineNumber} -- function '${node.name}'")
                    }
                }
                ExpressionType.FUNC_ARGUMENT_DEF -> {
                    try {
                        val arg = if (node.returnType!! == ReturnType.STRUCT || node.returnType == ReturnType.STRUCT_PTR) {
                            TODO()
                            //structSearchByName(node)
                        }
                        else {
                            CPrimitive(node.name!!, node.returnType, null)
                        }
                    }
                    catch (e: KotlinNullPointerException) {
                        throw UnresolvedReference("at line ${node.lineNumber} -- struct '${node.name}' as function argument")
                    }
                }
                else -> pileOfLeaves.push(node)
            }


            node.arguments.forEach { traverse1(it) }
            node.statements.forEach { traverse1(it) }


            // visit 2
            when (node.expressionType) {
                ExpressionType.FUNCTION_DEF -> {
                    assembly.append("return;\n")
                }

                else -> {}
            }
        }






        return null
    }




    ///////////////////////////////////////////////////
    // publicising things so that they can be tested //
    ///////////////////////////////////////////////////

    fun resolveTypeString(type: String, isPointer: Boolean = false): ReturnType {
        val isPointer = type.endsWith('*') or type.endsWith("_ptr") or isPointer

        return when (type) {
            "void" -> if (isPointer) ReturnType.VOID_PTR else ReturnType.VOID
            "char" -> if (isPointer) ReturnType.CHAR_PTR else ReturnType.CHAR
            "short" -> if (isPointer) ReturnType.SHORT_PTR else ReturnType.SHORT
            "int" -> if (isPointer) ReturnType.INT_PTR else ReturnType.INT
            "long" -> if (isPointer) ReturnType.LONG_PTR else ReturnType.LONG
            "float" -> if (isPointer) ReturnType.FLOAT_PTR else ReturnType.FLOAT
            "double" -> if (isPointer) ReturnType.DOUBLE_PTR else ReturnType.DOUBLE
            "bool" -> if (isPointer) ReturnType.BOOL_PTR else ReturnType.BOOL
            else -> if (isPointer) ReturnType.STRUCT_PTR else ReturnType.STRUCT
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
        fun debug1(any: Any?) { if (false) println(any) }




        // contradiction: auto AND extern

        val firstLeftParenIndex = tokens.indexOf("(")
        val lastRightParenIndex = tokens.lastIndexOf(")")
        val functionCallTokens: List<String>? = if (firstLeftParenIndex == -1) null else tokens.subList(0, firstLeftParenIndex)
        val functionCallTokensContainsTokens = if (functionCallTokens == null) false else
            (functionCallTokens.map { if (splittableTokens.contains(it)) 1 else 0 }.sum() > 0)
        // if TRUE, it's not a function call/def (e.g. foobar = funccall ( arg arg arg )


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
            argumentsDef.forEach { token ->
                if (argumentDefBadTokens.contains(token)) {
                    throw IllegalTokenException("at line $lineNumber -- illegal token '$token' used on function argument definition")
                }


                if (token == ",") {
                    if (typeHolder == null) throw SyntaxError("at line $lineNumber -- type not specified")
                    argTypeNamePair.add(Pair(typeHolder!!, nameHolder))
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
                }
                else {
                    throw InternalError("uncaught shit right there")
                }
            }


            debug1("!! -> $argTypeNamePair")
            debug1("================================")


            val funcDefNode = SyntaxTreeNode(ExpressionType.FUNCTION_DEF, returnType, funcName, lineNumber)
            if (returnType == ReturnType.STRUCT || returnType == ReturnType.STRUCT_PTR) {
                funcDefNode.structName = actualFuncType
            }

            argTypeNamePair.forEach { val (type, name) = it
                // TODO struct and structName
                val funcDefArgNode = SyntaxTreeNode(ExpressionType.FUNC_ARGUMENT_DEF, type, name, lineNumber)
                funcDefNode.addArgument(funcDefArgNode)
            }


            return funcDefNode
        }
        //////////////////////
        // as Function Call // (also works as keyworded code block (e.g. if, for, while))
        //////////////////////
        else if (!functionCallTokensContainsTokens && functionCallTokens != null && functionCallTokens.size == 1) { // e.g. main ( , fooo ( , doSomething (
            val funcName = functionCallTokens.last()

            // get arguments
            // complex_statements , ( value = funccall ( arg ) ) , "string,arg" , 42f
            val argumentsDef = tokens.subList(firstLeftParenIndex + 1, lastRightParenIndex)


            debug1("!! func call args")
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

                val argNodeLeaf = asTreeNode(lineNumber, it)
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
                        throw SyntaxError("Number of tokens is not 1; I have no idea on this level.")
                    }

                    newTokens.add("("); newTokens.add(")")
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
                    val leafNode = SyntaxTreeNode(ExpressionType.LITERAL_LEAF, ReturnType.CHAR_PTR, null, lineNumber)
                    leafNode.literalValue = tokens[0].substring(1, tokens[0].lastIndex) + nullchar
                    return leafNode
                }
                // bool literals
                else if (word.matches(regexBooleanWhole)) {
                    val leafNode = SyntaxTreeNode(ExpressionType.LITERAL_LEAF, ReturnType.BOOL, null, lineNumber)
                    leafNode.literalValue = word == "true"
                    return leafNode
                }
                // hexadecimal literals
                else if (word.matches(regexHexWhole)) {
                    val isLong = word.endsWith('L', true)
                    val leafNode = SyntaxTreeNode(
                            ExpressionType.LITERAL_LEAF,
                            if (isLong) ReturnType.LONG else ReturnType.INT,
                            null, lineNumber
                    )
                    try {
                        leafNode.literalValue = if (isLong)
                            word.replace(Regex("""[^0-9A-Fa-f]"""), "").toLong(16)
                        else
                            word.replace(Regex("""[^0-9A-Fa-f]"""), "").toInt(16)
                    }
                    catch (e: NumberFormatException) {
                        throw IllegalTokenException("at line $lineNumber -- $word is too large to be represented as ${leafNode.returnType?.toString()?.toLowerCase()}")
                    }

                    return leafNode
                }
                // octal literals
                else if (word.matches(regexOctWhole)) {
                    val isLong = word.endsWith('L', true)
                    val leafNode = SyntaxTreeNode(
                            ExpressionType.LITERAL_LEAF,
                            if (isLong) ReturnType.LONG else ReturnType.INT,
                            null, lineNumber
                    )
                    try {
                        leafNode.literalValue = if (isLong)
                            word.replace(Regex("""[^0-7]"""), "").toLong(8)
                        else
                            word.replace(Regex("""[^0-7]"""), "").toInt(8)
                    }
                    catch (e: NumberFormatException) {
                        throw IllegalTokenException("at line $lineNumber -- $word is too large to be represented as ${leafNode.returnType?.toString()?.toLowerCase()}")
                    }

                    return leafNode
                }
                // binary literals
                else if (word.matches(regexBinWhole)) {
                    val isLong = word.endsWith('L', true)
                    val leafNode = SyntaxTreeNode(
                            ExpressionType.LITERAL_LEAF,
                            if (isLong) ReturnType.LONG else ReturnType.INT,
                            null, lineNumber
                    )
                    try {
                        leafNode.literalValue = if (isLong)
                            word.replace(Regex("""[^01]"""), "").toLong(2)
                        else
                            word.replace(Regex("""[^01]"""), "").toInt(2)
                    }
                    catch (e: NumberFormatException) {
                        throw IllegalTokenException("at line $lineNumber -- $word is too large to be represented as ${leafNode.returnType?.toString()?.toLowerCase()}")
                    }

                    return leafNode
                }
                // int literals
                else if (word.matches(regexIntWhole)) {
                    val isLong = word.endsWith('L', true)
                    val leafNode = SyntaxTreeNode(
                            ExpressionType.LITERAL_LEAF,
                            if (isLong) ReturnType.LONG else ReturnType.INT,
                            null, lineNumber
                    )
                    try {
                        leafNode.literalValue = if (isLong)
                            word.replace(Regex("""[^0-9]"""), "").toLong()
                        else
                            word.replace(Regex("""[^0-9]"""), "").toInt()
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
                            ReturnType.DOUBLE, // DOUBLE used for SimpleC  //if (word.endsWith('F', true)) ReturnType.FLOAT else ReturnType.DOUBLE,
                            null, lineNumber
                    )
                    try {
                        leafNode.literalValue = if (word.endsWith('F', true))
                            word.slice(0..word.lastIndex - 1).toDouble() // DOUBLE when SimpleC; replace it with 'toFloat()' if you're standard C
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
                    val leafNode = SyntaxTreeNode(ExpressionType.VARIABLE_LEAF, null, word, lineNumber)
                    return leafNode
                }
            }
            else {

                /////////////////////////////////////////////////
                // return something; goto somewhere (keywords) //
                /////////////////////////////////////////////////
                if (tokens[0] == "goto") {
                    val gotoNode = SyntaxTreeNode(
                            ExpressionType.FUNCTION_CALL,
                            null,
                            "goto",
                            lineNumber
                    )
                    gotoNode.addArgument(tokens[1].toRawTreeNode(lineNumber))
                    return gotoNode
                }
                else if (tokens[0] == "return") {
                    val returnNode = SyntaxTreeNode(
                            ExpressionType.FUNCTION_CALL,
                            null,
                            "return",
                            lineNumber
                    )
                    returnNode.addArgument(turnInfixTokensIntoTree(lineNumber, tokens.subList(1, tokens.lastIndex + 1)))
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

                            val returnNode = SyntaxTreeNode(ExpressionType.FUNCTION_CALL, ReturnType.VOID, "#_assignvar", lineNumber)
                            returnNode.addArgument(tokensWithoutType.first().toRawTreeNode(lineNumber))
                            returnNode.addArgument(typeStr.toRawTreeNode(lineNumber))
                            returnNode.addArgument(infixNode)

                            return returnNode
                        }
                        else {
                            // #_declarevar(SyntaxTreeNode<RawString> varname, SyntaxTreeNode<RawString> vartype)

                            val leafNode = SyntaxTreeNode(ExpressionType.FUNCTION_CALL, ReturnType.VOID, "#_declarevar", lineNumber)
                            leafNode.addArgument(tokens[1].toRawTreeNode(lineNumber))
                            leafNode.addArgument(tokens[0].toRawTreeNode(lineNumber))

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

        fun debug(any: Any) { if (false) println(any) }


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

                if (rawElem is String)
                    return asTreeNode(lineNumber, listOf(rawElem))
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
                    treeNode.addArgument(popAsTree())
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
        else
            asTreeNode(lineNumber, listOf(treeArgsStack.peek() as String))
    }



    data class LineStructure(var lineNum: Int, var depth: Int, val tokens: MutableList<String>)

    class SyntaxTreeNode(
            val expressionType: ExpressionType,
            val returnType: ReturnType?, // STATEMENT, LITERAL_LEAF: valid ReturnType; VAREABLE_LEAF: always null
            var name: String?,
            val lineNumber: Int, // used to generate error message
            val isRoot: Boolean = false,
            val derefDepth: Int = 0 // how many ***s are there for pointer
    ) {

        var literalValue: Any? = null // for LITERALs only
        var structName: String? = null // for STRUCT return type

        val arguments = ArrayList<SyntaxTreeNode>() // for FUNCTION, CODE_BLOCK
        val statements = ArrayList<SyntaxTreeNode>()

        fun addArgument(node: SyntaxTreeNode) {
            arguments.add(node)
        }
        fun addStatement(node: SyntaxTreeNode) {
            statements.add(node)
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
                    "│ ".repeat(depth+1) + "RetnType : $returnType" +
                            if (returnType == ReturnType.STRUCT_PTR || returnType == ReturnType.STRUCT) " '$structName'"
                            else "",
                    "│ ".repeat(depth+1) + "LiteralV : '$literalValue'"
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
        val node = SyntaxTreeNode(ExpressionType.LITERAL_LEAF, ReturnType.CHAR_PTR, null, lineNumber)
        node.literalValue = this
        return node
    }

    enum class ExpressionType {
        FUNCTION_DEF, FUNC_ARGUMENT_DEF, // expect Arguments and Statements

        FUNCTION_CALL, // expect Arguments and Statements
        // the case of OPERATOR CALL //
        // returnType: variable type; name: "="; TODO add description for STRUCT
        // arg0: name of the variable (String)
        // arg1: (optional) assigned value, either LITERAL or FUNCTION_CALL or another OPERATOR CALL
        // arg2: (if STRUCT) struct identifier (String)

        LITERAL_LEAF, // literals, also act as a leaf of the tree; has returnType of null
        VARIABLE_LEAF // has returnType of null; typical use case: somefunction(somevariable) e.g. println(message)
    }
    enum class ReturnType {
        VOID, BOOL, CHAR, SHORT, INT, LONG, FLOAT, DOUBLE, STRUCT,
        VOID_PTR, BOOL_PTR, CHAR_PTR, SHORT_PTR, INT_PTR, LONG_PTR, FLOAT_PTR, DOUBLE_PTR, STRUCT_PTR,

        VARARG, ANY
    }


}*/

open class SyntaxError(msg: String? = null) : Exception(msg)
class IllegalTokenException(msg: String? = null) : SyntaxError(msg)
class UnresolvedReference(msg: String? = null) : SyntaxError(msg)
class UndefinedStatement(msg: String? = null) : SyntaxError(msg)
class DuplicatedDefinition(msg: String? = null) : SyntaxError(msg)
class PreprocessorErrorMessage(msg: String) : SyntaxError(msg)
