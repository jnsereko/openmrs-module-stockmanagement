# stockmanagement 2.1.2-uvl4

The published `org.openmrs.module:stockmanagement-omod:2.1.2` with four fixes, built for UVL-EMR
(MadiroGlobalHealth/UVL-EMR#447, #638, #641):

- `2.1.2-uvl1`: stock operation permissions are computed on every request.
- `2.1.2-uvl2`: creating or updating a stock operation is checked against the user's role
  scopes, and a refusal by the scopes is answered with a 403.
- `2.1.2-uvl3`: an operation's items keep their own received quantity in the single-operation
  representation.
- `2.1.2-uvl4`: an approved requisition can be issued from, and its Stock Issue completes it.

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

## uvl3: the received quantity of an operation's items

`GET stockoperation/{uuid}?v=full` builds `stockOperationItems` in
`StockOperationResource.getStockOperationItems`. 2.1.2 there fills each item's `quantityDispensed`,
`beginningBalance` and `stockInHand` from `getAggregatedStockItemSummaries` over the 30 days to the
operation date, and also replaced `quantityReceived` with the summary's `received`: everything the
stock item received in those 30 days. On UAT a Stock Issue of 6 showed 1144 and one of 7 showed
1251, the item's positive transactions in the 30 days to each operation date, while the operation
item rows and the list endpoint (`doSearch`, which doesn't call the getter's summaries) held 6 and
7. The stock app shows `quantityReceived` as « Quantité reçue » in the received items.

uvl3 no longer sets `quantityReceived` from the summary, nor to zero when the item has none: it is
the operation item's own value, as on upstream `master`, which has no summaries in this getter.
The other three summary fields are unchanged; the stock app 3.3.1 reads none of them.

## uvl4: issuing an approved requisition

2.1.2 approves an operation to `AUTHORIZED` (the published source on `master` and 3.0.0 approve to
`COMPLETED`), but `StockOperationDTO.isRequisitionAndCanIssueStock` still asks for a `COMPLETED`
requisition. So `permission.isRequisitionAndCanIssueStock` was never true for an approved
requisition, the stock app never showed « Émettre du Stock » (Issue Stock), and the central pharmacy made an
unlinked Stock Issue: no `requisitionStockOperationUuid`, no « Demandé », and the requisition
stayed `AUTHORIZED` for good (nothing in 2.1.2 moves it on; `COMPLETE` on it needs a privilege
nobody has).

- `StockOperationResource.getPermission` sets `isRequisitionAndCanIssueStock` for a locked
  requisition that is `AUTHORIZED` (approved) or `COMPLETED` (a type without approval), as long as
  no Stock Issue linked to it is still going (one not cancelled, rejected or voided, from
  `findStockOperationLinks`). The scope check is unchanged: `mutate` for Stock Issue at the
  requisition's destination, the store it asks from.
- `StockIssueOperationTypeProcessor.onCompleted` (api, inside `lib/stockmanagement-api-2.1.2.jar`)
  sets an `AUTHORIZED` requisition linked as the issue's parent to `COMPLETED` when the issue
  completes, in the same transaction. Its `completedBy`/`completedDate` stay the approval's: 2.1.2
  writes the approval there and has no other field. `onCompleted` also runs on approval, so only a
  `COMPLETED` issue acts.

The Stock Issue is linked by the stock app sending `requisitionStockOperationUuid` on create, which
2.1.2 already stores as a `StockOperationLink` and returns on the issue.

## Why this isn't a source build

No tag, branch or pull request in this repository carries the source of the published 2.1.x
releases: `permissionCacheHolder` and `enrichDisplayNames` don't appear anywhere in its history.
So this directory doesn't rebuild the module:

- `src/main/java/.../StockOperationResource.java` is the 2.1.2 class decompiled with CFR 0.152.
  It was first committed with one hand edit (a generic type javac needs). Its compiled methods,
  signatures and annotations match the published class. Later commits remove the cache, add
  the scope check and the requisition check.
- `src/main/java/.../StockOperationActionResource.java` is the 2.1.2 class decompiled the same
  way, first committed unedited (it compiles to the published bytecode, `javap -p -c`).
- `src/main/java/.../api/impl/StockIssueOperationTypeProcessor.java` is the 2.1.2 api class
  decompiled the same way, first committed unedited (same bytecode, with its anonymous classes).
- `pom.xml` compiles those classes against the published 2.1.2 omod and api and tests them
  against the unpacked omod with the classes replaced. It then zips
  `target/stockmanagement-2.1.2-uvl4.omod`: the published omod with the web classes swapped, the
  api class swapped inside `lib/stockmanagement-api-2.1.2.jar` (unzipped and zipped back under its
  own name), and `config.xml` at version `2.1.2-uvl4`. Every other file is the published 2.1.2.

```
mvn clean package
```

`StockOperationResourceTest` fails on the 2.1.2 class: the three `getPermission` tests reuse one
thread and one operation uuid, as Tomcat does, and the two `getStockOperationItems` tests get the
30-day total (or zero) instead of the item's received quantity. `StockOperationResourceSaveTest` and
`StockOperationActionResourceTest` cover the scope check; they override the two hooks it adds
(`userIsStockAdministrator`, `getNoPermissionMessage`), so they don't compile against 2.1.2.

`StockIssueOperationTypeProcessorTest` and the four requisition tests of `StockOperationResourceTest`
fail on the 2.1.2 classes (no `completeRequisitions`; no « Issue Stock » on an `AUTHORIZED`
requisition).

Delete this once UVL-EMR runs a stockmanagement release with all four fixes.
