package com.coding.exercise.bankapp.v2.support;

import com.coding.exercise.bankapp.e2e.E2ETestSupport;

public abstract class ApiV2TestSupport extends E2ETestSupport {

	protected V1V2Comparison compare() {
		return V1V2Comparison.using(rest);
	}

	protected static ApiV2SpecValidator spec(String groupId) {
		return ApiV2SpecValidator.forGroup(groupId);
	}
}
