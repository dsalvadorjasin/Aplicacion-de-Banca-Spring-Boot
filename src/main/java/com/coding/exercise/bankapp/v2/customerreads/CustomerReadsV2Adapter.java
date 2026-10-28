package com.coding.exercise.bankapp.v2.customerreads;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Component;

import com.coding.exercise.bankapp.domain.CustomerDetails;
import com.coding.exercise.bankapp.model.Customer;
import com.coding.exercise.bankapp.repository.CustomerRepository;
import com.coding.exercise.bankapp.service.helper.BankingServiceHelper;

/**
 * Reads customers like {@code BankingServiceImpl#findAll} and {@code #findByCustomerNumber},
 * but tolerates stored customers without an address or contact (V1 fails with a 500 on them).
 * Duplicate customer numbers surface as {@code IncorrectResultSizeDataAccessException} (409 in V2).
 */
@Component
public class CustomerReadsV2Adapter {

    private final CustomerRepository customerRepository;
    private final BankingServiceHelper helper;

    public CustomerReadsV2Adapter(CustomerRepository customerRepository, BankingServiceHelper helper) {
        this.customerRepository = customerRepository;
        this.helper = helper;
    }

    public List<CustomerDetails> findAll() {
        return StreamSupport.stream(customerRepository.findAll().spliterator(), false)
                .map(this::toDomain)
                .toList();
    }

    public Optional<CustomerDetails> findByCustomerNumber(Long customerNumber) {
        return customerRepository.findByCustomerNumber(customerNumber).map(this::toDomain);
    }

    private CustomerDetails toDomain(Customer customer) {
        if (customer.getCustomerAddress() != null && customer.getContactDetails() != null) {
            return helper.convertToCustomerDomain(customer);
        }
        return CustomerDetails.builder()
                .firstName(customer.getFirstName())
                .middleName(customer.getMiddleName())
                .lastName(customer.getLastName())
                .customerNumber(customer.getCustomerNumber())
                .status(customer.getStatus())
                .contactDetails(customer.getContactDetails() == null
                        ? null : helper.convertToContactDomain(customer.getContactDetails()))
                .customerAddress(customer.getCustomerAddress() == null
                        ? null : helper.convertToAddressDomain(customer.getCustomerAddress()))
                .build();
    }
}
