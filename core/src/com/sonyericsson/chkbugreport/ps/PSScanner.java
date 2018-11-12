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
package com.sonyericsson.chkbugreport.ps;

import com.sonyericsson.chkbugreport.BugReportModule;
import com.sonyericsson.chkbugreport.ProcessRecord;
import com.sonyericsson.chkbugreport.Section;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PSScanner {

    private BugReportModule mBr;
    private static final String HEADER_1 = "USER     PID   PPID  VSIZE  RSS    PCY  WCHAN    PC         NAME";
    private static final Pattern PATTERN_1 = Pattern.compile("([a-z0-9_]+) *([0-9]+) *([0-9]+) *([0-9]+) *([0-9]+) *(fg|bg|un)?  ([0-9-a-f]{8}) ([0-9-a-f]{8}) (.) (.*)");
    private static final String HEADER_2 = "USER     PID   PPID  VSIZE  RSS   PRIO  NICE  RTPRI SCHED  PCY  WCHAN    PC         NAME";
    private static final Pattern PATTERN_2 = Pattern.compile("([a-z0-9_]+) *([0-9]+) *([0-9]+) *([0-9]+) *([0-9]+) *([0-9-]+) *([0-9-]+) *([0-9-]+) *([0-9-]+) *(fg|bg|un)?  ([0-9-a-f]{8}) ([0-9-a-f]{8}) (.) (.*)");
    //add by john
    private static final String HEADER_3 = "LABEL                          USER           PID   TID  PPID     VSZ    RSS WCHAN            ADDR S PRI  NI RTPRIO SCH PCY CMD";
    private static final Pattern PATTERN_3 = Pattern.compile("(\\S+)\\s+(\\S+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\S+)\\s+([0-9a-f]+)\\s+((S|R|D|Z)?)\\s+(\\d+)\\s+([0-9-]+)\\s+([0-9-]+)\\s+(\\d)  (fg|bg|un|ta|  )?\\s+(.*)");
    private static final Pattern PATTERN_4 = Pattern.compile("(\\S+)\\s+(\\S+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\S+)\\s+([0-9a-f]+)\\s+((S|R|D|Z)?)\\s+(\\d+)\\s+([0-9-]+)\\s+([0-9-]+)\\s+(\\d) (fg|bg|un|ta|  )?\\s+(.*)");
    //end

    public PSScanner(BugReportModule br) {
        mBr = br;
    }

    public PSRecords run() {
        PSRecords ret = readPS(Section.PROCESSES_AND_THREADS);
        if (ret == null) {
            ret = readPS(Section.PROCESSES);
        }
        return ret;
    }

    private PSRecords readPS(String sectionName) {
        Section ps = mBr.findSection(sectionName);
        if (ps == null) {
            mBr.printErr(3, "Cannot find section: " + sectionName + " (ignoring it)");
            return null;
        }

        // Process the PS section
        PSRecords ret = new PSRecords();
        Pattern p = null;
        int lineIdx = 0, idxPid = -1, idxPPid = -1, idxPcy = -1, idxName = -1, idxNice = -1;
        //add by john
        int idxUser = -1, idxTid = -1, idxState = -1;
        //end
        for (int tries = 0; tries < 10 && lineIdx < ps.getLineCount(); tries++) {
            String buff = ps.getLine(lineIdx++);
            if (buff.equals(HEADER_1)) {
                p = PATTERN_1;
                idxUser = 1; //add by john
                idxPid = 2;
                idxPPid = 3;
                idxPcy = 6;
                idxName = 10;
                break;
            }
            if (buff.equals(HEADER_2)) {
                p = PATTERN_2;
                idxUser = 1; //add by john
                idxPid = 2;
                idxPPid = 3;
                idxNice = 7;
                idxPcy = 10;
                idxName = 14;
                break;
            }
            //add by john
            if (buff.startsWith(HEADER_3)) {
                p = PATTERN_3;
                idxUser = 2;
                idxPid = 3;
                idxTid = 4;
                idxPPid = 5;
                idxState = 11;
                idxNice = 13;
                idxPcy = 16;
                idxName = 17;
                break;
            }
            //end
        }
        if (p == null) {
            mBr.printErr(4, "Could not find header in ps output");
            return null;
        }

        // Now read and process every line
        int pidZygote = -1;
        int cnt = ps.getLineCount();
        for (int i = lineIdx; i < cnt; i++) {
            String buff = ps.getLine(i);
            if (buff.startsWith("[")) break;
            Matcher m = p.matcher(buff);
            if (!m.matches()) {
                //mod by john
                m = PATTERN_4.matcher(buff);
                if (!m.matches()) {
                    mBr.printErr(4, "Error parsing line: " + buff);
                    continue;
                }
                //end
            }

            //add by john
            String user = "";
            if (idxUser >= 0) {
                user = m.group(idxUser);
            }

            int tid = -1;
            if (idxTid >= 0) {
                String sTid = m.group(idxTid);
                try {
                    tid = Integer.parseInt(sTid);
                } catch (NumberFormatException nfe) {
                    mBr.printErr(4, "Error parsing tid from: " + sTid);
                    break;
                }
            }

            String state = "-";
            if (idxState >= 0) {
                state = m.group(idxState);
            }
            //end

            int pid = -1;
            if (idxPid >= 0) {
                String sPid = m.group(idxPid);
                try {
                    pid = Integer.parseInt(sPid);
                } catch (NumberFormatException nfe) {
                    mBr.printErr(4, "Error parsing pid from: " + sPid);
                    break;
                }
            }

            // Extract ppid
            int ppid = -1;
            if (idxPPid >= 0) {
                String sPid = m.group(idxPPid);
                try {
                    ppid = Integer.parseInt(sPid);
                } catch (NumberFormatException nfe) {
                    mBr.printErr(4, "Error parsing ppid from: " + sPid);
                    break;
                }
            }

            // Extract nice
            int nice = PSRecord.NICE_UNKNOWN;
            if (idxNice >= 0) {
                String sNice = m.group(idxNice);
                try {
                    nice = Integer.parseInt(sNice);
                } catch (NumberFormatException nfe) {
                    mBr.printErr(4, "Error parsing nice from: " + sNice);
                    break;
                }
            }

            // Extract scheduler policy
            int pcy = PSRecord.PCY_UNKNOWN;
            if (idxPcy >= 0) {
                String sPcy = m.group(idxPcy);
                if ("fg".equals(sPcy)) {
                    pcy = PSRecord.PCY_NORMAL;
                } else if ("bg".equals(sPcy)) {
                    pcy = PSRecord.PCY_BATCH;
                } else if ("un".equals(sPcy)) {
                    pcy = PSRecord.PCY_FIFO;
                } else {
                    pcy = PSRecord.PCY_OTHER;
                }
            }

            // Exctract name
            String name = "";
            if (idxName >= 0) {
                name = m.group(idxName);
            }

            // Fix the name
            //mod by john
            if (p == PATTERN_3 && pid != tid) {
                continue; //There need to find and record the main thread info for every process.
            }
            ret.put(pid, new PSRecord(pid, ppid, nice, pcy, name, user, state));
            //end

            // Check if we should create a ProcessRecord for this
            if (pidZygote == -1 && name.equals("zygote")) {
                pidZygote = pid;
            }
            ProcessRecord pr = mBr.getProcessRecord(pid, true, false);
            pr.suggestName(name, 10);
        }

        // Build tree structure as well
        for (PSRecord psr : ret) {
            int ppid = psr.mPPid;
            PSRecord parent = ret.getPSRecord(ppid);
            if (parent == null) {
                parent = ret.getPSTree();
            }
            parent.mChildren.add(psr);
            psr.mParent = parent;
        }

        return ret;
    }

}
