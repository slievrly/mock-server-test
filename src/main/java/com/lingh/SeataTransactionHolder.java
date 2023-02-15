package com.lingh;

import io.seata.tm.api.GlobalTransaction;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Seata transaction holder.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SeataTransactionHolder {
    
    private static final ThreadLocal<GlobalTransaction> CONTEXT = new ThreadLocal<>();
    
    /**
     * Set seata global transaction.
     *
     * @param transaction global transaction context
     */
    static void set(final GlobalTransaction transaction) {
        CONTEXT.set(transaction);
    }
    
    /**
     * Get seata global transaction.
     *
     * @return global transaction
     */
    static GlobalTransaction get() {
        return CONTEXT.get();
    }
    
    /**
     * Clear global transaction.
     */
    static void clear() {
        CONTEXT.remove();
    }
}
