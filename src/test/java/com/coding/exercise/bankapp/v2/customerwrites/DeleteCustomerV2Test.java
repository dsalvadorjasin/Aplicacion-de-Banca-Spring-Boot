package com.coding.exercise.bankapp.v2.customerwrites;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.SimpleValidationReportFormat;
import com.atlassian.oai.validator.report.ValidationReport;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class DeleteCustomerV2Test extends CustomerWritesV2TestSupport {

    private static final OpenApiInteractionValidator DELETE_SPEC = OpenApiInteractionValidator
            .createForSpecificationUrl(DeleteCustomerV2Test.class.getClassLoader()
                    .getResource("openapi/v2/" + GROUP + ".yaml").toExternalForm())
            .build();

    /**
     * Full request + response check for DELETE. The shared validator always sends
     * Accept: application/json, which a no-body operation whose only response media
     * type is application/problem+json cannot match, so the request here has no Accept.
     */
    private static void assertValidDelete(String path, ResponseEntity<String> response) {
        SimpleResponse.Builder simpleResponse = new SimpleResponse.Builder(response.getStatusCode().value());
        response.getHeaders().forEach(simpleResponse::withHeader);
        if (response.getBody() != null) {
            simpleResponse.withBody(response.getBody());
        }
        ValidationReport report = DELETE_SPEC.validate(
                SimpleRequest.Builder.delete(path).build(), simpleResponse.build());
        assertThat(report.hasErrors())
                .as("DELETE %s:%n%s", path, SimpleValidationReportFormat.getInstance().apply(report))
                .isFalse();
    }

    @Test
    void deletesLikeV1AndReturnsNoContent() throws Exception {
        long v1Number = nextNumber();
        long v2Number = nextNumber();
        createWithV1(v1Number, "Ada");
        createWithV1(v2Number, "Ada");

        ResponseEntity<String> v1 = send(HttpMethod.DELETE, "/customers/" + v1Number, null);
        ResponseEntity<String> v2 = send(HttpMethod.DELETE, "/v2/customers/" + v2Number, null);

        assertV1Text(v1, HttpStatus.OK, "Success: Customer deleted.");
        assertThat(v2.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(v2.getBody()).isNull();
        assertValidDelete("/v2/customers/" + v2Number, v2);
        assertSameSecurityHeaders(v1, v2);

        for (long number : new long[] {v1Number, v2Number}) {
            assertThat(storedCount(number)).isZero();
            ResponseEntity<String> afterDelete = v1Get(number);
            assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(afterDelete.getBody()).isNull();
        }
    }

    @Test
    void keepsTheCustomersAccountsLikeV1() throws Exception {
        long customer = nextNumber();
        long account = nextNumber();
        createWithV1(customer, "Ada");
        String accountJson = "{\"accountNumber\":" + account + ",\"accountBalance\":12.0,\"accountStatus\":\"ACTIVE\","
                + "\"accountType\":\"SAVINGS\",\"bankInformation\":{\"branchName\":\"Main\",\"branchCode\":12,"
                + "\"routingNumber\":34,\"branchAddress\":{\"city\":\"Boston\"}}}";
        assertThat(send(HttpMethod.POST, "/accounts/add/" + customer, accountJson).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> v2 = send(HttpMethod.DELETE, "/v2/customers/" + customer, null);

        assertThat(v2.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<String> stillThere = send(HttpMethod.GET, "/accounts/" + account, null);
        assertThat(stillThere.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(json(stillThere).get("accountBalance").asDouble()).isEqualTo(12.0);
    }

    @Test
    void missingCustomerIsNotFoundInV2AndBadRequestInV1() throws Exception {
        long number = nextNumber();

        ResponseEntity<String> v1 = send(HttpMethod.DELETE, "/customers/" + number, null);
        ResponseEntity<String> v2 = send(HttpMethod.DELETE, "/v2/customers/" + number, null);

        assertV1Text(v1, HttpStatus.BAD_REQUEST, "Customer does not exist.");
        assertV2Problem(v2, HttpStatus.NOT_FOUND, "/v2/customers/" + number);
        assertValidDelete("/v2/customers/" + number, v2);
    }

    @Test
    void secondDeleteIsNotFound() throws Exception {
        long number = nextNumber();
        createWithV1(number, "Ada");

        assertThat(send(HttpMethod.DELETE, "/v2/customers/" + number, null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<String> again = send(HttpMethod.DELETE, "/v2/customers/" + number, null);

        assertV2Problem(again, HttpStatus.NOT_FOUND, "/v2/customers/" + number);
        assertValidDelete("/v2/customers/" + number, again);
    }

    @Test
    void duplicateStoredNumberIsAConflictInV2AndServerErrorInV1WithoutDeleting() throws Exception {
        long number = nextNumber();
        createWithV1(number, "Ada");
        createWithV1(number, "Ada");

        ResponseEntity<String> v2 = send(HttpMethod.DELETE, "/v2/customers/" + number, null);
        assertV2Problem(v2, HttpStatus.CONFLICT, "/v2/customers/" + number);
        assertValidDelete("/v2/customers/" + number, v2);
        assertThat(storedCount(number)).isEqualTo(2);

        ResponseEntity<String> v1 = send(HttpMethod.DELETE, "/customers/" + number, null);
        assertV1BootError(v1, HttpStatus.INTERNAL_SERVER_ERROR, "/customers/" + number);
        assertThat(storedCount(number)).isEqualTo(2);
    }

    @Test
    void invalidCustomerNumbersAreProblemDetailsInV2AndBootErrorsInV1() throws Exception {
        for (String badNumber : new String[] {"nope", "1.5", "99999999999999999999"}) {
            ResponseEntity<String> v1 = send(HttpMethod.DELETE, "/customers/" + badNumber, null);
            ResponseEntity<String> v2 = send(HttpMethod.DELETE, "/v2/customers/" + badNumber, null);

            assertV1BootError(v1, HttpStatus.BAD_REQUEST, "/customers/" + badNumber);
            assertV2Problem(v2, HttpStatus.BAD_REQUEST, "/v2/customers/" + badNumber);
            SPEC.assertValidResponse(HttpMethod.DELETE, "/v2/customers/" + badNumber, v2);
        }
    }
}
