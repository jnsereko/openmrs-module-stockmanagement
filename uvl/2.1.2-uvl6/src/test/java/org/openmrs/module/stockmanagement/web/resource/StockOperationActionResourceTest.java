package org.openmrs.module.stockmanagement.web.resource;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.module.stockmanagement.api.StockManagementException;
import org.openmrs.module.stockmanagement.api.StockManagementService;
import org.openmrs.module.stockmanagement.api.dto.Result;
import org.openmrs.module.stockmanagement.api.dto.StockOperationAction;
import org.openmrs.module.stockmanagement.api.dto.StockOperationDTO;
import org.springframework.web.client.RestClientException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The service refuses an action outside the user's role scopes; the API answers that with a 403.
 */
public class StockOperationActionResourceTest {

	private static final String NO_PERMISSION = "You do not have permission to create nor update the stock operation type for the given location";

	private StockManagementService service;

	private StockOperationActionResource resource;

	@Before
	public void setUp() {
		service = mock(StockManagementService.class);
		StockOperationDTO operation = new StockOperationDTO();
		operation.setUuid("operation-uuid");
		when(service.findStockOperations(any())).thenReturn(new Result<StockOperationDTO>(Collections.singletonList(operation), 1L));

		resource = new StockOperationActionResource() {

			@Override
			protected StockManagementService getStockManagementService() {
				return service;
			}

			@Override
			protected String getNoPermissionMessage() {
				return NO_PERMISSION;
			}
		};
	}

	@Test(expected = APIAuthenticationException.class)
	public void save_shouldAnswerAScopeRefusalWithForbidden() {
		doThrow(new StockManagementException(NO_PERMISSION)).when(service).approveStockOperation(any());

		resource.save(action(StockOperationAction.Action.APPROVE));
	}

	@Test(expected = RestClientException.class)
	public void save_shouldAnswerOtherRefusalsAsBefore() {
		doThrow(new StockManagementException("Approval action not allowed")).when(service).approveStockOperation(any());

		resource.save(action(StockOperationAction.Action.APPROVE));
	}

	private static StockOperationAction action(StockOperationAction.Action name) {
		StockOperationAction action = new StockOperationAction();
		action.setUuid("operation-uuid");
		action.setName(name);
		return action;
	}
}
