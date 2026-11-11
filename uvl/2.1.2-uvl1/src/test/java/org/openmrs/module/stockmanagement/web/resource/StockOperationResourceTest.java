package org.openmrs.module.stockmanagement.web.resource;

import java.util.HashSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Location;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.UserContext;
import org.openmrs.api.context.UsernamePasswordAuthenticationScheme;
import org.openmrs.module.stockmanagement.api.StockManagementService;
import org.openmrs.module.stockmanagement.api.dto.PrivilegeScope;
import org.openmrs.module.stockmanagement.api.dto.StockOperationDTO;
import org.openmrs.module.stockmanagement.api.model.Party;
import org.openmrs.module.stockmanagement.api.model.StockOperationStatus;
import org.openmrs.module.stockmanagement.api.model.StockOperationType;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Every call here runs on the same thread against the same resource instance, which is how
 * Tomcat reuses a worker thread for successive requests. 2.1.2 answered each of them from the
 * first result it had cached for the operation uuid.
 */
public class StockOperationResourceTest {

	private static final String OPERATION_UUID = "operation-uuid";

	private static final String DESTINATION_UUID = "destination-party-uuid";

	private static final String RECEIVE_ITEMS = "Task: stockmanagement.stockoperations.receiveitems";

	private StockManagementService service;

	private StockOperationResource resource;

	@Before
	public void setUp() {
		StockOperationType stockIssue = new StockOperationType();
		stockIssue.setOperationType(StockOperationType.STOCK_ISSUE);
		stockIssue.setAllowBatchInfoUpdate(false);

		Party destination = new Party();
		destination.setLocation(new Location());

		service = mock(StockManagementService.class);
		when(service.getStockOperationTypeByUuid(any())).thenReturn(stockIssue);
		when(service.getPartyByUuid(DESTINATION_UUID)).thenReturn(destination);
		receiverScopes();

		resource = new StockOperationResource() {

			@Override
			protected StockManagementService getStockManagementService() {
				return service;
			}
		};

		// getPermission asks for the authenticated user's scopes; the service is mocked, so
		// a user context without a user is enough.
		Context.setUserContext(new UserContext(new UsernamePasswordAuthenticationScheme()));
	}

	@After
	public void tearDown() {
		Context.clearUserContext();
	}

	@Test
	public void getPermission_shouldLetTheReceiverReceiveItemsOnceTheIssueIsDispatched() {
		receiverScopes(RECEIVE_ITEMS);

		assertEquals(false, resource.getPermission(stockIssue(StockOperationStatus.AUTHORIZED)).get("canReceiveItems"));
		assertEquals(true, resource.getPermission(stockIssue(StockOperationStatus.DISPATCHED)).get("canReceiveItems"));
	}

	@Test
	public void getPermission_shouldReflectAScopeGrantedAfterAnEarlierRequest() {
		StockOperationDTO dispatched = stockIssue(StockOperationStatus.DISPATCHED);

		assertEquals(false, resource.getPermission(dispatched).get("canReceiveItems"));

		receiverScopes(RECEIVE_ITEMS);
		assertEquals(true, resource.getPermission(dispatched).get("canReceiveItems"));
	}

	@Test
	public void getPermission_shouldReflectTheCurrentStatusOfTheOperation() {
		assertEquals(true, resource.getPermission(stockIssue(StockOperationStatus.DISPATCHED)).get("canDisplayReceivedItems"));
		assertEquals(false, resource.getPermission(stockIssue(StockOperationStatus.CANCELLED)).get("canDisplayReceivedItems"));
	}

	private void receiverScopes(String... privileges) {
		HashSet<PrivilegeScope> scopes = new HashSet<>();
		for (String privilege : privileges) {
			PrivilegeScope scope = new PrivilegeScope();
			scope.setPrivilege(privilege);
			scopes.add(scope);
		}
		when(service.getPrivilegeScopes(any(), any(), any(), anyList())).thenReturn(scopes);
	}

	// Locked and not SUBMITTED, so neither editable nor approvable: getPermission looks up
	// scopes only for the destination party, and needs no LocationService.
	private static StockOperationDTO stockIssue(StockOperationStatus status) {
		StockOperationDTO operation = new StockOperationDTO();
		operation.setUuid(OPERATION_UUID);
		operation.setOperationTypeUuid("operation-type-uuid");
		operation.setOperationType(StockOperationType.STOCK_ISSUE);
		operation.setDestinationUuid(DESTINATION_UUID);
		operation.setStatus(status);
		operation.setLocked(true);
		operation.setVoided(false);
		return operation;
	}
}
