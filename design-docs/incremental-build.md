This spec defines some improvements to improve incremental build and task up-to-date checks

# Use cases

## Plugin author implements a task that does work only on input files that are out-of-date

Some tasks take multiple input files and transform them into multiple output files. When this transformation is expensive,
the task author may wish to implement the task such that it can perform the transformation only on those input files
that are out-of-date WRT their output files.

An example of such a task is a C++ compilation task. C++ source files are compiled individually to separate object files. Only those
source files which have changed (or whose output file has changed) since last time the compilation task has executed need to be compiled.
When the compile settings change, then all source files must be recompiled.

Gradle should provide some mechanism that allows an incremental task to be implemented.

## Plugin author implements a task that can accurately describe its output files

For some tasks, the exact output files are not known until after the task has completed. For these tasks, Gradle scans the output directories
both before and after task execution and determines the output files based on the differences. This has a couple of drawbacks:

- It's potentially quite slow, and often the task implementation can determine the output files more cheaply.
- It's not very accurate if the task does any kind of up-to-date checking itself or if it changes file timestamps.

The set of output files is currently used for:

- Detecting changes in the outputs when determining whether the task is up-to-date or not.
- Removing stale output files. Currently, this is something that a task must implement itself. More on this below.

An example of such a task is the Java compilation task. It uses the Java compiler API to perform the compilation. This API can be queried to
determine the output files that will be generated. The Java compilation task could make this available to Gradle, rather than
requiring two scans of the output directory to calculate the output files (one scan will still be necessary to check for changed
output files).

Gradle should provide some mechanism that allows a task to notify Gradle of its actual output files during task execution. We
should also investigate better mechanisms for detecting the output files of a task (eg native file change notifications).

## Plugin author implements a task that removes stale output files

For most tasks that produce output files, stale output files from previous executions of the task should be removed when the task is executed.
For example, old class files should be removed when the source file is renamed. Old test result files should be removed when the tests
no longer exist. And so on.

Accurate handling of stale output files makes running `clean` much less useful.

For a task with `@SkipWhenEmpty` applied to its inputs, all output files should be removed when the task has no inputs.

Gradle should provide some simple mechanism to declare that stale outputs for a given task (type) be removed.

## Simplify the process of writing task implementations

Currently, the task implementation classpath is not treated as an input of the task. This means that changing the task implementation
does not trigger task execution, which in turn makes developing the task implementation awkward.

Gradle should invalidate a task's outputs when its implementation changes.

## Fix up-to-date issues on copy tasks

## Plugin author implements a task that produces outputs other than files

## Plugin author implements a task that uses inputs other than files

# Implementation plan

## Story: Plugin author uses changes to input files to implement incremental task

Incremental input file changes will be provided via an optional parameter on the TaskAction method for a task. The TaskExecutionContext will provide access to the set of changed input files,
as well as a flag to indicate if incremental execution is possible.
If incremental execution is not possible, then the task action will be executed with a rebuild context, where every input file is regarded as 'out-of-date'.

Incremental execution is not possible when:
- Task class has changed since previous execution
- One or more of the input properties have changed since last execution
- Output files have changed or been removed since last execution
- No information available about a previous execution
- Task has no declared outputs
- Task.upToDate() is false
- Gradle build executed with '--rerun-tasks'

        class IncrementalSync extends DefaultTask {
            @InputFiles
            def FileCollection src

            @OutputDirectory
            def File destination

            @TaskAction
            void execute(IncrementalTaskInputs inputs) {
                if (!inputs.incremental) {
                    FileUtils.forceDelete(destination)
                }
                inputs.outOfDate({ change ->
                    FileUtils.copyFile(change.file, targetFile(change.file))
                } as Action)
                
                inputs.removed({ change ->
                    FileUtils.forceDelete(targetFile(change.file))
                } as Action)
            }

            def targetFile(def inputFile) {
                new File(destination, change.file.name)
            }
        }

### Test coverage

1. Incremental task action is executed with rebuild context when run for the first time
2. Incremental task action is skipped when run with no changes since last execution
3. Incremental task is informed of 'out-of-date' files when run with:
    - Single added input file
    - Single modified input file
    - Single removed input file
    - All input files removed
4. Incremental task is informed of 'out-of-date' files when:
    - Task has no declared input properties
    - Task has no declared outputs
5. Incremental task action is executed with every file 'out-of-date' when:
    - Input property value has changed since previous execution
    - Task class has changed since previous execution
    - Output directory changed since previous execution
    - Output file has changed since previous execution
    - Single output file removed since previous execution
    - All output files removed since previous execution
    - Task.upToDate() is false
    - Gradle build executed with '--rerun-tasks'
6. Incremental task action is informed of all files changed since last successful execution
7. Sad-day cases
    - Incremental task has input files declared
    - Incremental task action throws exception

### Questions

Handle multiple actions added via multiple calls to outOfDate() and removed()?
Provide a simpler API that separates outOfDate() and removed() processing temporally?

## Task implementation supports incremental execution for some types of input files

A task may take as input serveral different collections of input files. This story reworks the incremental task API to allow input file changes
to be delivered per-input file collection. It must be possible for a task to declare which of its input file collections it supports
incremental execution for. A change in any other input file collection should trigger a rebuild execution of the task:

- A file moves from one collection to another.
- A file is added to a second collection.
- A file is removed from some but not all collections.

## C++ compilation is incremental when a C++ source file changes

Change the `CppCompile` task to be incremental wrt source file changes:
    - When a source file is added, compile only that source file.
    - When a source file is changed, compile only that source file.
    - When a source file is removed, remove the object file for that source file.
    - When a header file is changed in some way, recompile all source files.

## GRADLE-918: Document task input and output annotations

## GRADLE-2936: Invalidate task outputs when task implementation changes

Add to the task history a hash of the task implementation, and rebuild the task's outputs when this changes.

- Add a mechanism to determine a hash given a classpath. Probably also add some persistent caching for this.
  This mechanism should be reusable, to allow us to cache the result of scanning a classpath for annotated classes,
  such as plugin-level services.
- The hash of a class is the hash of its ClassLoader's classpath.
- The hash of a task is the combination of the hash of the task's implementation class plus the hash of
  the implementation class of each task action attached to the task.

## GRADLE-2115: Handle `is` property accessors for boolean properties marked with `@Input`

## GRADLE-1646: Copy tasks do not consider filter/expansion properties in up-to-date checks

- Also GRADLE-2710

## GRADLE-1276: processResources task considered up-to-date although its spec has changed

## GRADLE-1814: Uptodate check fails when changing property includeEmptyDirs of Copy task

## GRADLE-1298: Change in filtered resource not picked up by archive tasks

## GRADLE-2082: Validate that input and output annotation are attached to property with valid type

## Change type decoration so that incremental API objects are not extensible

Allow type decoration to distinguish between domain objects and API objects.  API objects should not be
decorated with `DynamicObjectAware`, `IConventionAware` or `ExtensionAware` or any of the asssociated
state.

## Improve accuracy of output file detection

Change output file detection to use a change in file last modified time to detect that an output file has changed. Remove `OutputFilesSnapshotter`.

## Story: Java compile task specifies its output files

A task action will be able to use an API to notify Gradle of the output files it produces. For task implementations
that do not use this API, Gradle will scan the output directories before and after task execution to infer the task
outputs, as it does now.

- Change the Java and Groovy compile task types to use this.
- Change the Copy and Sync task types to use this.

TBD - the API, which needs to work for both 'build everything' task execution (which includes task implementations that
are not incremental aware) and incremental task execution.

TBD - Need to have a solution for Java and Groovy compile tasks with `useAnt=true`

## Story: Plugin author implements task that cleans up stale output files

A task implementation will be able to use an API or a declarative element to request that stale output files should
be removed.

- Change the Java and Groovy compile task types to use this.
- Change the ProcessResources and Sync task types to use this, possibly remove the ProcessResources type.
- When a `@SkipWhenEmpty` input is empty, remove all output files from a previous execution.

TBD - The API.

## Story: Remove stale classes when compile task history is not known

TBD - The stale outputs mechanism needs to handle the case where:

- multiple tasks generate their outputs into a given output directory
- some files exist in this output directory
- task history is not available for one or more of the tasks

TBD - The solution must handle the case where output files are generated into a directory that also contains
non-generated files, so that simply removing the output directory is not really a solution.

In particular, this story must solve the case where a classes directory is built from Java and Groovy source and static resources,
but the task history is not available because an upgraded version of Gradle is being used.

Potential solutions:

- When a task in the task graph has no history and one of its output directories already exists and is not empty, then automatically
  schedule a `clean` task for the project.
- As above, but notify the user that they should really do a clean build first.
- Handle this at the binary level, rather than the task level. For example, the `classes` task might remove unclaimed files (ie not known
  to be built by any task) from the classes directory. Or perhaps when a task that contributes to the classes directory is scheduled to
  run and no history is available for that task, then remove the classes directory and schedule a `classes` task to run.

## Story: Reporting tasks are incremental wrt the settings of enabled reports only

- Allow @Nested to be applied to a property that returns a collection.
- Change TaskReportContainer to return a property that contains only the enabled reports.
- Mark the report subclasses with the appropriate annotations.
- Remove the existing properties on TaskReportContainer and FindBugsReportsImpl.

### Test cases

- Task is considered up-to-date when the setting of a disabled report is changed.
- Task is considered out-of-date when a setting on an enabled report is changed.
- Task is considered out-of-date when the set of enabled reports changes.
- Task validation is applied to the settings of enabled reports.

## Story: Copy task is incremental wrt its input files

## Story: Plugin author uses changes to output files to implement incremental task

TBD

# Open issues

- Some tasks may need to know about changed output files.
- Look at making task history available across Gradle versions.
