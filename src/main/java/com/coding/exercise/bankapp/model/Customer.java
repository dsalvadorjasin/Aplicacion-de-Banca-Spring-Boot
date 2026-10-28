package com.coding.exercise.bankapp.model;

import java.util.Date;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Customer {

    @Id
    @GeneratedValue
    @Column(name="CUST_ID")
    private UUID id;
    
    private String firstName;
    
    private String lastName;
    
    private String middleName;
    
    private Long customerNumber;
    
    private String status;
    
    @ManyToOne(cascade=CascadeType.ALL)
    private Address customerAddress;
    
    @OneToOne(cascade=CascadeType.ALL)
    private Contact contactDetails;
    
    @Temporal(TemporalType.TIME)
	private Date createDateTime;
	
    @Temporal(TemporalType.TIME)
	private Date updateDateTime;
	
}
