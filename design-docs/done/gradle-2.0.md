## Remove support for the Gradle Open API implementation (DONE)

Now that we have reasonable tooling support via the tooling API, remove the Open API.

* Implement a stub to fail with a reasonable error message when attempting to use Gradle from the Open API.
* Add integration test coverage that using the Open API fails with a reasonable error message.

Note that the `openAPI` project must still remain, so that the stubs fail in the appropriate way when used by Open API clients.
This will be removed in Gradle 3.0.

## Remove the `GradleLauncher` API (DONE)

The public API for launching Gradle is now the tooling API. The `GradleBuild` task can also be used.

* Replace internal usages of the static `GradleLauncher` methods.
* Move the `GradleLauncher` type from the public API to an internal package.

## Remove usages of JNA and JNA-Posix (DONE)

Replace all usages of JNA and JNA-Posix with native-platform. Currently, this means that console support and
UNIX file permissions with JVMs earlier than Java 7 will not be supported on the following platforms:

* Linux-ia64
* Solaris-x86, -amd64, -sparc, -sparcv9

## Misc API tidy-ups (DONE)

* Remove unused `IllegalOperationAtExecutionTimeException`.
* Remove unused `AntJavadoc`.

## Remove Ivy types from the Gradle repository API (DONE)

These types expose the implementation details of dependency management and force a certain implementation on Gradle. Removing these types from the API
allows us to implement new features and remove some internal complexity.

* Remove methods from `ArtifactRepositoryContainer` and `RepositoryHandler` that accept an Ivy `DependencyResolver` as parameter.
* Remove methods from `ArtifactRepositoryContainer` that return `DependencyResolver`.
* Remove `RepositoryHandler.mavenRepo()`.
* Change the `AbstractMavenResolver` so that it no longer extends `DependencyResolver`.
* Change the `FlatDirRepository` implementation so that it no longer uses a `DependencyResolver` implementation.
* Remove Ivy packages from the Gradle API filter.
* Remove Ivy as a dependency of core.
* Remove Ivy version from the output of `gradle -v`.
* Remove loopback resolver, ModuleVersionRepository -> Ivy adapter.
* Remove properties from `ExternalResourceResolver` and subclasses.
* Remove `ModuleComponentRepository.canListModuleVersions()`.
* Fix `ExternalResourceResolver.getMetaDataArtifactName()` so that it is not nullable.

## Remove tooling API support for Gradle 1.1 clients and earlier (DONE)

Gradle 1.2 was released on 12th sept 2012. This change means that tooling more than roughly 18 months old as of the Gradle 2.0 release
will not be able to invoke Gradle 2.0 or later.

* Change the implementation of methods on `ConnectionVersion4` and `InternalConnection` to fail with a decent error message.
* The model implementations no longer need to implement `ProjectVersion3` of the protocol interfaces.
* Change test suite to default to tooling API versions >= 1.2.
* Add integration test coverage that tooling API versions <1.2 fail with a reasonable error message, when running build or fetching model.

## Remove tooling API support for Gradle providers 1.0-milestone-7 and earlier (DONE)

Gradle 1.0-milestone-8 was release on 14th feb 2012. This change means that tooling will not be able to run builds using Gradle versions more than
approximately 2 years old as of the Gradle 2.0 release.

* Consumer fails with a decent error message instead of falling back to the methods on `ConnectionVersion4`.
* Add support for fetching partial `BuildEnvironment` model for unsupported versions.
* Change the test suite to default to target Gradle version >= 1.0-milestone-8
* Add integration test coverage that running build with Gradle version < 1.0-milestone-8 fails with a reasonable error message, when running build or fetching model.
* Add integration test coverage that can fetch a partial `BuildEnvironment` model for Gradle version < 1.0-milestone-8.

## Un-deprecate using the packaging declared in a Maven POM to probe for the module artifacts (DONE)

Leave this behaviour in until the mechanisms to better infer the artifacts for a module have been implemented.

## Remove all features deprecated as at Gradle 1.12 (DONE)

In the Gradle 2.0-rc-1 release, remove all features that are deprecated as at Gradle 1.12 or earlier:

* Search for usages of `DeprecationLogger`, `@Deprecated`, `@deprecated` and remove the associated feature.
* Review usages of `DeprecationLogger.whileDisabled()`.
* Remove `JavaPluginGoodBehaviourTest#changing debug flag does not produce deprecation warning`

## Replace deprecation warnings with errors (DONE)

* Convert deprecated behaviours with errors.

## Reset deprecation warnings (DONE)

* Remove most calls to `DeprecationLogger.whileDisabled()`

## All Gradle scripts use UTF-8 encoding

* Change Gradle script parsing to assume UTF-8 encoding.
* Prefer character encoding specified by the server, if any.
* Update user guide to mention this.

## Upgrade to most recent Groovy 2.2.x

* Change the version of Groovy exposed via the Gradle API to most recent Groovy 2.2.x version.

## Remove support for running Gradle on Java 5

In order to add support for Java 8, we will need to upgrade to Groovy 2.3.x, which does not support Java 5.
Would still be able to build for Java 5.

* Add cross-compilation int tests for Java 5 - 8.
    * Java project.
    * Compile and run tests using JUnit and TestNG.
* Document the JVM requirements in the user guide.
* Update CI builds to use newer Java versions.
    * Increment the daemon and cross version builds to use newer JVM.
    * Disable the Java 5 build.
    * Change Java 8 builds to perform quickTest.
    * Add Java 8 builds as dependencies of stage 3.
* Entry points fail with reasonable error message when executed using Java 5.
    * Command-line.
    * Daemon, including single user daemon.
    * Wrapper.
    * Tooling API client.
    * Tooling API connection.
* Document how to build for Java 5 and include sample.
* Update CI builds to assert Java 5 is available.

## Add support for Java 8

* Change the version of Groovy exposed via the Gradle API to most recent Groovy 2.3.x version.
* Remove source exclusions for jdk6.
* Remove the special case logging from `LogbackLoggingConfigurer`.
* Clean up usages of `TestPrecondition.JDK5` and related preconditions.
* Add warning when using Java version > 8 to inform the user that the Java version may not be supported.
* Add int test coverage for Groovy compilation and groovydoc for various permutations of Groovy version, Java version and (`groovy` or `groovy-all`)
* Add int test coverage for Scala compilation and scaladoc for various permutations of Scala version, Java version and (`scala-library` or `scala-library-all`)
* Add int test coverage for PMD for various permutations of PMD and Java version.
* Add int test coverage for JaCoCo for various permutations of JaCoCo and Java version.
* Run Sonar int tests on Java 8.
* Run FindBugs int tests on Java 8.
* Remove the `JDK7_OR_EARLIER` precondition from various tests.
