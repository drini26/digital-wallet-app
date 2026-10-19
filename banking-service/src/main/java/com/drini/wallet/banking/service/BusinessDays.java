package com.drini.wallet.banking.service;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** When will a bank transfer arrive? */
public final class BusinessDays {

    private BusinessDays() {
    }

    public static LocalDate estimatedArrival(LocalDate requestedOn) {
        LocalDate date = requestedOn.plusDays(1);
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        return date;
    }
}
