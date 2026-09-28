package com.coding.exercise.bankapp.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@Entity
public class Contact {

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	@Column(name="CONTACT_ID")
	private UUID id;
	
	private String emailId;
	
	private String homePhone;
	
	private String workPhone;

}
