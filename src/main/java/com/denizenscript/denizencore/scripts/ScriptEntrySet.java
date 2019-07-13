package com.denizenscript.denizencore.scripts;

import com.denizenscript.denizencore.utilities.debugging.Debug;

import java.util.ArrayList;
import java.util.List;

public class ScriptEntrySet {

    public List<ScriptEntry> entries;

    public ScriptEntrySet(List<ScriptEntry> baseEntries) {
        entries = baseEntries;
    }

    public ScriptEntrySet duplicate() {
        List<ScriptEntry> newEntries = new ArrayList<>(entries.size());
        try {
            for (ScriptEntry entry : entries) {
                newEntries.add(entry.clone());
            }
        }
        catch (CloneNotSupportedException e) {
            Debug.echoError(e); // This should never happen
        }
        return new ScriptEntrySet(newEntries);
    }
}
