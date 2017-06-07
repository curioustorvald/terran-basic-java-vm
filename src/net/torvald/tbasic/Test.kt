package net.torvald.tbasic

import net.torvald.tbasic.runtime.compiler.simplec.TBasCC
import java.util.*

/**
 * Created by minjaesong on 2017-06-04.
 */

fun main(args: Array<String>) {
    val prg = """
int globalnum = 1<<10;
double floatnum = 3.14342f;
double scientificfloat = 1.e-2;

extern void testfuncdef (int *index, boolean* isSomething , double * someNumber, ...);

int main    () {
    if(true) {
        boolean s = true;
    }

    fprintf(stdout, "Hello, world!\n");
    return 0;
}
"""
    try {
        TBasCC(prg)
    }
    catch (gottaCatchEmAll: Exception) {

    }

    //val node = TBasCC.asTreeNode("""void testfn(int* index,boolean *isSomething,double * someNumber)""")
    //println(node)

    //val line = """return dup(foo("bar",0x24),null)"""
    //val line = """int foo"""
    //val node2 = TBasCC.asTreeNode(line, line)
    //println(node2)




    /*val equations = """int harambe = burr - argone * - argtwo + 42"""
    val eqNorm = """assignint(harambe,+(-(burr,*(argone,unaryminus(argtwo))),42))"""
    //println(TBasCC.preprocess(equations))
    val atre = """int printf(...)"""
    val node2 = TBasCC.asTreeNode(atre, atre)
    println(node2)*/







    fun test(line: String) {
        // sanities further
        var line = line.replace(Regex("""[\s]*=[\s]*"""), " = ")

        // turn infix notation into prefix
        val operatorStack = Stack<String>()
        val operandStack = Stack<Any>()


    }
}

