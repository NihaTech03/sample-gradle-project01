This spec outlines some initial stories for what's generally termed “managed model”.
This is a sub stream of the “unified-configuration-model” stream.

## Background

There are several key drivers for this stream of work:

1. Model insight
2. Bidirectional model externalisation
3. Model caching

The term “model insight” (#1) refers to deeply understanding the structure of model elements.
While not a goal with immediate user benefit in itself, it is included in this list as a proxy for general usability enhancements made possible by this.
For example, a model browsing tool would rely on this insight to generate visual representation of elements and facilitate browsing a model (schema and data).

The term “bidirectional model externalisation” refers to being able to serialize a data set to some “external” form (e.g. JSON, YAML, XML, Turtle etc.) for consumption by other systems, and the possibility of using such a format to construct a data set for Gradle to use.

The term “model caching” refers to the ability to safely reuse a previously “built” model element, avoiding the need to execute the user code that contributed to its final state.

Moreover, we consider owning the implementation of model elements an enabler for the general dependency based configuration model.

## Stories

### Support properties of type `File`

    @Managed
    interface Thing {
      File getFile();
      void setFile(File file)
    }

- Similar to `String` etc., getter must be accompanied by setter
- Similar to `String` etc. `setFile()` cannot be called when the object is read only

### Support managed types declaring properties of type `ModelMap<T>`

    @Managed
    interface Thing {
      ModelMap<Foo> getFoos();
    }

- No setter for property allowed
- Element type must be `@Managed`
- Type taking creation methods must support subtypes
- Rule taking methods (e.g. `all(Action)`) must throw when object is read only
- All created elements implementing `Named` must have `name` property populated, matching the node link name
- Can depend on model map element by specific type in rules
- Element type cannot be any kind of type var
- Can be top level element
- Can be property of managed type

### Support for polymorphic managed sets

```
interface ModelSet<T> implements Set<T> {
  void create(Class<? extends T> type, Action<? super T> initializer);
  <O> Set<O> ofType(Class<O> type);
}
```

- `<T>` does not need to be managed type (but can be)
- `type` given to `create()` must be a valid managed type
- All mutative methods of `Set` throw UnsupportedOperationException (like `ModelSet`).
- `create` throws exception when set has been realised (i.e. using as an input)
- “read” methods (including `ofType`) throws exception when called on mutable instance
- No constraints on `O` type parameter given to `ofType` method
- set returned by `ofType` is immutable (exception thrown by mutative methods should include information about the model element of which it was derived)

The initial target for this functionality will be to replace the `PlatformContainer` model element, but more functionality will be needed before this is possible.

## Feature: Tasks defined using `CollectionBuilder` are not eagerly created and configured

### Plugin uses `CollectionBuilder` API to apply rules to container elements

Add methods to `CollectionBuilder` to allow mutation rules to be defined for all elements or a particular container element.

- ~~Add `all(Action)` to `CollectionBuilder`~~
- ~~Add `afterEach(Action)` to `CollectionBuilder`~~
- ~~Add `named(String, Action)` to `CollectionBuilder`~~
- ~~Add `withType(Class, Action)` to `CollectionBuilder`~~
- ~~Add `withType(Class)` to `CollectionBuilder` to filter.~~
- ~~Verify usable from Groovy using closures.~~
- Verify usable from Java 8 using lambdas.
- Reasonable error message when actions fail.

#### Issues

- Sync up on `withType()` or `ofType()`.
- Error message when a rule and legacy DSL both declare a task with given name is unclear as to the cause.
- Fix methods that select by type using internal interfaces, as only the public contract type is visible.
- Type registration:
    - Separate out a type registry from the containers and share this.
    - Type registration handlers should not invoke rule method until required.
    - Remove dependencies on `ExtensionsContainer`.

### Plugin uses method rule to apply defaults to model element

Add a way to mutate a model element prior to it being exposed to 'user' code.

- ~~Add `@Defaults` annotation. Apply these before `@Mutate` rules.~~
- ~~Add `CollectionBuilder.beforeEach(Action)`.~~
- ~~Apply defaults to managed object before initializer method is invoked.~~
- Reasonable error message when actions fail

#### Issues

- Fail when target cannot accept defaults, eg is created using unmanaged object `@Model` method.
- Handle case where defaults are applied to tasks defined using legacy DSL, e.g. fail or perhaps define rules for items in task container early.

### Plugin uses method rule to validate model element

Add a way to validate a model element prior to it being used as an input by 'user' code.

- ~~Add `@Validate` annotation. Apply these after `@Finalize` rules.~~
- ~~Rename 'mutate' methods and types.~~
- Add `CollectionBuilder.validateEach(Action)`
- Don't include wrapper exception when rule fails, or add some validation failure collector.
- Add specific exception to be thrown on validation failure
- Nice error message when validation fails.

#### Issues

- Currently validates the element when it is closed, not when its graph is closed.

### Build script author uses DSL to apply rules to container elements

    model {
        components {
            mylib(SomeType) {
                ... initialisation ...
            }
            beforeEach {
                ... invoked before initialisation ...
            }
            mylib {
                ... configuration, invoked after initialisation ...
            }
            afterEach {
                ... invoked after configuration
            }
        }
    }

- Add factory to mix in Groovy DSL and state checking, share with managed types and managed set.
- Validate closure parameter types.
- Verify:
    - Can apply rule to all elements in container using DSL
    - Can apply rule to single element in container using DSL
    - Can create element in container using DSL
    - Decent error message when applying rule to unknown element in container
    - Decent error message when using DSL to create element of unknown/incorrect type

#### Issues

- Currently does not extract rules or input references at compile time. Rules are added at execution time via a `CollectionBuilder`.

### Tasks defined using `CollectionBuilder` are not eagerly created and configured

- Change `DefaultCollectionBuilder` implementation to register a creation rule rather than eagerly instantiating and configuring.
    - Verify construction and initialisation action is deferred, but happens before mutate rules when target is used as input.
    - Verify initialisation action happens before `project.tasks.all { }` and `project.tasks.$name { }` actions.
    - Reasonable error message is received when element type cannot be created.
- Attempt to discover an unknown node by closing its parent.
- Apply consistently to all model elements of type `PolymorphicDomainObjectContainer`.
- Apply DSL consistently to all model elements of type `PolymorphicDomainObjectContainer`.

### Implicit tasks are visible to model rules

Options:

- Bridge placeholders added to `TaskContainer` into the model space as the placeholders are defined.
- Flip the relationship so that implicit tasks are defined in model space and bridged across to the `TaskContainer`.

Tests:

- Verify can use model rules to configure and/or override implicit tasks.

Don't do this until project configuration closes only the tasks that are required for the task graph.

Other issues:

- Remove the special case to ignore bridged container elements added after container closed.
- Handle old style rules creating tasks during DAG building.
- Add validation to prevent removing links from an immutable model element.

## Feature: Support for managed container of tasks

### ModelSet defers creation of elements

- Reasonable error message when action fails.
- Support 'anonymous' links
- Sync view logic with `CollectionBuilder` views.
- Use same descriptor scheme as `CollectionBuilder`.
- Use more efficient implementation of set query methods.
- Allow `ModelSet` to be used as a task dependency.
- Fail when mutator rule for an input is not bound.

### ModelSet supports Groovy DSL

- Support passing a closure to `create()`
- Validate closure parameter types.

### Support for managed container of tasks

- Rename `CollectionBuilder` to `ManagedMap`.
- Currently it is possible to get an element via `CollectionBuilder`, to help with migration. Lock down access to `get()` and other query methods.
    - Same for `size()`.
- Lock down read-only view of `ManagedMap`, provide a `Map` as view.
- Change `ManagedMap` to extend `Map`.
- Mix in the DSL conveniences into the managed collections and managed objects, don't reuse the existing decorator.
- Allow a `ManagedMap` to be added to model space using a `@Model` rule.
- Synchronisation back to `TaskContainer`, so that `project.tasks.all { }` and `project.tasks { $name { } }` works.
- Need efficient implementation of query methods that does not scan all linked elements to check type.
- Implement `containsValue()`.
- Separate out type registry from the component/binary/source set containers.
- Allow `ManagedMap` and `ManagedMap.values()` to be used as a task dependency.
- Add `toString()` implementation.

### Expose a `ManagedMap` view for all model elements of type `PolymorphicDomainObjectContainer`.

- Currently blocked on `NativeToolChainRegistryInternal.addDefaultToolChains()`
- `TestSuiteContainer` should extend `PolymorphicDomainObjectContainer`.
- `ProjectSourceSet` should extend `PolymorphicDomainObjectContainer`.

### Support for managed container of source sets

- Add `all(Action<? super T)` method to `ModelSet`.
- Add DSL support for `all { }` rule.

## Backlog

- Documentation and samples

### Managed type constraints/features

- Value types:
    - Should support `is` style getters for boolean properties.
    - Should support all numeric types, including `Number`. (?)
    - Should support primitives. (?)
    - Should support more value types, such as `File` and `CharSequence`
- Support parameterized non-collection types as managed types

### Type coercions and general conveniences

- Mix DSL and Groovy methods into managed type implementations.
    - Add DSL and Groovy type coercion for enums, closures, files, etc
    - Improve 'missing property' and 'missing method' exceptions.
    - 'Cannot set read-only property' error messages should report public type instead of implementation type.
    - Error message attempting to set property using unsupported type should report public type and acceptable types.
    - Missing method exception in DSL closure reports the method missing on the containing project, rather than the delegate object. Works for abstract Groovy class.
- Add Java API for type coercion

### Collections

- Implement `add()` and treat it as adding a reference, and remove() and treat it as removing a reference.
- Collections of value elements
- Collections of collections
- Map type collections
- Ordered collections
- Semi ordered collections (e.g. command line, where some elements have an order relationship)
- Maps of value elements
- Maps of model elements
- Collections of implicitly keyed elements, acting as a map
- Equality concerns when using sets

### Extensibility & views

- Convenience and/or enforcement of “internal to plugin” properties and views/types
- Extending model elements with new properties
- Specializing model elements to apply new views
- Reporting on usages of deprecated and incubating properties and views/types

### Performance

- ModelSchema does not hold reference to schemas for property types or collection element types
    - Each time a managed object is created, need to do a cache lookup for the schema of each property.
    - Each time a managed set is created, need to do a cache lookup for the schema of the element type.
    - Each time a managed object property is set, need to do a cache lookup to do validation.
- Managed projections should be reused per type
- Unmanaged projections should be reused per type
- Unmanaged instance model views should be used for the life of the node (right now we create a new view each time we need the node value)
- Read only views should not strongly reference backing node once the view has been fully realized

### Behaviour

- Managed types should be able to internally use service (with potentially build local state) to provide convenience methods (?)
- Declaring “services” (i.e. behaviour is interesting, not state) using same “techniques” as managed types?

### Misc

- Audit of error messages
    - Invalid managed types
    - Immutability violations
- Semantics of equals/hashCode
- Support getting "address" (creation/canonical path) of a managed object
- Throw a meaningful exception instead of failing with `OutOfMemoryError` at runtime when a managed type instantiation cycle is encountered (a composite type that contains an instance of itself keeps on creating new instances indefinitely)
- Attempt to call setter method in (abstract class) managed model from non-abstract getter receives error when extracting type metadata (i.e. fail sooner than runtime)
