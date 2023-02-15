package com.lingh;

/**
 * Transaction type.
 */
@SuppressWarnings("unused")
public enum TransactionType {
    
    LOCAL, XA, BASE;
    
    /**
     * Judge whether distributed transaction.
     * 
     * @param transactionType transaction type
     * @return is distributed transaction or not
     */
    public static boolean isDistributedTransaction(final TransactionType transactionType) {
        return XA == transactionType || BASE == transactionType;
    }
}
