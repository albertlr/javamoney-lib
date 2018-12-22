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
 * Contributors: @atsticks, @keilw, @otjava
 */

package org.javamoney.moneta.convert.nbr;

import org.javamoney.moneta.Money;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.convert.ConversionQuery;
import javax.money.convert.ConversionQueryBuilder;
import javax.money.convert.CurrencyConversion;
import javax.money.convert.ExchangeRateProvider;
import java.math.BigDecimal;
import java.util.Objects;

import static java.util.Objects.nonNull;
import static javax.money.convert.MonetaryConversions.getExchangeRateProvider;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class NbrRateProviderTest {

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
    public void test() {
        System.out.printf("provider: %s %n", provider);
        System.out.printf("provider: %s %n", ((NbrRateProvider) provider).getExchangeRate(ConversionQueryBuilder.of()
                .setBaseCurrency("RON")
                .setTermCurrency("EUR")
                .build()));

        System.out.printf("provider: %s %n", ((NbrRateProvider) provider).rates);

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

        assertEquals(result.getCurrency(), EURO);
        assertTrue(result.getNumber().doubleValue() > 0);
    }

    @Test
    public void shouldConvertsEuroToLei() {
        CurrencyConversion currencyConversion = provider.getCurrencyConversion(ROMANIAN_LEI);
        assertNotNull(currencyConversion);
        MonetaryAmount money = Money.of(BigDecimal.TEN, EURO);
        MonetaryAmount result = currencyConversion.apply(money);

        assertEquals(result.getCurrency(), ROMANIAN_LEI);
        assertTrue(result.getNumber().doubleValue() > 0);
    }

    @Test
    public void shouldConvertForintToLei() {
        CurrencyConversion currencyConversion = provider.getCurrencyConversion(ROMANIAN_LEI);
        assertNotNull(currencyConversion);
        MonetaryAmount money = Money.of(BigDecimal.TEN, HUNGARIAN_FORINT);
        MonetaryAmount result = currencyConversion.apply(money);

        assertEquals(result.getCurrency(), ROMANIAN_LEI);
        assertTrue(result.getNumber().doubleValue() > 0);
    }

    @Test
    public void shouldConvertLeiToForint() {
        CurrencyConversion currencyConversion = provider.getCurrencyConversion(HUNGARIAN_FORINT);
        assertNotNull(currencyConversion);
        MonetaryAmount money = Money.of(BigDecimal.TEN, ROMANIAN_LEI);
        MonetaryAmount result = currencyConversion.apply(money);

        assertEquals(result.getCurrency(), HUNGARIAN_FORINT);
        assertTrue(result.getNumber().doubleValue() > 0);
    }
}
