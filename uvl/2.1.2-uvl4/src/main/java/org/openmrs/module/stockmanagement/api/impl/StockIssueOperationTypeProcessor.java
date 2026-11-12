/*
 * StockIssueOperationTypeProcessor from the published stockmanagement-api 2.1.2, decompiled with
 * CFR 0.152 because its source is not public. The decompiled text compiles to the 2.1.2
 * bytecode (javap -p -c, the class and its three anonymous classes).
 */
package org.openmrs.module.stockmanagement.api.impl;

import java.math.BigDecimal;
import org.openmrs.module.stockmanagement.api.impl.StockOperationTypeProcessorBase;
import org.openmrs.module.stockmanagement.api.model.ReservedTransaction;
import org.openmrs.module.stockmanagement.api.model.StockItemTransaction;
import org.openmrs.module.stockmanagement.api.model.StockOperation;
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
    }
}
