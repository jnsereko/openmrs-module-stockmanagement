# stockmanagement 2.1.2-uvl2

The published `org.openmrs.module:stockmanagement-omod:2.1.2` with two fixes, built for UVL-EMR
(MadiroGlobalHealth/UVL-EMR#447):

- `2.1.2-uvl1`: stock operation permissions are computed on every request.
- `2.1.2-uvl2`: creating or updating a stock operation is checked against the user's role
  scopes, and a refusal by the scopes is answered with a 403.

## uvl1: the permission cache

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

## uvl2: role scopes on create and update

Every action on an operation (submit, approve, reject, return, dispatch, complete, cancel) is
checked in `StockManagementServiceImpl.processStockOperationAction` against the user's role
scopes: the privilege the action needs, for the operation's type, at its location (the
destination for receiving). Creating and updating an operation is not: `saveStockOperation` only
carries `@Authorized("Task: stockmanagement.stockoperations.mutate")`, a global privilege. So a
Pharmacist scoped to one dispensing box could create an Opening Stock at the central pharmacy
through the API.

`StockOperationResource.save` now checks the same thing the actions check
(`StockOperationType.userCanProcess`, that is `userHasStockManagementPrivilege` with
`Task: stockmanagement.stockoperations.mutate`) at the location `saveStockOperation` will set:
the destination of a Receipt, otherwise the source. On update it also checks the stored
location, which `saveStockOperation` keeps, so an operation can't be moved out of scope either.
A user holding `Task: stockmanagement.userRoleScopes.mutate` (Inventory Administrator; System
Developer holds every privilege) skips this check; the actions still apply their own.

A refusal is an `APIAuthenticationException`, which the REST module answers with a 403 and the
module's own message (`stockmanagement.stockoperation.nopermission`).
`StockOperationActionResource` answers the service's refusal of an action the same way, where
2.1.2 gave a 500.

The module has no rule against approving an operation one created: neither the `permission`
flags (`canApprove`) nor `approveStockOperation` look at the creator. So uvl2 adds none.

## Why this isn't a source build

No tag, branch or pull request in this repository carries the source of the published 2.1.x
releases: `permissionCacheHolder` and `enrichDisplayNames` don't appear anywhere in its history.
So this directory doesn't rebuild the module:

- `src/main/java/.../StockOperationResource.java` is the 2.1.2 class decompiled with CFR 0.152.
  It was first committed with one hand edit (a generic type javac needs). Its compiled methods,
  signatures and annotations match the published class. Later commits remove the cache and add
  the scope check.
- `src/main/java/.../StockOperationActionResource.java` is the 2.1.2 class decompiled the same
  way, first committed unedited (it compiles to the published bytecode, `javap -p -c`).
- `pom.xml` compiles those classes against the published 2.1.2 omod and tests them against the
  unpacked omod with the classes replaced. It then zips `target/stockmanagement-2.1.2-uvl2.omod`:
  the published omod with those classes swapped and `config.xml` at version `2.1.2-uvl2`. Every
  other file is the published 2.1.2.

```
mvn clean package
```

`StockOperationResourceTest` fails on the 2.1.2 class: all three tests reuse one thread and one
operation uuid, as Tomcat does. `StockOperationResourceSaveTest` and
`StockOperationActionResourceTest` cover the scope check; they override the two hooks it adds
(`userIsStockAdministrator`, `getNoPermissionMessage`), so they don't compile against 2.1.2.

Delete this once UVL-EMR runs a stockmanagement release with both fixes.
