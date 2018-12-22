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
import javax.activation.DataSource;

class NbrRateReadingHandler {

    private final Map<LocalDate, Map<String, ExchangeRate>> excangeRates;

    private final ProviderContext context;

    public NbrRateReadingHandler(final Map<LocalDate, Map<String, ExchangeRate>> excangeRates,
                                 final ProviderContext context) {
        this.excangeRates = excangeRates;
        this.context = context;
    }

    void parse(final InputStream stream) throws JAXBException, ParseException {
        final String nbrDtoPackage = DataSet.class.getPackageName();
        final Unmarshaller unmarshaller = JAXBContext.newInstance(nbrDtoPackage).createUnmarshaller();
        DataSet dataSet = (DataSet) unmarshaller.unmarshal(stream);

        dataSet.getBody().getCube().forEach(
                cube -> {
                    LocalDate date = cube.getDate().toGregorianCalendar().toZonedDateTime().toLocalDate();
                    cube.getRate().stream()
                            // only for currencies that are recognised by our Monetary system
                            .filter(rate -> Monetary.getCurrency(rate.getCurrency()) != null)
                            .forEach(
                                    rate -> {
                                        addRate(date, rate);
                                    }
                            );
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
        System.out.printf("add rate %s %s = %s %s :: %s %n", (rateInfo.getMultiplier() == null ? 1 : rateInfo.getMultiplier()),
                rateInfo.getCurrency(), exchangeRate.getFactor(), NbrAbstractRateProvider.BASE_CURRENCY, exchangeRate);
        rateMap.put(rateInfo.getCurrency(), exchangeRate);
    }

    private NumberValue computeFactor(BigInteger multiplier, BigDecimal value) {
        NumberValue rateValue = DefaultNumberValue.of(value);
        if (multiplier == null) {
            return rateValue;
        } else {
            NumberValue nvmultiplier = DefaultNumberValue.of(multiplier);
            return NbrAbstractRateProvider.multiply(rateValue, nvmultiplier);
        }
    }
}
