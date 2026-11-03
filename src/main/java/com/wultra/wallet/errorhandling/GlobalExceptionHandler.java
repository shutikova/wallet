package com.wultra.wallet.errorhandling;

import java.net.URI;

import com.wultra.wallet.model.error.InsufficientFundsException;
import com.wultra.wallet.model.error.InvalidAmountException;
import com.wultra.wallet.model.error.InvalidCurrencyException;
import com.wultra.wallet.model.error.RateNotAvailableException;
import com.wultra.wallet.model.error.RatesUnavailableException;
import com.wultra.wallet.model.error.UnsupportedCurrencyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// Implements the error table of README decision 6. The base class handles the other HTTP errors, such as 404, 405
// and 415, with the default about:blank type.
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final String INVALID_REQUEST = "invalid-request";

	@ExceptionHandler(InvalidAmountException.class)
	public ProblemDetail handleInvalidAmount(InvalidAmountException ex) {
		return problem(HttpStatus.BAD_REQUEST, INVALID_REQUEST, "Invalid request", ex.getMessage());
	}

	@ExceptionHandler(InvalidCurrencyException.class)
	public ProblemDetail handleInvalidCurrency(InvalidCurrencyException ex) {
		return problem(HttpStatus.BAD_REQUEST, "invalid-currency", "Invalid currency", ex.getMessage());
	}

	@ExceptionHandler(UnsupportedCurrencyException.class)
	public ProblemDetail handleUnsupportedCurrency(UnsupportedCurrencyException ex) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "unsupported-currency", "Unsupported currency", ex.getMessage());
	}

	@ExceptionHandler(InsufficientFundsException.class)
	public ProblemDetail handleInsufficientFunds(InsufficientFundsException ex) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "insufficient-funds", "Insufficient funds", ex.getMessage());
	}

	@ExceptionHandler(RateNotAvailableException.class)
	public ProblemDetail handleRateNotAvailable(RateNotAvailableException ex) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "rate-not-available", "Rate not available", ex.getMessage());
	}

	@ExceptionHandler(RatesUnavailableException.class)
	public ProblemDetail handleRatesUnavailable(RatesUnavailableException ex) {
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "rates-unavailable", "Rates unavailable", ex.getMessage());
	}

	// The cause goes to the log only, so the response shows no internal details.
	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unexpected error", ex);
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		return invalidRequest(ex, "Request body must be JSON with amount and currency as strings", headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		return invalidRequest(ex, "Request body must contain amount and currency", headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return invalidRequest(ex, "Query parameter " + ex.getParameterName() + " is required", headers, status, request);
	}

	private ResponseEntity<Object> invalidRequest(Exception ex, String detail, HttpHeaders headers, HttpStatusCode status,
			WebRequest request) {
		ProblemDetail body = problem(HttpStatus.BAD_REQUEST, INVALID_REQUEST, "Invalid request", detail);
		return handleExceptionInternal(ex, body, headers, status, request);
	}

	private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create("/problems/" + type));
		problem.setTitle(title);
		return problem;
	}

}
