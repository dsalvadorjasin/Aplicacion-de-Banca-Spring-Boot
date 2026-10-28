# V2 OpenAPI specifications

Each endpoint group has one self-contained OpenAPI 3.0.3 file named
`<group id>.yaml`. Keep a local `ProblemDetail` schema in every file and do not
use cross-file `$ref` values. Set the server URL to `/`, and write complete
paths beginning with `/v2`.

Every operation must include `x-v1-differences`. List only intentional
differences from V1, and make each listed difference an assertion in a test.
Use `[]` when the operation has no differences.

Use these status-code conventions:

- Reads return `200`.
- Creates return `201`, a `Location` header, and a JSON body.
- Updates return `200` with the resource.
- Deletes return `204` with no body.
- Never return `302`.
- Errors use `application/problem+json` ProblemDetail bodies: `404` for missing
  resources, `400` for invalid requests, `409` for duplicates, and `422` for
  business-rule violations.

The V2 path is `/v2` followed by the V1 path. For example,
`GET /customers/{customerNumber}` becomes
`GET /v2/customers/{customerNumber}`.

Copy `_template.yaml` to the group file, replace its example operation, and
validate the specification and responses with `ApiV2SpecValidator.forGroup`
from `com.coding.exercise.bankapp.v2.support`.
