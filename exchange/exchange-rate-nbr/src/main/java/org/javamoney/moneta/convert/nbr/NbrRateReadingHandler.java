/*
 * Copyright (c) 2018, László-Róbert Albert.
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


import org.javamoney.moneta.convert.ExchangeRateBuilder;
import org.javamoney.moneta.convert.nbr.jaxb.DataSet;
import org.javamoney.moneta.convert.nbr.jaxb.LTCube;
import org.javamoney.moneta.spi.DefaultNumberValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.money.Monetary;
import javax.money.NumberValue;
import javax.money.convert.ConversionContext;
import javax.money.convert.ConversionContextBuilder;
import javax.money.convert.ExchangeRate;
import javax.money.convert.ProviderContext;
import javax.money.convert.RateType;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class NbrRateReadingHandler {
    private static final Logger log = LoggerFactory.getLogger(NbrRateReadingHandler.class);

    private final Map<LocalDate, Map<String, ExchangeRate>> excangeRates;

    private final ProviderContext context;

    public NbrRateReadingHandler(final Map<LocalDate, Map<String, ExchangeRate>> excangeRates,
                                 final ProviderContext context) {
        this.excangeRates = excangeRates;
        this.context = context;
    }

    void parse(final InputStream stream) throws JAXBException, ParseException {
        final String nbrDtoPackage = DataSet.class.getPackage().getName();
        final Unmarshaller unmarshaller = JAXBContext.newInstance(nbrDtoPackage).createUnmarshaller();
        DataSet dataSet = (DataSet) unmarshaller.unmarshal(stream);

        dataSet.getBody().getCube().forEach(
                cube -> {
                    LocalDate date = cube.getDate().toGregorianCalendar().toZonedDateTime().toLocalDate();
                    cube.getRate().stream()
                            // only for currencies that are recognised by our Monetary system
                            .filter(rate -> Monetary.getCurrency(rate.getCurrency()) != null)
                            .forEach(rate -> addRate(date, rate));
                }
        );
    }

    private void addRate(LocalDate date, LTCube.Rate rateInfo) {
        ConversionContext conversionContext = ConversionContextBuilder.create(context, RateType.HISTORIC)
                .build();
        final ExchangeRateBuilder builder = new ExchangeRateBuilder(conversionContext)
                .setBase(NbrAbstractRateProvider.BASE_CURRENCY)
                .setTerm(Monetary.getCurrency(rateInfo.getCurrency()))
                .setFactor(computeFactor(rateInfo.getMultiplier(), rateInfo.getValue()));

        final ExchangeRate exchangeRate = builder.build();

        Map<String, ExchangeRate> rateMap = this.excangeRates.get(date);
        if (Objects.isNull(rateMap)) {
            synchronized (this.excangeRates) {
                rateMap = Optional.ofNullable(this.excangeRates.get(date))
                        .orElse(new ConcurrentHashMap<>());
                this.excangeRates.putIfAbsent(date, rateMap);
            }
        }
        if (log.isTraceEnabled()) {
            log.trace("add rate {} {} = {} {} @ {} :: {}",
                    (rateInfo.getMultiplier() == null ? 1 : rateInfo.getMultiplier()),
                    rateInfo.getCurrency(), rateInfo.getValue(), NbrAbstractRateProvider.BASE_CURRENCY,
                    date, exchangeRate);
        }
        rateMap.put(rateInfo.getCurrency(), exchangeRate);
    }

    /**
     * Compute the correct exchange rate factor.
     * <p>
     * The NBR exchange rate is given as
     * <pre>
     * 1 EUR    = 4.6571 RON
     * 100 HUF  = 1.4434 RON
     * </pre>
     * but the Java Money exchange rate need to be in the following format
     * <pre>
     * 1 RON = 0.214725902385605 EUR
     * 1 RON = 69.2808646 HUF
     * </pre>
     * </p>
     *
     * @param multiplier The multiplier. <code>null</code> means that default, <code>1</code>, is used.
     * @param value      The exchange rate that we recive from the bank
     * @return Returns the correct rate factor {@code 1 RON = x CURRENCY}
     */
    static NumberValue computeFactor(BigInteger multiplier, BigDecimal value) {
        NumberValue rateValue = DefaultNumberValue.of(value);
        // eg: 100 HUF = 1.4434 RON
        // => 1 HUF = 1.4434 / 100 = 0.014434
        // => 1 RON = 100 / 1.4434 HUF = 69.2808646 HUF

        // eg: 1 EUR = 4.6571 RON
        // => 1 RON = 1 / 4.6571 EUR = 0.214725902385605 EUR
        if (multiplier == null) {
            multiplier = BigInteger.ONE;
        }

        NumberValue nbrMultiplier = DefaultNumberValue.of(multiplier);
        return NbrAbstractRateProvider.divide(nbrMultiplier, rateValue);
    }

}
