package net.torvald.terranvm.assets

object BiosSetup {
    operator fun invoke(): String = """
.stack; 16;

.data;

string welcome0 "                      ****  TerranBIOS Setup Utility  ****
";
string welcome1 "Version 0.3
";
string welcome2 "Type and run 'boot' to boot the system, run 'save' to save your changes, run 'changes' to review your changes.
";
string welcome3 "For more information, please refer to the manual that comes with your computer.
";

string prompt "
: ";

string nouns_db "boot,save,changes";
string verbs_db "set,get";

# 63 chars + terminator
bytes input_buffer 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0;
int max_input_len 63;

bytes default_boot_order 8h 9h Ch Dh; # disk drive 1, 2, scci 1, 2



.code;
jmp @start;

:putchar; # push char first before call; garbles r1 and r2
pop r2;                         # return addr
pop r1;                         # actual arg
push r2;
loadwordi r2, 00000200h;
or r1, r1, r2;
call r1, FFh;
return;






:start;
## print out message
loadwordi r1, 02000000h;

loadwordi r2, @welcome0; or r3, r1, r2; call r3, FFh;
loadwordi r2, @welcome1; or r3, r1, r2; call r3, FFh;
loadwordi r2, @welcome2; or r3, r1, r2; call r3, FFh;
loadwordi r2, @welcome3; or r3, r1, r2; call r3, FFh;

loadwordi r2, @prompt; or r3, r1, r2; call r3, FFh;




:read_user_input;
loadwordi r1, 00000102h;        # r3 <- getchar
call r1, FFh;                   #
push r3;                        # putchar
jsri @putchar;                  #
jmp @read_user_input;



"""
}