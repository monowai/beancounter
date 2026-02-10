package com.beancounter.common.contracts

import com.beancounter.common.model.AccountingType

data class AccountingTypeResponse(
    override val data: AccountingType
) : Payload<AccountingType>

data class AccountingTypesResponse(
    override val data: Collection<AccountingType>
) : Payload<Collection<AccountingType>>