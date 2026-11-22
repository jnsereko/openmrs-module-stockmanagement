package org.openmrs.module.stockmanagement.web.resource;

import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Location;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.UserContext;
import org.openmrs.api.context.UsernamePasswordAuthenticationScheme;
import org.openmrs.module.stockmanagement.api.StockManagementService;
import org.openmrs.module.stockmanagement.api.dto.Result;
import org.openmrs.module.stockmanagement.api.dto.StockOperationDTO;
import org.openmrs.module.stockmanagement.api.model.Party;
import org.openmrs.module.stockmanagement.api.model.StockOperation;
import org.openmrs.module.stockmanagement.api.model.StockOperationType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Create and update of a stock operation must be within the user's role scopes: the mutate
 * privilege for the operation's type at its location, as every action already requires.
 */
public class StockOperationResourceSaveTest {

	private static final String MUTATE = "Task: stockmanagement.stockoperations.mutate";

	private static final String NO_PERMISSION = "You do not have permission to create nor update the stock operation type for the given location";

	private static final String OPENING_STOCK_UUID = "opening-stock-type-uuid";

	private static final String RECEIPT_UUID = "44444444-4444-4444-4444-444444444444";

	private static final String OPERATION_UUID = "operation-uuid";

	private static final String BOX_PARTY_UUID = "box-party-uuid";

	private static final String CENTRAL_PARTY_UUID = "central-party-uuid";

	private final Location box = new Location(1);

	private final Location central = new Location(2);

	private StockManagementService service;

	private StockOperationType openingStock;

	private StockOperationType receipt;

	private boolean administrator;

	private StockOperationResource resource;

	@Before
	public void setUp() {
		openingStock = new StockOperationType();
		openingStock.setOperationType(StockOperationType.INITIAL);
		receipt = new StockOperationType();
		receipt.setOperationType(StockOperationType.RECEIPT);

		service = mock(StockManagementService.class);
		when(service.getStockOperationTypeByUuid(OPENING_STOCK_UUID)).thenReturn(openingStock);
		when(service.getStockOperationTypeByUuid(RECEIPT_UUID)).thenReturn(receipt);
		when(service.getPartyByUuid(BOX_PARTY_UUID)).thenReturn(party(box));
		when(service.getPartyByUuid(CENTRAL_PARTY_UUID)).thenReturn(party(central));
		// The user's scopes: every operation type at the box only.
		when(service.userHasStockManagementPrivilege(any(), same(box), any(), eq(MUTATE))).thenReturn(true);

		StockOperation saved = new StockOperation();
		saved.setUuid(OPERATION_UUID);
		when(service.saveStockOperation(any())).thenReturn(saved);
		when(service.findStockOperations(any())).thenReturn(new Result<StockOperationDTO>(new ArrayList<StockOperationDTO>(), 0L));

		administrator = false;
		resource = new StockOperationResource() {

			@Override
			protected StockManagementService getStockManagementService() {
				return service;
			}

			@Override
			protected boolean userIsStockAdministrator() {
				return administrator;
			}

			@Override
			protected String getNoPermissionMessage() {
				return NO_PERMISSION;
			}
		};

		Context.setUserContext(new UserContext(new UsernamePasswordAuthenticationScheme()));
	}

	@After
	public void tearDown() {
		Context.clearUserContext();
	}

	@Test
	public void save_shouldCreateAnOperationAtALocationInTheUsersScope() {
		resource.save(newOperation(OPENING_STOCK_UUID, BOX_PARTY_UUID, null));

		verify(service).saveStockOperation(any());
	}

	@Test
	public void save_shouldRefuseToCreateAnOperationAtALocationOutsideTheUsersScope() {
		assertRefused(newOperation(OPENING_STOCK_UUID, CENTRAL_PARTY_UUID, null));
	}

	@Test
	public void save_shouldRefuseAnOperationTypeOutsideTheUsersScope() {
		when(service.userHasStockManagementPrivilege(any(), same(box), same(openingStock), eq(MUTATE))).thenReturn(false);

		assertRefused(newOperation(OPENING_STOCK_UUID, BOX_PARTY_UUID, null));
	}

	@Test
	public void save_shouldCreateAReceiptWithItsDestinationInTheUsersScope() {
		resource.save(newOperation(RECEIPT_UUID, CENTRAL_PARTY_UUID, BOX_PARTY_UUID));

		verify(service).saveStockOperation(any());
	}

	@Test
	public void save_shouldRefuseAReceiptWithItsDestinationOutsideTheUsersScope() {
		assertRefused(newOperation(RECEIPT_UUID, BOX_PARTY_UUID, CENTRAL_PARTY_UUID));
	}

	@Test
	public void save_shouldRefuseToUpdateAnOperationStoredAtALocationOutsideTheUsersScope() {
		storedAt(central);

		assertRefused(existingOperation(BOX_PARTY_UUID));
	}

	@Test
	public void save_shouldRefuseToMoveAnOperationToALocationOutsideTheUsersScope() {
		storedAt(box);

		assertRefused(existingOperation(CENTRAL_PARTY_UUID));
	}

	@Test
	public void save_shouldUpdateAnOperationWithinTheUsersScope() {
		storedAt(box);

		resource.save(existingOperation(BOX_PARTY_UUID));

		verify(service).saveStockOperation(any());
	}

	@Test
	public void save_shouldLetAStockAdministratorCreateAnywhere() {
		administrator = true;

		resource.save(newOperation(OPENING_STOCK_UUID, CENTRAL_PARTY_UUID, null));

		verify(service).saveStockOperation(any());
	}

	private void assertRefused(StockOperationDTO operation) {
		try {
			resource.save(operation);
			fail("expected the save to be refused");
		}
		catch (APIAuthenticationException expected) {
			assertEquals(NO_PERMISSION, expected.getMessage());
		}
		verify(service, never()).saveStockOperation(any());
	}

	private void storedAt(Location location) {
		StockOperation stored = new StockOperation();
		stored.setUuid(OPERATION_UUID);
		stored.setAtLocation(location);
		when(service.getStockOperationByUuid(OPERATION_UUID)).thenReturn(stored);
	}

	private static StockOperationDTO newOperation(String operationTypeUuid, String sourceUuid, String destinationUuid) {
		StockOperationDTO operation = new StockOperationDTO();
		operation.setOperationTypeUuid(operationTypeUuid);
		operation.setSourceUuid(sourceUuid);
		operation.setDestinationUuid(destinationUuid);
		return operation;
	}

	private static StockOperationDTO existingOperation(String sourceUuid) {
		StockOperationDTO operation = newOperation(OPENING_STOCK_UUID, sourceUuid, null);
		operation.setUuid(OPERATION_UUID);
		return operation;
	}

	private static Party party(Location location) {
		Party party = new Party();
		party.setLocation(location);
		return party;
	}
}
