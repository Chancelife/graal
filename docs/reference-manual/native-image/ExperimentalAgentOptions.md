# Experimental Agent Options

The native-image-agent also comes with a set of experimental options. These options are subject to breaking changes and complete removal.

## Support For Predefined Classes

Native-image relies on the closed world assumption (all classes are known at image build time) to create an optimized binary. However, Java has support for defining new classes at runtime.
To help support this use case, the agent can be told to trace dynamically defined classes and save their bytecode for later use by the image builder.
This functionality can be enabled by adding `experimental-class-define-support` to the agent's command line.
Apart from the standard configuration files, the agent will create an `agent-extracted-predefined-classes` directory in the configuration output directory and write bytecode of newly defined classes on the go. The configuration directory can then be used by image builder without additional tweaks. At runtime, if a class is defined with the same name and bytecode as one of the traced classes, it will successfully be retrieved and made available to the application.

### Known Limitations

 - While a class with the same name may be defined multiple times in different classloaders, that will not work on native-image.
 - A predefined class can only be loaded by one class loader.
 - A predefined class cannot be initialized at build time (as it technically shouldn't exist at this point).
 - Since there is no way to know if a class definition originated from the JVM or from user code, class definitions are, as a heuristic, ignored if they originate from one of the JVM builtin classloaders.
 - Code on the JVM can freely generate classes with random names and place methods in different orders in the class. This practically means that this code would need tweaks to be able to run on native-image.

## Printing Configuration With Origins

For debugging, it may be useful to know the origin of certain configuration. If `experimental-configuration-with-origins` is added to the agent's command line, the agent will, instead of the usual configuration files, print a call tree. Each method in the tree will have configuration printed in the same line, if such configuration exists.

## Ignoring Configuration Already Present On The Classpath

The agent can pick up existing configuration from the JVM's classpath by adding `experimental-omit-config-from-classpath` to the agent's command line. Before writing configuration files, the agent will remove any parts of the configuration that were already present in configuration files on the classpath.

## Generating Conditional Configuration Using The Agent

The agent can, using a heuristic, generate configuration with reachability conditions on user specified classes. The agent will track configuration origins and try to deduce the conditions automatically. User classes are specified via an agent filter file (for more information on the format, see [Agent.md](Agent.md)). Additionally, the resulting configuration can further be filtered using another filter file.

Currently, this feature supports two modes:
 1. Generating conditional configuration during an agent run
 2. Generating conditional configuration from multiple agent runs

### Generating Conditional Configuration During An Agent Run

To enable this mode, add `experimental-conditional-config-filter-file=<path>` to the agent's command line, where `<path>` points to an agent filter file. Classes that match the rules in this filter file will be considered user code classes. To further filter the generated configuration, you can use `conditional-config-class-filter-file=<path>`, where `<path>` is a path to an agent filter file.

### Generating Conditional Configuration From Multiple Agent Runs

Conditional configuration can be generated from multiple agent runs that excercize different code paths in the application. In this mode, each agent outputs metadata collected during the run. `native-image-configure` is then used to consume the metadata and produce a conditional configuration.
To run the agent in this mode, add `experimental-conditional-config-part` to the agent's command line.
Once all the agent runs have finished, you can generate conditional configuration by invoking:
```shell
native-image-configure generate-conditional --user-code-filter=<path-to-filter-file> --class-name-filter=<path-to-filter-file> --input-dir=<path-to-agent-run-output-1> --input-dir=<path-to-agent-run-ouput-2> ... --output-dir=<path-to-resulting-conditional-config>
```
where:
 - `--user-code-filter=<path-to-filter-file>`: path to an agent filter file that specifies user classes
 - (optional) `--class-name-filter=<path-to-filter-file>`: path to an agent filter file that further filters the generated config

### The Underlying Heuristic

Conditions are generated using the call tree of the application. The heuristic works as follows:
 1. For each unique method, create a list of all nodes in the call tree that correspond to the method
 2. For each unique method, if the method has more than one call node in the tree:
  - Find common configuration accross all call nodes of that method
  - For each call node of the method, propagate configuration that isn't common accross these calls to the caller node
 3. Repeat 2. until an iteration produced no changes in the call tree.
 4. For each node that contains configuration, generate a conditional configuration entry with the method's class as the condition.

The primary goal of this heuristic is to attempt to find where a method creates different configuration entries depending on the caller (for example, a method that wraps `Class.forName` calls.)
This implies that the heuristic will not work well for code that generates configuration through a different dependency (for example, same method returns calls `Class.forName` with different class parameters depending on a system property).
