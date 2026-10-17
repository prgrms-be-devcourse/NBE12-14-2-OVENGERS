package com.ovengers.slotkey.stats.entity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * 랭킹 기간 종류. 날짜는 모두 KST 달력 날짜이고, 기간은 [startOf, endOf) 반열린 구간이다.
 * 주는 월요일에 시작한다. 근거: docs/decisions/space-ranking.md §2-4.
 */
public enum RankingPeriodType {
    DAILY {
        @Override
        public LocalDate startOf(LocalDate date) {
            return date;
        }

        @Override
        public LocalDate endOf(LocalDate date) {
            return date.plusDays(1);
        }
    },
    WEEKLY {
        @Override
        public LocalDate startOf(LocalDate date) {
            return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }

        @Override
        public LocalDate endOf(LocalDate date) {
            return startOf(date).plusWeeks(1);
        }
    },
    MONTHLY {
        @Override
        public LocalDate startOf(LocalDate date) {
            return date.withDayOfMonth(1);
        }

        @Override
        public LocalDate endOf(LocalDate date) {
            return startOf(date).plusMonths(1);
        }
    };

    /** date가 속한 기간의 첫 날(포함). */
    public abstract LocalDate startOf(LocalDate date);

    /** date가 속한 기간의 끝(제외) = 다음 기간의 첫 날. */
    public abstract LocalDate endOf(LocalDate date);
}
