/*
 * StockIssueOperationTypeProcessor from the published stockmanagement-api 2.1.2, decompiled with
 * CFR 0.152 because its source is not public. The decompiled text compiles to the 2.1.2
 * bytecode (javap -p -c, the class and its three anonymous classes). The only change since is
 * that a completed Stock Issue completes the approved requisition it was made from.
 */
package org.openmrs.module.stockmanagement.api.impl;

import java.math.BigDecimal;
import java.util.Date;
import org.openmrs.api.context.Context;
import org.openmrs.module.stockmanagement.api.impl.StockOperationTypeProcessorBase;
import org.openmrs.module.stockmanagement.api.model.ReservedTransaction;
import org.openmrs.module.stockmanagement.api.model.StockItemTransaction;
import org.openmrs.module.stockmanagement.api.model.StockOperation;
import org.openmrs.module.stockmanagement.api.model.StockOperationLink;
import org.openmrs.module.stockmanagement.api.model.StockOperationStatus;
import org.openmrs.module.stockmanagement.api.model.StockOperationType;
import org.openmrs.module.stockmanagement.api.utils.Action2;

public class StockIssueOperationTypeProcessor
extends StockOperationTypeProcessorBase {
    public StockIssueOperationTypeProcessor(StockOperationType stockOperationType) {
        super(stockOperationType);
    }

    @Override
    public boolean isNegativeItemQuantityAllowed() {
        return false;
    }

    @Override
    public boolean canBeRelatedToRequisition() {
        return true;
    }

    @Override
    public boolean shouldVerifyNegativeStockAmountsAtSource() {
        return true;
    }

    @Override
    public boolean requiresDispatchAcknowledgement() {
        return true;
    }

    @Override
    public BigDecimal getQuantityToApplyAtSource(BigDecimal quantity) {
        return quantity.multiply(BigDecimal.valueOf(-1L));
    }

    @Override
    public void onPending(final StockOperation operation) {
        this.executeCopyReserved(operation, new Action2<ReservedTransaction, StockItemTransaction>(){

            @Override
            public void apply(ReservedTransaction reserved, StockItemTransaction tx) {
                tx.setParty(operation.getSource());
                tx.setQuantity(StockIssueOperationTypeProcessor.this.getQuantityToApplyAtSource(tx.getQuantity()));
            }
        });
    }

    @Override
    public void onCancelled(final StockOperation operation) {
        this.executeCopyReservedAndClear(operation, new Action2<ReservedTransaction, StockItemTransaction>(){

            @Override
            public void apply(ReservedTransaction reserved, StockItemTransaction tx) {
                tx.setParty(operation.getSource());
            }
        });
    }

    @Override
    public void onCompleted(final StockOperation operation) {
        this.executeCopyReservedAndClear(operation, new Action2<ReservedTransaction, StockItemTransaction>(){

            @Override
            public void apply(ReservedTransaction reserved, StockItemTransaction tx) {
                tx.setQuantity(reserved.getStockOperationItem().getQuantityReceived());
                tx.setStockItemPackagingUOM(reserved.getStockOperationItem().getQuantityReceivedPackagingUOM());
                tx.setParty(operation.getDestination());
            }
        });
        this.completeRequisitions(operation);
    }

    /*
     * Approving a requisition in 2.1.2 leaves it AUTHORIZED and nothing moved it further, so a
     * requisition stayed « Autorisé » after the stock it asked for was received. When the Stock
     * Issue made from it completes, the requisition is completed too. Its completedBy and
     * completedDate are left as they are: 2.1.2 records the approval there and has no other
     * field for it. onCompleted also runs when an operation is approved, so this acts only on
     * an issue that is COMPLETED.
     */
    protected void completeRequisitions(StockOperation operation) {
        if (operation.getStatus() != StockOperationStatus.COMPLETED || operation.getParentStockOperationLinks() == null) {
            return;
        }
        for (StockOperationLink link : operation.getParentStockOperationLinks()) {
            StockOperation requisition = link.getParent();
            if (requisition == null || Boolean.TRUE.equals(requisition.getVoided())
                    || requisition.getStockOperationType() == null
                    || !StockOperationType.REQUISITION.equals(requisition.getStockOperationType().getOperationType())
                    || requisition.getStatus() != StockOperationStatus.AUTHORIZED) {
                continue;
            }
            requisition.setStatus(StockOperationStatus.COMPLETED);
            requisition.setChangedBy(Context.getAuthenticatedUser());
            requisition.setDateChanged(new Date());
        }
    }
}
