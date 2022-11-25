# Exceptions

Communicating errors is a critical part of understanding what is going on with your system and is foundational for
improving the client experience.

In general, exceptions are broken down into two categories

- Unexpected errors that cannot be resolved by the client or the system
- Error that could be resolved by the client if they reformulated their request

We realise these in GROW in this manner

- `SystemException` - Requires operational intervention, could be a system bug. Think of this as “Get out of bed” type
  conditions. These are signaled over HTTP as a `5XX` HttpStatus
- `BusinessException` - Something could not be resolved by the application. Testable conditions that result in an error,
  typically business logic failure. These are signaled over HTTP as a `4XX` HttpStatus

There are some sepecialised variations of the `BusinessExecption`, notably

- `ForbiddenException` - User is recognized, but has insufficient priviges to invoke the endpoint. These are signaled
  over HTTP as a `403` HttpStatus
- `UnauthorizedException` - Unable to Autheticate the user. These are signaled over HTTP as a `401` HttpStatus

## Not Found

Ideally, do not throw a `404` error. Instead, consider throwing a `BusinessException` This will help clients understand
the difference between "The end point does not exist" vs. “The resource does not exist”. If the response is no results,
e.g. then an empty `Collection` is returned with a `2XX` status code. The call worked, no records met the crietria and
the client should be instructed to loop until they get "no results”.

## Spring Backends

Utilise class level annotation`@ControllerAdvice` to handle errors in a consistent manner and provide a `@ResponseBody`
to the client to give them some idea as to why the call failed.

For example,

```kotlin
@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException::class)
    @ResponseBody
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleAccessDenied(request: HttpServletRequest, e: Throwable): SpringExceptionMessage =
        SpringExceptionMessage(
            error = "Access Denied.",
            message = e.message,
            path = request.requestURI
        )

    @ExceptionHandler(ConnectException::class, ResourceAccessException::class, FeignException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    fun handleSystemException(request: HttpServletRequest, e: Throwable): SpringExceptionMessage =
        SpringExceptionMessage(
            error = "Unable to contact dependent system.",
            message = e.message,
            path = request.requestURI
        ).also { log.error(e.message) }
        ...
```

## Clients

Clients are expected to handle the exceptions thrown by the backend and result

