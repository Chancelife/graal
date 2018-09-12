# :coffee: Espresso
A Java bytecode interpreter on top of [Truffle](https://github.com/oracle/graal/tree/master/truffle).

**Note:** Work in progress, early PoC.

### Features
  - Bytecode interpreter passing _100%_ of the JTT bytecode tests
  - OotB partial evaluation yields _10X_ speedup for a simple prime sieve
  - Uses guest class loaders
  - `.class` file parser

### Not supported/implemented (yet)
  - Threads
  - InvokeDynamic
  - Modules
  - Interop
  - Class/bytecode verification/access-checks/error-reporting
  - Full Java spec compliance (e.g. class loading and initialization, access checks, implicit exceptions)  

### Run _Espresso_ as a pure interpreter _~60s_
```bash
mx espresso -cp mxbuild/dists/espresso.jar com.oracle.truffle.espresso.TestMain
```

### Java equivalent _~1s_
```bash
java -cp mxbuild/dists/espresso.jar com.oracle.truffle.espresso.TestMain
```

### Run _Espresso_ + Truffle PE _~6s_
```bash
mx --dy /compiler --jdk jvmci espresso -cp mxbuild/dists/espresso.jar com.oracle.truffle.espresso.TestMain
```

### Terminal Tetris
```bash
mx espresso -cp mxbuild/dists/espresso.jar com.oracle.truffle.espresso.Tetris
```

## Dumping IGV graphs
```bash
mx -v --dy /compiler --jdk jvmci -J"-Dgraal.Dump=:4 -Dgraal.TraceTruffleCompilation=true -Dgraal.TruffleBackgroundCompilation=false" espresso -cp  mxbuild/dists/espresso.jar com.oracle.truffle.espresso.TestMain
```

### Run _Espresso_ tests _~20s_
```bash
mx unittest espresso
```
