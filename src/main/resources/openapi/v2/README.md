# API V2 specifications

Use OpenAPI 3.0.3. Keep one self-contained file per endpoint group, named
`<group id>.yaml`. Do not use cross-file `$ref`s; every file defines its own
`components.schemas.ProblemDetail` with `type`, `title`, `status`, `detail`, and
`instance` properties.

Set `servers` to `[{url: /}]`. Write full paths beginning with `/v2`; the
application context path `/bank-api` is not part of the specification.

Every operation must include `x-v1-differences` as a list of strings. Use an
empty list (`[]`) when there are no differences. Each listed difference must be
asserted by a test. Preserve V1 field names and values unless a difference is
listed.

Use these status conventions:

* Reads return `200`.
* Creates return `201`, an absolute-URI `Location` header, and a JSON body.
* Updates return `200` with the resource.
* Deletes return `204` with no body.
* Errors use `application/problem+json` with `400`, `404`, `409`, or `422`.
* Bad input must never be redirected (`302`) or reported as an internal error
  (`500`).
