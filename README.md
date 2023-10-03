# JSSCPUSpecCompiler
This is a Java application initially designed for compiling CPU spec files into Java source code, usable inside the [JavaSystemSimulator](https://github.com/ComputingMongoose/JavaSystemSimulator) project.
The application can also be used to define and expand macros (similar to macro-supporting languages, such as C/C++/ASM) in any Java source code.

Releases are available as Tags: https://github.com/ComputingMongoose/JSSCPUSpecCompiler/tags

Pre-built jar files are available for each release. The syntax for running the JSSCPUSpecCompiler is:
```
java -jar JSSCPUSpecCompiler.jar <source_spec_file> <output_java_file>
```

Spec files used in JavaSystemSimulator are available in their own repository [CPUSpecifications](https://github.com/ComputingMongoose/CPUSpecifications).

## Macro definition
A macro is defined on a line starting with "#define", followed by the macro name and parameters. If there are no macro parameteres, empty parantheses are required. The macro definition is written between "#{" and "#}". Currently, the macro is assumed to be on one line.

Examples:
```
#define GET_B() #{ registers[0] #}
#define DEC_SP() #{ SP--; SP&=0xFFFF; #}
#define STORE(a,v) #{ memoryBus.write(a,v); #}
#define SET_A(v) #{ acc=(v) & 0xFF; #}
```

## Macro usage
To use a macro in the source code you place the name of the macro preceeded by a "#" character. It is possible to use macros inside other macros. It is possible to use macros as parameters to other macros. 

Presently, macro expansion is performed recursively. Thus, if you have many macros called from inside other macros this will increase processing time. However, this will have no effect at runtime on the produced code.

Depending on the macro definition, it may not be required to end the macro call with a semicolon (for example if the definition itself contains a final semicolon). The macro expansion process does not impose any rules on ending the call with or without a semicolon.

Examples:
```
#SET_A(#LOAD(#GET_DE()));
#SET_L(#READ_NEXT())
tmp1=#READ_NEXT();
tmp2=((#READ_NEXT())<<8)|tmp1;
#define SET_ALU_FLAGS_CARRY(new) #{ #SET_FLAG_C( ((new&0x100)==0x100)?(1):(0)); #}
```

## Features for CPU specifications
CPU instructions often include fixed bits, identifying an instruction type, and variable bits identifying registers, condition flags or instruction variants. In the JSSCPUSpecCompiler it is possible to define bit values associated with intruction options as "MAP"s. These MAPs may be used in defining the macro name to be expanded (in a way they act as a special type of macro that will be expanded before regular macros). Inside each MAP definition are bit values followed by a string encoding, useful for invoking macros or other functions. The MAP definition ends with a line containing the keyword "END".

MAP example:
```
MAP RP
00 BC
01 DE
10 HL
11 SP
END
```

MAP usage example:
```
tmp1=#GET_$RP$();
```

Complete CPU instructions can be specified as a combination of fixed bits and MAPs. These are separated using "|".

Example instruction:
```
00|RP|0011
```

The instruction specification should be preceded by a line identifying the instruction name. This special line starts with ":", followed by the instruction name. The end of an instruction definition block is indicated by the next instruction specification or by a special line containing ":.".

Complete example:
```
MAP SSS
111 A
000 B
001 C
010 D
011 E
100 H
101 L
END

MAP DDD
111 A
000 B
001 C
010 D
011 E
100 H
101 L
END

:MOV R1,R2
01|DDD|SSS
#SET_$DDD$(#GET_$SSS$())

:.

```
The above example assumes there are macro definitions for #SET_A, #SET_B, #SET_C,... , #GET_A, #GET_B, #GET_c, ..., #GET_L.


# Youtube

Checkout my YouTube channel for interesting videos: https://www.youtube.com/@ComputingMongoose/

# Website

Checkout my website: https://ComputingMongoose.github.io


## License

For any part of this work for which the license is applicable, this work is licensed under the [Attribution-NonCommercial-NoDerivatives 4.0 International](http://creativecommons.org/licenses/by-nc-nd/4.0/) license. See LICENSE.CC-BY-NC-ND-4.0.

<a rel="license" href="http://creativecommons.org/licenses/by-nc-nd/4.0/"><img alt="Creative Commons License" style="border-width:0" src="https://i.creativecommons.org/l/by-nc-nd/4.0/88x31.png" /></a>

Any part of this work that is known to be derived from an existing work is licensed under the license of that existing work. Where such license is known, the license text is included in the LICENSE.ext file, where "ext" indicates the license.

