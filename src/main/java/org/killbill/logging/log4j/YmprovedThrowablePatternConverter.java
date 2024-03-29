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

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.PatternConverter;
import org.apache.logging.log4j.core.pattern.ThrowablePatternConverter;
import org.apache.logging.log4j.core.util.Constants;

/**
 * An "improved" version of the built-in (default) extended throwable converter
 * {@link org.apache.logging.log4j.core.pattern.ExtendedThrowablePatternConverter}
 * ... which gets installed by default for a PatternLayout to handle all exception's
 * stack-traces  if there's no other converter handling throwables (such as this one).
 *
 * @see org.apache.logging.log4j.core.pattern.PatternParser#parse(String, boolean, boolean)
 *
 * @author kares
 */
@Plugin(name = "YmprovedThrowablePatternConverter", category = PatternConverter.CATEGORY)
@ConverterKeys({ "yEx", "yThrowable", "yException" })
// NOTE: "mirroring" ExtendedThrowable's { "xEx", "xThrowable", "xException" }
public class YmprovedThrowablePatternConverter extends ThrowablePatternConverter {

    protected YmprovedThrowablePatternConverter(final String[] options) {
        super("YmprovedThrowable", "throwable", options);
    }

    public static YmprovedThrowablePatternConverter newInstance(final String[] options) {
        return new YmprovedThrowablePatternConverter(options);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void format(final LogEvent event, final StringBuilder toAppendTo) {
        final Throwable thrown = event.getThrown();
        if ( thrown != null && options.anyLines() ) {
            EnhancedThrowableProxy proxy = null;
            if ( event instanceof Log4jLogEvent ) {
                proxy = new EnhancedThrowableProxy(thrown);
            }

            if ( proxy == null ) {
                super.format(event, toAppendTo); return;
            }

            final CharSequence trace = proxy.getExtendedStackTraceAsString(options.getPackages());
            final int len = toAppendTo.length();
            if ( len > 0 && ! Character.isWhitespace( toAppendTo.charAt(len - 1) ) ) {
                toAppendTo.append(' ');
            }
            if ( ! options.allLines() || ! Constants.LINE_SEPARATOR.equals( options.getSeparator() ) ) {
                final String[] stackTrace = trace.toString().split( Constants.LINE_SEPARATOR );
                final int limit = options.minLines(stackTrace.length) - 1;
                final String separator = options.getSeparator();
                for ( int i = 0; i <= limit; ++i ) {
                    toAppendTo.append( stackTrace[i] );
                    if ( i < limit ) toAppendTo.append( separator );
                }
            }
            else {
                toAppendTo.append(trace);
            }
        }
    }

}