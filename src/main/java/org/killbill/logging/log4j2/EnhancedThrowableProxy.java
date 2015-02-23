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
package org.killbill.logging.log4j2;

import java.io.Serializable;
import java.net.URL;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import org.apache.logging.log4j.core.impl.ExtendedClassInfo;
import org.apache.logging.log4j.core.impl.ExtendedStackTraceElement;
//import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.core.util.Loader;
import org.apache.logging.log4j.core.util.Throwables;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.ReflectionUtil;
import org.apache.logging.log4j.util.Strings;

/**
 * @note {@link org.apache.logging.log4j.core.impl.ThrowableProxy} might still
 * be used on various places since it's kind of a Log4J internal class !
 *
 * @author kares
 */
// NOTE: would have extended ThrowableProxy but seeems meaningless at this point
public class EnhancedThrowableProxy implements Serializable {

    private static final long serialVersionUID = 7526536621491366135L;

    static final EnhancedThrowableProxy[] EMPTY_THROWABLE_PROXY_ARRAY = new EnhancedThrowableProxy[0];

    private static class CachedClassInfo {

        final ExtendedClassInfo element;
        final ClassLoader loader;

        CachedClassInfo(final ExtendedClassInfo element, final ClassLoader loader) {
            this.element = element;
            this.loader = loader;
        }

    }

    private final EnhancedThrowableProxy causeProxy;

    private final String localizedMessage;

    private final String message;

    private final String name;

    private final int commonElementCount;
    private final ExtendedStackTraceElement[] extendedStackTrace;

    private EnhancedThrowableProxy[] suppressedProxies;

    private final transient Throwable throwable;

    /**
     * For JSON and XML IO via Jackson.
     */
    @SuppressWarnings("unused")
    private EnhancedThrowableProxy() {
        this.throwable = null;
        this.name = null;
        this.extendedStackTrace = null;
        this.commonElementCount = 0;
        this.causeProxy = null;
        this.message = null;
        this.localizedMessage = null;
        this.suppressedProxies = EMPTY_THROWABLE_PROXY_ARRAY;
    }

    /**
     * Constructs the wrapper for the Throwable that includes packaging data.
     *
     * @param throwable
     *        The Throwable to wrap, must not be null.
     */
    EnhancedThrowableProxy(final Throwable throwable) {
        this.throwable = throwable;
        this.name = throwable.getClass().getName();
        this.message = throwable.getMessage();
        this.localizedMessage = throwable.getLocalizedMessage();

        final Stack<Class<?>> stack = ReflectionUtil.getCurrentStackTrace();

        final StackTraceElement[] stackTrace = throwable.getStackTrace();
        final Map<String, CachedClassInfo> cache = new HashMap<String, CachedClassInfo>(stackTrace.length);

        this.extendedStackTrace = toExtendedStackTrace(stack, cache, stackTrace, stackTrace.length);
        this.commonElementCount = 0;

        final Throwable throwableCause = throwable.getCause();
        this.causeProxy = throwableCause == null ? null : new EnhancedThrowableProxy(throwable, stack, cache, throwableCause);
    }

    /**
     * Constructs the wrapper for a Throwable that is referenced as the cause by another Throwable.
     *
     * @param parent
     *        The Throwable referencing this Throwable.
     * @param stack
     *        The Class stack.
     * @param cache
     *        The cache containing the packaging data.
     * @param cause
     *        The Throwable to wrap.
     */
    private EnhancedThrowableProxy(final Throwable parent, final Stack<Class<?>> stack, final Map<String, CachedClassInfo> cache,
            final Throwable cause) {
        this.throwable = cause;
        this.name = cause.getClass().getName();
        this.message = this.throwable.getMessage();
        this.localizedMessage = this.throwable.getLocalizedMessage();

        final StackTraceElement[] rootTrace = parent.getStackTrace();
        final StackTraceElement[] stackTrace = cause.getStackTrace();

        int rootIndex = rootTrace.length - 1; int stackIndex = stackTrace.length - 1;
        while (rootIndex >= 0 && stackIndex >= 0 && rootTrace[rootIndex].equals(stackTrace[stackIndex])) {
            --rootIndex; --stackIndex;
        }
        this.commonElementCount = stackTrace.length - 1 - stackIndex;
        final int stackLength = stackIndex + 1;

        this.extendedStackTrace = toExtendedStackTrace(stack, cache, stackTrace, stackLength);

        this.causeProxy = cause.getCause() == null ? null : new EnhancedThrowableProxy(parent, stack, cache, cause.getCause());
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (this.getClass() != obj.getClass()) {
            return false;
        }
        final EnhancedThrowableProxy other = (EnhancedThrowableProxy) obj;
        if (this.causeProxy == null) {
            if (other.causeProxy != null) {
                return false;
            }
        } else if (!this.causeProxy.equals(other.causeProxy)) {
            return false;
        }
        if (this.commonElementCount != other.commonElementCount) {
            return false;
        }
        if (this.name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!this.name.equals(other.name)) {
            return false;
        }
        if (!Arrays.equals(this.extendedStackTrace, other.extendedStackTrace)) {
            return false;
        }
        if (!Arrays.equals(this.getSuppressedProxies(), other.getSuppressedProxies())) {
            return false;
        }
        return true;
    }

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    private static void formatCause(final StringBuilder sb, final EnhancedThrowableProxy cause, final Collection<String> ignorePackages) {
        sb.append("Caused by: ").append(cause).append('\n');
        formatElements(sb, cause.commonElementCount, cause.getThrowable().getStackTrace(), cause.extendedStackTrace, ignorePackages);
        if ( cause.causeProxy != null ) formatCause(sb, cause.causeProxy, ignorePackages);
    }

    private static void formatElements(final StringBuilder sb,
        final int commonCount, final StackTraceElement[] causedTrace,
        final ExtendedStackTraceElement[] extStackTrace, final Collection<String> ignorePackages) {

        if (ignorePackages == null || ignorePackages.isEmpty()) {
            for (final ExtendedStackTraceElement element : extStackTrace) {
                formatEntry(element, sb);
            }
        } else {
            int count = 0;
            for (int i = 0; i < extStackTrace.length; ++i) {
                if ( ! ignoreElement(causedTrace[i], ignorePackages) ) {
                    if (count > 0) {
                        if (count == 1) {
                            sb.append("\t....\n");
                        } else {
                            sb.append("\t... suppressed ").append(count).append(" lines\n");
                        }
                        count = 0;
                    }
                    formatEntry(extStackTrace[i], sb);
                } else {
                    ++count;
                }
            }
            if (count > 0) {
                if (count == 1) {
                    sb.append("\t...\n");
                } else {
                    sb.append("\t... suppressed ").append(count).append(" lines\n");
                }
            }
        }
        if (commonCount != 0) {
            sb.append("\t... ").append(commonCount).append(" more").append('\n');
        }
    }

    private static void formatEntry(final ExtendedStackTraceElement extStackTraceElement, final StringBuilder sb) {
        sb.append("\tat ");
        sb.append(extStackTraceElement);
        sb.append('\n');
    }

    /**
     * Formats the specified Throwable.
     *
     * @param sb
     *        StringBuilder to contain the formatted Throwable.
     * @param cause
     *        The Throwable to format.
     */
    static void formatWrapper(final StringBuilder sb, final EnhancedThrowableProxy cause) {
        formatWrapper(sb, cause, null);
    }

    /**
     * Formats the specified Throwable.
     *
     * @param sb
     *        StringBuilder to contain the formatted Throwable.
     * @param cause
     *        The Throwable to format.
     * @param ignorePackages
     *        The List of packages to be suppressed from the trace.
     */
    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    private static void formatWrapper(final StringBuilder sb, final EnhancedThrowableProxy cause, final Collection<String> ignorePackages) {
        final Throwable caused = cause.getCauseProxy() != null ? cause.getCauseProxy().getThrowable() : null;
        if (caused != null) {
            formatWrapper(sb, cause.causeProxy);
            sb.append("Wrapped by: ");
        }
        sb.append(cause).append('\n');
        formatElements(sb, cause.commonElementCount, cause.getThrowable().getStackTrace(), cause.extendedStackTrace, ignorePackages);
    }

    public final EnhancedThrowableProxy getCauseProxy() { return this.causeProxy; }

    /**
     * Format the Throwable that is the cause of this Throwable.
     *
     * @return The formatted Throwable that caused this Throwable.
     */
    @SuppressWarnings("unchecked")
    public final String getCauseStackTraceAsString() {
        return this.getCauseStackTraceAsString(Collections.EMPTY_LIST);
    }

    /**
     * Format the Throwable that is the cause of this Throwable.
     *
     * @param ignorePackages
     *        The List of packages to be suppressed from the trace.
     * @return The formatted Throwable that caused this Throwable.
     */
    final String getCauseStackTraceAsString(final Collection<String> ignorePackages) {
        final StringBuilder sb = new StringBuilder(256);
        if (this.causeProxy != null) {
            formatWrapper(sb, this.causeProxy);
            sb.append("Wrapped by: ");
        }
        sb.append(this.toString());
        sb.append('\n');
        formatElements(sb, 0, this.throwable.getStackTrace(), this.extendedStackTrace, ignorePackages);
        return sb.toString();
    }

    /**
     * Return the number of elements that are being omitted because they are common with the parent Throwable's stack
     * trace.
     *
     * @return The number of elements omitted from the stack trace.
     */
    /*
    public int getCommonElementCount() {
        return this.commonElementCount;
    }

    /**
     * Gets the stack trace including packaging information.
     *
     * @return The stack trace including packaging information.
     */
    /*
    public ExtendedStackTraceElement[] getExtendedStackTrace() {
        return this.extendedStackTrace;
    }

    /**
     * Format the stack trace including packaging information.
     *
     * @return The formatted stack trace including packaging information.
     */
    @SuppressWarnings("unchecked")
    public final String getExtendedStackTraceAsString() {
        return this.getExtendedStackTraceAsString(Collections.EMPTY_LIST);
    }

    /**
     * Format the stack trace including packaging information.
     *
     * @param ignorePackages
     *        List of packages to be ignored in the trace.
     * @return The formatted stack trace including packaging information.
     */
    final String getExtendedStackTraceAsString(final Collection<String> ignorePackages) {
        final StringBuilder sb = new StringBuilder(512).append(this.name);
        final String msg = this.message;
        if ( msg != null ) sb.append(": ").append(msg);
        sb.append('\n');

        formatElements(sb, 0, this.throwable.getStackTrace(), this.extendedStackTrace, ignorePackages);

        if ( this.causeProxy != null ) formatCause(sb, this.causeProxy, ignorePackages);

        return sb.toString();
    }

    /*
    public final String getLocalizedMessage() {
        return this.localizedMessage;
    }

    public final String getMessage() {
        return this.message;
    }

    public final String getName() {
        return this.name;
    }

    public StackTraceElement[] getStackTrace() {
        return this.throwable == null ? null : this.throwable.getStackTrace();
    }

    /**
     * Gets proxies for suppressed exceptions.
     *
     * @return proxies for suppressed exceptions.
     */
    EnhancedThrowableProxy[] getSuppressedProxies() {
        if ( suppressedProxies != null ) return suppressedProxies;
        return suppressedProxies = toSuppressedProxies(throwable);
    }

    /**
     * Format the suppressed Throwables.
     *
     * @return The formatted suppressed Throwables.
     */
    public String getSuppressedStackTrace() {
        final EnhancedThrowableProxy[] suppressed = this.getSuppressedProxies();
        if (suppressed == null || suppressed.length == 0) return Strings.EMPTY;

        final StringBuilder sb = new StringBuilder("Suppressed Stack Trace Elements:\n");
        for (final EnhancedThrowableProxy proxy : suppressed) {
            sb.append( proxy.getExtendedStackTraceAsString() );
        }
        return sb.toString();
    }

    /**
     * The throwable or null if this object is deserialized from XML or JSON.
     *
     * @return The throwable or null if this object is deserialized from XML or JSON.
     */
    public final Throwable getThrowable() {
        return this.throwable;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (this.causeProxy == null ? 0 : this.causeProxy.hashCode());
        result = prime * result + this.commonElementCount;
        result = prime * result + (this.extendedStackTrace == null ? 0 : Arrays.hashCode(this.extendedStackTrace));
        // result = prime * result + ( Arrays.hashCode(getSuppressedProxies()) );
        result = prime * result + (this.name == null ? 0 : this.name.hashCode());
        return result;
    }

    private static boolean ignoreElement(final StackTraceElement element, final Collection<String> ignorePackages) {
        final String className = element.getClassName();
        for ( final String pkg : ignorePackages ) {
            if ( className.startsWith(pkg) ) return true;
        }
        return false;
    }

    /**
     * Loads classes not located via Reflection.getCallerClass.
     *
     * @param lastLoader
     *        The ClassLoader that loaded the Class that called this Class.
     * @param className
     *        The name of the Class.
     * @return The Class object for the Class or null if it could not be located.
     */
    private static Class<?> loadClass(final ClassLoader lastLoader, final String className) {
        // XXX: this is overly complicated
        Class<?> clazz;
        if (lastLoader != null) {
            try {
                clazz = Loader.initializeClass(className, lastLoader);
                if (clazz != null) {
                    return clazz;
                }
            }
            catch (final Throwable ignore) {
                // Ignore exception.
            }
        }
        try {
            clazz = Loader.loadClass(className);
        }
        catch (final ClassNotFoundException ignored) {
            final ClassLoader thisLoader = EnhancedThrowableProxy.class.getClassLoader();
            try {
                clazz = Loader.initializeClass(className, thisLoader);
            }
            catch (final ClassNotFoundException ignore) {
                return null;
            }
        }
        return clazz;
    }

    /**
     * Construct the ClassInfoCache from the Class's information.
     *
     * @param stackTraceElement
     *        The stack trace element
     * @param callerClass
     *        The Class.
     * @param exact
     *        True if the class was obtained via Reflection.getCallerClass.
     *
     * @return The ClassInfoCache.
     */
    private static CachedClassInfo toCacheEntry(final Class<?> callerClass, final boolean exact) {
        String location = "?";
        String version = "?";
        ClassLoader lastLoader = null;
        if (callerClass != null) {
            try {
                final CodeSource source = callerClass.getProtectionDomain().getCodeSource();
                if (source != null) {
                    final URL locationURL = source.getLocation();
                    if (locationURL != null) {
                        final String str = locationURL.toString().replace('\\', '/');
                        int index = str.lastIndexOf('/');
                        if (index >= 0 && index == str.length() - 1) {
                            index = str.lastIndexOf('/', index - 1);
                            location = str.substring(index + 1);
                        } else {
                            location = str.substring(index + 1);
                        }
                    }
                }
            }
            catch (final Exception ex) {
                // Ignore the exception.
            }
            final Package pkg = callerClass.getPackage();
            if (pkg != null) {
                final String ver = pkg.getImplementationVersion();
                if (ver != null) {
                    version = ver;
                }
            }
            lastLoader = callerClass.getClassLoader();
        }
        return new CachedClassInfo(new ExtendedClassInfo(exact, location, version), lastLoader);
    }

    /**
     * Resolve all the stack entries in this stack trace that are not common with the parent.
     *
     * @param stack
     *        The callers Class stack.
     * @param cache
     *        The cache of ClassInfoCache objects.
     * @param rootTrace
     *        The first stack trace resolve or null.
     * @param stackTrace
     *        The stack trace being resolved.
     * @return The StackTracePackageElement array.
     */
    private static ExtendedStackTraceElement[] toExtendedStackTrace(final Stack<Class<?>> stack,
            final Map<String, CachedClassInfo> cache,
            final StackTraceElement[] stackTrace, final int stackLength) {

        final ExtendedStackTraceElement[] extStackTrace = new ExtendedStackTraceElement[stackLength];

        Class<?> clazz = stack.isEmpty() ? null : stack.peek();
        ClassLoader lastLoader = null;

        for ( int i = stackLength - 1; i >= 0; --i ) {
            final StackTraceElement stackTraceElement = stackTrace[i];
            final String className = stackTraceElement.getClassName();
            // The stack returned from getCurrentStack may be missing entries for java.lang.reflect.Method.invoke()
            // and its implementation. The Throwable might also contain stack entries that are no longer
            // present as those methods have returned.
            ExtendedClassInfo extClassInfo;
            if (clazz != null && className.equals(clazz.getName())) {
                final CachedClassInfo entry = toCacheEntry(clazz, true); // exact = true
                extClassInfo = entry.element;
                lastLoader = entry.loader;
                stack.pop();
                clazz = stack.isEmpty() ? null : stack.peek();
            }
            else {
                CachedClassInfo entry = cache.get(className);
                if ( entry != null ) {
                    extClassInfo = entry.element;
                    if ( entry.loader != null ) lastLoader = entry.loader;
                }
                else {
                    Class<?> klass = loadClass(lastLoader, className);
                    entry = toCacheEntry(klass, false); // exact = false
                    extClassInfo = entry.element;
                    cache.put(className, entry);
                    if ( entry.loader != null ) lastLoader = entry.loader;
                }
            }
            extStackTrace[i] = new ExtendedStackTraceElement(stackTraceElement, extClassInfo);
        }
        return extStackTrace;
    }

    @Override
    public String toString() {
        final String msg = this.message;
        return msg != null ? ( this.name + ": " + msg ) : this.name;
    }

    private static EnhancedThrowableProxy[] toSuppressedProxies(final Throwable thrown) {
        try {
            @SuppressWarnings("deprecation")
            final Throwable[] suppressed = Throwables.getSuppressed(thrown);
            if ( suppressed == null ) return EMPTY_THROWABLE_PROXY_ARRAY;

            final EnhancedThrowableProxy[] proxies = new EnhancedThrowableProxy[suppressed.length];
            for (int i = 0; i < suppressed.length; i++) {
                proxies[i] = new EnhancedThrowableProxy( suppressed[i] );
            }
            return proxies;
        }
        catch (final RuntimeException e) {
            StatusLogger.getLogger().error(e);
        }
        return null;
    }
}
