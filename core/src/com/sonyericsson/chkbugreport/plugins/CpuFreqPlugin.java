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
import com.sonyericsson.chkbugreport.doc.Chapter;
import com.sonyericsson.chkbugreport.doc.DocNode;
import com.sonyericsson.chkbugreport.doc.Table;
import com.sonyericsson.chkbugreport.doc.PreText;



public class CpuFreqPlugin extends Plugin {

    private static final String TAG = "[CpuFreqPlugin]";
    private boolean mLoaded;
    private int mFreqCount;
    private long mTotalTime;
    private long[] mFreqs;
    private long[] mTimes;

    @Override
    public int getPrio() {
        return 85;
    }

    @Override
    public void reset() {
        mLoaded = false;
    }

    @Override
    public void load(Module mod) {
        Section sec = mod.findSection(Section.KERNEL_CPUFREQ);
        if (sec == null) {
            mod.printErr(3, TAG + "Section not found: " + Section.KERNEL_CPUFREQ + " (aborting plugin)");
            return;
        }

        // Find the battery history
        mFreqCount = sec.getLineCount();
        mFreqs = new long[mFreqCount];
        mTimes = new long[mFreqCount];
        mTotalTime = 0l;
        for (int i = 0; i < mFreqCount; i++) {
            String buff[] = sec.getLine(i).split(" ");
            if (buff.length != 2) {
                mFreqCount = i;
                break;
            }
            mFreqs[i] = Long.parseLong(buff[0]);
            mTimes[i] = Long.parseLong(buff[1]);
            mTotalTime += mTimes[i];
        }
        mLoaded = true;
    }

    @Override
    public void generate(Module mod) {
        if (!mLoaded) return;

        // Create the chapter
        Chapter ch = mod.findOrCreateChapter("CPU/Frequencies");
        Table t = new Table();
        ch.add(t);
        t.setCSVOutput(mod, "cpufreq");
        t.setTableName(mod, "cpufreq");
        t.addColumn("Frequency (MHz)", Table.FLAG_ALIGN_RIGHT, "freq int");
        t.addColumn("Times", Table.FLAG_ALIGN_RIGHT, "time_sec int");
        t.addColumn("Time (%)", Table.FLAG_ALIGN_RIGHT, "time_p int");
        t.begin();
        for (int i = 0; i < mFreqCount; i++) {
            float perc = mTotalTime == 0 ? 0f : (mTimes[i] * 100 / mTotalTime);
            t.addData(mFreqs[i] / 1000);
            t.addData(mTimes[i]);
            t.addData(String.format("%.1f%%", perc));
        }
        t.end();

        //add by john
        Chapter ch_cpuinfo = mod.findOrCreateChapter("CPU/Usage ");

        // Handle the cpu info section
        Section sec = mod.findSection(Section.DUMP_OF_CPUINFO);
        if (sec == null) {
            mod.printErr(3, TAG + "Section not found: " + Section.DUMP_OF_CPUINFO + " (ignoring section)");
        } else {
            generateCpuInfoSec(mod, ch_cpuinfo, sec);
        }


        Chapter ch_topinfo = mod.findOrCreateChapter("CPU/Snapshoot");

        // Handle the cpu info section
        sec = mod.findSection(Section.CPU_TOP_INFO);
        if (sec == null) {
            mod.printErr(3, TAG + "Section not found: " + Section.CPU_TOP_INFO + " (ignoring section)");
        } else {
            generateCpuInfoSec(mod, ch_topinfo, sec);
        }
        //end
    }

    private void generateCpuInfoSec(Module mod, Chapter ch, Section sec) {
        // Parse the values
        int cnt = sec.getLineCount();
        DocNode pt = new PreText();
        ch.add(pt);
        for (int i = 0; i < cnt; i++) {
            String line = sec.getLine(i);
            pt.addln(line);
        }
    }

}
