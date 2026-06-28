package com.beancounter.marketdata.assets

import com.beancounter.marketdata.SpringMvcDbTest
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * A composite policy (CPF) holds its balances in `private_asset_sub_account`
 * rows whose `asset_id` is NOT NULL. Deleting the parent config must DELETE
 * those child rows — never dissociate them by nulling the FK (the historical
 * unidirectional `@OneToMany @JoinColumn` wart, which fails the NOT NULL
 * constraint and blocks the user from deleting the asset).
 */
@SpringMvcDbTest
@Transactional
class PrivateAssetConfigDeleteTest {
    @Autowired
    private lateinit var configRepository: PrivateAssetConfigRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `deleting a composite config deletes its sub-accounts without nulling the FK`() {
        val assetId = "cpf-delete-me"
        configRepository.save(
            PrivateAssetConfig(
                assetId = assetId,
                policyType = PolicyType.CPF,
                subAccounts =
                    mutableListOf(
                        PrivateAssetSubAccount(
                            assetId = assetId,
                            code = "OA",
                            displayName = "Ordinary Account",
                            balance = BigDecimal("85000.0000"),
                            expectedReturnRate = BigDecimal("0.0250")
                        )
                    )
            )
        )
        entityManager.flush()
        entityManager.clear()

        assertThatCode {
            configRepository.deleteById(assetId)
            entityManager.flush()
        }.doesNotThrowAnyException()

        entityManager.clear()
        assertThat(configRepository.findById(assetId)).isEmpty
        val remaining =
            entityManager
                .createNativeQuery(
                    """select count(*) from "private_asset_sub_account" where "asset_id" = :id"""
                ).setParameter("id", assetId)
                .singleResult as Number
        assertThat(remaining.toInt()).isZero()
    }
}