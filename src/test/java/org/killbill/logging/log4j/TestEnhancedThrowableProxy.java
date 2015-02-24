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

import java.net.URL;
import java.net.URLClassLoader;

import static org.testng.Assert.*;
import org.testng.annotations.*;

/**
 * @author kares
 */
public class TestEnhancedThrowableProxy {

    @SuppressWarnings("serial")
    private static class MutableStackTraceException extends RuntimeException {

        MutableStackTraceException(String message) {
            super(message);
        }

        StackTraceElement[] stackTrace;

        @Override
        public StackTraceElement[] getStackTrace() {
            if ( stackTrace != null ) return stackTrace;
            return super.getStackTrace();
        }

    }

    @Test
    public void getExtendedStackTrace() {
        getExtendedStackTraceImpl("getExtendedStackTrace");
    }

    private void getExtendedStackTraceImpl(final String testMethod) {
        MutableStackTraceException e = new MutableStackTraceException(testMethod);

        StackTraceElement element;

        String className = "rubyjit.ActiveRecord::ConnectionAdapters::JdbcAdapter$$disconnect!_889690dc23468cf82b54ac9674be8ed625f8dbd11857039812";
        String methodName = "__file__(ActiveRecord::ConnectionAdapters::JdbcAdapter$$disconnect!_889690dc23468cf82b54ac9674be8ed625f8dbd11857039812, ThreadContext, IRubyObject, Block)";
        String fileName = null;

        element = new StackTraceElement(className, methodName, fileName, 1);

        e.stackTrace = new StackTraceElement[] { element };

        CharSequence extStackTrace = new EnhancedThrowableProxy(e).getExtendedStackTraceAsString();

        System.out.println( extStackTrace );

        className = "rubyjit.ActiveRecord::ConnectionAdapters::JdbcAdapter$$disconnect!_889690dc23468cf82b54ac9674be8ed625f8dbd11857039812";
        methodName = "__file__(ActiveRecord::ConnectionAdapters::JdbcAdapter$$disconnect!_889690dc23468cf82b54ac9674be8ed625f8dbd11857039812, ThreadContext, IRubyObject, Block)";
        fileName = "/var/tmp/bundles/plugins/ruby/killbill-litle/1.10.2/gems/gems/activerecord-jdbc-adapter-1.3.13/lib/arjdbc/jdbc/adapter.rb";

        element = new StackTraceElement(className, methodName, fileName, 2);

        e.stackTrace = new StackTraceElement[] { element };

        extStackTrace = new EnhancedThrowableProxy(e).getExtendedStackTraceAsString();

        System.out.println( extStackTrace );

        className = "org.jruby.RubyKernel$INVOKER$s$send19";
        methodName = "call(ThreadContext, IRubyObject, RubyModule, String, IRubyObject, Block)";
        fileName = "RubyKernel$INVOKER$s$send19.gen";

        element = new StackTraceElement(className, methodName, fileName, 3);

        e.stackTrace = new StackTraceElement[] { element };

        extStackTrace = new EnhancedThrowableProxy(e).getExtendedStackTraceAsString();

        System.out.println( extStackTrace );

        className = "MonitorMixin::ConditionVariable";
        methodName = "signal";
        fileName = "classpath:/META-INF/jruby.home/lib/ruby/1.9/monitor.rb";

        element = new StackTraceElement(className, methodName, fileName, 139);

        e.stackTrace = new StackTraceElement[] { element };

        extStackTrace = new EnhancedThrowableProxy(e).getExtendedStackTraceAsString();

        System.out.println( extStackTrace );
    }

    @Test
    public void getExtendedStackTraceWithThrowingClassLoader() {
        final ClassLoader tomcatLoader = new TomcatLikeClassLoader();
        //Thread.currentThread().setContextClassLoader( new TomcatLikeClassLoader() );

        final EnhancedThrowableProxy.LoaderDelegate prevDelegate = EnhancedThrowableProxy.loaderDelegate;
        EnhancedThrowableProxy.loaderDelegate = new EnhancedThrowableProxy.LoaderDelegate() {

            @Override
            Class<?> loadClass(final String name) throws ClassNotFoundException, RuntimeException {
                return Class.forName(name, false, tomcatLoader);
            }

        };

        try {
            getExtendedStackTraceImpl("getExtendedStackTraceWithThrowingClassLoader");

            String className = "invalid$Klass";
            String methodName = "foo()";
            String fileName = null;

            StackTraceElement element = new StackTraceElement(className, methodName, fileName, 3);

            MutableStackTraceException e = new MutableStackTraceException("getExtendedStackTraceWithThrowingClassLoader");
            e.stackTrace = new StackTraceElement[] { element };

            CharSequence extStackTrace = new EnhancedThrowableProxy(e).getExtendedStackTraceAsString();

            System.out.println( extStackTrace );
        }
        finally {
            EnhancedThrowableProxy.loaderDelegate = prevDelegate;
            //Thread.currentThread().setContextClassLoader( prevLoader );
        }
    }

    private static class TomcatLikeClassLoader extends URLClassLoader {

        TomcatLikeClassLoader() {
            this( TestEnhancedThrowableProxy.class.getClassLoader() );
        }

        TomcatLikeClassLoader(ClassLoader parent) {
            this(new URL[0], parent);
        }

        TomcatLikeClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        public synchronized Class<?> loadClass(String name) throws ClassNotFoundException {
            if ( name.startsWith("invalid$") ) {
                throw new IllegalArgumentException("name: " + name);
            }
            return super.loadClass(name);
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
            if ( name.startsWith("invalid$") ) {
                throw new IllegalArgumentException("name: " + name);
            }
            return super.loadClass(name, resolve);
        }

    }

    @Test
    public void isValidClassName() {
        assertTrue( EnhancedThrowableProxy.isValidClassName("ferko.Suska") );
        assertTrue( EnhancedThrowableProxy.isValidClassName("Ferko$Suska") );
        assertTrue( EnhancedThrowableProxy.isValidClassName("org.jruby.RubyKernel$INVOKER$s$send19") );
        assertFalse( EnhancedThrowableProxy.isValidClassName("MonitorMixin::ConditionVariable") );
    }

}
