/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.tehuti.utils;

public class Utils {
    /**
     * Check that the parameter t is not null
     * 
     * @param t The object to check
     * @return t if it isn't null
     * @throws NullPointerException if t is null.
     */
    public static <T> T notNull(T t) {
        if (t == null)
            throw new NullPointerException();
        else
            return t;
    }

    /**
     * This function splits a metric name into a triplet of {package name, bean name,
     * attribute name} by using the last two dots in the name to split on.
     * If there is only one dot in the name, this function returns an empty package
     * name for the first element of the triplet.
     * @param  name
     * @return String triplet
     * @throws IllegalArgumentException if there is dot in the name.
     */
    public static String[] splitMetricName(String name) {
        int attributeStart = name.lastIndexOf('.');
        if (attributeStart < 0)
            throw new IllegalArgumentException("No MBean name in metric name: " + name);
        String attributeName = name.substring(attributeStart + 1, name.length());
        String remainder = name.substring(0, attributeStart);
        int beanStart = remainder.lastIndexOf('.');
        if (beanStart < 0)
            return new String[] { "", remainder, attributeName };
        String packageName = remainder.substring(0, beanStart);
        String beanName = remainder.substring(beanStart + 1, remainder.length());
        return new String[] { packageName, beanName, attributeName };
    }
}
