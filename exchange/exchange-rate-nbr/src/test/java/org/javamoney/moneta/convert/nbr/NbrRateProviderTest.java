/*
 * Copyright (c) 2012, 2018, Werner Keil, Anatole Tresch and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * Contributors: @albertlr
 */

package org.javamoney.moneta.convert.nbr;

import org.javamoney.moneta.Money;
import org.javamoney.moneta.spi.DefaultNumberValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.NumberValue;
import javax.money.convert.ConversionQuery;
import javax.money.convert.ConversionQueryBuilder;
import javax.money.convert.CurrencyConversion;
import javax.money.convert.ExchangeRateProvider;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;

import static java.util.Objects.nonNull;
import static javax.money.convert.MonetaryConversions.getExchangeRateProvider;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class NbrRateProviderTest {
    private static final Logger log = LoggerFactory.getLogger(NbrRateProviderTest.class);

    private static final CurrencyUnit EURO = Monetary.getCurrency("EUR");
    private static final CurrencyUnit ROMANIAN_LEI = Monetary.getCurrency("RON");
    private static final CurrencyUnit HUNGARIAN_FORINT = Monetary.getCurrency("HUF");

    private ExchangeRateProvider provider;

    @BeforeTest
    public void setup() throws InterruptedException {
        provider = getExchangeRateProvider("NBR");
        Thread.sleep(1_000L);
    }

    @Test
    public void shouldReturnsNbrRateProvider() {
        assertTrue(nonNull(provider));
        assertEquals(provider.getClass(), NbrRateProvider.class);
    }

    @Test
    public void shouldReturnsSameLeiValue() {
        CurrencyConversion currencyConversion = provider.getCurrencyConversion(ROMANIAN_LEI);
        assertNotNull(currencyConversion);
        MonetaryAmount money = Money.of(BigDecimal.TEN, ROMANIAN_LEI);
        MonetaryAmount result = currencyConversion.apply(money);

        assertEquals(result.getCurrency(), ROMANIAN_LEI);
        assertEquals(result.getNumber().numberValue(BigDecimal.class), BigDecimal.TEN);
    }

    @Test
    public void shouldReturnsSameForintValue() {
        CurrencyConversion currencyConversion = provider.getCurrencyConversion(HUNGARIAN_FORINT);
        assertNotNull(currencyConversion);
        MonetaryAmount money = Money.of(BigDecimal.TEN, HUNGARIAN_FORINT);
        MonetaryAmount result = currencyConversion.apply(money);

        assertEquals(result.getCurrency(), HUNGARIAN_FORINT);
        assertEquals(result.getNumber().numberValue(BigDecimal.class), BigDecimal.TEN);
    }

    @Test
    public void shouldReturnsSameEuroValue() {
        CurrencyConversion currencyConversion = provider.getCurrencyConversion(EURO);
        assertNotNull(currencyConversion);
        MonetaryAmount money = Money.of(BigDecimal.TEN, EURO);
        MonetaryAmount result = currencyConversion.apply(money);

        assertEquals(result.getCurrency(), EURO);
        assertEquals(result.getNumber().numberValue(BigDecimal.class), BigDecimal.TEN);
    }

    @Test
    public void shouldConvertsLeiToEuro() {
        CurrencyConversion currencyConversion = provider.getCurrencyConversion(EURO);
        assertNotNull(currencyConversion);
        MonetaryAmount money = Money.of(BigDecimal.TEN, ROMANIAN_LEI);
        MonetaryAmount result = currencyConversion.apply(money);

        log.info("money {} converted as {}", money, result);
        assertEquals(result.getCurrency(), EURO);
        assertTrue(result.getNumber().doubleValue() > 0);
    }

    @Test
    public void shouldConvertsEuroToLei() {
        CurrencyConversion currencyConversion = provider.getCurrencyConversion(ROMANIAN_LEI);
        assertNotNull(currencyConversion);
        MonetaryAmount money = Money.of(BigDecimal.TEN, EURO);
        MonetaryAmount result = currencyConversion.apply(money);

        log.info("money {} converted as {}", money, result);
        assertEquals(result.getCurrency(), ROMANIAN_LEI);
        assertTrue(result.getNumber().doubleValue() > 0);
    }

    @Test
    public void shouldConvertForintToLei() {
        CurrencyConversion currencyConversion = provider.getCurrencyConversion(ROMANIAN_LEI);
        assertNotNull(currencyConversion);
        MonetaryAmount money = Money.of(BigDecimal.valueOf(100), HUNGARIAN_FORINT);
        MonetaryAmount result = currencyConversion.apply(money);

        log.info("money {} converted as {}", money, result);
        assertEquals(result.getCurrency(), ROMANIAN_LEI);
        assertTrue(result.getNumber().doubleValue() > 0);
    }

    @Test
    public void shouldConvertEuroToLeiOnKnownDate() {
        // 2018-01-03 - <Rate currency="EUR">4.6412</Rate>
        ConversionQuery conversionQuery = ConversionQueryBuilder.of()
                .setTermCurrency(ROMANIAN_LEI)
                .set(LocalDate.class, LocalDate.of(2018, 1, 3))
                .build();

        CurrencyConversion currencyConversion = provider.getCurrencyConversion(conversionQuery);
        assertNotNull(currencyConversion);
        MonetaryAmount hundredEuro = Money.of(BigDecimal.valueOf(100), EURO);
        MonetaryAmount result = currencyConversion.apply(hundredEuro);
        MonetaryAmount fourSixFourPointOneTwoRon = Money.of(BigDecimal.valueOf(464.12d), ROMANIAN_LEI);

        log.info("money {} converted as {}", hundredEuro, result);
        assertEquals(result.getCurrency(), ROMANIAN_LEI);
        assertEquals(result, fourSixFourPointOneTwoRon);
    }

    @Test
    public void shouldConvertForintToLeiOnKnownDate() {
        // 2018-01-03 - <Rate currency="HUF" multiplier="100">1.5010</Rate>
        ConversionQuery conversionQuery = ConversionQueryBuilder.of()
                .setTermCurrency(ROMANIAN_LEI)
                .set(LocalDate.class, LocalDate.of(2018, 1, 3))
                .build();

        CurrencyConversion currencyConversion = provider.getCurrencyConversion(conversionQuery);
        assertNotNull(currencyConversion);
        MonetaryAmount hundredHuf = Money.of(BigDecimal.valueOf(100), HUNGARIAN_FORINT);
        MonetaryAmount result = currencyConversion.apply(hundredHuf);
        MonetaryAmount onePointFiftyTenRon = Money.of(BigDecimal.valueOf(1.5010d), ROMANIAN_LEI);

        log.info("money {} converted as {}", hundredHuf, result);
        assertEquals(result.getCurrency(), ROMANIAN_LEI);
        assertEquals(result, onePointFiftyTenRon);
    }

    @Test
    public void shouldConvertLeiToForint() {
        CurrencyConversion currencyConversion = provider.getCurrencyConversion(HUNGARIAN_FORINT);
        assertNotNull(currencyConversion);
        MonetaryAmount money = Money.of(BigDecimal.TEN, ROMANIAN_LEI);
        MonetaryAmount result = currencyConversion.apply(money);

        log.info("money {} converted as {}", money, result);
        assertEquals(result.getCurrency(), HUNGARIAN_FORINT);
        assertTrue(result.getNumber().doubleValue() > 0);
    }

    @Test
    public void testWithMultiplier() {
        // eg: 100 HUF = 1.4434 RON
        // => 1 HUF = 1.4434 / 100 = 0.014434
        // => 1 RON = 100 / 1.4434 HUF = 69.2808646 HUF
        BigInteger multiplier = BigInteger.valueOf(100L);
        BigDecimal value = new BigDecimal("1.4434");

        NumberValue result = NbrRateReadingHandler.computeFactor(multiplier, value);

        assertEquals(result.numberValue(BigDecimal.class), new BigDecimal("69.28086462519052"));
    }
}
