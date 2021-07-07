package org.rascalmpl.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.concurrent.TimeUnit;


import io.usethesource.vallang.IDateTime;
import io.usethesource.vallang.IValueFactory;

public class DateTimeConversions {

    public static Temporal dateTimeToJava(IDateTime dt) {
        LocalDate datePart = null;
        if (!dt.isTime()) {
            datePart = LocalDate.of(dt.getYear(), dt.getMonthOfYear(), dt.getDayOfMonth());
        }

        OffsetTime timePart = null;
        if (!dt.isDate()) {
            // vallang always has timezone offset in case it's not a date
            timePart = OffsetTime.of(dt.getHourOfDay(), dt.getMinuteOfHour(), dt.getSecondOfMinute(),
                (int) TimeUnit.MILLISECONDS.toNanos(dt.getMillisecondsOfSecond()),
                ZoneOffset.ofHoursMinutes(dt.getTimezoneOffsetHours(), dt.getTimezoneOffsetMinutes()));
        }
        if (datePart == null && timePart != null) {
            return timePart;
        }
        if (timePart == null && datePart != null) {
            return datePart;
        }
        assert timePart != null && datePart != null;
        return datePart.atTime(timePart);
    }

    public static IDateTime temporalToIValue(IValueFactory values, TemporalAccessor t) {
        boolean hasDate = t.isSupported(ChronoField.YEAR);
        boolean hasTime = t.isSupported(ChronoField.HOUR_OF_DAY);
        boolean hasZoneOffset = t.isSupported(ChronoField.OFFSET_SECONDS);
        if (hasDate) {
            if (hasTime) {
                if (hasZoneOffset) {
                    return temporalToDateTime(values, OffsetDateTime.from(t));
                }
                return temporalToDateTime(values, LocalDateTime.from(t));
            }
            return temporalToDate(values, LocalDate.from(t));
        }
        if (hasZoneOffset) {
            return temporalToTime(values, OffsetTime.from(t));
        }
        return temporalToTime(values, LocalTime.from(t));
    }

	public static IDateTime temporalToDate(IValueFactory values, LocalDate t) {
		return values.date(t.getYear(), t.getMonthValue(), t.getDayOfMonth());
	}

	public static IDateTime temporalToTime(IValueFactory values, LocalTime t) {
		return values.time(t.getHour(), t.getMinute(), t.getSecond(), t.get(ChronoField.MILLI_OF_SECOND));
	}

    public static IDateTime temporalToDateTime(IValueFactory values, LocalDateTime t) {
    	return values.datetime(t.getYear(), t.getMonthValue(), t.getDayOfMonth(), t.getHour(), t.getMinute(),
    		t.getSecond(), t.get(ChronoField.MILLI_OF_SECOND)
    	);
    }

    public static IDateTime temporalToDateTime(IValueFactory values, OffsetDateTime t) {
    	return values.datetime(t.getYear(), t.getMonthValue(), t.getDayOfMonth(), t.getHour(), t.getMinute(),
    		t.getSecond(), t.get(ChronoField.MILLI_OF_SECOND),
    		(int) TimeUnit.HOURS.convert(t.getOffset().getTotalSeconds(), TimeUnit.SECONDS),
    		(int) (TimeUnit.MINUTES.convert(t.getOffset().getTotalSeconds(), TimeUnit.SECONDS) % 60));
    }

	public static IDateTime temporalToTime(IValueFactory values, OffsetTime t) {
		return values.time(t.getHour(), t.getMinute(), t.getSecond(), t.get(ChronoField.MILLI_OF_SECOND),
			(int) TimeUnit.HOURS.convert(t.getOffset().getTotalSeconds(), TimeUnit.SECONDS),
			(int) (TimeUnit.MINUTES.convert(t.getOffset().getTotalSeconds(), TimeUnit.SECONDS) % 60));
	}

}
