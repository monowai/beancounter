# Exception Handling

This document outlines how the BeanCounter system handles exceptions.

## Exception Types

Exceptions are categorized into two types:

- `SystemException`: These are unexpected errors
    - Requires operational intervention, possibly due to a system bug.
    - They are signaled over HTTP as a `5XX` HttpStatus.
- `BusinessException`: These are client errors
    - Could be resolved by the client if they reformulate their request.
    - They are signaled over HTTP as a `4XX` HttpStatus.

There are also specialized `BusinessException` types:

- `ForbiddenException`: The user is recognized
    - Lacks sufficient privileges to invoke the endpoint.
    - Signaled over HTTP as a `403` HttpStatus.
- `UnauthorizedException`: The system is unable to authenticate the user.
    - Signaled over HTTP as a `401` HttpStatus.

## Handling 'Not Found' Errors

Instead of throwing a `404` error, consider throwing a `BusinessException`.
This helps clients distinguish between "The endpoint does not exist" _and_
"The resource does not exist".
If no results are found, return an empty `Collection` with a `2XX` status code.
