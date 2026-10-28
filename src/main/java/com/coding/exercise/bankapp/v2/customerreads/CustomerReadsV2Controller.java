package com.coding.exercise.bankapp.v2.customerreads;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coding.exercise.bankapp.domain.CustomerDetails;
import com.coding.exercise.bankapp.v2.ApiV2;
import com.coding.exercise.bankapp.v2.ApiV2NotFoundException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(ApiV2.BASE_PATH + "/customers")
@Tag(name = "Customer V2 reads")
public class CustomerReadsV2Controller {

    private final CustomerReadsV2Adapter customers;

    public CustomerReadsV2Controller(CustomerReadsV2Adapter customers) {
        this.customers = customers;
    }

    @GetMapping(path = "/all")
    @Operation(summary = "Find all customers", description = "Gets details of all the customers")
    public List<CustomerDetails> getAllCustomers() {
        return customers.findAll();
    }

    @GetMapping(path = "/{customerNumber}")
    @Operation(summary = "Get customer details", description = "Get customer details by customer number.")
    public CustomerDetails getCustomer(@PathVariable Long customerNumber) {
        return customers.findByCustomerNumber(customerNumber)
                .orElseThrow(() -> new ApiV2NotFoundException("Customer Number " + customerNumber + " not found."));
    }
}
