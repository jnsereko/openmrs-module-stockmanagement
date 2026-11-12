/*
 * StockOperationResource from the published stockmanagement-omod 2.1.2, decompiled with
 * CFR 0.152 because its source is not public. The only hand edit to the decompiled text is
 * the type of existingStockOperationItems in setStockOperationItems (List<Object> to
 * List<StockOperationItemDTO>), which javac needs; the compiled class keeps the 2.1.2
 * methods, signatures and annotations.
 */
package org.openmrs.module.stockmanagement.web.resource;

import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.BooleanProperty;
import io.swagger.models.properties.DateProperty;
import io.swagger.models.properties.DateTimeProperty;
import io.swagger.models.properties.IntegerProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.StringProperty;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.openmrs.Concept;
import org.openmrs.Location;
import org.openmrs.User;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.context.Context;
import org.openmrs.module.stockmanagement.api.StockManagementException;
import org.openmrs.module.stockmanagement.api.StockManagementService;
import org.openmrs.module.stockmanagement.api.dto.PrivilegeScope;
import org.openmrs.module.stockmanagement.api.dto.Result;
import org.openmrs.module.stockmanagement.api.dto.StockItemLossesAndAdjustmentsDTO;
import org.openmrs.module.stockmanagement.api.dto.StockItemPackagingUOMDTO;
import org.openmrs.module.stockmanagement.api.dto.StockItemPackagingUOMSearchFilter;
import org.openmrs.module.stockmanagement.api.dto.StockItemSummaryDTO;
import org.openmrs.module.stockmanagement.api.dto.StockOperationDTO;
import org.openmrs.module.stockmanagement.api.dto.StockOperationItemDTO;
import org.openmrs.module.stockmanagement.api.dto.StockOperationItemSearchFilter;
import org.openmrs.module.stockmanagement.api.dto.StockOperationLinkDTO;
import org.openmrs.module.stockmanagement.api.dto.StockOperationSearchFilter;
import org.openmrs.module.stockmanagement.api.model.Party;
import org.openmrs.module.stockmanagement.api.model.StockItem;
import org.openmrs.module.stockmanagement.api.model.StockOperation;
import org.openmrs.module.stockmanagement.api.model.StockOperationStatus;
import org.openmrs.module.stockmanagement.api.model.StockOperationType;
import org.openmrs.module.stockmanagement.web.resource.ResourceBase;
import org.openmrs.module.stockmanagement.web.resource.StockOperationItemResource;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.PropertyGetter;
import org.openmrs.module.webservices.rest.web.annotation.PropertySetter;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.representation.FullRepresentation;
import org.openmrs.module.webservices.rest.web.representation.RefRepresentation;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.response.ResourceDoesNotSupportOperationException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.springframework.web.client.RestClientException;

@Resource(name="v1/stockmanagement/stockoperation", supportedClass=StockOperationDTO.class, supportedOpenmrsVersions={"1.9.*", "1.10.*", "1.11.*", "1.12.*", "2.*"})
public class StockOperationResource
extends ResourceBase<StockOperationDTO> {
    public StockOperationDTO getByUniqueId(String uniqueId) {
        Result<StockOperationLinkDTO> parents;
        StockOperationDTO stockOperationDTO;
        StockOperationSearchFilter filter = new StockOperationSearchFilter();
        filter.setStockOperationUuid(uniqueId);
        Result<StockOperationDTO> result = this.getStockManagementService().findStockOperations(filter);
        StockOperationDTO stockOperationDTO2 = stockOperationDTO = result.getData().isEmpty() ? null : result.getData().get(0);
        if (stockOperationDTO != null && "stockissue".equals(stockOperationDTO.getOperationType()) && (parents = this.getStockManagementService().getParentStockOperationLinks(stockOperationDTO.getUuid())).getData() != null && parents.getData().size() > 0) {
            stockOperationDTO.setRequisitionStockOperationUuid(parents.getData().get(0).getParentUuid());
        }
        return stockOperationDTO;
    }

    protected void delete(StockOperationDTO delegate, String reason, RequestContext context) throws ResponseException {
        throw new ResourceDoesNotSupportOperationException();
    }

    protected PageableResult doSearch(RequestContext context) {
        Result<StockOperationDTO> result;
        Date date;
        String[] params;
        StockOperationSearchFilter filter = new StockOperationSearchFilter();
        filter.setIncludeVoided(context.getIncludeAll());
        filter.setStartIndex(context.getStartIndex());
        int requestedLimit = context.getLimit();
        int maxPageSize = 50;
        filter.setLimit(Math.min(requestedLimit > 0 ? requestedLimit : maxPageSize, maxPageSize));
        String param = context.getParameter("q");
        if (StringUtils.isNotBlank((String)param)) {
            filter.setSearchText(param);
        }
        StockManagementService stockManagementService = this.getStockManagementService();
        param = context.getParameter("operationTypeUuid");
        if (StringUtils.isNotBlank((String)param)) {
            params = param.split(",", 10);
            ArrayList<Integer> typeIds = new ArrayList<Integer>();
            for (String typeUuid : params) {
                StockOperationType operationType = stockManagementService.getStockOperationTypeByUuid(typeUuid);
                if (operationType == null) continue;
                typeIds.add(operationType.getId());
            }
            if (typeIds.isEmpty()) {
                return this.emptyResult(context);
            }
            filter.setOperationTypeId(typeIds);
        }
        if (StringUtils.isNotBlank((String)(param = context.getParameter("status")))) {
            params = param.split(",", 10);
            ArrayList<StockOperationStatus> statusIds = new ArrayList<StockOperationStatus>();
            for (String status : params) {
                StockOperationStatus opStatus = Enum.valueOf(StockOperationStatus.class, status);
                statusIds.add(opStatus);
            }
            if (statusIds.isEmpty()) {
                return this.emptyResult(context);
            }
            filter.setStatus(statusIds);
        }
        if (StringUtils.isNotBlank((String)(param = context.getParameter("locationUuid")))) {
            Location location = Context.getLocationService().getLocationByUuid(param);
            if (location == null) {
                return this.emptyResult(context);
            }
            filter.setLocationId(location.getId());
        }
        if (StringUtils.isNotBlank((String)(param = context.getParameter("isLocationOther")))) {
            filter.setIsLocationOther("true".equalsIgnoreCase(param) || "1".equals(param));
        }
        if (StringUtils.isNotBlank((String)(param = context.getParameter("sourceTypeUuid")))) {
            params = param.split(",", 10);
            ArrayList<Integer> conceptIds = new ArrayList<Integer>();
            for (String conceptUuid : params) {
                Concept concept = Context.getConceptService().getConceptByUuid(conceptUuid);
                if (concept == null) continue;
                conceptIds.add(concept.getId());
            }
            if (conceptIds.isEmpty()) {
                return this.emptyResult(context);
            }
            filter.setSourceTypeIds(conceptIds);
        }
        if (StringUtils.isNotBlank((String)(param = context.getParameter("stockItemUuid")))) {
            StockItem stockItem = stockManagementService.getStockItemByUuid(param);
            if (stockItem == null) {
                return this.emptyResult(context);
            }
            filter.setStockItemId(stockItem.getId());
        }
        if (StringUtils.isNotBlank((String)(param = context.getParameter("operationDateMin")))) {
            date = (Date)ConversionUtil.convert((Object)param, Date.class);
            if (date == null) {
                return this.emptyResult(context);
            }
            filter.setOperationDateMin(date);
        }
        if (StringUtils.isNotBlank((String)(param = context.getParameter("operationDateMax")))) {
            date = (Date)ConversionUtil.convert((Object)param, Date.class);
            if (date == null) {
                return this.emptyResult(context);
            }
            filter.setOperationDateMax(date);
        }
        if (!(result = this.getStockManagementService().findStockOperations(filter)).getData().isEmpty() && context.getRepresentation() == Representation.FULL) {
            StockOperationItemSearchFilter itemSearchFilter = new StockOperationItemSearchFilter();
            itemSearchFilter.setIncludeStockUnitName(true);
            itemSearchFilter.setIncludePackagingUnitName(true);
            itemSearchFilter.setStockOperationUuids(result.getData().stream().map(StockOperationDTO::getUuid).collect(Collectors.toList()));
            Result<StockOperationItemDTO> items = this.getStockManagementService().findStockOperationItems(itemSearchFilter);
            for (StockOperationDTO stockOperation : result.getData()) {
                List<StockOperationItemDTO> operationItems = items.getData().stream().filter(p -> p.getStockOperationUuid().equals(stockOperation.getUuid())).collect(Collectors.toList());
                StockOperationResource.enrichDisplayNames(operationItems);
                stockOperation.setStockOperationItems(operationItems);
            }
        }
        return this.toAlreadyPaged(result, context);
    }

    protected PageableResult doGetAll(RequestContext context) throws ResponseException {
        return this.doSearch(context);
    }

    public StockOperationDTO newDelegate() {
        return new StockOperationDTO();
    }

    public StockOperationDTO save(StockOperationDTO delegate) {
        this.checkUserCanMutate(delegate);
        try {
            StockOperation stockOperation = this.getStockManagementService().saveStockOperation(delegate);
            return this.getByUniqueId(stockOperation.getUuid());
        }
        catch (StockManagementException exception) {
            throw new RestClientException(exception.getMessage());
        }
    }

    /*
     * 2.1.2 checks only the global "Task: stockmanagement.stockoperations.mutate" privilege on
     * create and update, so any user holding it could create or change an operation at any
     * location, of any type. Apply the check every action already makes
     * (StockOperationType.userCanProcess, used by processStockOperationAction and
     * voidStockOperationItem): the user's role scopes must give that privilege for the
     * operation's type at its location. The location is the one saveStockOperation sets
     * (the destination of a Receipt, otherwise the source), and on update also the stored one,
     * which saveStockOperation keeps. A stock administrator keeps full rights.
     */
    void checkUserCanMutate(StockOperationDTO delegate) {
        if (this.userIsStockAdministrator()) {
            return;
        }
        StockManagementService service = this.getStockManagementService();
        StockOperationType stockOperationType = service.getStockOperationTypeByUuid(delegate.getOperationTypeUuid());
        if (stockOperationType == null) {
            return;
        }
        ArrayList<Location> locations = new ArrayList<Location>();
        if (StringUtils.isNotBlank((String)delegate.getUuid())) {
            StockOperation stored = service.getStockOperationByUuid(delegate.getUuid());
            if (stored != null && stored.getAtLocation() != null) {
                locations.add(stored.getAtLocation());
            }
        } else if (StringUtils.isNotBlank((String)delegate.getAtLocationUuid())) {
            locations.add(Context.getLocationService().getLocationByUuid(delegate.getAtLocationUuid()));
        }
        Party party = null;
        if (RECEIPT_OPERATION_TYPE_UUID.equals(delegate.getOperationTypeUuid())) {
            if (StringUtils.isNotBlank((String)delegate.getDestinationUuid())) {
                party = service.getPartyByUuid(delegate.getDestinationUuid());
            }
        } else if (StringUtils.isNotBlank((String)delegate.getSourceUuid())) {
            party = service.getPartyByUuid(delegate.getSourceUuid());
        }
        if (party != null && party.getLocation() != null) {
            locations.add(party.getLocation());
        }
        for (Location location : locations) {
            if (location == null || !service.userHasStockManagementPrivilege(Context.getAuthenticatedUser(), location, stockOperationType, MUTATE_PRIVILEGE)) {
                throw new APIAuthenticationException(this.getNoPermissionMessage());
            }
        }
    }

    // StockManagementServiceImpl.RECEIPT_OPERATION_TYPE_UUID, private there.
    private static final String RECEIPT_OPERATION_TYPE_UUID = "44444444-4444-4444-4444-444444444444";

    private static final String MUTATE_PRIVILEGE = "Task: stockmanagement.stockoperations.mutate";

    static final String STOCK_ADMINISTRATOR_PRIVILEGE = "Task: stockmanagement.userRoleScopes.mutate";

    /*
     * Who may grant role scopes (Inventory Administrator) may act on any operation; a super user
     * (System Developer) holds every privilege.
     */
    protected boolean userIsStockAdministrator() {
        User user = Context.getAuthenticatedUser();
        return user != null && user.hasPrivilege(STOCK_ADMINISTRATOR_PRIVILEGE);
    }

    // The message StockManagementServiceImpl gives when the scopes refuse an action.
    protected String getNoPermissionMessage() {
        return Context.getMessageSourceService().getMessage("stockmanagement.stockoperation.nopermission");
    }

    public DelegatingResourceDescription getCreatableProperties() throws ResourceDoesNotSupportOperationException {
        DelegatingResourceDescription description = new DelegatingResourceDescription();
        description.addProperty("destinationUuid");
        description.addProperty("externalReference");
        description.addProperty("operationDate");
        description.addProperty("reasonUuid");
        description.addProperty("remarks");
        description.addProperty("sourceUuid");
        description.addProperty("operationTypeUuid");
        description.addProperty("responsiblePersonUuid");
        description.addProperty("approvalRequired");
        description.addProperty("responsiblePersonOther");
        description.addProperty("requisitionStockOperationUuid");
        description.addProperty("stockOperationItems");
        description.addProperty("requestType");
        return description;
    }

    public DelegatingResourceDescription getUpdatableProperties() {
        DelegatingResourceDescription description = new DelegatingResourceDescription();
        description.addProperty("destinationUuid");
        description.addProperty("externalReference");
        description.addProperty("operationDate");
        description.addProperty("reasonUuid");
        description.addProperty("remarks");
        description.addProperty("sourceUuid");
        description.addProperty("approvalRequired");
        description.addProperty("responsiblePersonUuid");
        description.addProperty("responsiblePersonOther");
        description.addProperty("stockOperationItems");
        description.addProperty("requestType");
        return description;
    }

    public void purge(StockOperationDTO delegate, RequestContext context) throws ResponseException {
        this.delete(delegate, null, context);
    }

    public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
        DelegatingResourceDescription description = new DelegatingResourceDescription();
        if (rep instanceof DefaultRepresentation || rep instanceof FullRepresentation) {
            description.addProperty("uuid");
            description.addProperty("cancelReason");
            description.addProperty("cancelledBy");
            description.addProperty("cancelledByGivenName");
            description.addProperty("cancelledByFamilyName");
            description.addProperty("cancelledDate");
            description.addProperty("completedBy");
            description.addProperty("completedByGivenName");
            description.addProperty("completedByFamilyName");
            description.addProperty("completedDate");
            description.addProperty("destinationUuid");
            description.addProperty("destinationName");
            description.addProperty("externalReference");
            description.addProperty("atLocationUuid");
            description.addProperty("atLocationName");
            description.addProperty("operationDate");
            description.addProperty("locked");
            description.addProperty("operationNumber");
            description.addProperty("operationOrder");
            description.addProperty("approvalRequired");
            description.addProperty("reasonUuid");
            description.addProperty("reasonName");
            description.addProperty("remarks");
            description.addProperty("sourceUuid");
            description.addProperty("sourceName");
            description.addProperty("status");
            description.addProperty("returnReason");
            description.addProperty("rejectionReason");
            description.addProperty("operationTypeUuid");
            description.addProperty("operationType");
            description.addProperty("operationTypeName");
            description.addProperty("responsiblePersonUuid");
            description.addProperty("responsiblePersonGivenName");
            description.addProperty("responsiblePersonFamilyName");
            description.addProperty("responsiblePersonOther");
            description.addProperty("creator");
            description.addProperty("dateCreated");
            description.addProperty("creatorGivenName");
            description.addProperty("creatorFamilyName");
            description.addProperty("requisitionStockOperationUuid");
            description.addProperty("submittedByGivenName");
            description.addProperty("submittedByFamilyName");
            description.addProperty("submittedDate");
            description.addProperty("returnedByGivenName");
            description.addProperty("returnedByFamilyName");
            description.addProperty("returnedDate");
            description.addProperty("requestType");
            description.addProperty("rejectedByGivenName");
            description.addProperty("rejectedByFamilyName");
            description.addProperty("rejectedDate");
            description.addProperty("dispatchedByGivenName");
            description.addProperty("dispatchedByFamilyName");
            description.addProperty("dispatchedDate");
        }
        if (rep instanceof DefaultRepresentation) {
            description.addLink("full", ".?v=" + RestConstants.REPRESENTATION_FULL);
        }
        if (rep instanceof FullRepresentation) {
            description.addProperty("permission");
            description.addProperty("stockOperationItems");
            description.addSelfLink();
        }
        if (rep instanceof RefRepresentation) {
            description.addProperty("uuid");
            description.addProperty("operationNumber");
        }
        return description;
    }

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    @PropertySetter(value="stockOperationItems")
    public void setStockOperationItems(StockOperationDTO instance, ArrayList<Map<String, ?>> items) {
        if (items == null) {
            instance.setStockOperationItems(null);
            return;
        }
        if (items.isEmpty()) {
            instance.setStockOperationItems(new ArrayList<StockOperationItemDTO>());
            return;
        }
        StockOperationItemResource handler = new StockOperationItemResource();
        DelegatingResourceDescription creatableProperties = handler.getCreatableProperties();
        DelegatingResourceDescription modifiableProperties = handler.getUpdatableProperties();
        List<StockOperationItemDTO> existingStockOperationItems = null;
        if (StringUtils.isBlank((String)instance.getUuid())) {
            existingStockOperationItems = new ArrayList<StockOperationItemDTO>();
        } else {
            StockOperationItemSearchFilter itemSearchFilter = new StockOperationItemSearchFilter();
            itemSearchFilter.setStockOperationUuids(Arrays.asList(instance.getUuid()));
            existingStockOperationItems = this.getStockManagementService().findStockOperationItems(itemSearchFilter).getData();
        }
        ArrayList<StockOperationItemDTO> itemsToUpdate = new ArrayList<StockOperationItemDTO>();
        for (Map<String, ?> item : items) {
            StockOperationItemDTO itemDTO = null;
            boolean isNew = true;
            if (item.containsKey("uuid") && item.get("uuid") != null && !StringUtils.isBlank((String)item.get("uuid").toString())) {
                Optional<StockOperationItemDTO> existingItem;
                String uuid = item.get("uuid").toString();
                if (existingStockOperationItems == null) {
                    StockOperationItemSearchFilter itemSearchFilter = new StockOperationItemSearchFilter();
                    itemSearchFilter.setUuid(uuid);
                    existingStockOperationItems = this.getStockManagementService().findStockOperationItems(itemSearchFilter).getData();
                    if (!existingStockOperationItems.isEmpty()) {
                        itemSearchFilter.setUuid(null);
                        itemSearchFilter.setStockOperationUuids(Arrays.asList(((StockOperationItemDTO)existingStockOperationItems.get(0)).getStockOperationUuid()));
                        existingStockOperationItems = this.getStockManagementService().findStockOperationItems(itemSearchFilter).getData();
                    }
                }
                if (!(existingItem = existingStockOperationItems.stream().filter(p -> uuid.equals(p.getUuid())).findFirst()).isPresent()) throw new RestClientException(String.format("Item %s not found", uuid));
                isNew = false;
                itemDTO = existingItem.get();
            } else {
                itemDTO = new StockOperationItemDTO();
            }
            DelegatingResourceDescription propertiesToApply = isNew ? creatableProperties : modifiableProperties;
            for (Map.Entry prop : propertiesToApply.getProperties().entrySet()) {
                if (!item.containsKey(prop.getKey()) || "type".equals(prop.getKey())) continue;
                handler.setProperty(itemDTO, (String)prop.getKey(), item.get(prop.getKey()));
            }
            itemsToUpdate.add(itemDTO);
        }
        instance.setStockOperationItems(itemsToUpdate);
    }

    /*
     * Computed on every call. 2.1.2 kept the result in a ThreadLocal map keyed by the
     * operation uuid that was never cleared, so a server thread answered later requests,
     * from any user, with the flags it computed first, whatever the operation's status
     * had become since (a dispatched Stock Issue without canReceiveItems for its
     * receiver). Upstream removes its own copy of this cache in O3-6248.
     */
    @PropertyGetter(value="permission")
    public SimpleObject getPermission(StockOperationDTO stockOperationDTO) {
        HashSet<PrivilegeScope> privilegeScopes;
        SimpleObject simpleObject = new SimpleObject();
        simpleObject.add("canView", (Object)true);
        boolean canEdit = stockOperationDTO.isUpdateable();
        boolean canApprove = stockOperationDTO.isApproveable();
        boolean canReceiveItems = stockOperationDTO.canReceiveItems();
        boolean canDisplayReceivedItems = stockOperationDTO.canDisplayReceivedItems();
        boolean isRequisitionAndCanIssueStock = stockOperationDTO.isRequisitionAndCanIssueStock();
        StockOperationType stockOperationType = this.getStockManagementService().getStockOperationTypeByUuid(stockOperationDTO.getOperationTypeUuid());
        boolean canUpdateBatchInformation = stockOperationDTO.canUpdateBatchInformation(stockOperationType);
        Boolean userHasEditPermissions = null;
        if (canEdit || canApprove) {
            privilegeScopes = this.getStockManagementService().getPrivilegeScopes(Context.getAuthenticatedUser(), Context.getLocationService().getLocationByUuid(stockOperationDTO.getAtLocationUuid()), this.getStockManagementService().getStockOperationTypeByUuid(stockOperationDTO.getOperationTypeUuid()), Arrays.asList("Task: stockmanagement.stockoperations.mutate", "Task: stockmanagement.stockoperations.approve"));
            if (canEdit) {
                canEdit = canEdit && privilegeScopes.stream().anyMatch(p -> p.getPrivilege().equals("Task: stockmanagement.stockoperations.mutate"));
            } else {
                userHasEditPermissions = privilegeScopes.stream().anyMatch(p -> p.getPrivilege().equals("Task: stockmanagement.stockoperations.mutate"));
            }
            boolean bl = canApprove = canApprove && privilegeScopes.stream().anyMatch(p -> p.getPrivilege().equals("Task: stockmanagement.stockoperations.approve"));
        }
        if (canReceiveItems || isRequisitionAndCanIssueStock) {
            if (StringUtils.isBlank((String)stockOperationDTO.getDestinationUuid())) {
                canReceiveItems = false;
                isRequisitionAndCanIssueStock = false;
            } else {
                HashSet<PrivilegeScope> privilegeScopes2;
                Party party = this.getStockManagementService().getPartyByUuid(stockOperationDTO.getDestinationUuid());
                if (party == null || party.getLocation() == null) {
                    canReceiveItems = false;
                    isRequisitionAndCanIssueStock = false;
                }
                if (canReceiveItems) {
                    privilegeScopes2 = this.getStockManagementService().getPrivilegeScopes(Context.getAuthenticatedUser(), party.getLocation(), this.getStockManagementService().getStockOperationTypeByUuid(stockOperationDTO.getOperationTypeUuid()), Arrays.asList("Task: stockmanagement.stockoperations.receiveitems"));
                    boolean bl = canReceiveItems = canReceiveItems && privilegeScopes2.stream().anyMatch(p -> p.getPrivilege().equals("Task: stockmanagement.stockoperations.receiveitems"));
                }
                if (isRequisitionAndCanIssueStock) {
                    privilegeScopes2 = this.getStockManagementService().getPrivilegeScopes(Context.getAuthenticatedUser(), party.getLocation(), this.getStockManagementService().getStockOperationTypeByType("stockissue"), Arrays.asList("Task: stockmanagement.stockoperations.mutate"));
                    boolean bl = isRequisitionAndCanIssueStock = isRequisitionAndCanIssueStock && privilegeScopes2.stream().anyMatch(p -> p.getPrivilege().equals("Task: stockmanagement.stockoperations.mutate"));
                }
            }
        }
        if (canUpdateBatchInformation) {
            if (userHasEditPermissions == null) {
                privilegeScopes = this.getStockManagementService().getPrivilegeScopes(Context.getAuthenticatedUser(), Context.getLocationService().getLocationByUuid(stockOperationDTO.getAtLocationUuid()), this.getStockManagementService().getStockOperationTypeByUuid(stockOperationDTO.getOperationTypeUuid()), Arrays.asList("Task: stockmanagement.stockoperations.mutate"));
                userHasEditPermissions = !privilegeScopes.isEmpty();
            }
            canUpdateBatchInformation = userHasEditPermissions;
        }
        simpleObject.add("canEdit", (Object)canEdit);
        simpleObject.add("canApprove", (Object)canApprove);
        simpleObject.add("canReceiveItems", (Object)canReceiveItems);
        simpleObject.add("canDisplayReceivedItems", (Object)canDisplayReceivedItems);
        simpleObject.add("isRequisitionAndCanIssueStock", (Object)isRequisitionAndCanIssueStock);
        simpleObject.add("canUpdateBatchInformation", (Object)canUpdateBatchInformation);
        return simpleObject;
    }

    @PropertyGetter(value="stockOperationItems")
    public Collection<StockOperationItemDTO> getStockOperationItems(StockOperationDTO stockOperationDTO) {
        if (stockOperationDTO.getStockOperationItems() != null) {
            StockOperationResource.enrichDisplayNames(stockOperationDTO.getStockOperationItems());
            return stockOperationDTO.getStockOperationItems();
        }
        StockOperationItemSearchFilter itemSearchFilter = new StockOperationItemSearchFilter();
        itemSearchFilter.setIncludeStockUnitName(true);
        itemSearchFilter.setIncludePackagingUnitName(true);
        itemSearchFilter.setStockOperationUuids(Arrays.asList(stockOperationDTO.getUuid()));
        Result<StockOperationItemDTO> itemsResult = this.getStockManagementService().findStockOperationItems(itemSearchFilter);
        List<StockOperationItemDTO> items = itemsResult.getData();
        if (!items.isEmpty()) {
            Set<Integer> stockItemIds = items.stream().map(StockOperationItemDTO::getStockItemId).filter(Objects::nonNull).collect(Collectors.toSet());
            Date operationOrToday = stockOperationDTO.getOperationDate() != null ? stockOperationDTO.getOperationDate() : new Date();
            Date periodStart = DateUtils.addDays((Date)operationOrToday, (int)-30);
            Map<Integer, StockItemSummaryDTO> summaries = this.getStockManagementService().getAggregatedStockItemSummaries(stockItemIds, operationOrToday, periodStart, operationOrToday);
            for (StockOperationItemDTO item : items) {
                StockItemSummaryDTO sum = summaries.get(item.getStockItemId());
                // quantityReceived stays the operation item's own (uvl3): sum.getReceived() is
                // what the stock item received at any location in the 30 days to the operation
                if (sum != null) {
                    item.setQuantityDispensed(sum.getDispensed());
                    item.setBeginningBalance(sum.getBeginningBalance());
                    item.setStockInHand(sum.getCurrentBalance());
                    item.setStockOutDays(0);
                    continue;
                }
                item.setQuantityDispensed(BigDecimal.ZERO);
                item.setBeginningBalance(BigDecimal.ZERO);
                item.setStockInHand(BigDecimal.ZERO);
                item.setStockOutDays(0);
            }
        }
        StockItemPackagingUOMSearchFilter filter = new StockItemPackagingUOMSearchFilter();
        filter.setIncludeVoided(false);
        filter.setStockItemUuids(items.stream().map(p -> p.getStockItemUuid()).distinct().collect(Collectors.toList()));
        List<StockItemPackagingUOMDTO> packagingUnits = this.getStockManagementService().findStockItemPackagingUOMs(filter).getData();
        List<StockItemLossesAndAdjustmentsDTO> lossesAndAdjustments = this.getStockManagementService().findStockItemLossesAndAdjustments(filter).getData();
        boolean canUpdateBatchInformation = false;
        SimpleObject permissions = this.getPermission(stockOperationDTO);
        Map<Integer, Boolean> stockBatchHasTransactions = null;
        if (permissions != null && permissions.containsKey((Object)"canUpdateBatchInformation") && (canUpdateBatchInformation = ((Boolean)permissions.get("canUpdateBatchInformation")).booleanValue())) {
            stockBatchHasTransactions = this.getStockManagementService().checkStockBatchHasTransactionsAfterOperation(stockOperationDTO.getId(), items.stream().filter(p -> p.getStockBatchId() != null).map(p -> p.getStockBatchId()).distinct().collect(Collectors.toList()));
        }
        for (StockOperationItemDTO itemDTO : items) {
            List<StockItemPackagingUOMDTO> units = packagingUnits.stream().filter(p -> p.getStockItemUuid().equals(itemDTO.getStockItemUuid())).collect(Collectors.toList());
            if (!units.isEmpty()) {
                itemDTO.setPackagingUnits(units);
            }
            if (!canUpdateBatchInformation) continue;
            itemDTO.setCanUpdateBatchInformation(stockBatchHasTransactions != null && !stockBatchHasTransactions.containsKey(itemDTO.getStockBatchId()));
        }
        for (StockOperationItemDTO itemDTO : items) {
            List<StockItemLossesAndAdjustmentsDTO> adj = lossesAndAdjustments.stream().filter(p -> p.getStockItemUuid().equals(itemDTO.getStockItemUuid())).collect(Collectors.toList());
            if (adj.isEmpty()) continue;
            itemDTO.setLossesAndAdjustments(adj);
        }
        for (StockOperationItemDTO itemDTO : items) {
            String nameSource = itemDTO.getDrugName() != null && !itemDTO.getDrugName().isEmpty() ? itemDTO.getDrugName() : itemDTO.getCommonName();
            if (nameSource == null || nameSource.isEmpty()) continue;
            int sep = nameSource.indexOf(" - ");
            itemDTO.setDisplayName(sep > -1 ? nameSource.substring(0, sep).trim() : nameSource);
        }
        return items;
    }

    public List<String> getPropertiesToExposeAsSubResources() {
        return Arrays.asList("action");
    }

    public Model getGETModel(Representation rep) {
        ModelImpl modelImpl = (ModelImpl)super.getGETModel(rep);
        if (rep instanceof DefaultRepresentation || rep instanceof FullRepresentation) {
            modelImpl.property("uuid", (Property)new StringProperty()).property("cancelReason", (Property)new StringProperty()).property("cancelledBy", (Property)new IntegerProperty()).property("cancelledByGivenName", (Property)new StringProperty()).property("cancelledByFamilyName", (Property)new StringProperty()).property("cancelledDate", (Property)new DateTimeProperty()).property("completedBy", (Property)new IntegerProperty()).property("completedByGivenName", (Property)new StringProperty()).property("completedByFamilyName", (Property)new StringProperty()).property("completedDate", (Property)new DateTimeProperty()).property("destinationUuid", (Property)new StringProperty()).property("destinationName", (Property)new StringProperty()).property("externalReference", (Property)new StringProperty()).property("atLocationUuid", (Property)new StringProperty()).property("atLocationName", (Property)new StringProperty()).property("operationDate", (Property)new DateTimeProperty()).property("locked", (Property)new BooleanProperty()).property("operationNumber", (Property)new StringProperty()).property("operationOrder", (Property)new IntegerProperty()).property("reasonUuid", (Property)new StringProperty()).property("reasonName", (Property)new StringProperty()).property("remarks", (Property)new StringProperty()).property("sourceUuid", (Property)new StringProperty()).property("sourceName", (Property)new StringProperty()).property("status", (Property)new StringProperty()).property("returnReason", (Property)new StringProperty()).property("rejectionReason", (Property)new StringProperty()).property("operationTypeUuid", (Property)new StringProperty()).property("operationType", (Property)new StringProperty()).property("operationTypeName", (Property)new StringProperty()).property("responsiblePersonUuid", (Property)new StringProperty()).property("responsiblePersonGivenName", (Property)new StringProperty()).property("responsiblePersonFamilyName", (Property)new StringProperty()).property("responsiblePersonOther", (Property)new StringProperty()).property("approvalRequired", (Property)new BooleanProperty()).property("dateCreated", (Property)new DateTimeProperty()).property("creatorGivenName", (Property)new StringProperty()).property("creatorFamilyName", (Property)new StringProperty()).property("submittedByGivenName", (Property)new StringProperty()).property("submittedByFamilyName", (Property)new StringProperty()).property("submittedDate", (Property)new DateProperty()).property("returnedByGivenName", (Property)new StringProperty()).property("returnedByFamilyName", (Property)new StringProperty()).property("returnedDate", (Property)new DateProperty()).property("rejectedByGivenName", (Property)new StringProperty()).property("rejectedByFamilyName", (Property)new StringProperty()).property("rejectedDate", (Property)new DateProperty()).property("requestType", (Property)new StringProperty());
        }
        if (rep instanceof DefaultRepresentation) {
            // empty if block
        }
        if (rep instanceof FullRepresentation) {
            modelImpl.property("stockOperationItems", (Property)new ArrayProperty());
        }
        if (rep instanceof RefRepresentation) {
            modelImpl.property("uuid", (Property)new StringProperty());
            modelImpl.property("operationNumber", (Property)new StringProperty());
        }
        return modelImpl;
    }

    private static void enrichDisplayNames(List<StockOperationItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (StockOperationItemDTO item : items) {
            String source;
            if (item.getDisplayName() != null || (source = item.getDrugName() != null && !item.getDrugName().isEmpty() ? item.getDrugName() : item.getCommonName()) == null || source.isEmpty()) continue;
            int sep = source.indexOf(" - ");
            item.setDisplayName(sep > -1 ? source.substring(0, sep).trim() : source);
        }
    }
}
