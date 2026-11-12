/*
 * StockOperationActionResource from the published stockmanagement-omod 2.1.2, decompiled with
 * CFR 0.152 because its source is not public. The decompiled text is unedited; the compiled
 * class keeps the 2.1.2 methods, signatures and annotations.
 */
package org.openmrs.module.stockmanagement.web.resource;

import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.StringProperty;
import org.openmrs.module.stockmanagement.api.StockManagementException;
import org.openmrs.module.stockmanagement.api.dto.Result;
import org.openmrs.module.stockmanagement.api.dto.StockOperationAction;
import org.openmrs.module.stockmanagement.api.dto.StockOperationDTO;
import org.openmrs.module.stockmanagement.api.dto.StockOperationSearchFilter;
import org.openmrs.module.stockmanagement.web.resource.ResourceBase;
import org.openmrs.module.webservices.rest.web.RequestContext;
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

@Resource(name="v1/stockmanagement/stockoperationaction", supportedClass=StockOperationAction.class, supportedOpenmrsVersions={"1.9.*", "1.10.*", "1.11.*", "1.12.*", "2.*"})
public class StockOperationActionResource
extends ResourceBase<StockOperationAction> {
    protected PageableResult doGetAll(RequestContext context) throws ResponseException {
        throw new ResourceDoesNotSupportOperationException();
    }

    public StockOperationAction getByUniqueId(String uniqueId) {
        throw new ResourceDoesNotSupportOperationException();
    }

    protected void delete(StockOperationAction delegate, String reason, RequestContext context) throws ResponseException {
        throw new ResourceDoesNotSupportOperationException();
    }

    public StockOperationAction newDelegate() {
        return new StockOperationAction();
    }

    public StockOperationAction save(StockOperationAction delegate) {
        StockOperationDTO stockOperation = null;
        try {
            StockOperationSearchFilter filter = new StockOperationSearchFilter();
            filter.setStockOperationUuid(delegate.getUuid());
            Result result = this.getStockManagementService().findStockOperations(filter);
            if (result.getData().isEmpty()) {
                throw new RestClientException("Stock operation " + delegate.getUuid() + " not found");
            }
            stockOperation = (StockOperationDTO)result.getData().get(0);
            switch (delegate.getName()) {
                case COMPLETE: {
                    this.getStockManagementService().completeStockOperation(stockOperation);
                    break;
                }
                case SUBMIT: {
                    this.getStockManagementService().submitStockOperation(stockOperation);
                    break;
                }
                case APPROVE: {
                    this.getStockManagementService().approveStockOperation(stockOperation);
                    break;
                }
                case RETURN: {
                    this.getStockManagementService().returnStockOperation(stockOperation, delegate.getReason());
                    break;
                }
                case REJECT: {
                    this.getStockManagementService().rejectStockOperation(stockOperation, delegate.getReason());
                    break;
                }
                case CANCEL: {
                    this.getStockManagementService().cancelStockOperation(stockOperation, delegate.getReason());
                    break;
                }
                case DISPATCH: {
                    this.getStockManagementService().dispatchStockOperation(stockOperation);
                    break;
                }
                default: {
                    throw new ResourceDoesNotSupportOperationException();
                }
            }
        }
        catch (StockManagementException exception) {
            throw new RestClientException(exception.getMessage());
        }
        return delegate;
    }

    public void purge(StockOperationAction delegate, RequestContext context) throws ResponseException {
        throw new ResourceDoesNotSupportOperationException();
    }

    public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
        DelegatingResourceDescription description = new DelegatingResourceDescription();
        if (rep instanceof DefaultRepresentation || rep instanceof FullRepresentation) {
            description.addProperty("reason");
            description.addProperty("name");
            description.addProperty("uuid");
            description.addProperty("lineItems");
        }
        if (rep instanceof DefaultRepresentation) {
            // empty if block
        }
        if (rep instanceof FullRepresentation) {
            // empty if block
        }
        if (rep instanceof RefRepresentation) {
            // empty if block
        }
        return description;
    }

    public Model getGETModel(Representation rep) {
        ModelImpl modelImpl = (ModelImpl)super.getGETModel(rep);
        if (rep instanceof DefaultRepresentation || rep instanceof FullRepresentation) {
            modelImpl.property("reason", (Property)new StringProperty()).property("name", (Property)new StringProperty()).property("uuid", (Property)new StringProperty()).property("lineItems", (Property)new ArrayProperty());
        }
        if (rep instanceof DefaultRepresentation) {
            // empty if block
        }
        if (rep instanceof FullRepresentation) {
            // empty if block
        }
        if (rep instanceof RefRepresentation) {
            // empty if block
        }
        return modelImpl;
    }

    public DelegatingResourceDescription getCreatableProperties() throws ResourceDoesNotSupportOperationException {
        DelegatingResourceDescription description = new DelegatingResourceDescription();
        description.addProperty("reason");
        description.addProperty("name");
        description.addProperty("uuid");
        description.addProperty("lineItems");
        return description;
    }

    public DelegatingResourceDescription getUpdatableProperties() {
        throw new ResourceDoesNotSupportOperationException();
    }
}
