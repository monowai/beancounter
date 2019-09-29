import org.springframework.cloud.contract.spec.Contract
Contract.make {
    description "should return fxRates on a Date"
    request{
        method POST()
        headers {
            contentType(applicationJson())
        }
        url("/api/fx") {
            body(file("fx/request.json"))
        }
    }
    response {
        status 200
        headers {
            contentType applicationJson()
        }
        body(file("fx/response.json"))
    }
}