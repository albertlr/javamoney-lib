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
import org.javamoney.moneta.spi.AbstractRateProvider;
import org.javamoney.moneta.spi.DefaultNumberValue;
import org.javamoney.moneta.spi.LoaderService;
import org.javamoney.moneta.spi.LoaderService.LoaderListener;
import org.javamoney.moneta.spi.MonetaryConfig;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryException;
import javax.money.NumberValue;
import javax.money.convert.ConversionContext;
import javax.money.convert.ConversionQuery;
import javax.money.convert.CurrencyConversionException;
import javax.money.convert.ExchangeRate;
import javax.money.convert.ProviderContext;
import javax.money.convert.RateType;
import javax.money.spi.Bootstrap;
import java.io.InputStream;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

/**
 * Base of National Bank of Romania provider implementation
 *
 * @author L&aacute;szl&oacute;-R&oacute;bert Albert (albertlaszlorobert@gmail.com)
 */
public abstract class NbrAbstractRateProvider extends AbstractRateProvider implements LoaderListener {

    private static final Logger log = Logger.getLogger(NbrAbstractRateProvider.class.getName());

    private static final String BASE_CURRENCY_CODE = "RON";
    private static final String DIGIT_FRACTION_KEY = "nbr.digit.fraction";

    static final CurrencyUnit BASE_CURRENCY = Monetary.getCurrency(BASE_CURRENCY_CODE);

    final Map<LocalDate, Map<String, ExchangeRate>> rates = new ConcurrentHashMap<>();
    private final ProviderContext context;
    protected volatile String loadState;
    protected final CountDownLatch loadLock = new CountDownLatch(1);

    /**
     * A protected helper method to multiply 2 {@link NumberValue} types.
     * If either of the values is <code>null</code> an {@link ArithmeticException} is thrown.
     *
     * @param multiplicand the first value to be multiplied
     * @param multiplier   the second value to be multiplied
     * @return the result of the multiplication as {@link NumberValue}
     */
    public static NumberValue multiply(NumberValue multiplicand, NumberValue multiplier) {
        return AbstractRateProvider.multiply(multiplicand, multiplier);
    }

    /**
     * A protected helper method to divide 2 {@link NumberValue} types.
     * If either of the values is <code>null</code> an {@link ArithmeticException} is thrown.
     *
     * @param dividend the first value to be divided
     * @param divisor  the value to be divided by
     * @return the result of the division as {@link NumberValue}
     */
    public static NumberValue divide(NumberValue dividend, NumberValue divisor) {
        return AbstractRateProvider.divide(dividend, divisor);
    }

    NbrAbstractRateProvider(ProviderContext context) {
        super(context);
        this.context = context;
        final LoaderService loader = Bootstrap.getService(LoaderService.class);
        loader.addLoaderListener(this, getDataId());
        loader.loadDataAsync(getDataId());
    }

    protected abstract String getDataId();

    @Override
    public void newDataLoaded(final String resourceId, final InputStream is) {
        final int oldSize = this.rates.size();
        try {
            final NbrRateReadingHandler parser = new NbrRateReadingHandler(rates, getContext());
            parser.parse(is);
            int newSize = this.rates.size();
            loadState = "Loaded " + resourceId + " exchange rates for days:" + (newSize - oldSize);
            log.info(loadState);
        } catch (Exception exception) {
            loadState = "Last Error during data load: " + exception.getMessage();
            log.log(Level.FINE, "Error during data load.", exception);
        } finally {
            loadLock.countDown();
        }
    }

    @Override
    public ExchangeRate getExchangeRate(ConversionQuery conversionQuery) {
        requireNonNull(conversionQuery);
        try {
            if (loadLock.await(30, TimeUnit.SECONDS)) {
                if (rates.isEmpty()) {
                    return null;
                }
                RateResult result = findExchangeRate(conversionQuery);

                ExchangeRateBuilder builder = getBuilder(conversionQuery);
                ExchangeRate sourceRate = result.targets.get(conversionQuery.getBaseCurrency().getCurrencyCode());
                ExchangeRate target = result.targets.get(conversionQuery.getCurrency().getCurrencyCode());
                return createExchangeRate(conversionQuery, builder, sourceRate, target);
            } else {
                throw new MonetaryException("Failed to load currency conversion data: " + loadState);
            }
        } catch (InterruptedException exception) {
            throw new MonetaryException("Failed to load currency conversion data: Load task has been interrupted.", exception);
        }
    }


    private RateResult findExchangeRate(ConversionQuery conversionQuery) {
        LocalDate[] dates = getQueryDates(conversionQuery);
        if (dates == null) {
            Comparator<LocalDate> comparator = Comparator.naturalOrder();
            LocalDate date = rates.keySet().stream()
                    .sorted(comparator.reversed())
                    .findFirst()
                    .orElseThrow(() -> new MonetaryException("There is not more recent exchange rate to  rate on ECBRateProvider."));
            return new RateResult(date, rates.get(date));
        } else {
            for (LocalDate localDate : dates) {
                Map<String, ExchangeRate> targets = this.rates.get(localDate);

                if (nonNull(targets)) {
                    return new RateResult(localDate, targets);
                }
            }
            String datesOnErros = Stream.of(dates).map(date -> date.format(DateTimeFormatter.ISO_LOCAL_DATE)).collect(Collectors.joining(","));
            throw new MonetaryException("There is not exchange on day " + datesOnErros + " to rate to  rate on ECBRateProvider.");
        }

    }

    private ExchangeRate createExchangeRate(ConversionQuery query,
                                            ExchangeRateBuilder builder, ExchangeRate sourceRate,
                                            ExchangeRate target) {

        if (areBothBaseCurrencies(query)) {
            builder.setFactor(DefaultNumberValue.ONE);
            return builder.build();
        } else if (BASE_CURRENCY_CODE.equals(query.getCurrency()
                .getCurrencyCode())) {
            if (Objects.isNull(sourceRate)) {
                return null;
            }

            return reverse(sourceRate);
        } else if (BASE_CURRENCY_CODE.equals(query.getBaseCurrency()
                .getCurrencyCode())) {
            return target;
        } else {

            ExchangeRate rate1 = getExchangeRate(query.toBuilder()
                    .setTermCurrency(Monetary.getCurrency(BASE_CURRENCY_CODE))
                    .build());

            ExchangeRate rate2 = getExchangeRate(query.toBuilder()
                    .setBaseCurrency(Monetary.getCurrency(BASE_CURRENCY_CODE))
                    .setTermCurrency(query.getCurrency()).build());

            if (nonNull(rate1) && nonNull(rate2)) {
                builder.setFactor(multiply(rate1.getFactor(), rate2.getFactor()));
                builder.setRateChain(rate1, rate2);
                return builder.build();
            }

            throw new CurrencyConversionException(query.getBaseCurrency(),
                    query.getCurrency(), sourceRate.getContext());
        }
    }

    private boolean areBothBaseCurrencies(ConversionQuery query) {
        return BASE_CURRENCY_CODE.equals(query.getBaseCurrency().getCurrencyCode()) &&
                BASE_CURRENCY_CODE.equals(query.getCurrency().getCurrencyCode());
    }


    private ExchangeRateBuilder getBuilder(ConversionQuery query) {
        return new ExchangeRateBuilder(getExchangeContext(DIGIT_FRACTION_KEY))
                .setBase(query.getBaseCurrency())
                .setTerm(query.getCurrency());
    }

    private ExchangeRate reverse(ExchangeRate rate) {
        if (Objects.isNull(rate)) {
            throw new IllegalArgumentException("Rate null is not reversible.");
        }

        return new ExchangeRateBuilder(rate)
                .setRate(rate)
                .setBase(rate.getCurrency())
                .setTerm(rate.getBaseCurrency())
                .setContext(getExchangeContext(DIGIT_FRACTION_KEY))
                .setFactor(divide(DefaultNumberValue.ONE, rate.getFactor(), MathContext.DECIMAL64))
                .build();
    }

    @Override
    public String toString() {
        return getClass().getName() + '(' + " context: " + context + ')';
    }

    private class RateResult {

        private final LocalDate date;
        private final Map<String, ExchangeRate> targets;

        RateResult(LocalDate date, Map<String, ExchangeRate> targets) {
            this.date = date;
            this.targets = targets;
        }
    }

    // Patch for post 1.0 API  in Moneta
    private static final String KEY_SCALE = "exchangeRateScale";

    @Override
    protected int getScale(String key) {
        String string = MonetaryConfig.getConfig()
                .getOrDefault(key, "-1");
        if (string.isEmpty()) {
            return -1;
        } else {
            try {
                return Integer.valueOf(string);
            } catch (NumberFormatException exception) {
                return -1;
            }
        }
    }

    @Override
    protected ConversionContext getExchangeContext(String key) {
        int scale = getScale(key);
        if (scale < 0) {
            return ConversionContext.of(this.context.getProviderName(), RateType.HISTORIC);
        } else {
            return ConversionContext.of(this.context.getProviderName(), RateType.HISTORIC)
                    .toBuilder()
                    .set(KEY_SCALE, scale)
                    .build();
        }
    }

    @Override
    protected LocalDate[] getQueryDates(ConversionQuery query) {
        if (nonNull(query.get(LocalDate.class)) || nonNull(query.get(LocalDateTime.class))) {
            LocalDate localDate = Optional.ofNullable(query.get(LocalDate.class))
                    .orElseGet(() -> query.get(LocalDateTime.class).toLocalDate());
            return new LocalDate[] {localDate};
        } else if (nonNull(query.get(LocalDate[].class))) {
            return query.get(LocalDate[].class);
        }
        // Return latest dates in data
        return rates.keySet().stream()
                .sorted(Comparator.reverseOrder())
                .findFirst()
                .map(date -> new LocalDate[] {date})
                .orElse(null);
    }
}
