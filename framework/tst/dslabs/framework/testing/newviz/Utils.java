/*
 * Copyright (c) 2022 Ellis Michael (emichael@cs.washington.edu)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package dslabs.framework.testing.newviz;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLaf;
import java.awt.Color;
import java.util.prefs.Preferences;
import javax.swing.Icon;
import javax.swing.UIManager;
import jiconfont.IconCode;
import jiconfont.icons.font_awesome.FontAwesome;
import jiconfont.swing.IconFontSwing;

abstract class Utils {
    static {
        IconFontSwing.register(FontAwesome.getIconFont());
        FlatLaf.registerCustomDefaultsSource("dslabs.framework.testing.newviz");
    }

    private static final int ICON_SIZE = 18;
    private static final String PREF_DARK_MODE = "dark_mode";

    private static Color iconColor() {
        return UIManager.getColor("Tree.icon.expandedColor");
    }

    static Icon makeIcon(IconCode iconCode) {
        return makeIcon(iconCode, iconColor());
    }

    static Icon makeIcon(IconCode iconCode, Color color) {
        return IconFontSwing.buildIcon(iconCode, ICON_SIZE, color);
    }

    static String colorToHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(),
                c.getBlue());
    }

    static Color desaturate(Color c, double factor) {
        if (factor < 0 || factor > 1) {
            throw new IllegalArgumentException(
                    "factor to desaturate by must be between 0 and 1");
        }
        float[] hsb =
                Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        hsb[1] *= factor;
        return new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]));
    }

    static boolean setupThemeOnStartup() {
        final Preferences prefs = Preferences.userNodeForPackage(Utils.class);
        final boolean darkModeEnabled = prefs.getBoolean(PREF_DARK_MODE, false);

        if (darkModeEnabled) {
            setupDarkTheme(false);
        } else {
            setupLightTheme(false);
        }

        return darkModeEnabled;
    }

    static void setupLightTheme(boolean savePreference) {
        FlatIntelliJLaf.setup();
        FlatIntelliJLaf.updateUI();

        if (savePreference) {
            saveThemePreference(false);
        }
    }

    static void setupDarkTheme(boolean savePreference) {
        FlatDarculaLaf.setup();
        FlatDarculaLaf.updateUI();

        if (savePreference) {
            saveThemePreference(true);
        }
    }

    private static void saveThemePreference(boolean darkMode) {
        final Preferences prefs = Preferences.userNodeForPackage(Utils.class);
        prefs.putBoolean(PREF_DARK_MODE, darkMode);
    }
}
