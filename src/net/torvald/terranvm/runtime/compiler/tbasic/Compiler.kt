package net.torvald.terranvm.runtime.compiler.tbasic

/**
 * Created by minjaesong on 2018-05-08.
 */
class Tbasic {

    private val keywords = hashSetOf<String>(
            "ABORT",
            "ABORTM",
            "ABS",
            "ASC",
            "BACKCOL",
            "BEEP",
            "CBRT",
            "CEIL",
            "CHR",
            "CLR",
            "CLS",
            "COS",
            "DEF FN",
            "DELETE",
            "DIM",
            "END",
            "FLOOR",
            "FOR",
            "GET",
            "GOSUB",
            "GOTO",
            "HTAB",
            "IF",
            "INPUT",
            "INT",
            "INV",
            "LABEL",
            "LEFT",
            "LEN",
            "LIST",
            "LOAD",
            "LOG",
            "MAX",
            "MID",
            "MIN",
            "NEW",
            "NEXT",
            "PRINT",
            "RAD",
            "REM",
            "RENUM",
            "RETURN",
            "RIGHT",
            "RND",
            "ROUND",
            "RUN",
            "SAVE",
            "SCROLL",
            "SGN",
            "SIN",
            "SQRT",
            "STR",
            "TAB",
            "TAN",
            "TEXTCOL",
            "TEMIT",
            "VAL",
            "VTAB",
            "PEEK",
            "POKE"
    )
    private val delimeters = hashSetOf<Char>(
            '(',')',',',':','"',' '
    )

    fun tokeniseLine(line: String): LineStructure {
        var charIndex = 0
        val sb = StringBuilder()
        val tokensList = ArrayList<String>()
        val lineNumber: Int


        fun appendToList(s: String) {
            if (s.isNotBlank()) tokensList.add(s)
        }


        if (line[0] !in '0'..'9') {
            throw IllegalArgumentException("Line number not prepended to this BASIC statement: $line")
        }

        // scan for the line number
        while (true) {
            if (line[charIndex] !in '0'..'9') {
                lineNumber = sb.toString().toInt()

                sb.delete(0, sb.length)
                charIndex++
                break
            }

            sb.append(line[charIndex])
            charIndex++
        }


        // the real tokenise

        // -->  scan until meeting numbers or delimeters
        while (charIndex <= line.length) {
            while (true) {
                if (charIndex == line.length || line[charIndex] in delimeters) {
                    appendToList(sb.toString())

                    sb.delete(0, sb.length)
                    charIndex++
                    break
                }

                sb.append(line[charIndex])
                charIndex++
            }
        }





        return LineStructure(lineNumber, tokensList)
    }


    data class LineStructure(var lineNum: Int, val tokens: MutableList<String>)
}