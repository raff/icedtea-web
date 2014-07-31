/*Copyright (C) 2014 Red Hat, Inc.

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

package net.sourceforge.jnlp.security.policyeditor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sourceforge.jnlp.util.FileUtils;
import net.sourceforge.jnlp.util.MD5SumWatcher;
import net.sourceforge.jnlp.util.logging.OutputController;

public class PolicyFileModel {

    private static final String AUTOGENERATED_NOTICE = "/* DO NOT MODIFY! AUTO-GENERATED */";

    private File file;
    /**
     * Maps Codebases to Maps of Permissions and whether that Permission is set or not. The Codebase keys correspond to
     * the Codebases in the list UI, and the Permission->Boolean maps correspond to the checkboxes associated with
     * each Codebase.
     */
    private final Map<String, Map<PolicyEditorPermissions, Boolean>> codebasePermissionsMap = new HashMap<>();
    private final Map<String, Set<CustomPermission>> customPermissionsMap = new HashMap<>();
    private MD5SumWatcher fileWatcher;

    PolicyFileModel(final String filepath) {
        this(new File(filepath));
    }

    PolicyFileModel(final File file) {
        setFile(file);
    }

    PolicyFileModel() {
    }

    void setFile(final File file) {
        this.file = file;
    }

    File getFile() {
        return file;
    }

    /**
     * Open the file pointed to by the filePath field. This is either provided by the
     * "-file" command line flag, or if none given, comes from DeploymentConfiguration.
     */
    void openAndParsePolicyFile() throws IOException {
        fileWatcher = new MD5SumWatcher(file);
        fileWatcher.update();
        codebasePermissionsMap.clear();
        customPermissionsMap.clear();
        final FileLock fileLock = FileUtils.getFileLock(file.getAbsolutePath(), false, true);
        try {
            // User-level policy files are expected to be short enough that loading them in as a String
            // should not actually be *too* bad, and it's easy to work with.
            final String contents = FileUtils.loadFileAsString(file);
            // Split on newlines, both \r\n and \n style, for platform-independence
            final String[] lines = contents.split("\\r?\\n+");
            String codebase = "";
            boolean openBlock = false, commentBlock = false;
            for (final String line : lines) {
                // Matches eg `grant {` as well as `grant codeBase "http://redhat.com" {`
                final Pattern openBlockPattern = Pattern.compile("grant\\s*\"?\\s*(?:codeBase)?\\s*\"?([^\"\\s]*)\"?\\s*\\{");
                final Matcher openBlockMatcher = openBlockPattern.matcher(line);
                if (openBlockMatcher.matches()) {
                    // Codebase URL
                    codebase = openBlockMatcher.group(1);
                    addCodebase(codebase);
                    openBlock = true;
                }

                // Matches '};', the closing block delimiter, with any amount of whitespace on either side
                boolean commentLine = false;
                if (line.matches("\\s*\\};\\s*")) {
                    openBlock = false;
                }
                // Matches '/*', the start of a block comment
                if (line.matches(".*/\\*.*")) {
                    commentBlock = true;
                }
                // Matches '*/', the end of a block comment, and '//', a single-line comment
                if (line.matches(".*\\*/.*")) {
                    commentBlock = false;
                }
                if (line.matches(".*/\\*.*") && line.matches(".*\\*/.*")) {
                    commentLine = true;
                }
                if (line.matches("\\s*//.*")) {
                    commentLine = true;
                }

                if (!openBlock || commentBlock || commentLine) {
                    continue;
                }
                final PolicyEditorPermissions perm = PolicyEditorPermissions.fromString(line);
                if (perm != null) {
                    codebasePermissionsMap.get(codebase).put(perm, true);
                } else {
                    final CustomPermission cPerm = CustomPermission.fromString(line.trim());
                    if (cPerm != null) {
                        customPermissionsMap.get(codebase).add(cPerm);
                    }
                }
            }
        } finally {
            try {
                fileLock.release();
            } catch (final IOException e) {
                OutputController.getLogger().log(e);
            }
        }
    }

    /**
     * Save the policy model into the file pointed to by the filePath field.
     */
    void savePolicyFile() throws FileNotFoundException, IOException {
        final StringBuilder sb = new StringBuilder();
        sb.append(AUTOGENERATED_NOTICE);
        final String currentDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime());
        sb.append("\n/* Generated by PolicyEditor at ").append(currentDate).append(" */");
        sb.append(System.getProperty("line.separator"));
        FileLock fileLock = null;
        try {
            fileLock = FileUtils.getFileLock(file.getAbsolutePath(), false, true);
            final Set<PolicyEditorPermissions> enabledPermissions = new HashSet<>();
            for (final String codebase : codebasePermissionsMap.keySet()) {
                enabledPermissions.clear();
                for (final Map.Entry<PolicyEditorPermissions, Boolean> entry : codebasePermissionsMap.get(codebase).entrySet()) {
                    if (entry.getValue()) {
                        enabledPermissions.add(entry.getKey());
                    }
                }
                sb.append(new PolicyEntry(codebase, enabledPermissions, customPermissionsMap.get(codebase)).toString());
            }
        } catch (final IOException e) {
            OutputController.getLogger().log(e);
        } finally {
            if (fileLock != null) {
                try {
                    fileLock.release();
                } catch (final IOException e) {
                    OutputController.getLogger().log(e);
                }

            }
        }

        FileUtils.saveFile(sb.toString(), file);
        fileWatcher = new MD5SumWatcher(file);
        fileWatcher.update();
    }

    boolean hasChanged() throws FileNotFoundException, IOException {
        return fileWatcher != null && fileWatcher.update();
    }

    Set<String> getCodebases() {
        return new HashSet<>(codebasePermissionsMap.keySet());
    }

    /**
     * Add a new codebase. No action is taken if the codebase has already been added.
     * @param codebase for which a permissions mapping is required
     * @return true iff there was already an entry for this codebase
     */
    boolean addCodebase(final String codebase) {
        Objects.requireNonNull(codebase);
        boolean existingCodebase = true;

        if (!codebasePermissionsMap.containsKey(codebase)) {
            final Map<PolicyEditorPermissions, Boolean> map = new HashMap<>();
            for (final PolicyEditorPermissions perm : PolicyEditorPermissions.values()) {
                map.put(perm, false);
            }
            codebasePermissionsMap.put(codebase, map);
            existingCodebase = false;
        }

        if (!customPermissionsMap.containsKey(codebase)) {
            final Set<CustomPermission> set = new HashSet<>();
            customPermissionsMap.put(codebase, set);
            existingCodebase = false;
        }

        return existingCodebase;
    }

    void clearPermissions() {
        codebasePermissionsMap.clear();
    }

    void removeCodebase(final String codebase) {
        Objects.requireNonNull(codebase);
        codebasePermissionsMap.remove(codebase);
        customPermissionsMap.remove(codebase);
    }

    void setPermission(final String codebase, final PolicyEditorPermissions permission, final boolean state) {
        Objects.requireNonNull(codebase);
        Objects.requireNonNull(permission);
        addCodebase(codebase);
        codebasePermissionsMap.get(codebase).put(permission, state);
    }

    boolean getPermission(final String codebase, final PolicyEditorPermissions permission) {
        Objects.requireNonNull(codebase);
        Objects.requireNonNull(permission);
        if (!codebasePermissionsMap.containsKey(codebase)) {
            return false;
        }
        return codebasePermissionsMap.get(codebase).get(permission);
    }

    Map<String, Map<PolicyEditorPermissions, Boolean>> getCopyOfPermissions() {
        return new HashMap<>(codebasePermissionsMap);
    }

    void clearCustomPermissions() {
        customPermissionsMap.clear();
    }

    void clearCustomCodebase(final String codebase) {
        Objects.requireNonNull(codebase);
        if (!customPermissionsMap.containsKey(codebase)) {
            return;
        }
        customPermissionsMap.get(codebase).clear();
    }

    void addCustomPermissions(final String codebase, final Collection<CustomPermission> permissions) {
        Objects.requireNonNull(codebase);
        Objects.requireNonNull(permissions);
        addCodebase(codebase);
        customPermissionsMap.get(codebase).addAll(permissions);
    }

    Map<String, Set<CustomPermission>> getCopyOfCustomPermissions() {
        return new HashMap<>(customPermissionsMap);
    }
}
