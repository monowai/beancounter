package com.beancounter.common.contracts

import com.beancounter.common.model.Trn
import org.springframework.boot.context.properties.ConstructorBinding

/**
 * Reponse to a TrnRequest.
 */
data class TrnResponse @ConstructorBinding constructor(override val data: Collection<Trn> = ArrayList()) :
    Payload<Collection<Trn>>
