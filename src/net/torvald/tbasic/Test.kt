package net.torvald.tbasic

import net.torvald.tbasic.runtime.compiler.simplec.TBasCC

/**
 * Created by minjaesong on 2017-06-04.
 */

fun main(args: Array<String>) {
    val prg = """
double globalnum = 42.0;

void testfn (int index, boolean isSomething , double someNumber);

int main    () {
    if (true) {
        boolean s = true;
    }

    fprintf(stdout, "Hello, world!\n");
    return 0;
}
"""
    /*try {
        TBasCC(prg)
    }
    catch (gottaCatchEmAll: Exception) {

    }*/

    //val node = TBasCC.asTreeNode("""void testfn(int* index,boolean *isSomething,double * someNumber)""")
    //println(node)

    //val line = """return dup(foo("bar",0x24),null)"""
    val line = """int foo"""
    val node2 = TBasCC.asTreeNode(line, line)
    println(node2)

    //val node3 = TBasCC.asTreeNode("""if(true,false,"quote,wut")""")
    //print(node3)
}

