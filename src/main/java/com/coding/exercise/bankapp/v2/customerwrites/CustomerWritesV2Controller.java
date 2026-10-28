package com.coding.exercise.bankapp.v2.customerwrites;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coding.exercise.bankapp.domain.CustomerDetails;
import com.coding.exercise.bankapp.v2.ApiV2;
import com.coding.exercise.bankapp.v2.ApiV2BadRequestException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(ApiV2.BASE_PATH + "/customers")
@Tag(name = "Customer V2 write endpoints")
public class CustomerWritesV2Controller {

    private final CustomerWritesV2Service customerWrites;

    public CustomerWritesV2Controller(CustomerWritesV2Service customerWrites) {
        this.customerWrites = customerWrites;
    }

    @PostMapping(path = "/add")
    @Operation(summary = "Add a customer", description = "Creates a customer and returns it with its Location.")
    public ResponseEntity<CustomerDetails> addCustomer(@RequestBody CustomerDetails customer) {
        if (customer.getCustomerNumber() == null) {
            throw new ApiV2BadRequestException("customerNumber is required.");
        }
        requireAddressAndContact(customer);
        CustomerDetails created = customerWrites.create(customer);
        return ResponseEntity
                .created(ApiV2.location(ApiV2.BASE_PATH + "/customers/{customerNumber}", created.getCustomerNumber()))
                .body(created);
    }

    @PutMapping(path = "/{customerNumber}")
    @Operation(summary = "Update a customer", description = "Updates a customer and returns the updated customer.")
    public ResponseEntity<CustomerDetails> updateCustomer(@RequestBody CustomerDetails customer,
            @PathVariable Long customerNumber) {
        requireAddressAndContact(customer);
        return ResponseEntity.ok(customerWrites.update(customerNumber, customer));
    }

    @DeleteMapping(path = "/{customerNumber}")
    @Operation(summary = "Delete a customer", description = "Deletes a customer; its accounts are kept, as in V1.")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long customerNumber) {
        customerWrites.delete(customerNumber);
        return ResponseEntity.noContent().build();
    }

    private static void requireAddressAndContact(CustomerDetails customer) {
        if (customer.getCustomerAddress() == null) {
            throw new ApiV2BadRequestException("customerAddress is required.");
        }
        if (customer.getContactDetails() == null) {
            throw new ApiV2BadRequestException("contactDetails is required.");
        }
    }
}
