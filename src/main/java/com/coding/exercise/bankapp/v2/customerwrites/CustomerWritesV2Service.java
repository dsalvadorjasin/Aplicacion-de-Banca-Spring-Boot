package com.coding.exercise.bankapp.v2.customerwrites;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.coding.exercise.bankapp.domain.CustomerDetails;
import com.coding.exercise.bankapp.repository.CustomerRepository;
import com.coding.exercise.bankapp.service.BankingServiceImpl;
import com.coding.exercise.bankapp.v2.ApiV2ConflictException;
import com.coding.exercise.bankapp.v2.ApiV2NotFoundException;

/**
 * Adapts the V1 {@link BankingServiceImpl} customer writes to V2 semantics: the
 * written customer is read back so it can be returned, and missing or duplicate
 * customer numbers become V2 exceptions. Duplicate stored numbers make
 * {@code findByCustomerNumber} throw IncorrectResultSizeDataAccessException,
 * which the shared V2 handler maps to 409 before anything is changed.
 */
@Service
public class CustomerWritesV2Service {

    private final BankingServiceImpl bankingService;
    private final CustomerRepository customerRepository;
    private final Object createLock = new Object();

    public CustomerWritesV2Service(BankingServiceImpl bankingService, CustomerRepository customerRepository) {
        this.bankingService = bankingService;
        this.customerRepository = customerRepository;
    }

    public CustomerDetails create(CustomerDetails customer) {
        Long customerNumber = customer.getCustomerNumber();
        // V1's schema allows duplicate numbers; the lock only serialises V2 creates in this instance.
        synchronized (createLock) {
            if (customerRepository.findByCustomerNumber(customerNumber).isPresent()) {
                throw new ApiV2ConflictException("Customer Number " + customerNumber + " already exists.");
            }
            bankingService.addCustomer(customer);
            return readBack(customerNumber);
        }
    }

    public CustomerDetails update(Long customerNumber, CustomerDetails customer) {
        requireExisting(customerNumber);
        ResponseEntity<Object> result = bankingService.updateCustomer(customer, customerNumber);
        if (result.getStatusCode() == HttpStatus.NOT_FOUND) {
            throw notFound(customerNumber);
        }
        return readBack(customerNumber);
    }

    public void delete(Long customerNumber) {
        requireExisting(customerNumber);
        ResponseEntity<Object> result = bankingService.deleteCustomer(customerNumber);
        if (!result.getStatusCode().is2xxSuccessful()) {
            throw notFound(customerNumber);
        }
    }

    private void requireExisting(Long customerNumber) {
        if (customerRepository.findByCustomerNumber(customerNumber).isEmpty()) {
            throw notFound(customerNumber);
        }
    }

    private CustomerDetails readBack(Long customerNumber) {
        CustomerDetails stored = bankingService.findByCustomerNumber(customerNumber);
        if (stored == null) {
            throw notFound(customerNumber);
        }
        return stored;
    }

    private static ApiV2NotFoundException notFound(Long customerNumber) {
        return new ApiV2NotFoundException("Customer Number " + customerNumber + " not found.");
    }
}
