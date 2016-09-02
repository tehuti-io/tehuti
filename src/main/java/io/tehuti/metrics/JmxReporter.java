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
package io.tehuti.metrics;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.JMException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import io.tehuti.utils.Utils;
import org.apache.log4j.Logger;
import io.tehuti.TehutiException;

/**
 * Register metrics in JMX as dynamic mbeans based on the metric names
 */
public class JmxReporter implements MetricsReporter {

    private static final Logger log = Logger.getLogger(JmxReporter.class);
    private static final Object lock = new Object();
    private String prefix;
    private final Map<String, TehutiMbean> mbeans = new HashMap<String, TehutiMbean>();

    public JmxReporter() {
        this("");
    }

    /**
     * Create a JMX reporter that prefixes all metrics with the given string.
     * The metric full name (prefix + metric name) has to have least one dot
     * separator in order to let reporter work properly.
     *
     * When the prefix ends with dot ("XXX."), it might be interpreted as a
     * stand-alone package name or mbean name if there are less than 2 dot
     * separators in the metric name.
     * @param prefix that appends in front of metrics' own names
     */
    public JmxReporter(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public void configure(Map<String, ?> configs) {
    }

    @Override
    public void init(List<TehutiMetric> metrics) {
        synchronized (lock) {
            for (TehutiMetric metric : metrics)
                addAttribute(metric);
            for (TehutiMbean mbean : mbeans.values())
                register(mbean);
        }
    }

    @Override
    public void metricChange(TehutiMetric metric) {
        synchronized (lock) {
            TehutiMbean mbean = addAttribute(metric);
            register(mbean);
        }
    }

    @Override
    public void addMetric(TehutiMetric metric) {
        synchronized (lock) {
            TehutiMbean mbean = addAttribute(metric);
            register(mbean);
        }
    }

    @Override
    public void removeMetric(TehutiMetric metric) {
        synchronized (lock) {
            String[] names = Utils.splitMetricName(prefix + metric.name());
            String qualifiedName = names[0] + "." + names[1];
            if (mbeans.remove(qualifiedName) != null) {
                unregister(mbeans.get(qualifiedName));
            }
        }
    }

    private TehutiMbean addAttribute(TehutiMetric metric) {
        try {
            String[] names = Utils.splitMetricName(prefix + metric.name());
            String qualifiedName = names[0] + "." + names[1];
            if (!this.mbeans.containsKey(qualifiedName))
                mbeans.put(qualifiedName, new TehutiMbean(names[0], names[1]));
            TehutiMbean mbean = this.mbeans.get(qualifiedName);
            mbean.setAttribute(names[2], metric);
            return mbean;
        } catch (JMException e) {
            throw new TehutiException("Error creating mbean attribute " + metric.name(), e);
        }
    }

    public void close() {
        synchronized (lock) {
            for (TehutiMbean mbean : this.mbeans.values())
                unregister(mbean);
        }
    }

    private void unregister(TehutiMbean mbean) {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        try {
            if (server.isRegistered(mbean.name()))
                server.unregisterMBean(mbean.name());
        } catch (JMException e) {
            throw new TehutiException("Error unregistering mbean", e);
        }
    }

    private void register(TehutiMbean mbean) {
        unregister(mbean);
        try {
            ManagementFactory.getPlatformMBeanServer().registerMBean(mbean, mbean.name());
        } catch (JMException e) {
            throw new TehutiException("Error registering mbean " + mbean.name(), e);
        }
    }

    private static class TehutiMbean implements DynamicMBean {
        private final String beanName;
        private final ObjectName objectName;
        private final Map<String, TehutiMetric> metrics;

        public TehutiMbean(String packageName, String beanName) throws MalformedObjectNameException {
            this.beanName = beanName;
            this.metrics = new HashMap<String, TehutiMetric>();
            this.objectName = new ObjectName(packageName + ":type=" + beanName);
        }

        public ObjectName name() {
            return objectName;
        }

        public synchronized void setAttribute(String name, TehutiMetric metric) {
            this.metrics.put(name, metric);
        }

        @Override
        public synchronized Object getAttribute(String name) throws AttributeNotFoundException, MBeanException, ReflectionException {
            if (this.metrics.containsKey(name))
                return this.metrics.get(name).value();
            else
                throw new AttributeNotFoundException("Could not find attribute " + name);
        }

        @Override
        public synchronized AttributeList getAttributes(String[] names) {
            try {
                AttributeList list = new AttributeList();
                for (String name : names)
                    list.add(new Attribute(name, getAttribute(name)));
                return list;
            } catch (Exception e) {
                log.error("Error getting JMX attribute: ", e);
                return new AttributeList();
            }
        }

        @Override
        public MBeanInfo getMBeanInfo() {
            MBeanAttributeInfo[] attrs = new MBeanAttributeInfo[metrics.size()];
            int i = 0;
            for (Map.Entry<String, TehutiMetric> entry : this.metrics.entrySet()) {
                String attribute = entry.getKey();
                TehutiMetric metric = entry.getValue();
                attrs[i] = new MBeanAttributeInfo(attribute, double.class.getName(), metric.description(), true, false, false);
                i += 1;
            }
            return new MBeanInfo(beanName, "", attrs, null, null, null);
        }

        @Override
        public Object invoke(String name, Object[] params, String[] sig) throws MBeanException, ReflectionException {
            throw new UnsupportedOperationException("Set not allowed.");
        }

        @Override
        public void setAttribute(Attribute attribute) throws AttributeNotFoundException,
                                                     InvalidAttributeValueException,
                                                     MBeanException,
                                                     ReflectionException {
            throw new UnsupportedOperationException("Set not allowed.");
        }

        @Override
        public AttributeList setAttributes(AttributeList list) {
            throw new UnsupportedOperationException("Set not allowed.");
        }

    }

}
