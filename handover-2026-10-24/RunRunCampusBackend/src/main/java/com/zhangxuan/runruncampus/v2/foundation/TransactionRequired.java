package com.zhangxuan.runruncampus.v2.foundation;

import org.springframework.transaction.support.TransactionSynchronizationManager;

final class TransactionRequired {
    private TransactionRequired() {}
    static void check() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new IllegalStateException("A writable business transaction is required");
    }
}
