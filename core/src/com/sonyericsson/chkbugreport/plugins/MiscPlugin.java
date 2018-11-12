/*
 * Copyright (C) 2011 Sony Ericsson Mobile Communications AB
 * Copyright (C) 2012 Sony Mobile Communications AB
 *
 * This file is part of ChkBugReport.
 *
 * ChkBugReport is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * ChkBugReport is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ChkBugReport.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sonyericsson.chkbugreport.plugins;

import com.sonyericsson.chkbugreport.Module;
import com.sonyericsson.chkbugreport.Plugin;
import com.sonyericsson.chkbugreport.Section;
import com.sonyericsson.chkbugreport.doc.Block;
import com.sonyericsson.chkbugreport.doc.Chapter;
import com.sonyericsson.chkbugreport.doc.DocNode;
import com.sonyericsson.chkbugreport.doc.Hint;
import com.sonyericsson.chkbugreport.doc.Table;
import com.sonyericsson.chkbugreport.doc.TreeView;
import com.sonyericsson.chkbugreport.doc.PreText;
import com.sonyericsson.chkbugreport.util.DumpTree;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Vector;



public class MiscPlugin extends Plugin {

    private static final String TAG = "[MiscPlugin]";
    //add by john
    private static final Pattern RESTRICT_PT_1 = Pattern.compile("--State ([A-Z_]+)\\s+UserId: ([0-9]+)---PkgName: (.*)---Running");
    private static final Pattern RESTRICT_PT_2 = Pattern.compile("--State ([A-Z_]+)\\s+UserId: ([0-9]+)---PkgName: (.*)");
    private static final Pattern RESTRICT_PT_3 = Pattern.compile(".*:\\s+([0-9]+)");

    private static final Pattern PKGINFO_PT_1 = Pattern.compile("  Package \\[(.*)\\].*");
    private static final Pattern PKGINFO_PT_2 = Pattern.compile("\\s+.*=(.*)");
    private static final Pattern PKGINFO_PT_3 = Pattern.compile("\\s+.*=(.*)\\s+.*=(.*)\\s+.*=(.*)");

    private static final Pattern SERVICE_PT_1 = Pattern.compile("\\s+\\*\\s+ServiceRecord\\{.*\\s+u([0-9]+)\\s+(.*)\\}");
    private static final Pattern SERVICE_PT_2 = Pattern.compile("\\s+.*=(.*)\\s+.*=(.*)\\s+.*=(.*)");

    private Vector<PackageInfo> mPackageInfoList;
    private Vector<RestrictInfo> mRestrictInfoList;
    private Vector<ServiceInfo> mServiceInfoList;
    private int restrictedPkgCount = 0;
    private int unCorePkgCount = 0;
    private int totalPkgCount = 0;
    private int systemPkgCount = 0;
    private int persistentPkgCount = 0;
    private int privilegedPkgCount = 0;
    private int serviceCount = 0;
    private int fgServiceCount = 0;
    //end

    @Override
    public int getPrio() {
        return 99;
    }

    @Override
    public void reset() {
        //mod by john
        mRestrictInfoList = null;
        mPackageInfoList = null;
        //end
    }

    @Override
    public void load(Module mod) {
        //mod by john
        loadActivityServicesSec(mod);
        loadPackageSec(mod);
        //end
    }

    @Override
    public void generate(Module mod) {
        //mod by john
        //convertToTreeView(mod, Section.APP_ACTIVITIES, "ActivityManager/App activities");
        //convertToTreeView(mod, Section.APP_SERVICES, "ActivityManager/App services");
        //convertToTreeView(mod, Section.DUMP_OF_SERVICE_PACKAGE, "PackageManager");
        generatePackageList(mod);
        generateRestrictSec(mod);
        generateActivitySettings(mod);
        generateServiceList(mod);
        generateProcessList(mod);
        //end
    }

    //add by john
        static class ServiceInfo {
        String serviceName;
        String userId;
        String processName;
        String lastActivity;
        String restartTime;
        String createdFromFg;
        String isForeground;

        public ServiceInfo(String serviceName) {
            this.serviceName = serviceName;
        }
    }

    static class RestrictInfo {
        String pkg;
        String userId;
        String restrict;
        String running;

        public RestrictInfo(String pkg, String userId, String restrict, String running) {
            this.pkg = pkg;
            this.userId = userId;
            this.restrict = restrict;
            this.running = running;
        }
    }

    static class PackageInfo {
        String name;
        long userId;
        String version;
        int targetSdk;
        boolean isSystem;
        boolean isPersistent;
        boolean isPrivileged;
        String firstInstallTime;
        String lastUpdateTime;
        String installerPkg;

        public PackageInfo(String name) {
            this.name = name;
        }
    }

    private void loadPackageSec(Module mod){
        Section section = mod.findSection(Section.DUMP_OF_SERVICE_PACKAGE);
        if (section == null) {
            mod.printErr(3, TAG + "Section not found: " + Section.DUMP_OF_SERVICE_PACKAGE + " (ignoring)");
            return;
        }

        final int cnt = section.getLineCount();
        int idx = 0;
        String line = "";
        Matcher m = null;
        RestrictInfo restrictInfo = null;
        PackageInfo pkgInfo = null;
        String PackageName = "";
        int user = 0;
        while (true) {
            line = (idx < cnt) ? section.getLine(idx++) : null;
            if (line == null || line.startsWith("---------")) {
                break;
            }

            // Parse package list.
            if (line.startsWith("Packages:")) {
                mPackageInfoList = new Vector<PackageInfo>();
                 while (true) {
                    line = section.getLine(idx++);
                    if (!line.startsWith("  ")) {
                        break;
                    }

                    m = PKGINFO_PT_1.matcher(line);
                    if (m.matches()) {
                        pkgInfo = new PackageInfo(m.group(1));
                        mPackageInfoList.add(pkgInfo);
                        totalPkgCount++;
                        while (true) {
                            line = section.getLine(idx++);
                            if (!line.startsWith("  ")) {
                                break;
                            }

                            if (line.startsWith("  Package [")) {
                                idx--; //roll back.
                                break;
                            }

                            if (line.startsWith("    userId=")) {
                                m = PKGINFO_PT_2.matcher(line);
                                if (!m.matches()) {
                                    mod.printErr(3, "Cannot pares line: " + line);
                                }
                                pkgInfo.userId = Long.parseLong(m.group(1));
                            } else if (line.startsWith("    versionCode=")) {
                                m = PKGINFO_PT_3.matcher(line);
                                if (!m.matches()) {
                                    mod.printErr(3, "Cannot pares line: " + line);
                                }
                                pkgInfo.targetSdk = Integer.parseInt(m.group(3));
                            } else if (line.startsWith("    versionName=")) {
                                m = PKGINFO_PT_2.matcher(line);
                                if (!m.matches()) {
                                    mod.printErr(3, "Cannot pares line: " + line);
                                }
                                pkgInfo.version = m.group(1);
                            } else if (line.startsWith("    flags=")) {
                                if (line.contains(" SYSTEM ")) {
                                    pkgInfo.isSystem = true;
                                    systemPkgCount++;
                                }
                                if (line.contains(" PERSISTENT ")) {
                                    pkgInfo.isPersistent = true;
                                    persistentPkgCount++;
                                }
                            } else if (line.startsWith("    privateFlags=")) {
                                if (line.contains(" PRIVILEGED ")) {
                                    pkgInfo.isPrivileged = true;
                                    privilegedPkgCount++;
                                }
                            } else if (line.startsWith("    firstInstallTime=")) {
                                m = PKGINFO_PT_2.matcher(line);
                                if (!m.matches()) {
                                    mod.printErr(3, "Cannot pares line: " + line);
                                }
                                pkgInfo.firstInstallTime = m.group(1);
                            } else if (line.startsWith("    lastUpdateTime=")) {
                                m = PKGINFO_PT_2.matcher(line);
                                if (!m.matches()) {
                                    mod.printErr(3, "Cannot pares line: " + line);
                                }
                                pkgInfo.lastUpdateTime = m.group(1);
                            } else if (line.startsWith("    installerPackageName=")) {
                                m = PKGINFO_PT_2.matcher(line);
                                if (!m.matches()) {
                                    mod.printErr(3, "Cannot pares line: " + line);
                                }
                                pkgInfo.installerPkg = m.group(1);
                            }
                        }
                    } else {
                        break;
                    }
                 }
                continue;
            }

            // Parse restrict info.
            if (line.startsWith("Restrict Policy Manager:")) {
                mRestrictInfoList = new Vector<RestrictInfo>();
                while (true) {
                    line = section.getLine(idx++);
                    m = RESTRICT_PT_1.matcher(line);
                    if (m.matches()) {
                        restrictInfo = new RestrictInfo(m.group(3), m.group(2),
                                m.group(1), "true");
                        mRestrictInfoList.add(restrictInfo);
                        continue;
                    }

                    m = RESTRICT_PT_2.matcher(line);
                    if (m.matches()) {
                        restrictInfo = new RestrictInfo(m.group(3), m.group(2),
                                m.group(1), "false");
                        mRestrictInfoList.add(restrictInfo);
                        continue;
                    }

                    break;
                }
                continue;
            }

            if (line.startsWith("--Managed App Count:")) {
                m = RESTRICT_PT_3.matcher(line);
                if (!m.matches()) {
                    mod.printErr(3, "Cannot pares line: " + line);
                } else {
                    unCorePkgCount = Integer.parseInt(m.group(1));
                }
                continue;
            }

            if (line.startsWith("--Restricted App Count:")) {
                m = RESTRICT_PT_3.matcher(line);
                if (!m.matches()) {
                    mod.printErr(3, "Cannot pares line: " + line);
                } else {
                    restrictedPkgCount = Integer.parseInt(m.group(1));
                }
                continue;
            }
        }
    }

    private void loadActivityServicesSec(Module mod){
        Section section = mod.findSection(Section.ACTIVITY_SERVICES);
        if (section == null) {
            mod.printErr(3, TAG + "Section not found: " + Section.ACTIVITY_SERVICES + " (ignoring)");
            return;
        }

        final int cnt = section.getLineCount();
        int idx = 0;
        String line = "";
        Matcher m = null;
        ServiceInfo serviceInfo = null;
        String PackageName = "";
        mServiceInfoList = new Vector<ServiceInfo>();
        while (true) {
            line = (idx < cnt) ? section.getLine(idx++) : null;
            if (line == null || line.startsWith("---------")) {
                break;
            }

            // Parse activity services list.
            if (line.startsWith("  * ServiceRecord")) {
                m = SERVICE_PT_1.matcher(line);
                if (m.matches()) {
                    serviceInfo = new ServiceInfo(m.group(2));
                    serviceInfo.userId = m.group(1);
                    mServiceInfoList.add(serviceInfo);
                    serviceCount++;

                    while (true) {
                        line = section.getLine(idx++);
                        if (!line.startsWith("    ")) {
                             break;
                        }

                        if (line.startsWith("  * ServiceRecord")) {
                            idx--; //roll back.
                            break;
                        }

                        if (line.startsWith("    processName=")) {
                            m = PKGINFO_PT_2.matcher(line);
                            if (!m.matches()) {
                                mod.printErr(3, "Cannot pares line: " + line);
                            }
                            serviceInfo.processName = m.group(1);
                        } else if (line.startsWith("    lastActivity=")) {
                            m = SERVICE_PT_2.matcher(line);
                            if (!m.matches()) {
                               mod.printErr(3, "Cannot pares line: " + line);
                            }
                            serviceInfo.lastActivity = m.group(1);
                            serviceInfo.restartTime = m.group(2);
                            serviceInfo.createdFromFg = m.group(3);
                        } else if (line.startsWith("    isForeground=")) {
                            serviceInfo.isForeground = "true";
                            fgServiceCount++;
                        }
                    }
                } else {
                        continue;
                }
            }
        }
    }

    private void generateRestrictSec(Module mod) {
        if (mRestrictInfoList == null || mRestrictInfoList.size() <= 0) {
            return;
        }

        Chapter mainChapter = mod.findOrCreateChapter("PackageManager");
        Chapter ch = new Chapter(mod.getContext(), "Restrict Info");
        mainChapter.addChapter(ch);
        Table t = new Table(Table.FLAG_SORT, ch);
        new Hint(t)
                .add("listed app counts: " + unCorePkgCount
                    + ", restricted app counts: " + restrictedPkgCount);
        t.addColumn("Pkg", Table.FLAG_NONE);
        t.addColumn("User", Table.FLAG_ALIGN_RIGHT);
        t.addColumn("Restricted", Table.FLAG_ALIGN_RIGHT);
        t.addColumn("Running", Table.FLAG_ALIGN_RIGHT);
        t.begin();
        for (RestrictInfo info : mRestrictInfoList) {
            t.addData(info.pkg);
            t.addData(info.userId);
            t.addData("NO_RESTRICT".equals(info.restrict)? "false" : "true");
            t.addData(info.running);
        }
        t.end();
    }

    private void generatePackageList(Module mod) {
        if (mPackageInfoList == null || mPackageInfoList.size() <= 0) {
            return;
        }

        Chapter mainChapter = mod.findOrCreateChapter("PackageManager");
        Chapter ch = new Chapter(mod.getContext(), "Package List");
        mainChapter.addChapter(ch);
        Table t = new Table(Table.FLAG_SORT, ch);
        new Hint(t)
                .add("Total " + totalPkgCount + " packages: "
                    + "        * " + persistentPkgCount + " belong to persistent,"
                    + "        * " + privilegedPkgCount + " belong to privileged,"
                    + "        * " + systemPkgCount + " belong to system.");
        t.addColumn("Pkg", Table.FLAG_NONE);
        t.addColumn("Uid", Table.FLAG_ALIGN_RIGHT);
        t.addColumn("Version", Table.FLAG_ALIGN_RIGHT);
        t.addColumn("TargetSdk", Table.FLAG_ALIGN_RIGHT);
        t.addColumn("System", Table.FLAG_ALIGN_RIGHT);
        t.addColumn("Privileged", Table.FLAG_ALIGN_RIGHT);
        t.addColumn("Persistent", Table.FLAG_ALIGN_RIGHT);
        t.addColumn("FirstInstall", Table.FLAG_ALIGN_RIGHT);
        t.addColumn("LastUpdate", Table.FLAG_ALIGN_RIGHT);
//        t.addColumn("InstallerPkg", Table.FLAG_ALIGN_RIGHT);
        t.begin();
        for (PackageInfo info : mPackageInfoList) {
            t.addData(info.name);
            t.addData(info.userId);
            t.addData(info.version);
            t.addData(info.targetSdk);
            t.addData(info.isSystem ? "true" : "false");
            t.addData(info.isPrivileged ? "true" : "false");
            t.addData(info.isPersistent ? "true" : "false");
            t.addData(info.firstInstallTime == null ? "" : info.firstInstallTime);
            t.addData(info.lastUpdateTime == null ? "" : info.lastUpdateTime);
//            t.addData(info.installerPkg == null ? "" : info.installerPkg);
        }
        t.end();
    }

    private void generateServiceList(Module mod) {
        if (mServiceInfoList == null || mServiceInfoList.size() <= 0) {
            return;
        }

        Chapter mainChapter = mod.findOrCreateChapter("ActivityManager");
        Chapter ch = new Chapter(mod.getContext(), "Active Services");
        mainChapter.addChapter(ch);
        new Block(ch).addStyle("note-box")
            .add("Color coding:")
            .add(new Block().addStyle("level75").add("Service is running at foreground."));

        Table t = new Table(Table.FLAG_SORT, ch);
        new Hint(t)
                .add("Total " + serviceCount + " services, " + fgServiceCount
                    + " of them are set foreground.");
        t.addColumn("Name", Table.FLAG_NONE);
        t.addColumn("User", Table.FLAG_NONE);
        t.addColumn("Process", Table.FLAG_NONE);
        t.addColumn("LastActivity", Table.FLAG_ALIGN_RIGHT);
        t.addColumn("RestartTime", Table.FLAG_ALIGN_RIGHT);
        t.addColumn("CreatedFromFg", Table.FLAG_ALIGN_RIGHT);
        t.addColumn("Foreground", Table.FLAG_ALIGN_RIGHT);
        t.begin();
        for (ServiceInfo info : mServiceInfoList) {
            String style = "";
            if (info.isForeground != null) {
                style = "level75";
            }
            t.setNextRowStyle(style);
            t.addData(info.serviceName);
            t.addData(info.userId);
            t.addData(info.processName);
            t.addData(info.lastActivity);
            t.addData(info.restartTime);
            t.addData(info.createdFromFg);
            t.addData(info.isForeground == null ? "false" : info.isForeground);
        }
        t.end();
    }

    private void generateActivitySettings(Module mod){
        Section section = mod.findSection(Section.ACTIVITY_SETTINGS);
        if (section == null) {
            mod.printErr(3, TAG + "Section not found: " + Section.ACTIVITY_SETTINGS + " (ignoring)");
            return;
        }

        Chapter ch = mod.findOrCreateChapter("ActivityManager/Settings");

        final int cnt = section.getLineCount();
        DocNode pt = new PreText();
        ch.add(pt);
        for (int i = 0; i < cnt; i++) {
            String line = section.getLine(i);
            pt.addln(line);
        }
    }

    private void generateProcessList(Module mod){
        Section section = mod.findSection(Section.ACTIVITY_PROCESSES);
        if (section == null) {
            mod.printErr(3, TAG + "Section not found: " + Section.ACTIVITY_PROCESSES + " (ignoring)");
            return;
        }

        Chapter ch = mod.findOrCreateChapter("ActivityManager/Process LRU list");

        final int cnt = section.getLineCount();
        DocNode pt = new PreText();
        ch.add(pt);
        boolean readingProcessLruList = false;
        for (int i = 0; i < cnt; i++) {
            String line = section.getLine(i);
            if (line.startsWith("  Process LRU list")) {
                readingProcessLruList = true;
                pt.addln(line);
                continue;
            }

            if (!line.startsWith("    ")) {
                readingProcessLruList = false;
                continue;
            }

            if (readingProcessLruList) {
                pt.addln(line);
            }
        }
    }
    //end

    private void convertToTreeView(Module mod, String secName, String chName) {
        // Load data
        Section section = mod.findSection(secName);
        if (section == null) {
            mod.printErr(3, TAG + "Section not found: " + secName + " (ignoring)");
            return;
        }

        // Parse the data
        DumpTree dump = new DumpTree(section, 0);
        Chapter ch = mod.findOrCreateChapter(chName);
        new Hint(ch).add("Under construction! For now it contains the raw data in a tree-view.");
        ch.add(convertToTreeView(dump.getRoot(), 0));
    }

    private TreeView convertToTreeView(DumpTree.Node node, int level) {
        TreeView ret = new TreeView(node.getLine(), level++);
        for (DumpTree.Node child : node) {
            ret.add(convertToTreeView(child, level));
        }
        return ret;
    }

}
