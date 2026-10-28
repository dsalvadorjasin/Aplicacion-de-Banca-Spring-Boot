package com.coding.exercise.bankapp.v2.customerwrites;

import java.util.Optional;

import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.coding.exercise.bankapp.domain.CustomerDetails;
import com.coding.exercise.bankapp.service.BankingServiceImpl;
import com.coding.exercise.bankapp.v2.BadRequestException;
import com.coding.exercise.bankapp.v2.ConflictException;
import com.coding.exercise.bankapp.v2.NotFoundException;

@Component
final class CustomerWritesAdapter {

	private final BankingServiceImpl bankingService;

	CustomerWritesAdapter(BankingServiceImpl bankingService) {
		this.bankingService = bankingService;
	}

	CustomerDetails create(CustomerDetails customer) {
		if (customer.getCustomerNumber() == null) {
			throw new BadRequestException("customerNumber is required");
		}
		requireWritable(customer);
		Long customerNumber = customer.getCustomerNumber();
		if (find(customerNumber).isPresent()) {
			throw new ConflictException("Customer " + customerNumber + " already exists");
		}

		expectStatus(bankingService.addCustomer(customer), 201);
		return find(customerNumber)
				.orElseThrow(() -> new IllegalStateException("Created customer " + customerNumber + " was not found"));
	}

	CustomerDetails update(Long customerNumber, CustomerDetails customer) {
		requireWritable(customer);
		require(customerNumber);
		expectStatus(bankingService.updateCustomer(customer, customerNumber), 200);
		return require(customerNumber);
	}

	void delete(Long customerNumber) {
		require(customerNumber);
		expectStatus(bankingService.deleteCustomer(customerNumber), 200);
	}

	private Optional<CustomerDetails> find(Long customerNumber) {
		try {
			return Optional.ofNullable(bankingService.findByCustomerNumber(customerNumber));
		} catch (IncorrectResultSizeDataAccessException exception) {
			throw new ConflictException("More than one customer has customer number " + customerNumber);
		}
	}

	private CustomerDetails require(Long customerNumber) {
		return find(customerNumber)
				.orElseThrow(() -> new NotFoundException("Customer " + customerNumber + " not found"));
	}

	private static void requireWritable(CustomerDetails customer) {
		if (customer.getContactDetails() == null) {
			throw new BadRequestException("contactDetails is required");
		}
		if (customer.getCustomerAddress() == null) {
			throw new BadRequestException("customerAddress is required");
		}
	}

	private static void expectStatus(ResponseEntity<Object> response, int expectedStatus) {
		if (response.getStatusCode().value() != expectedStatus) {
			throw new IllegalStateException(
					"Expected service status " + expectedStatus + " but received " + response.getStatusCode().value());
		}
	}
}
