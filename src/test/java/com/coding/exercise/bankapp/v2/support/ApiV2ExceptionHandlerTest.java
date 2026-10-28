package com.coding.exercise.bankapp.v2.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.coding.exercise.bankapp.v2.ApiV2ExceptionHandler;
import com.coding.exercise.bankapp.v2.BadRequestException;
import com.coding.exercise.bankapp.v2.BusinessRuleException;
import com.coding.exercise.bankapp.v2.ConflictException;
import com.coding.exercise.bankapp.v2.NotFoundException;

class ApiV2ExceptionHandlerTest extends ApiV2TestSupport {

	private MockMvc mockMvc;

	@BeforeEach
	void createStandaloneMvc() {
		mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
				.setControllerAdvice(new ApiV2ExceptionHandler())
				.build();
	}

	@Test
	void mapsEachSharedExceptionToProblemDetail() throws Exception {
		assertProblem("/errors/not-found", 404, "Not Found", "missing resource");
		assertProblem("/errors/conflict", 409, "Conflict", "duplicate resource");
		assertProblem("/errors/business-rule", 422, "Unprocessable Entity", "rule failed");
		assertProblem("/errors/bad-request", 400, "Bad Request", "invalid request");
	}

	@Test
	void mapsPathTypeMismatchAndUnreadableJsonToBadRequestProblems() throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.get("/errors/type/not-a-number"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400));

		mockMvc.perform(post("/errors/unreadable")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{not-json"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void hidesUnexpectedExceptionDetailsInProblemResponse() throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.get("/errors/unexpected"))
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Internal Server Error"))
				.andExpect(jsonPath("$.status").value(500))
				.andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
				.andExpect(result -> assertThat(result.getResponse().getContentAsString())
						.doesNotContain("sensitive exception message"));
	}

	@Test
	void leavesV1ErrorsAsBootDefaultJson() {
		var response = rest.getForEntity("/accounts/not-a-number", String.class);

		assertThat(response.getStatusCode().value()).isEqualTo(400);
		assertThat(response.getHeaders().getContentType()).isNotNull();
		assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isFalse();
		assertThat(response.getBody()).contains("\"status\":400");
	}

	private void assertProblem(String path, int code, String title, String detail) throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path))
				.andExpect(status().is(code))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value(title))
				.andExpect(jsonPath("$.status").value(code))
				.andExpect(jsonPath("$.detail").value(detail));
	}

	@RestController
	public static class ProbeController {

		@GetMapping("/errors/not-found")
		public void notFound() {
			throw new NotFoundException("missing resource");
		}

		@GetMapping("/errors/conflict")
		public void conflict() {
			throw new ConflictException("duplicate resource");
		}

		@GetMapping("/errors/business-rule")
		public void businessRule() {
			throw new BusinessRuleException("rule failed");
		}

		@GetMapping("/errors/bad-request")
		public void badRequest() {
			throw new BadRequestException("invalid request");
		}

		@GetMapping("/errors/type/{value}")
		public int typeMismatch(@PathVariable int value) {
			return value;
		}

		@PostMapping(path = "/errors/unreadable", consumes = MediaType.APPLICATION_JSON_VALUE)
		public int unreadable(@RequestBody ProbePayload payload) {
			return payload.value;
		}

		@GetMapping("/errors/unexpected")
		public void unexpected() {
			throw new IllegalStateException("sensitive exception message");
		}
	}

	public static class ProbePayload {
		public int value;
	}
}
