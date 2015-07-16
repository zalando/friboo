package org.zalando.stups.friboo;

import clojure.lang.IFn;

public final class TransactionMarker {
    private TransactionMarker() {
    }

    /**
     * This method can be matched in AppDynamics and used to parse the first parameter for transaction naming
     */
    public static Object run(Object name, IFn fn) {
        return fn.invoke();
    }
}
