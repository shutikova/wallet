package com.wultra.wallet.service.cnb;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CzechHolidayCalendarTest {

	private static final DateTimeFormatter YEAR_FILE_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

	private final CzechHolidayCalendar calendar = new CzechHolidayCalendar();

	// CNB's list without the skipped Easter holidays:
	// https://www.cnb.cz/en/public/media-service/schedules-and-other-info/bank-holidays-in-the-czech-republic/
	@ParameterizedTest
	@ValueSource(strings = { "2026-01-01", "2026-05-01", "2026-05-08", "2026-07-05", "2026-07-06", "2026-09-28",
			"2026-10-28", "2026-11-17", "2026-12-24", "2026-12-25", "2026-12-26", "2027-01-01", "2027-05-01", "2027-05-08",
			"2027-07-05", "2027-07-06", "2027-09-28", "2027-10-28", "2027-11-17", "2027-12-24", "2027-12-25", "2027-12-26" })
	void cnbsBankHolidaysAreNotWorkingDays(LocalDate holiday) {
		assertThat(this.calendar.isWorkingDay(holiday)).isFalse();
	}

	// CNB publishes no table on Good Friday and Easter Monday, which are skipped (see README known limitations).
	@ParameterizedTest
	@CsvSource({ "year-2025.txt,2025-04-18,2025-04-21", "year-2026.txt,2026-04-03,2026-04-06" })
	void workingDaysAreTheDaysCnbPublishedATablePlusTheSkippedEasterHolidays(String file, LocalDate goodFriday,
			LocalDate easterMonday) {
		List<LocalDate> published = CnbFiles.read(file).lines()
			.filter(line -> Character.isDigit(line.charAt(0)))
			.map(line -> LocalDate.parse(line.substring(0, 10), YEAR_FILE_DATE))
			.toList();
		LocalDate firstOfYear = published.getFirst().withDayOfYear(1);
		LocalDate lastPublished = published.getLast();

		List<LocalDate> workingDays = firstOfYear.datesUntil(lastPublished.plusDays(1))
			.filter(this.calendar::isWorkingDay)
			.toList();

		assertThat(workingDays).contains(goodFriday, easterMonday);
		assertThat(workingDays).filteredOn(day -> !day.equals(goodFriday) && !day.equals(easterMonday))
			.containsExactlyElementsOf(published);
	}

	@ParameterizedTest
	@ValueSource(strings = { "2026-12-28", "2027-05-03", "2027-05-10" })
	void aHolidayOnAWeekendDoesNotMoveToMonday(LocalDate monday) {
		assertThat(this.calendar.isWorkingDay(monday)).isTrue();
	}

	@ParameterizedTest
	@CsvSource({ "2026-11-02,2026-11-02", "2026-11-01,2026-10-30", "2026-10-31,2026-10-30", "2026-10-28,2026-10-27",
			"2026-12-27,2026-12-23" })
	void findsTheLastWorkingDay(LocalDate date, LocalDate expected) {
		assertThat(this.calendar.lastWorkingDayOnOrBefore(date)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({ "2026-11-02T13:29:59,2026-10-30", "2026-11-02T13:30:00,2026-11-02", "2026-11-02T23:59:00,2026-11-02",
			"2026-10-31T13:30:00,2026-10-30", "2026-11-01T15:00:00,2026-10-30", "2026-10-28T14:00:00,2026-10-27",
			"2026-10-29T09:00:00,2026-10-27", "2026-12-28T09:00:00,2026-12-23" })
	void findsTheNewestTableThatCanExist(LocalDateTime pragueNow, LocalDate expected) {
		assertThat(this.calendar.newestPossibleTable(pragueNow)).isEqualTo(expected);
	}

	@Test
	void businessTimeZoneIsPrague() {
		assertThat(CzechHolidayCalendar.PRAGUE.getId()).isEqualTo("Europe/Prague");
	}

}
