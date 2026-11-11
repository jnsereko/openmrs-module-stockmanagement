# stockmanagement 2.1.2-uvl1

The published `org.openmrs.module:stockmanagement-omod:2.1.2` with one fix: stock operation
permissions are computed on every request. Built for UVL-EMR (MadiroGlobalHealth/UVL-EMR#447).

## The defect

In 2.1.2, `StockOperationResource.getPermission` keeps each operation's permission flags
(`canEdit`, `canApprove`, `canReceiveItems`, `isRequisitionAndCanIssueStock`, ...) in a
`ThreadLocal` map keyed by the operation uuid and never clears it. Tomcat reuses its worker
threads, so a thread that once computed the flags for an operation answers every later request
for it the same way, for any user and whatever the operation's status has become. The stock app's
operations list then hides "Complete dispatch" from the receiver of a dispatched Stock Issue, and
a role scope granted since does not change it, until OpenMRS restarts.

The fix removes the cache. `getPermission` now runs its checks on each call. That costs a second
evaluation when a full representation also asks for `stockOperationItems`, which calls
`getPermission` itself.

Upstream `master` has the same cache as an instance field. It's being removed in
[O3-6248](https://openmrs.atlassian.net/browse/O3-6248),
openmrs/openmrs-module-stockmanagement#57.

## Why this isn't a source build

No tag, branch or pull request in this repository carries the source of the published 2.1.x
releases: `permissionCacheHolder` and `enrichDisplayNames` don't appear anywhere in its history.
So this directory doesn't rebuild the module:

- `src/main/java/.../StockOperationResource.java` is the 2.1.2 class decompiled with CFR 0.152.
  The first commit on this branch has it with one hand edit (a generic type javac needs). Its
  compiled methods, signatures and annotations match the published class. The second commit
  removes the cache.
- `pom.xml` compiles that class against the published 2.1.2 omod and tests it against the
  unpacked omod with the class replaced. It then zips `target/stockmanagement-2.1.2-uvl1.omod`:
  the published omod with that class swapped and `config.xml` at version `2.1.2-uvl1`. Every other
  file is the published 2.1.2.

```
mvn clean package
```

`StockOperationResourceTest` fails on the 2.1.2 class: all three tests reuse one thread and one
operation uuid, as Tomcat does. It passes with the fix.

Delete this once UVL-EMR runs a stockmanagement release without the cache.
