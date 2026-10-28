package com.coding.exercise.bankapp.v2.customerwrites;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.coding.exercise.bankapp.domain.CustomerDetails;
import com.coding.exercise.bankapp.v2.ApiV2;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(ApiV2.BASE_PATH + "/customers")
@Tag(name = "Customers V2")
public class CustomerWritesV2Controller {

	private final CustomerWritesAdapter customerWritesAdapter;

	public CustomerWritesV2Controller(CustomerWritesAdapter customerWritesAdapter) {
		this.customerWritesAdapter = customerWritesAdapter;
	}

	@PostMapping("/add")
	@Operation(summary = "Add a Customer", description = "Add customer and create an account")
	public ResponseEntity<CustomerDetails> addCustomer(@RequestBody CustomerDetails customer) {
		CustomerDetails created = customerWritesAdapter.create(customer);
		URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
				.path(ApiV2.BASE_PATH + "/customers/{customerNumber}")
				.buildAndExpand(created.getCustomerNumber())
				.toUri();
		return ResponseEntity.created(location).body(created);
	}

	@PutMapping("/{customerNumber}")
	@Operation(summary = "Update customer",
			description = "Update customer and any other account information associated with him.")
	public ResponseEntity<CustomerDetails> updateCustomer(@PathVariable Long customerNumber,
			@RequestBody CustomerDetails customer) {
		return ResponseEntity.ok(customerWritesAdapter.update(customerNumber, customer));
	}

	@DeleteMapping("/{customerNumber}")
	@Operation(summary = "Delete customer and related accounts",
			description = "Delete customer and all accounts associated with him.")
	public ResponseEntity<Void> deleteCustomer(@PathVariable Long customerNumber) {
		customerWritesAdapter.delete(customerNumber);
		return ResponseEntity.noContent().build();
	}
}
