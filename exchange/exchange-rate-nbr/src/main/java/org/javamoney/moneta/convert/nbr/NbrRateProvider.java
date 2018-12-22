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

import javax.money.convert.ProviderContext;
import javax.money.convert.ProviderContextBuilder;
import javax.money.convert.RateType;

public class NbrRateProvider extends NbrAbstractRateProvider {

	public static final String PROVIDER = "NBR";

	private static final String DATA_ID = NbrRateProvider.class.getSimpleName();

	private static final ProviderContext CONTEXT = ProviderContextBuilder
			.of(PROVIDER, RateType.HISTORIC)
			.set("providerDescription", "National Bank of Romania currency rates")
			.set("days", 1)
			.build();

	public NbrRateProvider() {
		super(CONTEXT);
	}

	@Override
	protected String getDataId() {
		return DATA_ID;
	}

}
