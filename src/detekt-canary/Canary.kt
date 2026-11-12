package canary

fun unsafeCall(value: String?): Int = value!!.length

fun unnecessaryOperator(value: String): Int = value!!.length
