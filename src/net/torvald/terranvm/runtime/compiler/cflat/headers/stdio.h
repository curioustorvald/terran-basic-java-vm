#ifndef	_STDIO_H_
#define	_STDIO_H_



#ifndef NULL
#define	NULL	0
#endif

#define	EOF (-1)

#define	FOPEN_MAX 20	/* must be <= OPEN_MAX <sys/syslimits.h> */
#define	FILENAME_MAX 256	/* must be <= PATH_MAX <sys/syslimits.h> */



#define	stdin (&__sF[0]) // TODO do it properly
#define	stdout (&__sF[1]) // TODO do it properly
#define	stderr (&__sF[2]) // TODO do it properly



//////////////////////////
// FUNCTION DEFINITIONS //
//////////////////////////

int printf(const char* input, ...) {
    // TODO format string
    char* outstr = input;
    __asm__("loadstrinline 1, " + outstr + "; printstr;")
}

int putchar(int ch) {
    __asm__("loadnum 1, " + itoa(ch) + "; putchar;")
}




#endif /* _STDIO_H_ */