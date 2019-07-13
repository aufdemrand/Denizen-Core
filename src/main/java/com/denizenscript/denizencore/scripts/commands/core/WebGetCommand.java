package com.denizenscript.denizencore.scripts.commands.core;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.*;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.scripts.commands.Holdable;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.denizenscript.denizencore.utilities.scheduling.Schedulable;
import com.denizenscript.denizencore.DenizenCore;
import com.denizenscript.denizencore.scripts.ScriptEntry;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class WebGetCommand extends AbstractCommand implements Holdable {

    // <--[command]
    // @Name Webget
    // @Syntax webget [<url>] (post:<data>) (headers:<header>/<value>|...) (timeout:<duration>/{10s}) (savefile:<path>)
    // @Required 1
    // @Short Gets the contents of a web page.
    // @Group core
    //
    // @Description
    // TODO: Document Command Details
    // Note that while this replace URL spaces to %20, you are responsible for any other necessary URL encoding. You may want to use the element.url_encode tag for this.
    // Optionally, specify a set of data to post to the server (changes the message from GET to POST).
    // Optionally specify a list of headers as list of key/value pairs separated by slashes.
    // Optionally specify a path to save the gotten file to. This will remove the 'result' entry savedata. Path is relative to server base directory.
    //
    // @Tags
    // <entry[saveName].failed> returns whether the webget failed.
    // <entry[saveName].result> returns the result of the webget, if it did not fail.
    // <el@element.url_encode>
    //
    // @Usage
    // Use to download the google home page.
    // - ~webget "http://google.com" save:google
    // - narrate "<entry[google].result>"
    //
    // -->

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsException {

        for (Argument arg : ArgumentHelper.interpretArguments(scriptEntry.aHArgs)) {

            if (!scriptEntry.hasObject("url")) {
                scriptEntry.addObject("url", new Element(arg.raw_value));
            }

            else if (!scriptEntry.hasObject("post")
                    && arg.matchesOnePrefix("post")) {
                scriptEntry.addObject("post", arg.asElement());
            }

            else if (!scriptEntry.hasObject("timeout")
                    && arg.matchesPrefix("timeout", "t")
                    && arg.matchesArgumentType(Duration.class)) {
                scriptEntry.addObject("timeout", arg.asType(Duration.class));
            }

            else if (!scriptEntry.hasObject("headers")
                    && arg.matchesPrefix("headers")) {
                scriptEntry.addObject("headers", arg.asType(dList.class));
            }

            else if (!scriptEntry.hasObject("savefile")
                    && arg.matchesPrefix("savefile")) {
                scriptEntry.addObject("savefile", arg.asElement());
            }

            else {
                arg.reportUnhandled();
            }
        }

        if (!scriptEntry.hasObject("url")) {
            throw new InvalidArgumentsException("Must have a valid URL!");
        }

        Element url = scriptEntry.getElement("url");
        if (!url.asString().startsWith("http://") && !url.asString().startsWith("https://")) {
            throw new InvalidArgumentsException("Must have a valid (HTTP/HTTPS) URL! Attempted: " + url.asString());
        }

        scriptEntry.defaultObject("timeout", new Duration(10));

    }


    @Override
    public void execute(final ScriptEntry scriptEntry) {

        if (!DenizenCore.getImplementation().allowedToWebget()) {
            Debug.echoError(scriptEntry.getResidingQueue(), "WebGet disabled by config!");
            return;
        }

        final Element url = scriptEntry.getElement("url");
        final Element postData = scriptEntry.getElement("post");
        final Duration timeout = scriptEntry.getdObject("timeout");
        final dList headers = scriptEntry.getdObject("headers");
        final Element saveFile = scriptEntry.getElement("savefile");

        if (scriptEntry.dbCallShouldDebug()) {
            Debug.report(scriptEntry, getName(), url.debug()
                            + (postData != null ? postData.debug() : "")
                            + (timeout != null ? timeout.debug() : "")
                            + (saveFile != null ? saveFile.debug() : "")
                            + (headers != null ? headers.debug() : ""));
        }

        Thread thr = new Thread(new Runnable() {
            @Override
            public void run() {
                webGet(scriptEntry, postData, url, timeout, headers, saveFile);
            }
        });
        thr.start();
    }

    public void webGet(final ScriptEntry scriptEntry, final Element postData, Element urlp, Duration timeout, dList headers, Element saveFile) {

        BufferedReader buffIn = null;
        try {
            URL url = new URL(urlp.asString().replace(" ", "%20"));
            final HttpURLConnection uc = (HttpURLConnection) url.openConnection();
            uc.setDoInput(true);
            uc.setDoOutput(true);
            if (postData != null) {
                uc.setRequestMethod("POST");
            }
            if (headers != null) {
                for (String str : headers) {
                    int ind = str.indexOf('/');
                    if (ind > 0) {
                        uc.setRequestProperty(str.substring(0, ind), str.substring(ind + 1));
                    }
                }
            }
            uc.setConnectTimeout((int) timeout.getMillis());
            uc.connect();
            if (postData != null) {
                uc.getOutputStream().write(postData.asString().getBytes("UTF-8"));
            }
            final StringBuilder sb = new StringBuilder();
            if (saveFile != null) {
                File file = new File(saveFile.asString());
                if (!DenizenCore.getImplementation().canWriteToFile(file)) {
                    Debug.echoError("Cannot write to that file, as dangerous file paths have been disabled in the Denizen config.");
                }
                else {
                    InputStream in = uc.getInputStream();
                    FileOutputStream fout = new FileOutputStream(file);
                    byte[] buffer = new byte[8 * 1024];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        fout.write(buffer, 0, len);
                    }
                    fout.flush();
                    fout.close();
                }
            }
            else {
                buffIn = new BufferedReader(new InputStreamReader(uc.getInputStream()));
                // Probably a better way to do this bit.
                while (true) {
                    try {
                        String temp = buffIn.readLine();
                        if (temp == null) {
                            break;
                        }
                        sb.append(temp).append("\n");
                    }
                    catch (Exception ex) {
                        break;
                    }
                }
                buffIn.close();
                buffIn = null;
            }
            DenizenCore.schedule(new Schedulable() {
                @Override
                public boolean tick(float seconds) {
                    try {
                        scriptEntry.addObject("failed", new Element(uc.getResponseCode() == 200 ? "false" : "true"));
                    }
                    catch (Exception e) {
                        Debug.echoError(e);
                    }
                    if (saveFile == null) {
                        scriptEntry.addObject("result", new Element(sb.toString()));
                    }
                    scriptEntry.setFinished(true);
                    return false;
                }
            });
        }
        catch (Exception e) {
            Debug.echoError(e);
            try {
                DenizenCore.schedule(new Schedulable() {
                    @Override
                    public boolean tick(float seconds) {
                        scriptEntry.addObject("failed", new Element("true"));
                        scriptEntry.setFinished(true);
                        return false;
                    }
                });
            }
            catch (Exception e2) {
                Debug.echoError(e2);
            }
        }
        finally {
            try {
                if (buffIn != null) {
                    buffIn.close();
                }
            }
            catch (Exception e) {
                Debug.echoError(e);
            }
        }
    }
}
