package com.contracts.data

import com.beancounter.auth.AuthUtilService
import com.beancounter.common.contracts.PortfolioResponse
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.cash.CashService
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.portfolio.PortfolioRepository
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.data.domain.Sort
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.io.File
import java.io.IOException
import java.util.Optional

/**
 * Base class for Portfolio contracts.
 */
class PortfolioBase : ContractVerifierBase() {
    @Autowired
    private lateinit var currencyService: CurrencyService

    @MockitoBean
    private lateinit var portfolioRepository: PortfolioRepository

    @MockitoBean
    internal lateinit var cashService: CashService

    @Autowired
    internal lateinit var authUtilService: AuthUtilService

    companion object {
        @JvmStatic
        fun getPortfolio(jsonFile: File): Portfolio {
            val (data) =
                objectMapper.readValue(
                    jsonFile,
                    PortfolioResponse::class.java
                )
            return data
        }

        @JvmStatic
        @get:Throws(IOException::class)
        val testPortfolio: Portfolio
            get() {
                return getPortfolio(ClassPathResource("contracts/portfolio/test.json").file)
            }

        @JvmStatic
        @get:Throws(IOException::class)
        val cashPortfolio: Portfolio
            get() {
                return getPortfolio(ClassPathResource("contracts/portfolio/cash-ladder.json").file)
            }

        @JvmStatic
        @get:Throws(IOException::class)
        val emptyPortfolio: Portfolio
            get() {
                val jsonFile = ClassPathResource("contracts/portfolio/empty.json").file
                return getPortfolio(jsonFile)
            }

        private fun mockPortfolio(
            portfolio: Portfolio,
            systemUser: SystemUser,
            portfolioRepository: PortfolioRepository
        ) {
            // For the sake of convenience when testing; id and code are the same
            `when`(portfolioRepository.findById(portfolio.id))
                .thenReturn(Optional.of(portfolio))
            `when`(
                portfolioRepository.findByCodeAndOwner(
                    portfolio.code,
                    systemUser
                )
            ).thenReturn(Optional.of(portfolio))
        }

        @JvmStatic
        fun portfolios(
            systemUser: SystemUser,
            keyGenUtils: KeyGenUtils,
            portfolioRepository: PortfolioRepository
        ) {
            val dateUtils = DateUtils()
            mockPortfolio(
                emptyPortfolio,
                systemUser,
                portfolioRepository
            )
            mockPortfolio(
                testPortfolio,
                systemUser,
                portfolioRepository
            )
            mockPortfolio(
                cashPortfolio,
                systemUser,
                portfolioRepository
            )
            val portfolioCode = "TEST"

            // All Portfolio
            `when`(
                portfolioRepository.findByOwner(
                    systemUser,
                    Sort.by(Sort.Order.asc("code"))
                )
            ).thenReturn(
                objectMapper
                    .readValue(
                        ClassPathResource("contracts/portfolio/portfolios.json").file,
                        PortfoliosResponse::class.java
                    ).data
            )
            `when`(
                portfolioRepository.findDistinctPortfolioByAssetIdAndTradeDate(
                    "KMI",
                    dateUtils.getDate("2020-05-01")
                )
            ).thenReturn(
                arrayListOf(
                    Portfolio(
                        id = portfolioCode,
                        code = portfolioCode,
                        name = "${Constants.NZD.code} Portfolio",
                        currency = Constants.NZD,
                        base = Constants.USD,
                        owner = systemUser
                    )
                )
            )
            `when`(keyGenUtils.id).thenReturn(portfolioCode)
            `when`(
                portfolioRepository.saveAll(
                    arrayListOf(
                        Portfolio(
                            id = portfolioCode,
                            code = Constants.SGD.code,
                            name = "${Constants.SGD.code} Balanced",
                            currency = Constants.SGD,
                            base = Constants.USD,
                            owner = systemUser
                        )
                    )
                )
            ).thenReturn(
                arrayListOf(
                    Portfolio(
                        id = portfolioCode,
                        code = Constants.SGD.code,
                        name = "${Constants.SGD.code} Balanced",
                        currency = Constants.SGD,
                        base = Constants.USD,
                        owner = systemUser
                    )
                )
            )
        }
    }

    @BeforeEach
    fun mockPortfolio() {
        val systemUser =
            ContractHelper(authUtilService).defaultUser(
                systemUserService = systemUserService
            )
        `when`(systemUserService.getOrThrow()).thenReturn(systemUser)
        portfolios(
            systemUser,
            keyGenUtils,
            portfolioRepository
        )
    }
}