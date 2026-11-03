package com.wultra.wallet.service.cnb;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class CzechHolidayCalendar {

	public static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");

	// An hour before the usual publication at about 14:30, to cover an early release (see README decision 3).
	private static final LocalTime FIRST_CHECK = LocalTime.of(13, 30);

	// Good Friday and Easter Monday are deliberately left out (see README known limitations).
	private static final Set<MonthDay> FIXED_HOLIDAYS = Set.of(MonthDay.of(1, 1), MonthDay.of(5, 1), MonthDay.of(5, 8),
			MonthDay.of(7, 5), MonthDay.of(7, 6), MonthDay.of(9, 28), MonthDay.of(10, 28), MonthDay.of(11, 17),
			MonthDay.of(12, 24), MonthDay.of(12, 25), MonthDay.of(12, 26));

	public boolean isWorkingDay(LocalDate date) {
		return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY
				&& !FIXED_HOLIDAYS.contains(MonthDay.from(date));
	}

	public LocalDate lastWorkingDayOnOrBefore(LocalDate date) {
		LocalDate day = date;
		while (!isWorkingDay(day)) {
			day = day.minusDays(1);
		}
		return day;
	}

	public LocalDate newestPossibleTable(LocalDateTime pragueNow) {
		LocalDate today = pragueNow.toLocalDate();
		if (isWorkingDay(today) && !pragueNow.toLocalTime().isBefore(FIRST_CHECK)) {
			return today;
		}
		return lastWorkingDayOnOrBefore(today.minusDays(1));
	}

}
