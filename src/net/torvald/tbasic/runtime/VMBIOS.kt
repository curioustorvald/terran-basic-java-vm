package net.torvald.tbasic.runtime

/**
 * Created by minjaesong on 2017-06-03.
 */
class VMBIOS() : VMPeripheralHardware {

    lateinit var vm: VM

    fun setVM(vm: VM) {
        this.vm = vm
    }




    override fun call(arg: Int) {
        when (arg) {
        // memory check
        // @return memory size in Number, saved to r1
            0 -> {
                vm.r1 = vm.memory.size.toDouble()
            }
        // find boot device and load boot script to memory, move PC
        // @return modified memory
            1 -> {

            }
            else -> vm.interruptIllegalOp()
        }
    }

}