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
package io.tehuti.utils;

/**
 * An interface abstracting the clock to use in unit testing classes that make use of clock time
 */
public interface Time {

    public final static long HOURS_PER_DAY = 24;
    public final static long MINUTES_PER_HOUR = 60;
    public final static long SECONDS_PER_MINUTE = 60;
    public final static long MS_PER_SECOND = 1000;
    public final static long US_PER_MS = 1000;
    public final static long NS_PER_US = 1000;
    public final static long NS_PER_MS = US_PER_MS * NS_PER_US;
    public final static long US_PER_SECOND = US_PER_MS * MS_PER_SECOND;
    public final static long NS_PER_SECOND = NS_PER_US * US_PER_SECOND;
    public final static long SECONDS_PER_HOUR = SECONDS_PER_MINUTE * MINUTES_PER_HOUR;
    public final static long SECONDS_PER_DAY = HOURS_PER_DAY * SECONDS_PER_HOUR;
    public final static long MS_PER_HOUR = SECONDS_PER_HOUR * MS_PER_SECOND;
    public final static long MS_PER_DAY = SECONDS_PER_DAY * MS_PER_SECOND;
    public final static long MS_PER_MINUTE = MS_PER_SECOND * SECONDS_PER_MINUTE;

    /**
     * The current time in milliseconds
     */
    public long milliseconds();

    /**
     * The current time in nanoseconds
     */
    public long nanoseconds();

    /**
     * Sleep for the given number of milliseconds
     */
    public void sleep(long ms);

}
