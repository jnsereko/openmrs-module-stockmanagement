package org.openmrs.module.stockmanagement.api.impl;

import java.util.Collections;
import java.util.HashSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.UserContext;
import org.openmrs.api.context.UsernamePasswordAuthenticationScheme;
import org.openmrs.module.stockmanagement.api.model.StockOperation;
import org.openmrs.module.stockmanagement.api.model.StockOperationLink;
import org.openmrs.module.stockmanagement.api.model.StockOperationStatus;
import org.openmrs.module.stockmanagement.api.model.StockOperationType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * 2.1.2 approves a requisition to AUTHORIZED and nothing moved it further, so it stayed
 * « Autorisé » after its stock was received (UVL-EMR#641).
 */
public class StockIssueOperationTypeProcessorTest {

	private StockIssueOperationTypeProcessor processor;

	@Before
	public void setUp() {
		processor = new StockIssueOperationTypeProcessor(operationType(StockOperationType.STOCK_ISSUE));
		Context.setUserContext(new UserContext(new UsernamePasswordAuthenticationScheme()));
	}

	@After
	public void tearDown() {
		Context.clearUserContext();
	}

	@Test
	public void completeRequisitions_shouldCompleteTheApprovedRequisitionOfACompletedIssue() {
		StockOperation requisition = operation(StockOperationType.REQUISITION, StockOperationStatus.AUTHORIZED);
		StockOperation issue = issueOf(requisition, StockOperationStatus.COMPLETED);

		processor.completeRequisitions(issue);

		assertEquals(StockOperationStatus.COMPLETED, requisition.getStatus());
		assertNotNull(requisition.getDateChanged());
	}

	@Test
	public void completeRequisitions_shouldLeaveTheRequisitionOfAnIssueThatIsNotCompleted() {
		StockOperation requisition = operation(StockOperationType.REQUISITION, StockOperationStatus.AUTHORIZED);

		processor.completeRequisitions(issueOf(requisition, StockOperationStatus.AUTHORIZED));
		processor.completeRequisitions(issueOf(requisition, StockOperationStatus.DISPATCHED));

		assertEquals(StockOperationStatus.AUTHORIZED, requisition.getStatus());
		assertNull(requisition.getDateChanged());
	}

	@Test
	public void completeRequisitions_shouldLeaveAParentThatIsNotAnApprovedRequisition() {
		StockOperation submitted = operation(StockOperationType.REQUISITION, StockOperationStatus.SUBMITTED);
		StockOperation cancelled = operation(StockOperationType.REQUISITION, StockOperationStatus.CANCELLED);
		StockOperation transfer = operation(StockOperationType.TRANSFER_OUT, StockOperationStatus.AUTHORIZED);
		StockOperation voided = operation(StockOperationType.REQUISITION, StockOperationStatus.AUTHORIZED);
		voided.setVoided(true);

		processor.completeRequisitions(issueOf(submitted, StockOperationStatus.COMPLETED));
		processor.completeRequisitions(issueOf(cancelled, StockOperationStatus.COMPLETED));
		processor.completeRequisitions(issueOf(transfer, StockOperationStatus.COMPLETED));
		processor.completeRequisitions(issueOf(voided, StockOperationStatus.COMPLETED));

		assertEquals(StockOperationStatus.SUBMITTED, submitted.getStatus());
		assertEquals(StockOperationStatus.CANCELLED, cancelled.getStatus());
		assertEquals(StockOperationStatus.AUTHORIZED, transfer.getStatus());
		assertEquals(StockOperationStatus.AUTHORIZED, voided.getStatus());
	}

	@Test
	public void completeRequisitions_shouldDoNothingForAnIssueWithoutRequisition() {
		StockOperation issue = operation(StockOperationType.STOCK_ISSUE, StockOperationStatus.COMPLETED);

		processor.completeRequisitions(issue);
		issue.setParentStockOperationLinks(new HashSet<>());
		processor.completeRequisitions(issue);

		assertEquals(StockOperationStatus.COMPLETED, issue.getStatus());
	}

	private static StockOperation issueOf(StockOperation requisition, StockOperationStatus status) {
		StockOperation issue = operation(StockOperationType.STOCK_ISSUE, status);
		StockOperationLink link = new StockOperationLink();
		link.setParent(requisition);
		link.setChild(issue);
		issue.setParentStockOperationLinks(new HashSet<>(Collections.singletonList(link)));
		return issue;
	}

	private static StockOperation operation(String type, StockOperationStatus status) {
		StockOperation operation = new StockOperation();
		operation.setStockOperationType(operationType(type));
		operation.setStatus(status);
		operation.setVoided(false);
		return operation;
	}

	private static StockOperationType operationType(String type) {
		StockOperationType operationType = new StockOperationType();
		operationType.setOperationType(type);
		return operationType;
	}
}
