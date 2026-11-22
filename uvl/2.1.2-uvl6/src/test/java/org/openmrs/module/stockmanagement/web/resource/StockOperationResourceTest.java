package org.openmrs.module.stockmanagement.web.resource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Location;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.UserContext;
import org.openmrs.api.context.UsernamePasswordAuthenticationScheme;
import org.openmrs.module.stockmanagement.api.StockManagementService;
import org.openmrs.module.stockmanagement.api.dto.PrivilegeScope;
import org.openmrs.module.stockmanagement.api.dto.Result;
import org.openmrs.module.stockmanagement.api.dto.StockItemSummaryDTO;
import org.openmrs.module.stockmanagement.api.dto.StockOperationDTO;
import org.openmrs.module.stockmanagement.api.dto.StockOperationItemDTO;
import org.openmrs.module.stockmanagement.api.dto.StockOperationLinkDTO;
import org.openmrs.module.stockmanagement.api.model.Party;
import org.openmrs.module.stockmanagement.api.model.StockOperationStatus;
import org.openmrs.module.stockmanagement.api.model.StockOperationType;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
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

	private static final Integer STOCK_ITEM_ID = 1;

	private static final String RECEIVE_ITEMS = "Task: stockmanagement.stockoperations.receiveitems";

	private static final String MUTATE = "Task: stockmanagement.stockoperations.mutate";

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
		when(service.findStockItemPackagingUOMs(any())).thenReturn(new Result<>(new ArrayList<>(), 0));
		when(service.findStockItemLossesAndAdjustments(any())).thenReturn(new Result<>(new ArrayList<>(), 0));
		receiverScopes();
		links();

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

	// 2.1.2 approves a requisition to AUTHORIZED but offered "Issue Stock" only on a COMPLETED one
	// (UVL-EMR#641).
	@Test
	public void getPermission_shouldLetTheIssuingPharmacistIssueAnApprovedRequisition() {
		receiverScopes(MUTATE);

		assertEquals(true, resource.getPermission(requisition(StockOperationStatus.AUTHORIZED)).get("isRequisitionAndCanIssueStock"));
		assertEquals(true, resource.getPermission(requisition(StockOperationStatus.COMPLETED)).get("isRequisitionAndCanIssueStock"));
		assertEquals(false, resource.getPermission(requisition(StockOperationStatus.CANCELLED)).get("isRequisitionAndCanIssueStock"));
		assertEquals(false, resource.getPermission(requisition(StockOperationStatus.REJECTED)).get("isRequisitionAndCanIssueStock"));
	}

	@Test
	public void getPermission_shouldNotOfferToIssueAnApprovedRequisitionWithoutScopeAtItsStore() {
		receiverScopes(RECEIVE_ITEMS);

		assertEquals(false, resource.getPermission(requisition(StockOperationStatus.AUTHORIZED)).get("isRequisitionAndCanIssueStock"));
	}

	@Test
	public void getPermission_shouldNotOfferASecondIssueWhileTheFirstIsGoing() {
		receiverScopes(MUTATE);

		links(issueOf(OPERATION_UUID, StockOperationStatus.DISPATCHED, false));
		assertEquals(false, resource.getPermission(requisition(StockOperationStatus.AUTHORIZED)).get("isRequisitionAndCanIssueStock"));

		links(issueOf(OPERATION_UUID, StockOperationStatus.COMPLETED, false));
		assertEquals(false, resource.getPermission(requisition(StockOperationStatus.COMPLETED)).get("isRequisitionAndCanIssueStock"));
	}

	@Test
	public void getPermission_shouldOfferToIssueAgainOnceTheIssueIsCancelledRejectedOrVoided() {
		receiverScopes(MUTATE);

		links(issueOf(OPERATION_UUID, StockOperationStatus.CANCELLED, false),
		    issueOf(OPERATION_UUID, StockOperationStatus.REJECTED, false),
		    issueOf(OPERATION_UUID, StockOperationStatus.NEW, true),
		    issueOf("another-requisition-uuid", StockOperationStatus.NEW, false));
		assertEquals(true, resource.getPermission(requisition(StockOperationStatus.AUTHORIZED)).get("isRequisitionAndCanIssueStock"));
	}

	// 2.1.2 replaced each item's quantityReceived with what the stock item had received in the
	// 30 days to the operation date: 1144 for an issue of 6 on UAT (UVL-EMR#638).
	@Test
	public void getStockOperationItems_shouldKeepTheQuantityReceivedOfTheOperationItem() {
		operationItems(item(new BigDecimal("6.00")));
		StockItemSummaryDTO summary = new StockItemSummaryDTO();
		summary.setStockItemId(STOCK_ITEM_ID);
		summary.setReceived(new BigDecimal("1144.00"));
		summary.setDispensed(new BigDecimal("12.00"));
		summary.setBeginningBalance(new BigDecimal("40.00"));
		summary.setCurrentBalance(new BigDecimal("1172.00"));
		when(service.getAggregatedStockItemSummaries(anyCollection(), any(), any(), any()))
		        .thenReturn(Collections.singletonMap(STOCK_ITEM_ID, summary));

		StockOperationItemDTO item = resource.getStockOperationItems(stockIssue(StockOperationStatus.COMPLETED)).iterator().next();

		assertEquals(new BigDecimal("6.00"), item.getQuantityReceived());
		assertEquals(new BigDecimal("40.00"), item.getBeginningBalance());
		assertEquals(new BigDecimal("1172.00"), item.getStockInHand());
	}

	@Test
	public void getStockOperationItems_shouldLeaveTheQuantityReceivedOfAnItemWithoutStockSummary() {
		operationItems(item(new BigDecimal("7.00")), item(null));

		Collection<StockOperationItemDTO> items = resource.getStockOperationItems(stockIssue(StockOperationStatus.COMPLETED));

		List<BigDecimal> received = new ArrayList<>();
		for (StockOperationItemDTO item : items) {
			received.add(item.getQuantityReceived());
		}
		assertEquals(Arrays.asList(new BigDecimal("7.00"), null), received);
	}

	private void operationItems(StockOperationItemDTO... items) {
		List<StockOperationItemDTO> data = new ArrayList<>(Arrays.asList(items));
		when(service.findStockOperationItems(any())).thenReturn(new Result<>(data, data.size()));
	}

	private static StockOperationItemDTO item(BigDecimal quantityReceived) {
		StockOperationItemDTO item = new StockOperationItemDTO();
		item.setStockOperationUuid(OPERATION_UUID);
		item.setStockItemId(STOCK_ITEM_ID);
		item.setStockItemUuid("stock-item-uuid");
		item.setQuantity(quantityReceived);
		item.setQuantityReceived(quantityReceived);
		return item;
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

	private void links(StockOperationLinkDTO... links) {
		List<StockOperationLinkDTO> data = new ArrayList<>(Arrays.asList(links));
		when(service.findStockOperationLinks(any())).thenReturn(new Result<>(data, data.size()));
	}

	private static StockOperationLinkDTO issueOf(String requisitionUuid, StockOperationStatus status, boolean voided) {
		StockOperationLinkDTO link = new StockOperationLinkDTO();
		link.setParentUuid(requisitionUuid);
		link.setChildUuid("issue-uuid");
		link.setChildStatus(status);
		link.setChildVoided(voided);
		return link;
	}

	// The store a requisition asks from is its destination.
	private static StockOperationDTO requisition(StockOperationStatus status) {
		StockOperationDTO operation = stockIssue(status);
		operation.setOperationType(StockOperationType.REQUISITION);
		return operation;
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
