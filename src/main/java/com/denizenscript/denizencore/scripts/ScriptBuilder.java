package com.denizenscript.denizencore.scripts;

import com.denizenscript.denizencore.scripts.containers.ScriptContainer;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.denizenscript.denizencore.objects.ArgumentHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ScriptBuilder {
    /**
     * Adds an object to a list of ScriptEntries. Can later be retrieved from the ScriptEntry
     * by using getObject(String key)
     *
     * @param scriptEntryList the list of ScriptEntries
     * @param key             the key (name) of the object being added
     * @param obj             the object
     * @return the List of ScriptEntries, with the object added in each member
     */
    public static List<ScriptEntry> addObjectToEntries(List<ScriptEntry> scriptEntryList, String key, Object obj) {
        for (ScriptEntry entry : scriptEntryList) {
            entry.addObject(key, obj);
            entry.trackObject(key);
        }
        return scriptEntryList;
    }

    /*
     * Builds ScriptEntry(ies) of items read from a script
     */

    public static List<ScriptEntry> buildScriptEntries(List<Object> contents, ScriptContainer parent, ScriptEntryData data) {
        List<ScriptEntry> scriptCommands = new ArrayList<>();

        if (contents == null || contents.isEmpty()) {
            if (Debug.showScriptBuilder) {
                Debug.echoError("Building script entries... no entries to build!");
            }
            return null;
        }

        if (Debug.showScriptBuilder) {
            Debug.echoDebug(parent, "Building script entries:");
        }

        for (Object ientry : contents) {

            if (ientry == null) {
                ientry = "null";
            }

            String entry;
            List<Object> inside;

            if (ientry instanceof Map) {
                Object key = ((Map) ientry).keySet().toArray()[0];
                entry = key.toString();
                inside = (List<Object>) ((Map) ientry).get(key);
            }
            else {
                entry = ientry.toString();
                inside = null;
            }


            String[] scriptEntry;
            String[] splitEntry = entry.split(" ", 2);

            if (splitEntry.length == 1) {
                scriptEntry = new String[2];
                scriptEntry[0] = entry;
                scriptEntry[1] = null;
            }
            else {
                scriptEntry = splitEntry;
            }

            try {
                /* Build new script commands */
                String[] args = ArgumentHelper.buildArgs(scriptEntry[1]);
                if (Debug.showScriptBuilder) {
                    Debug.echoDebug(parent, "Adding '" + scriptEntry[0] + "'  Args: " + Arrays.toString(args));
                }
                ScriptEntry newEntry = new ScriptEntry(scriptEntry[0], args, parent, inside);
                newEntry.internal.originalLine = entry;
                newEntry.entryData.transferDataFrom(data);
                scriptCommands.add(newEntry);
            }
            catch (Exception e) {
                Debug.echoError(e);
            }
        }

        return scriptCommands;
    }
}
