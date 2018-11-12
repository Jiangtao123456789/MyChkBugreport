/*
 * Copyright (C) 2011 Sony Ericsson Mobile Communications AB
 * Copyright (C) 2012-2013 Sony Mobile Communications AB
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

import com.sonyericsson.chkbugreport.BugReportModule;
import com.sonyericsson.chkbugreport.Module;
import com.sonyericsson.chkbugreport.Plugin;
import com.sonyericsson.chkbugreport.Section;
import com.sonyericsson.chkbugreport.doc.Anchor;
import com.sonyericsson.chkbugreport.doc.Chapter;
import com.sonyericsson.chkbugreport.doc.Link;
import com.sonyericsson.chkbugreport.doc.Para;
import com.sonyericsson.chkbugreport.doc.ShadedValue;
import com.sonyericsson.chkbugreport.doc.Table;
import com.sonyericsson.chkbugreport.util.DumpTree;
import com.sonyericsson.chkbugreport.util.Util;
import com.sonyericsson.chkbugreport.util.DumpTree.Node;

import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AlarmManagerPlugin extends Plugin {

    private static final String TAG = "[AlarmManagerPlugin]";
    //add by john
    private static final String ALAMR_STATS = "  Alarm Stats:";
    private static final String ACTIVE_FLAG = "*ACTIVE*";
    private static final Pattern ALARM_LIST_HEADER = Pattern.compile("\\s+([A-Z_]+) #[0-9]+: Alarm\\{[a-f0-9]+ type [0-3] when [0-9]+ (.*)\\}");
    private static final Pattern ALARM_LIST_ITEM_1 = Pattern.compile("\\s+tag=\\*(.*)\\*:(.*)");
    private static final Pattern ALARM_LIST_ITEM_2 = Pattern.compile("\\s+type=(.?) whenElapsed=(.*) when=(.*)");
    private static final Pattern ALARM_LIST_ITEM_3 = Pattern.compile("\\s+window=(.*) repeatInterval=(.*) count=(.*) flags=(.*)");
    private static final Pattern ALARM_LIST_OP_1 = Pattern.compile("\\s+operation=PendingIntent\\{[0-9a-f]+: PendingIntentRecord\\{[0-9a-f]+ (.*) ([a-zA-Z]*)\\}\\}");
    private static final Pattern ALARM_LIST_OP_2 = Pattern.compile("\\s+operation=(.*)");

    private static final Pattern ALARM_STATS_HEADER_1 = Pattern.compile(".* (.*):(.*) (.*) running, ([0-9]+) wakeups:");
    private static final Pattern ALARM_STATS_HEADER_2 = Pattern.compile("(.*):(.*) (.*) running, ([0-9]+) wakeups:");
    private static final Pattern ALARM_STATS_ITEM_1 = Pattern.compile(".* (.*) ([0-9]+) wakes ([0-9]+) alarms, last (.*):");
    private static final Pattern ALARM_STATS_ITEM_2 = Pattern.compile("\\s(.*) ([0-9]+) wakes ([0-9]+) alarms, last (.*):");
    private static final Pattern ALARM_STATS_ACTION = Pattern.compile("\\s+\\*(.*)\\*:(.*)");
    //end

    private boolean mLoaded;
    private Section mSection;
    private Vector<Alarm> mAlarms = new Vector<Alarm>();
    private Vector<AlarmStat> mStats = new Vector<AlarmStat>();
    private int mNextAlarmAnchor;

    @Override
    public int getPrio() {
        return 91;
    }

    @Override
    public void reset() {
        // Reset
        mLoaded = false;
        mSection = null;
        mAlarms.clear();
        mNextAlarmAnchor = 0;
    }

    @Override
    public void load(Module br) {
        // Load data
        mSection = br.findSection(Section.DUMP_OF_SERVICE_ALARM);
        if (mSection == null) {
            br.printErr(3, TAG + "Section not found: " + Section.DUMP_OF_SERVICE_ALARM + " (aborting plugin)");
            return;
        }

        // Parse the data
        DumpTree dump = new DumpTree(mSection, 0);
        //mod by john
        /*final String nodeKey = "Current Alarm Manager state:";
        DumpTree.Node root = dump.find(nodeKey, false);
        if (root == null) {
            br.printErr(3, "Cannot find node '" + nodeKey + "'");
            return;
        }
        if (root.getChildCount() == 0) {
            loadFromScrewedUpIndentation(br, root.getParent());
        } else {
            loadOldFormat(br, root);
        }*/
        final int cnt = mSection.getLineCount();
        int idx = 0;
        String line = "";
        Matcher m = null;
        //parse alarms list.
        //boolean readingAlarmList = false;
        Alarm alarm = null;
        while (true) {
            line = (idx < cnt) ? mSection.getLine(idx++) : null;
            if (line == null || line.startsWith("---------")
                    || line.startsWith("    Number of delayed alarms")) {
                break;
            }

            if (line.startsWith("    ELAPSED")
                    || line.startsWith("    RTC")) {
                //readingAlarmList = true;
                alarm = new Alarm();
                m = ALARM_LIST_HEADER.matcher(line);
                if (!m.matches()) {
                    br.printErr(4, "Cannot parse alarm list header: " + line);
                } else {
                    alarm.type = m.group(1);
                    alarm.pkg = m.group(2);
                }

                line = mSection.getLine(idx++);
                m = ALARM_LIST_ITEM_1.matcher(line);
                if (!m.matches()) {
                    br.printErr(4, "Cannot parse alarm list item 1: " + line);
                }

                line = mSection.getLine(idx++);
                m = ALARM_LIST_ITEM_2.matcher(line);
                if (!m.matches()) {
                    br.printErr(4, "Cannot parse alarm list item 2: " + line);
                } else {
                    alarm.whenS = m.group(3);
                    //alarm.when = readTs(alarm.whenS);
                }

                line = mSection.getLine(idx++);
                m = ALARM_LIST_ITEM_3.matcher(line);
                if (!m.matches()) {
                    br.printErr(4, "Cannot parse alarm list item 3: " + line);
                } else {
                    alarm.repeat = Long.parseLong(m.group(2));
                    alarm.count = Long.parseLong(m.group(3));
                }

                line = mSection.getLine(idx++);
                m = ALARM_LIST_OP_1.matcher(line);
                if (!m.matches()) {
                    m = ALARM_LIST_OP_2.matcher(line);
                    if (!m.matches()) {
                        br.printErr(4, "Cannot parse alarm list operation: " + line);
                    }
                } else {
                    alarm.opPkg = m.group(1);
                    alarm.opMet = m.group(2);
                }

                mAlarms.add(alarm);
            }
        }

        //parse alarm stats.
        boolean readingAlarmStats = false;
        int next_info_type = -1;
        final int info_header = 0;
        final int info_detail = 1;
        final int info_action = 2;
        AlarmStat stat = null;
        AlarmAction action = null;
        String packgeName ="";
        while (true) {
            line = (idx < cnt) ? mSection.getLine(idx++) : null;
            if (line == null || line.startsWith("---------")) {
                break;
            }

            if (line.startsWith(ALAMR_STATS)) {
                readingAlarmStats = true;
                next_info_type = 0;
                continue;
            }

            if (readingAlarmStats) {
                switch (next_info_type) {
                    case 2:
                        m = ALARM_STATS_ACTION.matcher(line);
                        if (!m.matches()) {
                            //cannot parse, try next type.
                            next_info_type = 1;
                        } else {
                            action.type = m.group(1);
                            action.action = m.group(2);
                            next_info_type = 1;
                            break;
                        }
                    case 1:
                        m = line.contains(ACTIVE_FLAG) ?
                            ALARM_STATS_ITEM_1.matcher(line) : ALARM_STATS_ITEM_2.matcher(line);
                        if (!m.matches()) {
                            //cannot parse, try next type.
                            next_info_type = 0;
                        } else {
                            action = new AlarmAction();
                            action.time = Util.parseRelativeTimestamp(m.group(1));
                            action.wakeCount = Long.parseLong(m.group(2));
                            action.alarmCount = Long.parseLong(m.group(3));
                            action.last = m.group(4);
                            stat.alarms += action.alarmCount;
                            stat.actions.add(action);
                            next_info_type = 2;
                            break;
                        }
                    case 0:
                        m = line.contains(ACTIVE_FLAG) ?
                            ALARM_STATS_HEADER_1.matcher(line) : ALARM_STATS_HEADER_2.matcher(line);
                        if (!m.matches()) {
                            next_info_type = 0;
                            br.printErr(3, "Cannot parse line: " + line);
                            break;
                        }

                        stat = new AlarmStat();
                        stat.anchor = new Anchor("a" + mNextAlarmAnchor++);
                        stat.pkg = m.group(2);
                        stat.runtime = Util.parseRelativeTimestamp(m.group(3));
                        stat.wakeups = Long.parseLong(m.group(4));
                        mStats.add(stat);
                        next_info_type = 1;
                        break;
                }
            }
        }

        //end
        // Done
        mLoaded = true;
    }

    private void loadFromScrewedUpIndentation(Module br, Node root) {
        // Parse stats
        final String nodeKey = "Alarm Stats:";
        DumpTree.Node stats = root.find(nodeKey, true);
        if (stats == null) {
            br.printErr(3, "Cannot find node '" + nodeKey + "'");
        } else {
            Node parent = stats.getParent();
            int idx = parent.indexOf(stats);
            if (idx < 0) {
                br.printErr(3, "Something's fishy with node '" + nodeKey + "'");
            } else {
                int childCount = parent.getChildCount();
                while (++idx < childCount) {
                    Node item = parent.getChild(idx);
                    if (item.getLine().endsWith("wakeups:")) {
                        addPackageStat(br, item);
                    }
                }
            }
        }

        // Parse alarms
        for (DumpTree.Node batch : root) {
            String line = batch.getLine();
            if (!line.startsWith("Batch{")) continue;
            for (DumpTree.Node item : batch) {
                line = item.getLine();
                if (line.startsWith("ELAPSED ")) {
                    addAlarm(br, item);
                } else if (line.startsWith("ELAPSED_WAKEUP ")) {
                    addAlarm(br, item);
                } else if (line.startsWith("RTC ")) {
                    addAlarm(br, item);
                } else if (line.startsWith("RTC_WAKEUP ")) {
                    addAlarm(br, item);
                }
            }
        }
    }

    private void loadOldFormat(Module br, Node root) {
        boolean stats = false;
        for (DumpTree.Node item : root) {
            String line = item.getLine();
            if (stats) {
                addPackageStat(br, item);
            } else if (line.startsWith("Alarm Stats:")) {
                stats = true;
            } else if (line.startsWith("ELAPSED ")) {
                addAlarm(br, item);
            } else if (line.startsWith("ELAPSED_WAKEUP ")) {
                addAlarm(br, item);
            } else if (line.startsWith("RTC ")) {
                addAlarm(br, item);
            } else if (line.startsWith("RTC_WAKEUP ")) {
                addAlarm(br, item);
            }
        }
    }

    private void addPackageStat(Module br, Node item) {
        AlarmStat stat = new AlarmStat();
        stat.anchor = new Anchor("a" + mNextAlarmAnchor++);
        stat.pkg = item.getLine();
        for (int i = 0; i < item.getChildCount(); i++) {
            Node child = item.getChild(i);
            if (i == 0) {
                // Note: either the first raw (old style) or the parent line (new style) contains this info
                String summary = child.getLine();
                boolean newStyle = stat.pkg.contains(" ");
                if (newStyle) {
                    int idx = stat.pkg.indexOf(' ');
                    summary = stat.pkg.substring(idx + 1);
                    stat.pkg = stat.pkg.substring(0, idx);
                }
                Pattern p = Pattern.compile("(.*) running, (.*) wakeups:?");
                Matcher m = p.matcher(summary);
                if (!m.matches()) {
                    br.printErr(4, "Cannot parse alarm stat: " + child.getLine());
                    return;
                }
                stat.runtime = Util.parseRelativeTimestamp(m.group(1));
                stat.wakeups = Long.parseLong(m.group(2));
                if (!newStyle) {
                    continue; // if old style, then first line already parsed
                }
            }

            //mod by john
            /*Pattern pOld = Pattern.compile("(.*) alarms: (.*)");
            Pattern pNew = Pattern.compile("(.*) ([0-9]+) wakes ([0-9]+) alarms: (.*)");
            Matcher m = pNew.matcher(child.getLine());
            AlarmAction action = new AlarmAction();
            if (m.matches()) {
                // New format detected
                action.time = Util.parseRelativeTimestamp(m.group(1));
                action.wakeCount = Long.parseLong(m.group(2));
                action.alarmCount = Long.parseLong(m.group(3));
                action.action = m.group(4);
            } else {
                m = pOld.matcher(child.getLine());
                if (!m.matches()) {
                    br.printErr(4, "Cannot parse alarm stat: " + child.getLine());
                    return;
                }
                // New format detected
                action.time = -1;
                action.wakeCount = -1;
                action.alarmCount = Long.parseLong(m.group(1));
                action.action = m.group(2);
            }*/
            String active = "*ACTIVE*";
            Pattern p_1 = Pattern.compile(".* (.*) ([0-9]+) wakes ([0-9]+) alarms, last (.*):*");
            Pattern p_2 = Pattern.compile("(.*) ([0-9]+) wakes ([0-9]+) alarms, last (.*):*");
            String buff = child.getLine();
            br.printErr(4, buff);
            Matcher m = buff.startsWith(active) ? p_1.matcher(buff) : p_2.matcher(buff);
            AlarmAction action = new AlarmAction();
            if (m.matches()) {
                action.time = Util.parseRelativeTimestamp(m.group(1));
                action.wakeCount = Long.parseLong(m.group(2));
                action.alarmCount = Long.parseLong(m.group(3));
            }
            //end
            stat.actions.add(action);
            stat.alarms += action.alarmCount;
        }
        mStats.add(stat);
    }

    private void addAlarm(Module br, Node item) {
        Alarm alarm = new Alarm();
        Pattern p = Pattern.compile("([A-Z_]+) #[0-9]+: Alarm\\{[a-f0-9]+ type [0-3] (.*)\\}");
        Matcher m = p.matcher(item.getLine());
        if (!m.matches()) {
            br.printErr(4, "Cannot parse alarm: " + item.getLine());
            return;
        }

        alarm.type = m.group(1);
        alarm.pkg = m.group(2);

        String props = item.getChild(0).getLine();
        p = Pattern.compile("type=(.?) .*when=([^ ]*)( window=.*)? repeatInterval=(.*) count=(.*)");
        m = p.matcher(props);
        if (!m.matches()) {
            br.printErr(4, "Cannot parse alarm properties: " + props);
            return;
        }
        alarm.whenS = m.group(2);
        alarm.when = readTs(alarm.whenS);
        alarm.repeat = Long.parseLong(m.group(4));
        alarm.count = Long.parseLong(m.group(5));

        String op = item.getChild(1).getLine();
        p = Pattern.compile("operation=PendingIntent\\{[0-9a-f]+: PendingIntentRecord\\{[0-9a-f]+ (.*) ([a-zA-Z]*)\\}\\}");
        m = p.matcher(op);
        if (!m.matches()) {
            br.printErr(4, "Cannot parse alarm operation: " + op);
            return;
        }
        alarm.opPkg = m.group(1);
        alarm.opMet = m.group(2);

        mAlarms.add(alarm);
    }

    private long readTs(String s) {
        s = Util.strip(s);
        long ret = 0;
        int idx;

        // skip over the negative and positive signs
        if (s.charAt(0) == '-') {
            s = s.substring(1);
        }
        if (s.charAt(0) == '+') {
            s = s.substring(1);
        }

        // Remove the "ms" from the end... it screws up our parsing
        if (s.endsWith("ms")) {
            s = s.substring(0, s.length() - 2);
        }

        // Ignore everything before the first digit
        while (s.length() > 0 && !Character.isDigit(s.charAt(0))) {
            s = s.substring(1);
        }

        // parse day
        idx = s.indexOf("d");
        if (idx >= 0) {
            int day = Integer.parseInt(s.substring(0, idx));
            s = s.substring(idx + 1);
            ret += day * (24 * 3600000L);
        }
        // parse hours
        idx = s.indexOf("h");
        if (idx >= 0) {
            int hour = Integer.parseInt(s.substring(0, idx));
            s = s.substring(idx + 1);
            ret += hour * 3600000L;
        }

        // parse minutes
        idx = s.indexOf("m");
        if (idx >= 0) {
            int min = Integer.parseInt(s.substring(0, idx));
            s = s.substring(idx + 1);
            ret += min * 60000L;
        }

        // parse seconds
        idx = s.indexOf("s");
        if (idx >= 0) {
            int sec = Integer.parseInt(s.substring(0, idx));
            s = s.substring(idx + 1);
            ret += sec * 1000L;
        }

        // parse millis
        int ms = Integer.parseInt(s);
        ret += ms;

        return ret;
    }

    @Override
    public void generate(Module rep) {
        if (!mLoaded) return;
        BugReportModule br = (BugReportModule) rep;

        // Generate the report
        Chapter mainCh = new Chapter(br.getContext(), "AlarmManager");
        br.addChapter(mainCh);

        genAlarmList(br, mainCh);
        genAlarmStat(br, mainCh);
        genAlarmStatDetailed(br, mainCh);
        genAlarmStatCombined(br, mainCh);
    }

    private Chapter genAlarmList(BugReportModule br, Chapter mainCh) {
        Chapter ch = new Chapter(br.getContext(), "Alarms");
        mainCh.addChapter(ch);

        new Para(ch).add("List of alarms (" + mAlarms.size() + "):");

        Table tg = new Table(Table.FLAG_SORT, ch);
        tg.setCSVOutput(br, "alarm_list");
        tg.setTableName(br, "alarm_list");
        tg.addColumn("Type", null, Table.FLAG_NONE, "type");
        tg.addColumn("Pkg", null, Table.FLAG_NONE, "pkg");
        tg.addColumn("When", null, Table.FLAG_ALIGN_RIGHT, "time varchar");
        //tg.addColumn("Time(ms)", null, Table.FLAG_ALIGN_RIGHT, "time_ms int");
        tg.addColumn("RepeatInterval(ms)", null, Table.FLAG_ALIGN_RIGHT, "interv_ms int");
        tg.addColumn("Count", null, Table.FLAG_ALIGN_RIGHT, "count int");
        tg.addColumn("OpPkg", null, Table.FLAG_NONE, "op_pkg varchar");
        tg.addColumn("OpMet", null, Table.FLAG_NONE, "op_met varchar");
        tg.begin();

        for (Alarm alarm : mAlarms) {
            tg.addData(alarm.type);
            tg.addData(alarm.pkg);
            tg.addData(alarm.whenS);
            //tg.addData(new ShadedValue(alarm.when));
            tg.addData(new ShadedValue(alarm.repeat));
            tg.addData(alarm.count);
            tg.addData(alarm.opPkg == null ? "" : alarm.opPkg);
            tg.addData(alarm.opMet == null ? "" : alarm.opMet);
        }
        tg.end();
        return ch;
    }

    private Chapter genAlarmStat(BugReportModule br, Chapter mainCh) {
        Chapter ch = new Chapter(br.getContext(), "Alarm stats");
        mainCh.addChapter(ch);
        Table tg = new Table(Table.FLAG_SORT, ch);
        tg.setCSVOutput(br, "alarm_stat");
        tg.setTableName(br, "alarm_stat");
        tg.addColumn("Pkg", null, Table.FLAG_NONE, "pkg");
        tg.addColumn("Runtime(ms)", null, Table.FLAG_ALIGN_RIGHT, "runtime_ms int");
        tg.addColumn("Wakeups", null, Table.FLAG_ALIGN_RIGHT, "wakeups int");
        tg.addColumn("Alarms", null, Table.FLAG_ALIGN_RIGHT, "alarms int");
        tg.begin();

        for (AlarmStat stat : mStats) {
            tg.addData(new Link(stat.anchor, stat.pkg));
            tg.addData(new ShadedValue(stat.runtime));
            tg.addData(new ShadedValue(stat.wakeups));
            tg.addData(new ShadedValue(stat.alarms));
        }
        tg.end();
        return ch;
    }

    private Chapter genAlarmStatDetailed(BugReportModule br, Chapter mainCh) {
        Chapter ch = new Chapter(br.getContext(), "Alarm detailed stats");
        mainCh.addChapter(ch);

        for (AlarmStat stat : mStats) {
            Chapter childCh = new Chapter(br.getContext(), stat.pkg);
            ch.addChapter(childCh);

            childCh.add(stat.anchor);
            Table tg = new Table(Table.FLAG_SORT, childCh);
            tg.setCSVOutput(br, "alarm_stat_detailed_" + stat.pkg);
            tg.addColumn("Runtime(ms)", Table.FLAG_ALIGN_RIGHT);
            tg.addColumn("Type", Table.FLAG_ALIGN_RIGHT);
            tg.addColumn("Wakes", Table.FLAG_ALIGN_RIGHT);
            tg.addColumn("Alarms", Table.FLAG_ALIGN_RIGHT);
            tg.addColumn("Last", Table.FLAG_ALIGN_RIGHT);
            tg.addColumn("Action", Table.FLAG_NONE);
            tg.begin();

            for (AlarmAction act : stat.actions) {
                if (act.time < 0) {
                    tg.addData("NA");
                } else {
                    tg.addData(new ShadedValue(act.time));
                }
                tg.addData(act.type);
                if (act.wakeCount < 0) {
                    tg.addData("NA");
                } else {
                    tg.addData(new ShadedValue(act.wakeCount));
                }
                tg.addData(new ShadedValue(act.alarmCount));
                tg.addData(act.last);
                tg.addData(act.action);
            }
            tg.end();
        }
        return ch;
    }

    private Chapter genAlarmStatCombined(BugReportModule br, Chapter mainCh) {
        Chapter ch = new Chapter(br.getContext(), "Alarm combined stats");
        mainCh.addChapter(ch);

        Table t = new Table(Table.FLAG_SORT, ch);
        t.setCSVOutput(br, "alarm_stat_combined");
        t.setTableName(br, "alarm_stat_combined");
        t.addColumn("Pkg", null, Table.FLAG_NONE, "pkg varchar");
        t.addColumn("Runtime(ms)", null, Table.FLAG_ALIGN_RIGHT, "time int");
        t.addColumn("Type", Table.FLAG_ALIGN_RIGHT, "type varchar");
        t.addColumn("Wakes", null, Table.FLAG_ALIGN_RIGHT, "wakes int");
        t.addColumn("Alarms", null, Table.FLAG_ALIGN_RIGHT, "alarms int");
        t.addColumn("Last", Table.FLAG_ALIGN_RIGHT, "last varchar");
        t.addColumn("Action", null, Table.FLAG_NONE, "action varchar");
        t.begin();

        for (AlarmStat stat : mStats) {
            for (AlarmAction act : stat.actions) {
                t.addData(stat.pkg);
                t.addData(new ShadedValue(act.time));
                t.addData(act.type);
                t.addData(new ShadedValue(act.wakeCount));
                t.addData(new ShadedValue(act.alarmCount));
                t.addData(act.last);
                t.addData(act.action);
            }
        }

        t.end();
        return ch;
    }

    class Alarm {
        public String type;
        public String pkg;
        public String whenS;
        public long when;
        public long repeat;
        public long count;
        public String opPkg;
        public String opMet;
    }

    class AlarmStat {
        public long runtime;
        public String pkg;
        public long wakeups;
        public long alarms;
        public Vector<AlarmAction> actions = new Vector<AlarmAction>();
        public Anchor anchor;
    }

    class AlarmAction {
        public String action;
        public long wakeCount;
        public long alarmCount;
        public long time;
        public String last; //add by john
        public String type; //add by john
    }
}
