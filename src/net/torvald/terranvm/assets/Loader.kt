package net.torvald.terranvm.assets

object Loader {
    operator fun invoke() = """
## LOADER
##
## The most basic firmware that reads hexadecimal machine code, writes them to the memory and then executes it
## Used to programatically (or manually, if you got balls) enter and run native program
## ALL INPUT ARE LOWER-CASE
## Addressing within buffer is relative; actual commands will require absolute memory adderss
##
## The commands are executed whenever you hit the letter. In fact, RETURN key is never being used.
##
## Syntax:
##      [0-9A-F]{0,8}[KPRT]
##
## Commands:
##      K: read what word is in the current address
##      P: write a word to memory; advance cursor (takes hexadecimal number)
##      R: execute opcode starting from current cursor position
##      T: move cursor of buffer (takes 2, 4, 6, 8 digits of hexadecimal number)
##
## Created by minjaesong on 2018-04-16



## TODO: support backspace key


.stack; 12;

.data;
string loadertext "LOADER
";
int literalbuffer 0;
int startingptr 0;

.code;

jsri @reset_buffer;
jmp @code;

:reset_buffer; # garbles r1
loadwordi r1, 0;
storewordimem r1, @literalbuffer;
return;

:getchar; # r1 <- char
loadwordi r1, 00000100h;
call r1, FFh;
return;

:putchar; # push char first before call; garbles r1 and r2
pop r2;                         # return addr
pop r1;                         # actual arg
push r2;
loadwordi r2, 00000200h;
or r1, r1, r2;
call r1, FFh;
return;

:putstring; # push label first before call; garbles r1 and r2
pop r2;                         # return addr
pop r1;                         # actual arg
push r2;
loadwordi r2, 02000000h;
or r1, r1, r2;
call r1, FFh;
return;

:to_nibble; # push argument first; '0' to 0h, '1' to 1h, etc.; garbles r1, r2, r7; stack top has return value
pop r2;                         # return addr
pop r1;                         # actual arg
loadwordi r8, 39h;              # '9'

cmp r1, r8;                     # IF
                                # (r1 < r8) aka r1 in '0'..'9' THEN
loadwordils r7, 48;                 # r1 = r1 - 48
subls r1, r1, r7;                   #
                                # (r1 > r8) THEN
loadwordigt r7, 55;                 # r1 = r1 - 55
subgt r1, r1, r7;                   #
                                # ENDIF

loadwordi r7, 1111b;            # sanitise r1 by
and r1, r1, r7;                 # ANDing with 1111b

push r1;                        # push return value
push r2;                        # push returning PC address
return;

:putchar_verbatim;
push r3;                        # putchar
jsri @putchar;                  #
return;

:putchar_capital;
loadwordi r1, 32;               #
sub r8, r3, r1;                 #
push r8;                        # putchar
jsri @putchar;                  #
return;


############################################################################################################


:code;

################
## initialise ##
################

loadwordi r5, 0;                # byte accumulator
loadwordi r6, 0;                # byte literal read and acc counter (7 downTo 0)
loadwordi r8, 0;                # constant zero

loadwordi r1, @loadertext;      # print out LOADER
push r1;                        #
jsri @putstring;                #

loadwordi r2, 2048;             # allocate buffer, r4 contains address (NOT an offset)
malloc r4, r2;                  # (2 KBytes)
storewordimem r4, @startingptr; #
loadwordi r4, 0;                # now r4 contains distance to the starting pointer



#################################
:loop; ##########################
#################################



loadwordi r1, 00000102h;        # r3 <- getchar
call r1, FFh;                   #

###############################
## print 'a'..'f' as capital ##
###############################

loadwordi r1, 61h;              # r1 <- 'a'
cmp r3, r1;                     # IF (r3, 'a')
jgt @r1_geq_a;                  # 'a'
jz  @r1_geq_a;                  # 'b'..'f'
jls @r1_ls_a;                   # lesser
:r1_geq_a;
loadwordi r1, 66h;              # r1 <- 'f'
cmp r3, r1;                     # IF (r3, 'f')
jsriz  @putchar_capital;            # 'f'
jz @accept_byte_literal;            #
jsrils @putchar_capital;            # 'a'..'e'
jls @accept_byte_literal;           #
jgt @r1_gt_f;                   # greater than f
                                # ENDIF

:r1_gt_f;                       # alias
jmp @function_keys;             # derp


:r1_ls_a;
# compare if r3 in '0'..'9'
# putchar and goto accept_byte_literal

loadwordi r1, 30h;              # r1 <- '0'
cmp r3, r1;                     # IF (r3, '0')
jls @loop;                          # deny if r3 < '0'
loadwordi r1, 39h;              # r1 <- '9'
cmp r3, r1;                     # IF (r3, '9')
jgt @loop;                          # deny if r3 > '9'

jsri @putchar_verbatim;         #
jmp @accept_byte_literal;       #


##################
:function_keys; ##
##################

loadwordi r1, 70h;              #
cmp r3, r1;                     # IF (r3 == 'p') THEN
jsriz @putchar_verbatim;            # printout
jz @write_to_mem;                   # goto write_to_mem
                                # ENDIF
loadwordi r1, 74h;              #
cmp r3, r1;                     # IF (r3 == 't') THEN
jsriz @putchar_verbatim;            # printout
jz @move_pointer;                   # goto move_pointer
                                # ENDIF
loadwordi r1, 6Bh;              #
cmp r3, r1;                     # IF (r3 == 'k') THEN
jsriz @putchar_verbatim;            # printout
jz @peek_buffer;                    # goto peek_buffer
                                # ENDIF
loadwordi r1, 72h;              #
cmp r3, r1;                     # IF (r3 == 'r') THEN
jsriz @putchar_verbatim;            # printout
jz @execute;                    # goto execute
                                # ENDIF
jnz @loop;                      # deny

########################
:accept_byte_literal; ##
########################


loadwordi r8, 57;               # '9'
cmp r3, r8;                     # IF
                                # (r3 < r8) aka r1 in '0'..'9' THEN
loadwordils r7, 30h;                # r3 = r3 - 48
subls r3, r3, r7;                   #
                                # (r3 > r8) THEN
loadwordigt r7, 55;                 # r3 = r3 - 55
subgt r3, r3, r7;                   #
                                # ENDIF

loadwordi r7, 1111b;            # sanitise r3 by
and r3, r3, r7;                 # ANDing with 1111b


#######################
## now r3 has nibble ##
#######################

loadwordi r2, 1;                # flip about with r6, keep it to r2
xor r2, r6, r2;                 # r2 = r6 xor 1 (01234567 -> 10325476)

## accumulate to r5 ##
loadwordi r7, 4;                #
mulint r7, r7, r2;              # r8 = 8, 12, 0, 4 for r6: 2, 3, 0, 1
shl r3, r3, r7;                 #
or r5, r5, r3;                  # r5 = r5 or (r3 shl r7)

storewordimem r5, @literalbuffer;# put r5 into literalbuffer

loadwordi r1, 7;                # a number to compare against
cmp r6, r1;                     # IF
                                # (r6 == 7) THEN
loadwordiz r6, 0;                   # r6 = 0
loadwordiz r5, 0;                   # r5 = 0
                                # (r6 != 7) THEN
incnz r6;                           # r6++
                                # ENDIF

jmp @loop;

#################
:write_to_mem; ##
#################

loadwordimem r5, @literalbuffer;# deref literalbuffer into r5 (r5 is zero in this case if full word is written in buffer)
loadwordi r1, 0;                #

loadwordimem r2, @startingptr;  #
add r2, r2, r4;                 # r2 now contains real address (startingptr + distance)
storeword r5, r2, r1;           #

loadwordi r1, 4;                # r4 += 4
add r4, r4, r1;                 #

jsri @reset_buffer;
loadwordiz r6, 0;               # r6 = 0
loadwordiz r5, 0;               # r5 = 0

jmp @loop;

#################
:move_pointer; ##
#################

loadwordimem r5, @literalbuffer;# deref literalbuffer into r5 (r5 is zero in this case if full word is written in buffer)

loadwordi r1, 2;                # make r5 word-aligned
ushr r5, r5, r1; shl r5, r5, r1;#

mov r4, r5;                     # r4 = literalbuffer

itox r1, r4;                    # r1 = r4 (distance from startingptr) as a String

loadwordi r7, 020Ah;           #
call r7, FFh;                   # print '\n'

loadwordi r8, 023Eh;           #
call r8, FFh;                   # print '>'

loadwordi r8, 02000000h;        # base BIOS call for print string
or r8, r8, r1;                  #
call r8, FFh;                   # print out new offset (aka distance)

call r7, FFh; # print '\n'      # print '\n' using r7 we overwrote above

jsri @reset_buffer;
loadwordiz r6, 0;               # r6 = 0
loadwordiz r5, 0;               # r5 = 0

jmp @loop;

################
:peek_buffer; ##
################

itox r1, r4;                    # r1 = r4 (distance from startingptr) as a String
loadwordimem r2, @startingptr;  #
add r2, r2, r4;                 # r2 now contains real address (startingptr + distance)

loadwordi r8, 02000000h;        # base BIOS call for print string
loadwordi r3, 0;

## PRINT 1 ##

loadwordi r3, 020Ah; call r3, FFh; # print '\n'
loadwordi r7, 023Eh; call r7, FFh; # print '>'
or r3, r8, r1;        call r3, FFh; # print distance from startingptr

## END OF PRINT 1 ##

## PRINT 2 ##

loadwordi r3, 023Ah; call r3, FFh; # print ':'
loadwordi r3, 0220h; call r3, FFh; # print ' '

## print out 4 consecutive bytes

loadwordi r3, 0;
# loadwordi r8, 02000000h;
loadwordi r7, 0220h;

loadbyte r1, r2, r3;            # r1 now contains whatever byte was contained in old r2
itox r1, r1;                    # r1 now contains string pointer for hex str
or r1, r1, r8; call r1, FFh;    # printout r1

# call r2, FFh;                   # printout ' '

inc r2;                         #
loadbyte r1, r2, r3;            # r1 now contains whatever byte was contained in old r2
itox r1, r1;                    # r1 now contains string pointer for hex str
or r1, r1, r8; call r1, FFh;    # printout r1

# call r2, FFh;                   # printout ' '

inc r2;                         #
loadbyte r1, r2, r3;            # r1 now contains whatever byte was contained in old r2
itox r1, r1;                    # r1 now contains string pointer for hex str
or r1, r1, r8; call r1, FFh;    # printout r1

# call r2, FFh;                   # printout ' '

inc r2;                         #
loadbyte r1, r2, r3;            # r1 now contains whatever byte was contained in old r2
itox r1, r1;                    # r1 now contains string pointer for hex strW
or r1, r1, r8; call r1, FFh;    # printout r1

loadwordi r3, 020Ah; call r3, FFh; # print '\n'

## END OF PRINT 2 ##

jmp @loop;

############
:execute; ##
############

loadwordimem r2, @startingptr;  #
add r2, r2, r4;                 # r2 now contains real address (startingptr + distance)

srw r1, r2;                     # pc <- r2


jmp @loop;
"""
}