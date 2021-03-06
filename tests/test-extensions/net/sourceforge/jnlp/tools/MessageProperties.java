/* MessageProperties.java
   Copyright (C) 2013 Red Hat, Inc.

This file is part of IcedTea.

IcedTea is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License as published by
the Free Software Foundation, version 2.

IcedTea is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License
along with IcedTea; see the file COPYING.  If not, write to
the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
02110-1301 USA.

Linking this library statically or dynamically with other modules is
making a combined work based on this library.  Thus, the terms and
conditions of the GNU General Public License cover the whole
combination.

As a special exception, the copyright holders of this library give you
permission to link this library with independent modules to produce an
executable, regardless of the license terms of these independent
modules, and to copy and distribute the resulting executable under
terms of your choice, provided that you also meet, for each linked
independent module, the terms and conditions of the license of that
module.  An independent module is a module which is not derived from
or based on this library.  If you modify this library, you may extend
this exception to your version of the library, but you are not
obligated to do so.  If you do not wish to do so, delete this
exception statement from your version.
*/

package net.sourceforge.jnlp.tools;

import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.io.IOException;

public class MessageProperties {

    public enum SupportedLanguage {
        en("en"), cs("cs"), de("de"), pl("pl");
        private Locale locale;

        private SupportedLanguage(String lang) {
            this.locale = new Locale(lang);
        }

        public Locale getLocale() {
            return this.locale;
        }
    }

    private static final String resourcePath = "net/sourceforge/jnlp/resources/Messages";

    /**
     * Same as {@link #getMessage(Locale, String)}, using the current default Locale
     */
    public static String getMessage(String key) {
        return getMessage(Locale.getDefault(), key);
    }

    /**
     * Retrieve a localized message from resource file
     * @param locale the localization of Messages.properties to search
     * @param key
     * @return the message corresponding to the given key from the specified localization
     * @throws IOException if the specified Messages localization is unavailable
     */
    public static String getMessage(Locale locale, String key) {
        ResourceBundle bundle = PropertyResourceBundle.getBundle(resourcePath, locale);
        return bundle.getString(key);
    }

}
