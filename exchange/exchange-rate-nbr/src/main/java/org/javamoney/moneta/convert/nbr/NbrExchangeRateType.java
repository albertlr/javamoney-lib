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

import javax.money.convert.ExchangeRateProviderSupplier;

public enum NbrExchangeRateType implements ExchangeRateProviderSupplier {
	NBR("NBR", "Exchange rate to the Yahoo finance.");

	private final String type;
	private final String description;

	private NbrExchangeRateType(String type, String description) {
		this.type = type;
		this.description = description;
	}

	@Override
	public String get() {
		return type;
	}

	public String getDescription() {
		return description;
	}
}
