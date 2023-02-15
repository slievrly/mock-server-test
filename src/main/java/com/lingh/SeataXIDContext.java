package com.lingh;

import com.alibaba.ttl.TransmittableThreadLocal;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * Seata xid context.
 */
@SuppressWarnings("unused")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class SeataXIDContext {
    
    private static final TransmittableThreadLocal<String> XID = new TransmittableThreadLocal<>();
    
    /**
     * Judge whether xid is empty or not.
     *
     * @return whether xid is empty or not
     */
    public static boolean isEmpty() {
        return null == XID.get();
    }
    
    /**
     * Get xid.
     * 
     * @return xid
     */
    public static String get() {
        return XID.get();
    }
    
    /**
     * Set xid.
     * 
     * @param xid xid
     */
    public static void set(final String xid) {
        XID.set(xid);
    }
    
    /**
     * Remove xid.
     */
    public static void remove() {
        XID.remove();
    }
}
