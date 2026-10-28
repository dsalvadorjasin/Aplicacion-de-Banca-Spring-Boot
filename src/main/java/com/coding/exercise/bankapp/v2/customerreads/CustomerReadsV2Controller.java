package com.coding.exercise.bankapp.v2.customerreads;

import java.util.List;

import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coding.exercise.bankapp.domain.CustomerDetails;
import com.coding.exercise.bankapp.service.BankingServiceImpl;
import com.coding.exercise.bankapp.v2.ApiV2;
import com.coding.exercise.bankapp.v2.ConflictException;
import com.coding.exercise.bankapp.v2.NotFoundException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(ApiV2.BASE_PATH + "/customers")
@Tag(name = "Customer reads (V2)")
public class CustomerReadsV2Controller {

	private final BankingServiceImpl bankingService;

	public CustomerReadsV2Controller(BankingServiceImpl bankingService) {
		this.bankingService = bankingService;
	}

	@GetMapping("/all")
	@Operation(summary = "Find all customers", description = "Gets details of all the customers")
	public List<CustomerDetails> getAllCustomers() {
		return bankingService.findAll();
	}

	@GetMapping("/{customerNumber}")
	@Operation(summary = "Get customer details", description = "Get Customer details by customer number.")
	public CustomerDetails getCustomer(@PathVariable Long customerNumber) {
		CustomerDetails customer;
		try {
			customer = bankingService.findByCustomerNumber(customerNumber);
		} catch (IncorrectResultSizeDataAccessException exception) {
			throw new ConflictException(
					"Customer number " + customerNumber + " is shared by more than one customer");
		}
		if (customer == null) {
			throw new NotFoundException("Customer " + customerNumber + " not found");
		}
		return customer;
	}
}
