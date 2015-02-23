/*
 * Copyright 2015 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.killbill.logging.log4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.status.StatusLogger;

import static org.testng.Assert.*;
import org.testng.annotations.*;

/**
 * @author kares
 */
public class TestLog4j {

    @SuppressWarnings("serial")
    private static class SimpleException extends RuntimeException {

        SimpleException(String message) {
            super(message);
        }

        int callCount;

        @Override
        public StackTraceElement[] getStackTrace() {
            callCount++;
            //System.out.println(this + " getStackTrace() from: ");
            //for ( StackTraceElement el : Thread.currentThread().getStackTrace() ) {
            //    System.out.println("  " + el.toString());
            //}
            return super.getStackTrace();
        }

    }

    @Test
    public void getStackTraceCalledOnce() throws IOException {
        configureShared();

        SimpleException throwable = new SimpleException("getStackTraceCalledOnce");
        getLogger(TestLog4j.class).info("exception", throwable);
        assertEquals(1, throwable.callCount);
    }

    @SuppressWarnings("serial")
    private static class CallerMemoException extends RuntimeException {

        CallerMemoException(String message) {
            super(message);
        }

        transient StackTraceElement[] callerStackTrace;

        @Override
        public String getMessage() {
            callerStackTrace = Thread.currentThread().getStackTrace();

            return super.getMessage();
        }

    }

    @Test
    public void usingEnhancedThrowableProxy() throws IOException {
        configureShared();

        CallerMemoException throwable = new CallerMemoException("usingEnhancedThrowableProxy");
        getLogger(TestLog4j.class).info("exception", throwable);

        assertNotNull(throwable.callerStackTrace);

        final StackTraceElement[] stackTrace = throwable.callerStackTrace;
        boolean foundEnhancedThrowableProxy = false;
        boolean foundThrowableProxy = false;
        for ( StackTraceElement element : stackTrace ) {
            if ( element.getClassName().indexOf( EnhancedThrowableProxy.class.getName() ) >= 0 ) {
                foundEnhancedThrowableProxy = true; //break;
            }
            if ( element.getClassName().indexOf( ThrowableProxy.class.getName() ) >= 0 ) {
                foundThrowableProxy = true; //break;
            }
        }

        assertTrue(foundEnhancedThrowableProxy, "EnhancedThrowableProxy not found in: " + Arrays.toString(stackTrace));
        assertFalse(foundThrowableProxy, "EnhancedThrowableProxy not found in: " + Arrays.toString(stackTrace));
    }

    private static Logger getLogger(final String name) {
        return LogManager.getLogger(name);
    }

    private static Logger getLogger(final Class<?> name) {
        return LogManager.getLogger(name);
    }

    private static boolean sharedConfiguration;

    static void configureShared() throws IOException {
        if ( sharedConfiguration ) return;

        configure( sharedConfiguration() );

        sharedConfiguration = true;
    }

    static void configure(final InputStream configStream) throws IOException {
        sharedConfiguration = false;

        System.setProperty("log4j2.disable.jmx", "true");
        StatusLogger status = StatusLogger.getLogger();
        status.clear(); // remove old listeners that may prevent status output
        //status.setLevel(Level.DEBUG);

        ConfigurationSource configurationSource = new ConfigurationSource(configStream);
        Configurator.initialize(classLoader(), configurationSource);
    }

    static InputStream sharedConfiguration() {
        return classLoader().getResourceAsStream("log4j2-test.xml");
    }

    private static ClassLoader classLoader() { return TestLog4j.class.getClassLoader(); }

}
