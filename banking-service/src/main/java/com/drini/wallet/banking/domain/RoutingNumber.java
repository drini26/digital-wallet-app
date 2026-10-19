package com.drini.wallet.banking.domain;

/**
 * Checks a US bank routing number (ABA): 9 digits whose weighted sum 3*(d1+d4+d7) + 7*(d2+d5+d8) + (d3+d6+d9) is a
 * multiple of 10.
 */
public final class RoutingNumber {

    private static final int[] WEIGHTS = {3, 7, 1, 3, 7, 1, 3, 7, 1};

    private RoutingNumber() {
    }

    public static boolean isValid(String routingNumber) {
        if (routingNumber == null || routingNumber.length() != 9) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            char digit = routingNumber.charAt(i);
            if (digit < '0' || digit > '9') {
                return false;
            }
            sum = sum + WEIGHTS[i] * (digit - '0');
        }
        return sum % 10 == 0;
    }
}
