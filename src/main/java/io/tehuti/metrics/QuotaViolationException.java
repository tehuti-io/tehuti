/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.tehuti.metrics;

import io.tehuti.TehutiException;

/**
 * Thrown when a sensor records a value that causes a metric to go outside the bounds configured as its quota
 */
public class QuotaViolationException extends TehutiException {

    private static final long serialVersionUID = 2L;

    private final double value;

    public QuotaViolationException(String m, double value) {
        super(m + ", current value is: " + value);
        this.value = value;
    }

    /**
     * @return The value of the {@link TehutiMetric} at the time it violated its {@link Quota}
     */
    public double getValue() {
        return this.value;
    }

}
