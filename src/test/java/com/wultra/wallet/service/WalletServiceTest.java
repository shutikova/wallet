package com.wultra.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;

import com.wultra.wallet.model.Balance;
import com.wultra.wallet.model.error.InsufficientFundsException;
import com.wultra.wallet.model.error.InvalidAmountException;
import com.wultra.wallet.model.error.InvalidCurrencyException;
import com.wultra.wallet.model.error.UnsupportedCurrencyException;
import com.wultra.wallet.repository.InMemoryWalletStorage;
import com.wultra.wallet.service.money.CurrencyConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class WalletServiceTest {

	// Deposits, withdrawals and balances never need CNB, so any call to it fails the test.
	private final WalletService service = new WalletService(new InMemoryWalletStorage(), new CurrencyConverter(), () -> {
		throw new AssertionError("CNB should not be needed");
	});

	@Test
	void moneyMovementsAreLogged(CapturedOutput output) {
		this.service.deposit("10", "USD");
		this.service.withdraw("3", "USD");

		assertThat(output).contains("Deposited 10.00 USD, new balance 10.00", "Withdrew 3.00 USD, new balance 7.00");
	}

	@Test
	void aRejectedWithdrawalIsNotLoggedAsAMovement(CapturedOutput output) {
		assertThatThrownBy(() -> this.service.withdraw("3", "USD")).isInstanceOf(InsufficientFundsException.class);

		assertThat(output).doesNotContain("Withdrew");
	}

	@Test
	void depositReturnsTheNewBalance() {
		assertThat(this.service.deposit("10", "USD")).isEqualTo(balance("USD", "10.00"));
		assertThat(this.service.deposit("2.50", "USD")).isEqualTo(balance("USD", "12.50"));
	}

	@Test
	void withdrawalReturnsTheNewBalance() {
		this.service.deposit("10", "USD");

		assertThat(this.service.withdraw("3", "USD")).isEqualTo(balance("USD", "7.00"));
	}

	@Test
	void currencyCodesAreAcceptedInAnyLetterCase() {
		assertThat(this.service.deposit("10", "usd")).isEqualTo(balance("USD", "10.00"));
		assertThat(this.service.withdraw("3", "uSd")).isEqualTo(balance("USD", "7.00"));
	}

	@Test
	void currenciesAreKeptSeparate() {
		this.service.deposit("10", "USD");
		this.service.deposit("5", "EUR");

		assertThat(this.service.withdraw("3", "USD")).isEqualTo(balance("USD", "7.00"));
		assertThat(this.service.balances()).containsExactly(balance("EUR", "5.00"), balance("USD", "7.00"));
	}

	@Test
	void jpyHasNoDecimals() {
		assertThat(this.service.deposit("100", "JPY").amount().toPlainString()).isEqualTo("100");
	}

	@Test
	void balancesAreEmptyAtTheStart() {
		assertThat(this.service.balances()).isEmpty();
	}

	@Test
	void withdrawingEverythingIsAllowedAndRemovesTheBalance() {
		this.service.deposit("10", "USD");

		assertThat(this.service.withdraw("10.00", "USD")).isEqualTo(balance("USD", "0.00"));
		assertThat(this.service.balances()).isEmpty();
	}

	@Test
	void withdrawalLargerThanTheBalanceIsRejectedAndChangesNothing() {
		this.service.deposit("10", "USD");

		assertThatThrownBy(() -> this.service.withdraw("10.01", "USD")).isInstanceOf(InsufficientFundsException.class);
		assertThat(this.service.balances()).containsExactly(balance("USD", "10.00"));
	}

	@Test
	void withdrawalOfASupportedCurrencyNotHeldIsInsufficientFunds() {
		assertThatThrownBy(() -> this.service.withdraw("1", "USD")).isInstanceOf(InsufficientFundsException.class);
	}

	@Test
	void balanceCanGrowBeyondFifteenIntegerDigits() {
		this.service.deposit("999999999999999.99", "USD");

		assertThat(this.service.deposit("999999999999999.99", "USD").amount().toPlainString())
			.isEqualTo("1999999999999999.98");
	}

	@ParameterizedTest
	@ValueSource(strings = { "AED", "XDR", "DEM" })
	void currenciesOutsideTheListAreRejectedForDepositsAndWithdrawals(String code) {
		assertThatThrownBy(() -> this.service.deposit("1", code)).isInstanceOf(UnsupportedCurrencyException.class);
		assertThatThrownBy(() -> this.service.withdraw("1", code)).isInstanceOf(UnsupportedCurrencyException.class);
		assertThat(this.service.balances()).isEmpty();
	}

	@Test
	void invalidCodesAreRejectedForDepositsAndWithdrawals() {
		assertThatThrownBy(() -> this.service.deposit("1", "ABC")).isInstanceOf(InvalidCurrencyException.class);
		assertThatThrownBy(() -> this.service.withdraw("1", "ABC")).isInstanceOf(InvalidCurrencyException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "-1", "1.001", "abc", "1000000000000000" })
	void invalidAmountsAreRejectedAndChangeNothing(String amount) {
		this.service.deposit("10", "USD");

		assertThatThrownBy(() -> this.service.deposit(amount, "USD")).isInstanceOf(InvalidAmountException.class);
		assertThatThrownBy(() -> this.service.withdraw(amount, "USD")).isInstanceOf(InvalidAmountException.class);
		assertThat(this.service.balances()).containsExactly(balance("USD", "10.00"));
	}

	private static Balance balance(String code, String amount) {
		return new Balance(Currency.getInstance(code), new BigDecimal(amount));
	}

}
